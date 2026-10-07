package com.tengwear.appopspro.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题配色选项。
 *
 * 分三类：
 * - [Default]：**原本的中性灰配色**（[defaultWearColorScheme]），出厂默认。
 * - [System]：跟随表盘 / 系统动态取色（Wear OS 4+ 的 `dynamicColorScheme`）。
 * - 其余：官方调色板的**种子色**（Material 500 档），由 [colorSchemeFromSeed]
 *   用其色相重新生成整套配色（强调色 + 中性表面）。
 *
 * [seed] 为 null 表示特殊项（不走种子色生成）。[id] 用于落盘持久化，**不要随意改名**。
 */
enum class ThemeSeed(
    val id: String,
    val label: String,
    /** 种子色；null 表示特殊项（[Default] / [System]）。 */
    val seed: Color?
) {
    /** 原始中性灰配色 —— **默认**。 */
    Default("default", "默认配色", null),

    /** 跟随表盘 / 系统自动取色。 */
    System("system", "跟随系统", null),

    Purple("purple", "紫", Color(0xFF6750A4)),
    Indigo("indigo", "靛", Color(0xFF3F51B5)),
    Blue("blue", "蓝", Color(0xFF2196F3)),
    Cyan("cyan", "青", Color(0xFF00BCD4)),
    Teal("teal", "蓝绿", Color(0xFF009688)),
    Green("green", "绿", Color(0xFF4CAF50)),
    Lime("lime", "黄绿", Color(0xFFAFB42B)),
    Amber("amber", "琥珀", Color(0xFFFFC107)),
    Orange("orange", "橙", Color(0xFFFF9800)),
    DeepOrange("deep_orange", "橘红", Color(0xFFFF5722)),
    Red("red", "红", Color(0xFFF44336)),
    Pink("pink", "粉", Color(0xFFE91E63)),
    Brown("brown", "棕", Color(0xFF795548)),
    BlueGrey("blue_grey", "灰蓝", Color(0xFF607D8B));

    companion object {
        /** 由持久化的 id 反查；未知 / null（旧数据）一律回落到 [Default]。 */
        fun fromId(id: String?): ThemeSeed =
            entries.firstOrNull { it.id == id } ?: Default

        /** 只含带种子色的项（用于色块行）。 */
        val colorSeeds: List<ThemeSeed> get() = entries.filter { it.seed != null }
    }
}
