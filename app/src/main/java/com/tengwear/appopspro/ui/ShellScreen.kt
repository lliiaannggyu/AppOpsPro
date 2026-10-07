package com.tengwear.appopspro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
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
import com.tengwear.appopspro.ShizukuShell

/** 常用命令预设。手表上打字很痛苦，预设能省掉大部分输入。 */
private val CommandPresets = listOf(
    "第三方应用数" to "pm list packages -3 | wc -l",
    "电量" to "dumpsys battery | grep -E \"level|status\"",
    "飞行模式" to "settings get global airplane_mode_on",
    "系统版本" to "getprop ro.build.version.release",
)

/** 输出区默认最多显示的行数，超出部分折叠。 */
private const val COLLAPSED_MAX_LINES = 10

/**
 * 命令行执行页。
 *
 * 命令经 [ShizukuShell] 以 **shell（或 root）身份**执行，等价于 `adb shell <command>`，
 * 因此支持管道 / 重定向 / `&&` 等 shell 语法。
 *
 * 界面结构：
 * - **终端风格**命令输入框（`$` 提示符 + 等宽字体 + 聚焦高亮描边）——
 *   刻意不复用 [SearchField]：那是「搜索胶囊」的视觉，用在这里会显得格格不入
 * - 「执行」动作行 + 「粘贴」动作行（从系统剪贴板读入）
 * - 输入为空时展示常用命令预设
 * - 输出日志：每条记录显示命令、退出码、耗时与输出；右上角有「复制输出」按钮，
 *   输出过长时点击卡片可展开/折叠
 *
 * 日志由调用方持有（MainActivity），这样返回上级再进来不会丢历史输出。
 */
@Composable
fun ShellScreen(
    log: List<ShizukuShell.Result>,
    isRunning: Boolean,
    isShizukuReady: Boolean,
    onRun: (String) -> Unit,
    onClearLog: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var command by remember { mutableStateOf("") }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    fun submit(text: String = command) {
        val cmd = text.trim()
        if (cmd.isEmpty() || isRunning) return
        onRun(cmd)
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
                    Text("命令行")
                }
            }

            item {
                CommandInputField(
                    command = command,
                    onCommandChange = { command = it },
                    onClear = { command = "" },
                    enabled = isShizukuReady,
                    transformationSpec = transformationSpec,
                    onSubmit = { submit() }
                )
            }

            item {
                ActionItem(
                    icon = Icons.Filled.PlayArrow,
                    title = if (isRunning) "执行中…" else "执行",
                    subtitle = null,
                    enabled = !isRunning && isShizukuReady && command.isNotBlank(),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    transformationSpec = transformationSpec,
                    onClick = { submit() }
                )
            }

            item {
                ActionItem(
                    icon = Icons.Filled.ContentPaste,
                    title = "粘贴",
                    subtitle = "从剪贴板填入命令",
                    enabled = !isRunning,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    transformationSpec = transformationSpec,
                    onClick = {
                        val text = context.readClipboardText()
                        if (text.isNullOrBlank()) {
                            context.toast("剪贴板为空")
                        } else {
                            command = text
                        }
                    }
                )
            }

            if (command.isBlank()) {
                item {
                    SectionHeaderItem(
                        text = "常用命令",
                        transformationSpec = transformationSpec
                    )
                }
                items(CommandPresets, key = { "preset_${it.first}" }) { preset ->
                    PresetItem(
                        label = preset.first,
                        command = preset.second,
                        enabled = !isRunning && isShizukuReady,
                        transformationSpec = transformationSpec,
                        onClick = {
                            command = preset.second
                            submit(preset.second)
                        }
                    )
                }
            }

            if (log.isNotEmpty()) {
                item {
                    SectionHeaderItem(
                        text = "输出（${log.size}）",
                        transformationSpec = transformationSpec
                    )
                }
                // 最新的在最上面，省得每次都滚到底部。
                // key 用「在原 log 里的下标」而不是对象本身 —— Result 是 data class，
                // 两次相同命令若输出与耗时都一样，对象相等会导致 key 冲突（LazyColumn 会崩）。
                val reversedLog = log.asReversed()
                itemsIndexed(
                    reversedLog,
                    key = { index, _ -> "out_${reversedLog.size - 1 - index}" }
                ) { _, result ->
                    ShellResultItem(
                        result = result,
                        transformationSpec = transformationSpec,
                        onCopy = { context.copyToClipboard("命令输出", result.displayText) }
                    )
                }
                item {
                    ActionItem(
                        icon = Icons.Filled.DeleteOutline,
                        title = "清空输出",
                        subtitle = null,
                        enabled = true,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        transformationSpec = transformationSpec,
                        onClick = onClearLog
                    )
                }
            }
        }
    }
}

