package com.tengwear.appopspro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tengwear.appopspro.LogCaptureService
import com.tengwear.appopspro.LogCaptureState
import com.tengwear.appopspro.LogFilterHistoryStore
import com.tengwear.appopspro.LogLevel
import com.tengwear.appopspro.model.InstalledApp

/** 应用选择器里最多列出的候选数（手表屏幕小 + 列表可能上百条）。 */
private const val MAX_APP_RESULTS = 30

/**
 * 日志抓取页（工具页第四项）——**实时流式抓取**。
 *
 * 通过 [LogcatRepository] 以 shell 身份跑 `logcat`（不带 `-d`），进程常驻输出，
 * 服务逐行收、按批写进 [LogCaptureState]，界面实时刷新；点「停止抓取」才结束。
 *
 * 过滤器：
 * - **级别**：全部 / D / I / W / E（logcat 原生 `*:P`）
 * - **关键字**：大小写不敏感匹配整行；旁边的**漏斗可点，展开过滤词历史**
 *
 * 状态全在 [LogCaptureState]（全局快照状态），离开页面回来结果仍在。
 */
@Composable
fun LogScreen(
    apps: List<InstalledApp>,
    isShizukuReady: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var level by remember { mutableStateOf(LogLevel.All) }
    var keyword by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(LogFilterHistoryStore.load()) }
    /** 目标应用；null = 全部应用（抓全局日志）。 */
    var targetApp by remember { mutableStateOf<InstalledApp?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    /** 过滤器说明展开态。 */
    var showHelp by remember { mutableStateOf(false) }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    // 抓取在后台服务里跑，状态从全局读
    val isRunning = LogCaptureState.isRunning
    val status = LogCaptureState.status
    val isError = LogCaptureState.isError
    val lines = LogCaptureState.lines
    val total = LogCaptureState.total

    // 应用选择器的候选（⚠️ 派生数据不要用 remember 包：apps 是 SnapshotStateList，
    // remember 会缓存住旧列表；这里组合期直接算，读它就会自动订阅变化）。
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

    fun start() {
        if (isRunning || !isShizukuReady) return
        history = LogFilterHistoryStore.add(keyword)
        showHistory = false
        showAppPicker = false
        LogCaptureService.start(context, level, keyword, targetApp?.packageName.orEmpty())
    }

    fun stop() {
        LogCaptureService.stop(context)
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
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("日志抓取")
                }
            }

            item { HelpItem(expanded = showHelp, onClick = { showHelp = !showHelp }, transformationSpec) }
            if (showHelp) {
                item { HelpContent(transformationSpec) }
            }

            item { SectionHeaderItem("级别", transformationSpec) }
            item { LevelChipsRow(level, { level = it }, transformationSpec) }

            item { SectionHeaderItem("目标应用", transformationSpec) }
            item {
                TargetAppItem(
                    target = targetApp,
                    expanded = showAppPicker,
                    transformationSpec = transformationSpec,
                    onClick = { showAppPicker = !showAppPicker }
                )
            }

            if (showAppPicker) {
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
                item {
                    ActionItem(
                        icon = Icons.Filled.Clear,
                        title = "全部应用（不限定）",
                        subtitle = null,
                        enabled = true,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        transformationSpec = transformationSpec,
                        onClick = {
                            targetApp = null
                            showAppPicker = false
                        }
                    )
                }
                if (matchedApps.isEmpty()) {
                    item { HintItem("没有匹配的应用", false, transformationSpec) }
                } else {
                    itemsIndexed(matchedApps) { _, app ->
                        AppPickItem(
                            app = app,
                            selected = targetApp?.packageName == app.packageName,
                            transformationSpec = transformationSpec,
                            onClick = {
                                targetApp = app
                                showAppPicker = false
                            }
                        )
                    }
                }
            }

            item { SectionHeaderItem("关键字（可选）", transformationSpec) }
            item {
                CapsuleTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = "过滤 tag 或内容",
                    leadingIcon = Icons.Filled.FilterAlt,
                    onLeadingClick = { showHistory = !showHistory },
                    onPaste = {
                        val text = context.readClipboardText()
                        if (text.isNullOrBlank()) context.toast("剪贴板为空")
                        else keyword = text.trim()
                    },
                    onClear = { keyword = "" },
                    enabled = isShizukuReady,
                    transformationSpec = transformationSpec,
                    onSubmit = { start() }
                )
            }

            if (showHistory) {
                if (history.isEmpty()) {
                    item { HintItem("暂无过滤词历史", false, transformationSpec) }
                } else {
                    item { SectionHeaderItem("过滤词历史", transformationSpec) }
                    itemsIndexed(history) { _, word ->
                        HistoryItem(
                            word = word,
                            transformationSpec = transformationSpec,
                            onClick = {
                                keyword = word
                                showHistory = false
                            }
                        )
                    }
                    item {
                        ActionItem(
                            icon = Icons.Filled.DeleteOutline,
                            title = "清空历史",
                            subtitle = null,
                            enabled = true,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            transformationSpec = transformationSpec,
                            onClick = { history = LogFilterHistoryStore.clear() }
                        )
                    }
                }
            }

            item {
                if (isRunning) {
                    ActionItem(
                        icon = Icons.Filled.Stop,
                        title = "停止抓取",
                        subtitle = "实时抓取中 · 已 $total 行",
                        enabled = true,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        transformationSpec = transformationSpec,
                        onClick = { stop() }
                    )
                } else {
                    ActionItem(
                        icon = Icons.Filled.PlayArrow,
                        title = "开始抓取",
                        subtitle = targetApp?.let { "只看「${it.appLabel}」· 实时输出" }
                            ?: "全局 logcat 实时输出 · 点停止结束",
                        enabled = isShizukuReady,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        transformationSpec = transformationSpec,
                        onClick = { start() }
                    )
                }
            }

            status?.let { text ->
                item { HintItem(text, isError, transformationSpec) }
            }

            if (lines.isNotEmpty()) {
                item {
                    SectionHeaderItem(
                        "日志（${lines.size}${if (total > lines.size) " / 累计 $total" else ""}）",
                        transformationSpec
                    )
                }
                item {
                    ActionItem(
                        icon = Icons.Filled.ContentCopy,
                        title = "复制全部",
                        subtitle = null,
                        enabled = true,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        transformationSpec = transformationSpec,
                        onClick = {
                            context.copyToClipboard("日志", lines.joinToString("\n"))
                            context.toast("已复制 ${lines.size} 行")
                        }
                    )
                }
                item {
                    ActionItem(
                        icon = Icons.Filled.DeleteOutline,
                        title = "清空",
                        subtitle = null,
                        enabled = true,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        transformationSpec = transformationSpec,
                        onClick = { LogCaptureState.clear() }
                    )
                }
                itemsIndexed(lines) { _, line ->
                    LogLineItem(line, transformationSpec)
                }
            }
        }
    }
}

