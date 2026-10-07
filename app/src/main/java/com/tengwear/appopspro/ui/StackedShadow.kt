package com.tengwear.appopspro.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope

// ── 堆叠层次感（stacked elevation）───────────────────────────────────────
//
// 需求：「给卡片堆叠列表做阴影，凸显层次感」。
// 参考实现：鹿了么 `M3eList.kt` 的 `stackedShadow()`（同样跑在 Wear Compose 1.5.0 上）。
//
// 思路：不改底色，只给**被叠住的上方卡片**叠一层投影阴影 —— 卡片越往上滚出，
// 投影越重，视觉上"浮"起来压住下面的卡片；从下方新滑出来的卡片保持零阴影。
//
// 位置数据来自 `TransformingLazyColumnItemScope` 在 `graphicsLayer` 里注入的
// `scrollProgress`（item 相对视口比例），**不在组合期读任何 state**，
// 因此每帧实时跟随，也不会与 `transformedHeight` 的收窄打架。
//
// 与参考实现的差别：参考用 `.shadow()` + 两个 `graphicsLayer` + `mutableFloatStateOf`
// 中转（每帧会触发一次重组）。而 `Modifier.shadow` 本身就是一个 `graphicsLayer`
// （内部只设 `shadowElevation/shape/clip/ambientShadowColor/spotShadowColor`），
// 所以这里把三者合并成**单个 graphicsLayer**：在同一个 block 里读 `scrollProgress`
// 并写 `shadowElevation`。渲染路径完全相同，但零重组、少两层图层。

/** 卡片滚到屏顶时的最大阴影高度（dp 值）。深色底上黑色阴影偏弱，故取值偏大。 */
private const val STACKED_SHADOW_MAX_ELEVATION = 18f

/**
 * 堆叠层次感：**被叠住的上方卡片加投影阴影、下方保持不变**。
 *
 * 作为 [TransformingLazyColumnItemScope] 的扩展，必须在 `TransformingLazyColumn`
 * 的 item 内部调用（需要 ItemScope 提供的 `scrollProgress`）。
 *
 * ## 阴影规则（单向上方）
 * 只看卡片**顶部**相对视口的位置 `top`（0 = 屏顶，1 = 屏底）：
 *  - `top >= 0.42`（卡片顶部还在中线附近及以下）→ **零阴影**（与背景齐平）；
 *  - `top < 0.42`（越过中线往上方滚出）→ 越往上阴影越重，屏顶达到 [maxElevation]。
 *
 * 也就是只有"往上滚、被上方叠住"的卡片浮起来；下方新滑出的卡片始终无阴影。
 *
 * @param shape 阴影形状（须与卡片轮廓一致，否则阴影会溢出 / 内缩）。
 * @param shadowColor 阴影色，默认黑色。
 * @param maxElevation 卡片滚到屏顶时的最大阴影高度（dp 值）。
 */
fun TransformingLazyColumnItemScope.stackedShadow(
    shape: Shape,
    shadowColor: Color = Color.Black,
    maxElevation: Float = STACKED_SHADOW_MAX_ELEVATION,
): Modifier {
    val scope = this
    return Modifier.graphicsLayer {
        // `GraphicsLayerScope.scrollProgress` 由框架在应用 item 变换时主动注入，
        // 时机严格同步；不可用时解出 -1f（哨兵），此时保持零阴影。
        val top = resolveTopFraction(scope.run { this@graphicsLayer.scrollProgress })
        // shadowElevation 单位是 px，故 .dp.toPx()（GraphicsLayerScope 自带 Density）。
        shadowElevation = if (top < 0f) 0f else (shadowStrength(top) * maxElevation).dp.toPx()
        this.shape = shape
        clip = false
        ambientShadowColor = shadowColor
        spotShadowColor = shadowColor
    }
}

/**
 * 由「卡片顶部相对视口比例」解出阴影强度（0..1，单向上方）。
 *
 * `top >= 0.42` → 0（卡片还在中线附近及以下，不投影）；
 * `top` 从 0.42 递减到 0 时强度从 0 平滑升到 1，屏顶达到最强。
 */
private fun shadowStrength(top: Float): Float {
    val rise = ((0.42f - top) / 0.42f).coerceIn(0f, 1f)
    // 平滑阶跃（smoothstep）：刚过中线时很轻，越往上越明显。
    return rise * rise * (3f - 2f * rise)
}

/**
 * 从官方进度值解出「卡片顶部相对视口的比例」（0 = 屏顶，1 = 屏底，可负 / 超 1）。
 * 不可用（`Unspecified` / 非有限值）时返回 `-1f` 作哨兵。
 */
private fun resolveTopFraction(
    p: androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScrollProgress,
): Float {
    if (p.isUnspecified) return -1f
    val t = p.topOffsetFraction
    return if (t.isFinite()) t else -1f
}
