package com.tengwear.appopspro.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import com.tengwear.appopspro.ThemePrefs

/**
 * M3E 主题。
 *
 * 配色优先级（[ThemeSeed]）：
 * 1. [ThemeSeed.System] → 跟随表盘动态取色（Wear OS 4+ / API 33+，[dynamicColorScheme]）
 * 2. [ThemeSeed.Default]（**出厂默认**）→ 原本的中性灰 [defaultWearColorScheme]
 * 3. 其余种子色 → [colorSchemeFromSeed]（整套重新生成，含中性表面）
 */
@Composable
fun AppOpsProTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 读取全局状态：用户一改配色，这里重组、整棵树换色。
    val seed = ThemePrefs.current
    val colorScheme = when {
        seed == ThemeSeed.System ->
            dynamicColorScheme(context) ?: defaultWearColorScheme()

        // Default 及任何无种子色的项 → 原始中性灰
        seed.seed == null -> defaultWearColorScheme()

        else -> colorSchemeFromSeed(seed.seed!!)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/**
 * API 30 回退配色（Wear OS 深色基调）。
 * 注意：Wear M3 的 ColorScheme 没有 surface / surfaceVariant 参数，
 * 使用 surfaceContainer / surfaceContainerLow / surfaceContainerHigh 替代。
 *
 * 也是 [colorSchemeFromSeed] 的中性底：只替换强调色族，表面色沿用这里的**中性灰**
 * （不跟随种子色相，避免整屏底色被染）。
 */
internal fun defaultWearColorScheme(): ColorScheme {
    return ColorScheme(
        primary = Color(0xFFA8C7FA),
        onPrimary = Color(0xFF00315D),
        primaryContainer = Color(0xFF004A77),
        onPrimaryContainer = Color(0xFFD3E3FD),
        secondary = Color(0xFFBFC6DC),
        onSecondary = Color(0xFF293041),
        secondaryContainer = Color(0xFF3F4759),
        onSecondaryContainer = Color(0xFFDBE2F9),
        tertiary = Color(0xFFDDBCE0),
        onTertiary = Color(0xFF3F2844),
        tertiaryContainer = Color(0xFF573E5C),
        onTertiaryContainer = Color(0xFFFAD7FC),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF1B1B1F),
        onBackground = Color(0xFFE3E2E6),
        surfaceContainerLow = Color(0xFF1B1B1F),
        surfaceContainer = Color(0xFF1B1B1F),
        surfaceContainerHigh = Color(0xFF2B2B2F),
        onSurface = Color(0xFFE3E2E6),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
    )
}