package com.tengwear.appopspro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
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
import com.tengwear.appopspro.DisplayRepository
import com.tengwear.appopspro.ShizukuShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 应用新显示参数后的确认倒计时秒数；超时未确认即自动回滚。 */
private const val CONFIRM_SECONDS = 30

/** 待确认变更的种类（可被 rememberSaveable 保存，故用字符串而非持有 lambda 的对象）。 */
private const val KIND_DENSITY = "density"
private const val KIND_SIZE = "size"

/**
 * 按种类回滚到物理默认值。
 *
 * ⚠️ 之所以不把 reset 动作（lambda）存进状态：改 `wm density` / `wm size` 会触发
 * density / screenSize 配置变更，若 Activity 仍被重建，持有 lambda 的对象无法被
 * `rememberSaveable` 保存，确认页就会丢。改用「种类字符串」保存，重建后照样能回滚。
 */
private suspend fun resetDisplayKind(kind: String): ShizukuShell.Result =
    if (kind == KIND_DENSITY) DisplayRepository.resetDensity()
    else DisplayRepository.resetSize()

/**
 * 分辨率与 DPI 修改页。
 *
 * 底层是 `wm size` / `wm density`（经 [DisplayRepository] 走 Shizuku shell），
 * 等价于 `adb shell wm ...`。本页**自包含**：自己读当前状态、自己执行修改并刷新，
 * 不与其他页面共享状态，所以 MainActivity 只需传 `isShizukuReady` + `onBack`。
 *
 * 布局：
 * - 当前状态（密度 / 分辨率，标注「覆盖」与否）
 * - 「显示密度 (DPI)」：**自己填**的 DPI 输入框 + 应用 / 重置
 * - 「分辨率」：**自己填**的 `宽 高`（空格分隔）输入框 + 应用 / 重置
 *
 * 两处都用输入框而不是固定预设 —— 不同设备物理密度 / 支持范围不同，写死预设没意义。
 *
 * ⚠️ 分辨率 / 密度改错会让表盘变形甚至难以操作，所以：
 *   · 应用前做格式校验，非法输入直接提示、不执行
 *   · 每次「应用」成功后整屏切到「确认显示更改」独立页（[DisplayConfirmContent]）：
 *     点「确认保留」保留；点「立即重置」立即回滚；**倒计时归零仍无操作则自动回滚为物理默认值**
 *     —— 这样即使屏幕被改到无法操作，也会自己恢复。
 */
