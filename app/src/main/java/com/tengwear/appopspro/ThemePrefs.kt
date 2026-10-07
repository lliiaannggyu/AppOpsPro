package com.tengwear.appopspro

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tengwear.appopspro.ui.theme.ThemeSeed

/**
 * 主题种子色的持久化 + 全局可观察状态。
 *
 * [current] 是 Compose 快照状态：[AppOpsProTheme] 读它 → 用户在关于页改选后
 * 整棵树自动重组换色，无需重启。
 *
 * 默认值 = [ThemeSeed.Default]（原本的中性灰）；旧数据 / 未知 id 也回落到它。
 * 与 [UserPrefs] 共用同一个 SharedPreferences 文件（`appopspro_prefs`）。
 */
object ThemePrefs {

    private const val PREF_NAME = "appopspro_prefs"
    private const val KEY_SEED = "theme_seed"

    @Volatile
    private var context: Context? = null

    /** 当前选中的配色；出厂默认 = [ThemeSeed.Default]（原本的中性灰）。 */
    var current: ThemeSeed by mutableStateOf(ThemeSeed.Default)
        private set

    /** 应用启动时调用一次：注入 context 并载入已保存的选择。 */
    fun init(context: Context) {
        this.context = context.applicationContext
        current = ThemeSeed.fromId(read())
    }

    /** 选择配色，并落盘。 */
    fun select(seed: ThemeSeed) {
        current = seed
        write(seed.id)
    }

    private fun read(): String? = try {
        prefs()?.getString(KEY_SEED, null)
    } catch (e: Exception) {
        null
    }

    private fun write(id: String) {
        try {
            prefs()?.edit()?.putString(KEY_SEED, id)?.apply()
        } catch (_: Exception) {
        }
    }

    private fun prefs() = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
