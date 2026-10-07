package com.tengwear.appopspro

import android.content.Context

/**
 * 本应用自己的少量偏好设置。
 *
 * 目前只有「首次启动是否看过关于页」一个标记 —— 新用户第一次打开应用时
 * 直接展示关于页（作者 / 交流群 / 说明），看过一次之后就不再自动弹。
 */
object UserPrefs {

    private const val PREF_NAME = "appopspro_prefs"
    private const val KEY_ABOUT_SHOWN = "about_shown"

    @Volatile
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /** 是否已经看过关于页。读不到时保守返回 false（宁可多展示一次）。 */
    fun isAboutShown(): Boolean = try {
        prefs()?.getBoolean(KEY_ABOUT_SHOWN, false) == true
    } catch (e: Exception) {
        false
    }

    fun markAboutShown() {
        try {
            prefs()?.edit()?.putBoolean(KEY_ABOUT_SHOWN, true)?.apply()
        } catch (_: Exception) {
        }
    }

    private fun prefs() = context?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