@Composable
fun ResolutionScreen(
    isShizukuReady: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<DisplayRepository.DisplayState?>(null) }
    var dpiInput by remember { mutableStateOf("") }
    var sizeInput by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    // 待确认的显示变更（种类 + 展示文案）；非 null 时整屏切到倒计时确认页。
    // 用 rememberSaveable：即便系统因密度 / 分辨率变更重建 Activity，确认页也不会丢。
    var pendingKind by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingLabel by rememberSaveable { mutableStateOf("") }
    var secondsLeft by rememberSaveable { mutableStateOf(CONFIRM_SECONDS) }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scope = rememberCoroutineScope()

    fun toast(msg: String) = context.toast(msg)

    /** 读取当前状态（不切换 isWorking，由调用方管理）。 */
    suspend fun doRead(): Pair<DisplayRepository.DisplayState?, String?> {
        if (!ShizukuShell.isReady()) return null to "Shizuku 未就绪"
        val (s, err) = DisplayRepository.read()
        return s to err?.let { "读取失败：${it.displayText}" }
    }

    /**
     * 明确的重置操作：执行 → 提示 → 刷新，**不弹确认框**
     * （用户主动点「重置」，本身就是要回到物理默认值）。
     */
    fun runSimple(run: suspend () -> ShizukuShell.Result, okToast: String) {
        if (isWorking) return
        scope.launch {
            isWorking = true
            try {
                val r = run()
                if (r.error != null || r.exitCode != 0) {
                    notice = "设置失败：${r.displayText}"
                    toast("设置失败")
                } else {
                    notice = null
                    toast(okToast)
                    val (s, e) = doRead()
                    state = s
                    if (e != null) notice = e
                }
            } finally {
                isWorking = false
            }
        }
    }

    /**
     * 应用新值：执行 → 提示 → 刷新 → 整屏切到 30s 倒计时确认页。
     * [kind] 决定超时回滚时是 reset 密度还是分辨率；[isWorking] 期间忽略重复点击。
     */
    fun applyChange(
        kind: String,
        label: String,
        run: suspend () -> ShizukuShell.Result,
        okToast: String
    ) {
        if (isWorking) return
        scope.launch {
            isWorking = true
            try {
                val r = run()
                if (r.error != null || r.exitCode != 0) {
                    notice = "设置失败：${r.displayText}"
                    toast("设置失败")
                } else {
                    notice = null
                    toast(okToast)
                    val (s, e) = doRead()
                    state = s
                    if (e != null) notice = e
                    // 触发倒计时确认
                    pendingLabel = label
                    pendingKind = kind
                }
            } finally {
                isWorking = false
            }
        }
    }

    /** 解析并应用 DPI 输入；非法格式只提示、不执行。 */
    fun applyDpiInput(text: String) {
        val dpi = parseDpi(text)
        if (dpi == null) {
            notice = "DPI 应为正整数，如 320"
            toast("格式错误")
            return
        }
        applyChange(
            kind = KIND_DENSITY,
            label = "密度 $dpi",
            run = { DisplayRepository.setDensity(dpi) },
            okToast = "密度已设为 $dpi"
        )
    }

    /** 解析并应用分辨率输入；非法格式只提示、不执行。 */
    fun applySizeInput(text: String) {
        val parsed = parseSize(text)
        if (parsed == null) {
            notice = "格式应为 宽 高（空格分隔），如 360 360"
            toast("格式错误")
            return
        }
        applyChange(
            kind = KIND_SIZE,
            label = "分辨率 ${parsed.first} ${parsed.second}",
            run = { DisplayRepository.setSize(parsed.first, parsed.second) },
            okToast = "分辨率已设为 ${parsed.first} ${parsed.second}"
        )
    }

    // 进入页面即读一次
    LaunchedEffect(Unit) {
        if (!isShizukuReady) return@LaunchedEffect
        isWorking = true
        try {
            val (s, e) = doRead()
            state = s
            notice = e
        } finally {
            isWorking = false
        }
    }

    // 30s 确认倒计时：pendingKind 变化即重启；归零仍未确认 → 自动回滚为物理默认值。
    // pendingKind 被置回 null（点确认/重置）时，本协程在 delay 处被取消，不会误触发回滚。
    LaunchedEffect(pendingKind) {
        val kind = pendingKind ?: return@LaunchedEffect
        secondsLeft = CONFIRM_SECONDS
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        // 超时：先回滚（此时 pendingKind 仍非 null，协程仍存活），再清空状态
        runCatching { resetDisplayKind(kind) }
        val (s, _) = doRead()
        state = s
        notice = "未确认，已自动重置为物理默认值"
        toast("未确认，已自动重置")
        pendingKind = null
    }

    // ── 待确认：整屏切到「确认显示更改」页 ──────────────
    // 不用 AlertDialog（手表上是独立窗口，实测不可靠），改为把整页替换成确认页。
    // 注意：上面两个 LaunchedEffect 必须声明在本 return 之前，否则倒计时协程不会被组合。
    val currentKind = pendingKind
    if (currentKind != null) {
        DisplayConfirmContent(
            changeLabel = pendingLabel,
            secondsLeft = secondsLeft,
            onConfirm = { pendingKind = null },
            onReset = {
                pendingKind = null
                scope.launch {
                    runCatching { resetDisplayKind(currentKind) }
                    val (s, _) = doRead()
                    state = s
                    notice = "已重置为物理默认值"
                    toast("已重置")
                }
            }
        )
        return
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
                    Text("分辨率与 DPI")
                }
            }

            // ── 当前状态 ────────────────────────────────
            item {
                StatusItem(
                    state = state,
                    transformationSpec = transformationSpec
                )
            }

            if (notice != null) {
                item {
                    HintItem(
                        text = notice!!,
                        error = true,
                        transformationSpec = transformationSpec
                    )
                }
            }

            // ── 显示密度 (DPI) ──────────────────────────
            item {
                SectionHeaderItem("显示密度 (DPI)", transformationSpec)
            }

            item {
                InputField(
                    value = dpiInput,
                    onValueChange = { dpiInput = it.filter(Char::isDigit) },
                    onClear = { dpiInput = "" },
                    enabled = isShizukuReady && !isWorking,
                    leadingGlyph = "◎",
                    placeholder = "输入 DPI，如 320",
                    keyboardType = KeyboardType.Number,
                    transformationSpec = transformationSpec,
                    onSubmit = { applyDpiInput(dpiInput) }
                )
            }

            item {
                DisplayActionItem(
                    title = "应用密度",
                    subtitle = if (dpiInput.isBlank()) "先在上方输入 DPI" else "应用 $dpiInput",
                    icon = Icons.Filled.Check,
                    enabled = isShizukuReady && !isWorking && dpiInput.isNotBlank(),
                    transformationSpec = transformationSpec,
                    onClick = { applyDpiInput(dpiInput) }
                )
            }

            item {
                DisplayActionItem(
                    title = "重置密度",
                    subtitle = "恢复物理密度",
                    icon = Icons.Filled.Refresh,
                    enabled = isShizukuReady && !isWorking,
                    transformationSpec = transformationSpec,
                    onClick = {
                        runSimple(
                            run = { DisplayRepository.resetDensity() },
                            okToast = "已重置密度"
                        )
                    }
                )
            }

            // ── 分辨率 ──────────────────────────────────
            item {
                SectionHeaderItem("分辨率", transformationSpec)
            }

            item {
                InputField(
                    value = sizeInput,
                    onValueChange = { sizeInput = it },
                    onClear = { sizeInput = "" },
                    enabled = isShizukuReady && !isWorking,
                    leadingGlyph = "▦",
                    placeholder = "输入 宽 高，如 360 360",
                    keyboardType = KeyboardType.Text,
                    transformationSpec = transformationSpec,
                    onSubmit = { applySizeInput(sizeInput) }
                )
            }

            item {
                DisplayActionItem(
                    title = "应用分辨率",
                    subtitle = if (sizeInput.isBlank()) "先在上方输入 宽 高" else "应用 $sizeInput",
                    icon = Icons.Filled.Check,
                    enabled = isShizukuReady && !isWorking && sizeInput.isNotBlank(),
                    transformationSpec = transformationSpec,
                    onClick = { applySizeInput(sizeInput) }
                )
            }

            item {
                DisplayActionItem(
                    title = "重置分辨率",
                    subtitle = "恢复物理分辨率",
                    icon = Icons.Filled.Refresh,
                    enabled = isShizukuReady && !isWorking,
                    transformationSpec = transformationSpec,
                    onClick = {
                        runSimple(
                            run = { DisplayRepository.resetSize() },
                            okToast = "已重置分辨率"
                        )
                    }
                )
            }

            item {
                HintItem(
                    text = "分辨率 / 密度改错可能让表盘变形；改完若显示异常，重进本页点「重置」即可恢复。",
                    error = false,
                    transformationSpec = transformationSpec
                )
            }
        }
    }
}

