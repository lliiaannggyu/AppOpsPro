package com.tengwear.appopspro.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
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
import com.tengwear.appopspro.AppOpsRepository
import com.tengwear.appopspro.model.AppOpsCatalog
import com.tengwear.appopspro.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 按权限批量授予页（工具页入口）。
 *
 * 两阶段：
 *   1) 选一条权限（op）—— 从 [AppOpsCatalog.ALL] 里按名称 / 权限串搜索；
 *   2) 列出「该权限能授予的应用」，多选后一次性 `setMode(allow)`。
 *
 * 「能授予」的判定与单应用授权页完全一致（复用 [AppOpsRepository.canGrantToApp]，
 * 即 [AppOpsRepository.resolveWritable] 的可写性规则）：非权限类 op 始终可写、
 * 应用声明了对应权限可写、运行时权限托底未声明也可写、特殊访问权限未声明则不可写。
 * 列表只展示可授予的应用，避免把必然失败的应用放进多选误导用户。
 *
 * 授予逻辑走 [AppOpsRepository.batchGrantOpToApps]：逐包写入 + 回读校验，
 * 结果以系统真实存储值为准（与 [com.tengwear.appopspro.ui.PermissionScreen] 一致）。
 */
@Composable
fun BatchGrantScreen(
    apps: List<InstalledApp>,
    isShizukuReady: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 阶段：0 = 选权限，1 = 选应用
    var stage by remember { mutableStateOf(0) }
    var selectedOp by remember { mutableStateOf<AppOpsCatalog.Op?>(null) }

    var opQuery by remember { mutableStateOf("") }
    var appQuery by remember { mutableStateOf("") }

    // 已勾选应用的包名集合（应用全集可能上百，用一个 Set 标记即可）
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 阶段 1 的可授予列表（进入时算一次，避免每次按键重算 100+ 次 PM 查询）
    var grantableApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var computing by remember { mutableStateOf(false) }

    var isGranting by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    // 进入阶段 1 时，一次性算出可授予列表（IO 线程，不卡 UI）
    LaunchedEffect(selectedOp) {
        val op = selectedOp
        if (op == null) {
            grantableApps = emptyList()
            return@LaunchedEffect
        }
        computing = true
        val result = withContext(Dispatchers.IO) {
            AppOpsRepository.appsGrantableForOp(op, apps)
        }
        grantableApps = result
        computing = false
        // 重选权限时清空勾选，避免上一轮选择串到本轮
        selectedPackages = emptySet()
        appQuery = ""
    }

    // 派生：权限搜索（非挂起，纯文本过滤，开销极小）
    val matchedOps: List<AppOpsCatalog.Op> = run {
        val q = opQuery.trim()
        if (q.isEmpty()) AppOpsCatalog.ALL
        else AppOpsCatalog.ALL.filter {
            it.label.contains(q, ignoreCase = true) ||
                it.opName.contains(q, ignoreCase = true) ||
                it.name.contains(q, ignoreCase = true) ||
                (it.permission?.contains(q, ignoreCase = true) ?: false)
        }
    }

    // 派生：应用搜索（在已算好的可授予列表上过滤，开销极小）
    val matchedApps: List<InstalledApp> = run {
        val q = appQuery.trim()
        val src = if (q.isEmpty()) grantableApps else grantableApps.filter {
            it.appLabel.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
        src.take(MAX_BATCH_APP_RESULTS)
    }

    fun doGrant() {
        val op = selectedOp ?: return
        val targets = grantableApps.filter { it.packageName in selectedPackages }
        if (targets.isEmpty() || isGranting) return
        isGranting = true
        lastResult = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                AppOpsRepository.batchGrantOpToApps(op, targets)
            }
            isGranting = false
            // 授予后清空勾选，方便下一轮；成功数同步到提示
            selectedPackages = emptySet()
            lastResult = "已处理：${r.total} 个应用 · 成功 ${r.success} · " +
                "失败 ${r.failed} · 跳过 ${r.skipped}"
            Toast.makeText(
                context,
                "成功 ${r.success} · 失败 ${r.failed} · 跳过 ${r.skipped}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
            if (stage == 0 || selectedOp == null) {
                // ── 阶段 1：选权限 ────────────────────────
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text("选择权限（${AppOpsCatalog.ALL.size}）")
                    }
                }

                item {
                    SearchField(
                        query = opQuery,
                        onQueryChange = { opQuery = it },
                        onClear = { opQuery = "" },
                        placeholder = "搜索权限名 / 权限串",
                        widthFraction = 1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec)
                    )
                }

                if (!isShizukuReady) {
                    item {
                        HintItem(
                            text = "Shizuku 未就绪，无法写入权限",
                            error = true,
                            transformationSpec = transformationSpec
                        )
                    }
                }
                if (matchedOps.isEmpty()) {
                    item {
                        HintItem(
                            text = "没有匹配的权限",
                            error = false,
                            transformationSpec = transformationSpec
                        )
                    }
                } else {
                    items(matchedOps, key = { it.opName }) { op ->
                        OpPickItem(
                            op = op,
                            transformationSpec = transformationSpec,
                            onClick = {
                                selectedOp = op
                                opQuery = ""
                                stage = 1
                            }
                        )
                    }
                }
            } else {
                // ── 阶段 2：选应用并批量授予 ───────────────
                val op = selectedOp!!
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    ) {
                        Text("选择应用（可授予 ${grantableApps.size}）")
                    }
                }

                item {
                    // 点顶部行退回重选权限（清空本轮选择）
                    BarCapsule(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        secondaryContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        onClick = {
                            stage = 0
                            selectedOp = null
                            selectedPackages = emptySet()
                        },
                        leading = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = op.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = "点此重新选择权限",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }

                if (!isShizukuReady) {
                    item {
                        HintItem(
                            text = "Shizuku 未就绪，无法授予",
                            error = true,
                            transformationSpec = transformationSpec
                        )
                    }
                }

                // 全选 / 取消全选
                item {
                    val allSelected = grantableApps.isNotEmpty() &&
                        grantableApps.all { it.packageName in selectedPackages }
                    BatchToggleItem(
                        text = if (allSelected) "取消全选" else "全选可授予应用",
                        selected = allSelected,
                        enabled = grantableApps.isNotEmpty() && !computing,
                        transformationSpec = transformationSpec,
                        onClick = {
                            selectedPackages = if (allSelected) {
                                emptySet()
                            } else {
                                grantableApps.map { it.packageName }.toSet()
                            }
                        }
                    )
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

                if (computing) {
                    item {
                        HintItem(
                            text = "正在计算可授予的应用…",
                            error = false,
                            transformationSpec = transformationSpec
                        )
                    }
                } else if (grantableApps.isEmpty()) {
                    item {
                        HintItem(
                            text = "没有可授予「${op.label}」的应用\n（该权限需要应用自行声明，或由运行时权限托底）",
                            error = false,
                            transformationSpec = transformationSpec
                        )
                    }
                } else if (matchedApps.isEmpty()) {
                    item {
                        HintItem(
                            text = "没有匹配的应用",
                            error = false,
                            transformationSpec = transformationSpec
                        )
                    }
                } else {
                    items(matchedApps, key = { it.packageName }) { app ->
                        val checked = app.packageName in selectedPackages
                        AppCheckItem(
                            app = app,
                            checked = checked,
                            transformationSpec = transformationSpec,
                            onClick = {
                                selectedPackages = if (checked) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                            }
                        )
                    }
                }

                // 批量授予动作（已选 > 0 才可点）
                item {
                    val selectedCount = selectedPackages.size
                    BatchGrantActionItem(
                        count = selectedCount,
                        granting = isGranting,
                        enabled = selectedCount > 0 && isShizukuReady && !computing,
                        transformationSpec = transformationSpec,
                        onClick = { doGrant() }
                    )
                }

                lastResult?.let { msg ->
                    item {
                        HintItem(
                            text = msg,
                            error = false,
                            transformationSpec = transformationSpec
                        )
                    }
                }
            }
        }
    }
}

