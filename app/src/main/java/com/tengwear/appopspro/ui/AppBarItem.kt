package com.tengwear.appopspro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * 应用图标。加载失败时回退为中性占位图标，
 * 保证列表中图标列宽度一致、不会出现空洞。
 *
 * 尺寸取 22dp，与 [BarCapsule] 的 `leading` 插槽容器（22dp）一致 ——
 * 超出会被插槽裁切，所以两者必须对齐。
 */
@Composable
fun AppIcon(
    icon: ImageBitmap?,
    contentDescription: String?
) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Android,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 状态圆点 + 文案的内联徽标，用于权限状态等次要信息。 */
@Composable
fun StatusDot(
    color: Color,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/**
 * 自绘条形胶囊卡片。
 *
 * 为什么不用 M3E 的 `Button` / `SwitchButton`：
 * 这两个组件都是「自带容器绘制」的复合组件，把它们放进 [androidx.wear.compose.foundation.lazy.TransformingLazyColumn]
 * 时，容器的缩放/淡出需要靠 `SurfaceTransformation` 施加，会和列表本身的
 * `transformedHeight` 高度变换叠加，导致堆叠过渡生硬（卡片缩成一小块方块）。
 *
 * 参考 `jisuanqi2/PeriodicTableScreen.kt` 的 `ElementRow`：卡片背景完全自绘
 * （`clip(RoundedCornerShape)` + `background`），列表项只负责 `transformedHeight`，
 * 堆叠过渡就变成「整行平滑收窄 + 变暗」，非常自然。
 *
 * 因此这里手绘一个条形胶囊：形状、底色、点击行为全部自己控制，不依赖组件内部绘制。
 *
 * ## 堆叠阴影（层次感）
 * 本组件是 [TransformingLazyColumnItemScope] 的扩展，内部自动叠一层 [stackedShadow]：
 * 只有"往上滚、被上方叠住"的行卡会浮起投影，下方新滑出的行卡保持零阴影 ——
 * 层次感就出来了。所有调用点都已在 `TransformingLazyColumn` 的 item 内，无需改动。
 *
 * @param containerColor 胶囊底色
 * @param contentColor 主内容色（图标 / 主文字）
 * @param secondaryContentColor 次要内容色（副标题 / 尾随文字）
 * @param onClick 整卡点击回调（若为 null 则不可点击，不显示水波纹）
 */
@Composable
fun TransformingLazyColumnItemScope.BarCapsule(
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    secondaryContentColor: Color = contentColor,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
    secondaryLabel: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            // 堆叠层次感：在底色之前叠投影，沿卡片轮廓投射、不压内容。
            .then(stackedShadow(BarCapsuleShape))
            .fillMaxWidth()
            .height(BarCapsuleHeight)
            .clip(BarCapsuleShape)
            .background(containerColor)
            .then(clickModifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    leading()
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (leading != null) 8.dp else 0.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                label()
            }
            if (secondaryLabel != null) {
                CompositionLocalProvider(LocalContentColor provides secondaryContentColor) {
                    secondaryLabel()
                }
            }
        }

        if (trailing != null) {
            Box(
                modifier = Modifier.padding(start = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    trailing()
                }
            }
        }
    }
}

/**
 * 条形胶囊的形状与高度。
 *
 * 形状用 `RoundedCornerShape(percent = 50)` —— 按卡片实际测量尺寸取 50% 圆角，
 * 即**无论卡片高低，两端恒为完整半圆**（跑道形 / Stadium）。
 * 这与 M3E `SwitchButton` 的 Stadium 形状一致。
 *
 * 为什么不用固定 dp 值（如 `CornerLarge = 26.dp`）：
 * 卡片处于 `TransformingLazyColumn` 的堆叠变换区时，会被 `graphicsLayer` 缩放，
 * 固定 dp 圆角在视觉上会随缩放一起变小，比例失真；
 * 用 percent 则始终跟随实际尺寸，保持跑道形观感。
 */
private val BarCapsuleShape = RoundedCornerShape(percent = 50)

/** 条形胶囊高度：与 M3E 条形卡片一致（52dp）。 */
private val BarCapsuleHeight = 52.dp