/**
 * 「确认显示更改」独立页（整屏替换列表，非弹窗）。
 *
 * 应用新显示参数后进入本页：大号倒计时 [secondsLeft]，配「确认保留 / 立即重置」两个动作。
 * 倒计时归零仍无操作由调用方自动回滚，所以即使屏幕被改到难以操作也能恢复。
 */
@Composable
private fun DisplayConfirmContent(
    changeLabel: String,
    secondsLeft: Int,
    onConfirm: () -> Unit,
    onReset: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    Text("确认显示更改")
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .transformedHeight(this, transformationSpec),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "已应用：$changeLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "$secondsLeft",
                        style = TextStyle(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "秒内未确认，将自动重置为物理默认值",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                DisplayActionItem(
                    title = "确认保留",
                    subtitle = "保持当前显示设置",
                    icon = Icons.Filled.Check,
                    enabled = true,
                    transformationSpec = transformationSpec,
                    onClick = onConfirm
                )
            }

            item {
                DisplayActionItem(
                    title = "立即重置",
                    subtitle = "恢复物理默认值",
                    icon = Icons.Filled.Refresh,
                    enabled = true,
                    transformationSpec = transformationSpec,
                    onClick = onReset
                )
            }
        }
    }
}

/** 解析 DPI（正整数）；非法返回 null。 */
private fun parseDpi(text: String): Int? {
    val v = text.trim().toIntOrNull() ?: return null
    return if (v > 0) v else null
}

