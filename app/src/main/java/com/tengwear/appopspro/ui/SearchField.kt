package com.tengwear.appopspro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * 搜索框胶囊底色：无色彩偏移的中性灰。
 *
 * 为什么不直接用 `MaterialTheme.colorScheme.surfaceContainerHigh`：
 * M3E baseline 色板里它是 `Neutral30 = rgb(73, 68, 83)`，蓝分量明显高于红绿，
 * 叠加在深色表盘上会呈现紫/蓝色调，与「灰色胶囊」的设计意图不符。
 */
private val SearchFieldContainerColor = Color(0xFF3C3C3C)

/**
 * 自绘搜索框（Wear M3E 1.5.0 没有提供 `TextField`，只能用 foundation 的
 * [BasicTextField] 手搓一个）：胶囊容器 + 放大镜 + 可编辑文本 + 清除按钮。
 *
 * 应用搜索页与权限列表页共用，保证两处观感一致。
 *
 * @param placeholder 空内容时的提示文案
 * @param widthFraction 占屏宽比例。**圆形表盘几何约束**：距圆心 dy 处可用弦半宽
 *   `sqrt(R² - dy²)`，靠上的元素四角最容易溢出圆边（480px 表盘、dy=180 时 W 要 < 276px）。
 *   默认 0.60f 时实际渲染宽约 258px，四角均留有余量。
 * @param focusRequester 传入则可用于程序化聚焦（如进入页面自动弹键盘）；不传则只靠点击聚焦
 * @param onSearchAction 按下键盘搜索键时回调，用于记录搜索历史
 * @param onPaste 非 null 时，空内容状态下显示「粘贴」按钮（回调里自行读剪贴板）
 * @param onLeadingClick 非 null 时左侧图标变为可点（如点漏斗展开过滤词历史）
 * @param leadingIcon 左侧图标，默认放大镜；命令行页会换成终端图标
 * @param imeAction 键盘右下角动作，默认「搜索」
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    widthFraction: Float = 0.60f,
    focusRequester: FocusRequester? = null,
    onSearchAction: (() -> Unit)? = null,
    onPaste: (() -> Unit)? = null,
    onLeadingClick: (() -> Unit)? = null,
    leadingIcon: ImageVector = Icons.Filled.Search,
    imeAction: ImeAction = ImeAction.Search
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .background(
                    color = SearchFieldContainerColor,
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onLeadingClick != null) {
                // 可点：用主色提示「这里能交互」（如点漏斗展开过滤词历史）
                IconButton(
                    onClick = onLeadingClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = scheme.primary
                    )
                }
            } else {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = scheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onSearch = { onSearchAction?.invoke() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清除",
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else if (onPaste != null) {
                IconButton(
                    onClick = onPaste,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentPaste,
                        contentDescription = "粘贴",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
