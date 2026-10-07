package com.tengwear.appopspro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.appopspro.AppStats
import com.tengwear.appopspro.AppStatsRepository
import com.tengwear.appopspro.model.InstalledApp
import kotlinx.coroutines.delay

/** 应用选择器里最多列出的候选数（手表屏小 + 列表可能上百条）。 */
private const val MAX_APP_RESULTS = 30

/**
 * 单应用实时性能 / 资源监控页（工具页的独立入口）。
 *
 * 本页**自带应用选择器**（与日志页同款）：未选应用时先列应用让用户挑，选定后才进入监控；
 * 点顶部已选应用行可退回重选。
 *
 * 指标（见 [AppStatsRepository] 注释）：CPU% / 运存(PSS) / 存储 / 电量(全局) / 单应用累计耗电。
 * ⚠️ **未做 GPU**：Android 不暴露单应用 GPU 占用，全局占用也仅部分机型有，实用性低。
 *
 * - 选定应用后自动开始实时刷新（每约 1.5s 拉一次全量快照）；可用「开始/停止」按钮暂停。
 * - 应用未运行（无 PID）时 CPU / 运存标「未运行」；存储若 shell 无权限读 /data/data 会标「读不到」。
 * - 耗电是**自上次充电的累计估算值**，非实时瞬时功率（Android 无此接口）。
 */