/** 解析 `宽 高`（以空格分隔），返回正整数对；非法返回 null。 */
private fun parseSize(text: String): Pair<Int, Int>? {
    val parts = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.size != 2) return null
    val w = parts[0].toIntOrNull() ?: return null
    val h = parts[1].toIntOrNull() ?: return null
    if (w <= 0 || h <= 0) return null
    return w to h
}

/** 当前状态展示：密度 + 分辨率，覆盖值标注。 */
@Composable
private fun TransformingLazyColumnItemScope.StatusItem(
    state: DisplayRepository.DisplayState?,
    transformationSpec: TransformationSpec
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .transformedHeight(this, transformationSpec)
    ) {
        val density = state?.currentDensity
        val size = state?.currentSize
        Text(
            text = "当前密度：${density?.let { "$it dp" } ?: "读取中…"}" +
                if (state?.overrideDensity != null) "（覆盖）" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "当前分辨率：${size ?: "读取中…"}" +
                if (state?.overrideSize != null) "（覆盖）" else "",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (state != null && state.overrideSize == null && state.overrideDensity == null) {
            Text(
                text = "（均为物理值，未做覆盖）",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 终端风格的输入框（DPI / 分辨率共用）。复用 [BarCapsule] 保持与列表行一致的跑道形外观，
 * 内部用等宽字体 BasicTextField；空值时提供粘贴按钮。
 */
@Composable
private fun TransformingLazyColumnItemScope.InputField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    leadingGlyph: String,
    placeholder: String,
    keyboardType: KeyboardType,
    transformationSpec: TransformationSpec,
    onSubmit: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val monoBody = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val context = LocalContext.current

    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = null,
        leading = {
            Text(
                text = leadingGlyph,
                style = monoBody,
                color = if (enabled) scheme.primary else scheme.outline,
                maxLines = 1
            )
        },
        label = {
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = if (enabled) placeholder else "Shizuku 未就绪",
                        style = monoBody,
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
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
            } else {
                IconButton(
                    onClick = {
                        val text = context.readClipboardText()
                        if (text.isNullOrBlank()) context.toast("剪贴板为空")
                        else onValueChange(text.trim())
                    },
                    modifier = Modifier.size(28.dp)
                ) {
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

/** 通用动作行（应用 / 重置 共用）。 */
@Composable
private fun TransformingLazyColumnItemScope.DisplayActionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (enabled) scheme.onSurface else scheme.onSurfaceVariant
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = content,
        secondaryContentColor = scheme.onSurfaceVariant,
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
        secondaryLabel = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** 分组标题（比页面标题低一级，主色小字）。 */
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