/**
 * 命令输入框。
 *
 * **直接复用 [BarCapsule]**：这样它跟下面「执行 / 粘贴 / 预设」那些条目的形状、
 * 高度、圆角、底色完全一致（跑道形胶囊 + 52dp + `surfaceContainerHigh`），
 * 不会再出现「某个控件跟整屏不是一个体系」的割裂感。
 *
 * 在胶囊外壳之上保留终端语义：左起 `$` 提示符 + 等宽字体（`FontFamily.Monospace`），
 * 让命令和下面的输出看起来是同一种东西。
 *
 * 注意：这里**不额外加描边或特殊底色** —— 那会破坏与列表行的一致性；
 * 「可编辑」的暗示交给 placeholder「输入命令」即可。
 */
@Composable
private fun TransformingLazyColumnItemScope.CommandInputField(
    command: String,
    onCommandChange: (String) -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onSubmit: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val monoBody = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = null,
        leading = {
            Text(
                text = "$",
                style = monoBody,
                color = if (enabled) scheme.primary else scheme.outline,
                maxLines = 1
            )
        },
        label = {
            Box(contentAlignment = Alignment.CenterStart) {
                if (command.isEmpty()) {
                    Text(
                        text = if (enabled) "输入命令" else "Shizuku 未就绪",
                        style = monoBody,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = command,
                    onValueChange = onCommandChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        trailing = {
            if (command.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    )
}

/** 通用动作行（执行 / 粘贴 / 清空输出共用）。 */
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
    val active = enabled
    BarCapsule(
        containerColor = if (active) containerColor else scheme.surfaceContainerHigh,
        contentColor = if (active) contentColor else scheme.onSurfaceVariant,
        secondaryContentColor = if (active) contentColor else scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (active) onClick else null,
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

/** 预设命令：点一下直接执行。 */
@Composable
private fun TransformingLazyColumnItemScope.PresetItem(
    label: String,
    command: String,
    enabled: Boolean,
    transformationSpec: TransformationSpec,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    BarCapsule(
        containerColor = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        secondaryContentColor = scheme.onSurfaceVariant,
        modifier = Modifier.transformedHeight(this, transformationSpec),
        onClick = if (enabled) onClick else null,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = {
            Text(
                text = command,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/**
 * 一条命令的执行结果。
 *
 * 输出可能很长（`pm list packages` 有 150+ 行），默认只显示 [COLLAPSED_MAX_LINES] 行，
 * 点击整块可展开 / 折叠；右上角的按钮把整段输出复制到剪贴板。
 */
@Composable
private fun TransformingLazyColumnItemScope.ShellResultItem(
    result: ShizukuShell.Result,
    transformationSpec: TransformationSpec,
    onCopy: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var expanded by remember(result) { mutableStateOf(false) }

    val output = result.displayText
    val lines = output.lines()
    val collapsible = lines.size > COLLAPSED_MAX_LINES
    val shown = if (!collapsible || expanded) output else {
        lines.take(COLLAPSED_MAX_LINES).joinToString("\n") +
                "\n…还有 ${lines.size - COLLAPSED_MAX_LINES} 行（点击展开）"
    }

    val ok = result.error == null && result.exitCode == 0
    val statusColor = if (ok) scheme.primary else scheme.error
    val statusText = result.error ?: "exit=${result.exitCode} · ${result.durationMs}ms"

    Column(
        modifier = Modifier
            .transformedHeight(this, transformationSpec)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHigh)
            .then(
                if (collapsible) Modifier.clickable { expanded = !expanded } else Modifier
            )
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$ ${result.command}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    onCopy()
                    context.toast("已复制输出")
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "复制输出",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .padding(end = 8.dp)
        ) {
            Text(
                text = shown,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = scheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MAX_LINES + 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 分组标题（比页面标题低一级）。
 *
 * 与 `PermissionScreen` 里的同名组件保持一致；这里各自私有，避免为一个 8 行组件
 * 把两个页面耦合到一起。
 */
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

// ── 剪贴板 ──────────────────────────────────────────────
// 实现见 ui/Clipboard.kt（与关于页共用）