@Composable
fun AppStatsScreen(
    apps: List<InstalledApp>,
    isShizukuReady: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var targetApp by remember { mutableStateOf<InstalledApp?>(null) }
    var appQuery by remember { mutableStateOf("") }

    var stats by remember { mutableStateOf(AppStats()) }
    var running by remember { mutableStateOf(false) }
    var appRunning by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    // 派生候选：不要 remember 包（apps 是 SnapshotStateList，remember 会缓存旧列表）
    val matchedApps: List<InstalledApp> = run {
        val q = appQuery.trim()
        val src = if (q.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.appLabel.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
        src.take(MAX_APP_RESULTS)
    }

    // 实时刷新循环：选定应用且 running 时持续拉快照
    LaunchedEffect(running, targetApp) {
        val app = targetApp ?: return@LaunchedEffect
        if (!running) return@LaunchedEffect
        while (true) {
            if (isShizukuReady) {
                runCatching { AppStatsRepository.snapshot(app.packageName) }
                    .onSuccess { s ->
                        stats = s
                        appRunning = s.cpuPercent != null || s.pssKb != null
                        error = null
                    }
                    .onFailure { e -> error = "采集失败：${e.message}" }
            } else {
                error = "Shizuku 未就绪"
            }
            delay(1500L)
        }
    }

    // 选定应用后自动开始（仅 Shizuku 就绪时）
    LaunchedEffect(targetApp) {
        if (targetApp != null && isShizukuReady) running = true
    }

    // 离开页面确保停止循环
    DisposableEffect(Unit) { onDispose { running = false } }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onBack, buttonSize = EdgeButtonSize.Small) {
                Text("返回")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (targetApp == null) {
                // ── 未选应用：列应用让用户挑 ──────────────
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text("选择应用")
                    }
                }

                item {
                    SearchField(
                        query = appQuery,
                        onQueryChange = { appQuery = it },
                        onClear = { appQuery = "" },
                        placeholder = "搜索应用名 / 包名",
                        widthFraction = 1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }

                if (matchedApps.isEmpty()) {
                    item {
                        HintItem(
                            text = if (apps.isEmpty()) "应用列表为空（Shizuku 未就绪？）" else "没有匹配的应用",
                            error = false,
                            transformationSpec = transformationSpec
                        )
                    }
                } else {
                    itemsIndexed(matchedApps) { _, app ->
                        StatAppPickItem(
                            app = app,
                            transformationSpec = transformationSpec,
                            onClick = {
                                targetApp = app
                                appQuery = ""
                            }
                        )
                    }
                }
            } else {
                // ── 已选应用：监控视图 ────────────────────
                val app = targetApp!!
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text("性能监控 · ${app.appLabel}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                item {
                    // 点顶部行退回重选
                    BarCapsule(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        secondaryContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onClick = { targetApp = null },
                        leading = { AppIcon(icon = app.icon, contentDescription = app.appLabel) },
                        label = {
                            Text(
                                text = app.appLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = "点此重新选择应用",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }

                item {
                    HintItem(
                        text = if (!appRunning) "应用未运行：CPU / 运存无数据，其余指标仍可看"
                        else "实时采集中…",
                        error = false,
                        transformationSpec = transformationSpec
                    )
                }

                item {
                    StatRow(
                        glyph = "⚙",
                        label = "CPU 占用",
                        value = stats.cpuPercent?.let { "%.1f%%".format(it) } ?: "未运行",
                        note = "按单核口径，实时采样",
                        transformationSpec = transformationSpec
                    )
                }

                item {
                    StatRow(
                        glyph = "▤",
                        label = "运存 (PSS)",
                        value = stats.pssKb?.let { formatKb(it) } ?: "未运行",
                        note = "dumpsys meminfo",
                        transformationSpec = transformationSpec
                    )
                }

                item {
                    StatRow(
                        glyph = "▦",
                        label = "存储占用",
                        value = stats.storageKb?.let { formatKb(it) } ?: "读不到",
                        note = "shell 无权限时读不到",
                        transformationSpec = transformationSpec
                    )
                }

                item {
                    StatRow(
                        glyph = "▮",
                        label = "电量 (全局)",
                        value = stats.batteryPercent?.let { "%d%%".format(it) } ?: "—",
                        note = "设备剩余电量",
                        transformationSpec = transformationSpec
                    )
                }

                item {
                    StatRow(
                        glyph = "⚡",
                        label = "累计耗电",
                        value = stats.powerMah?.let { "%.1f mAh".format(it) } ?: "不可用",
                        note = "自上次充电累计估算",
                        transformationSpec = transformationSpec
                    )
                }

                item {
                    StatToggleItem(
                        running = running,
                        enabled = isShizukuReady,
                        transformationSpec = transformationSpec,
                        onClick = {
                            running = !running
                            context.toast(if (running) "已开始实时刷新" else "已停止")
                        }
                    )
                }

                error?.let { msg ->
                    item {
                        HintItem(
                            text = msg,
                            error = true,
                            transformationSpec = transformationSpec
                        )
                    }
                }
            }
        }
    }
}

/** KB 转友好单位：≥1GB 用 GB，否则 MB。 */
private fun formatKb(kb: Long): String = when {
    kb >= 1024 * 1024 -> "%.2f GB".format(kb / (1024.0 * 1024.0))
    else -> "%.1f MB".format(kb / 1024.0)
}

/** 应用选择列表的一行。 */
@Composable
private fun TransformingLazyColumnItemScope.StatAppPickItem(
    app: InstalledApp,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = { AppIcon(icon = app.icon, contentDescription = app.appLabel) },
        label = {
            Text(
                text = app.appLabel,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 单项指标卡片（复用 BarCapsule 外观，与列表行一致）。 */
@Composable
private fun TransformingLazyColumnItemScope.StatRow(
    glyph: String,
    label: String,
    value: String,
    note: String?,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = null,
        leading = {
            Text(
                text = glyph,
                style = mono,
                color = scheme.primary,
                maxLines = 1
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = note?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailing = {
            Text(
                text = value,
                style = mono,
                color = scheme.primary,
                maxLines = 1
            )
        }
    )
}

/** 开始 / 停止实时刷新的动作项。 */
@Composable
private fun TransformingLazyColumnItemScope.StatToggleItem(
    running: Boolean,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (running) scheme.errorContainer else scheme.tertiaryContainer,
        contentColor = if (running) scheme.onErrorContainer else scheme.onTertiaryContainer,
        secondaryContentColor = if (running) scheme.onErrorContainer else scheme.onTertiaryContainer,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (enabled) onClick else null,
        leading = {
            Icon(
                imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = if (running) "停止实时刷新" else "开始实时刷新",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = if (running) "每约 1.5s 刷新" else "点开始后实时刷新",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 提示行（错误=红，普通=灰）。 */
@Composable
private fun TransformingLazyColumnItemScope.HintItem(
    text: String,
    error: Boolean,
    transformationSpec: TransformationSpec
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .transformedHeight(this, transformationSpec),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