/** 级别选择：一排可横向滑动的小胶囊，选中高亮。 */
@Composable
private fun TransformingLazyColumnItemScope.LevelChipsRow(
    selected: LogLevel,
    onSelect: (LogLevel) -> Unit,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .transformedHeight(this, transformationSpec)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogLevel.entries.forEach { lv ->
            val active = lv == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) scheme.primaryContainer else scheme.surfaceContainerHigh)
                    .clickable { onSelect(lv) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lv.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) scheme.onPrimaryContainer else scheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 胶囊输入框：外壳**直接复用 [BarCapsule]**。
 *
 * ⚠️ 不用通用 `SearchField`：那是「搜索胶囊」的自绘样式（固定灰底 + 8dp 竖向内边距），
 * 高度和下面 [ActionItem] / [HistoryItem] 那些 52dp 的 `BarCapsule` 行对不齐。
 * 套壳之后尺寸、形状、配色、堆叠阴影全都一致。
 *
 * @param onLeadingClick 非 null 时左侧图标可点（点漏斗展开过滤词历史）
 * @param onPaste 非 null 时，空内容状态下尾部显示「粘贴」按钮
 */
@Composable
private fun TransformingLazyColumnItemScope.CapsuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    onLeadingClick: (() -> Unit)?,
    onPaste: (() -> Unit)?,
    onClear: () -> Unit,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onSubmit: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = null,
        leading = {
            // leading 插槽是 22dp 见方，这里整块可点，保证有可用的点击区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (onLeadingClick != null) {
                            Modifier.clickable(onClick = onLeadingClick)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (onLeadingClick != null) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        label = {
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = if (enabled) placeholder else "Shizuku 未就绪",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        trailing = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除",
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else if (onPaste != null) {
                IconButton(onClick = onPaste, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = "粘贴",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    )
}

/** 目标应用行：点一下展开 / 收起应用选择。 */
@Composable
private fun TransformingLazyColumnItemScope.TargetAppItem(
    target: InstalledApp?,
    expanded: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val active = target != null
    BarCapsule(
        containerColor = if (active) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurface,
        secondaryContentColor = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            Icon(
                imageVector = if (active) Icons.Filled.Apps else Icons.Filled.FilterAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = target?.appLabel ?: "全部应用",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = target?.packageName ?: "不限定应用（抓全局日志）",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

/** 应用选择列表的一行。 */
@Composable
private fun TransformingLazyColumnItemScope.AppPickItem(
    app: InstalledApp,
    selected: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        secondaryContentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = onClick,
        leading = {
            AppIcon(icon = app.icon, contentDescription = app.appLabel)
        },
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

/** 一条过滤词历史：点一下填进输入框。 */
@Composable
private fun TransformingLazyColumnItemScope.HistoryItem(
    word: String,
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
                imageVector = Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 单行日志：等宽小字，最多 4 行后省略（避免超长行撑出巨大的 item）。 */
@Composable
private fun TransformingLazyColumnItemScope.LogLineItem(
    line: String,
    transformationSpec: TransformationSpec
) {
    Text(
        text = line,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .transformedHeight(this, transformationSpec)
    )
}

/** 通用动作行（抓取 / 停止 / 复制 / 清空共用）。 */
@Composable
private fun TransformingLazyColumnItemScope.ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = if (enabled) containerColor else scheme.surfaceContainerHigh,
        contentColor = if (enabled) contentColor else scheme.onSurfaceVariant,
        secondaryContentColor = if (enabled) contentColor else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (enabled) onClick else null,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

/** 分组标题。 */
@Composable
private fun TransformingLazyColumnItemScope.SectionHeaderItem(
    text: String,
    transformationSpec: TransformationSpec
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
            .transformedHeight(this, transformationSpec)
    )
}

/** 状态提示行（错误=红）。 */
@Composable
private fun TransformingLazyColumnItemScope.HintItem(
    text: String,
    isError: Boolean,
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
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 过滤器说明：可折叠的帮助项，点一下展开 / 收起。 */
@Composable
private fun TransformingLazyColumnItemScope.HelpItem(
    expanded: Boolean,
    onClick: () -> Unit,
    transformationSpec: TransformationSpec
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
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = {
            Text(
                text = "过滤器说明",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

/** 展开后的说明正文：解释级别 / 关键字 / 目标应用三个过滤器及其「与」关系。 */
@Composable
private fun TransformingLazyColumnItemScope.HelpContent(
    transformationSpec: TransformationSpec
) {
    Text(
        text = "级别（D/I/W/E）：按日志严重程度过滤，越往后越严、越能砍掉噪声：\n" +
            "· 全部：抓所有日志（含最啰嗦的调试）\n" +
            "· D：调试 Debug\n" +
            "· I：普通信息 Info\n" +
            "· W：警告 Warn\n" +
            "· E：错误 Error（只留报错）\n" +
            "关键字：整行大小写不敏感匹配，空则不过滤。\n" +
            "目标应用：限定某个 App 的进程（logcat --pid），不选则抓全局。\n" +
            "三者同时满足（逻辑「与」）才显示。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .transformedHeight(this, transformationSpec)
    )
}