/** 权限选择列表的一行。 */
@Composable
private fun TransformingLazyColumnItemScope.OpPickItem(
    op: AppOpsCatalog.Op,
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
        leading = {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = scheme.onSurfaceVariant
            )
        },
        label = {
            Text(
                text = op.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = op.permission ?: op.opName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 应用勾选行：选中态用主色容器 + 对勾。 */
@Composable
private fun TransformingLazyColumnItemScope.AppCheckItem(
    app: InstalledApp,
    checked: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (checked) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (checked) scheme.onPrimaryContainer else scheme.onSurface,
        secondaryContentColor = if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
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
        },
        trailing = {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选",
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

/** 全选 / 取消全选开关行。 */
@Composable
private fun TransformingLazyColumnItemScope.BatchToggleItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (selected) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onTertiaryContainer else scheme.onSurface,
        secondaryContentColor = if (selected) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (enabled) onClick else null,
        leading = {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) scheme.onTertiaryContainer else scheme.onSurfaceVariant
            )
        },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 批量授予动作行（底部）。 */
@Composable
private fun TransformingLazyColumnItemScope.BatchGrantActionItem(
    count: Int,
    granting: Boolean,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (enabled) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        secondaryContentColor = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (enabled) onClick else null,
        label = {
            Text(
                text = if (granting) "授予中…" else "批量授予（已选 $count）",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = if (count == 0) {
            {
                Text(
                    text = "先勾选要授予的应用",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null
    )
}

/** 提示行（error=红，普通=灰）。 */
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

/** 批量授予页应用列表最多展示的候选数（手表屏小，避免列表过长）。 */
private const val MAX_BATCH_APP_RESULTS = 200
