package com.tengwear.appopspro

import android.content.Context
import android.content.SharedPreferences

/**
 * 搜索历史（SharedPreferences 持久化）。
 *
 * 只保存「用户真正提交过的搜索」——即按下键盘搜索键、或点开了某个结果时的那次查询，
 * 而不是每敲一个字符就记一条，否则历史会被「相」「相机」「相机权」这类半成品塞满。
 *
 * 存储格式：用 `\n` 拼接的字符串（搜索词里不会出现换行，分隔安全）。
 * 最新的一条在最前，重复词会被提到最前而不是新增一条。
 */
object SearchHistoryStore {

    private const val PREF_NAME = "appopspro_search"
    private const val KEY_HISTORY = "history"
    private const val SEPARATOR = "\n"

    /** 最多保留的历史条数。手表屏幕小，再多就得靠滚动才看得全。 */
    private const val MAX_SIZE = 8

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 由 MainActivity 在启动时注入。 */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 按「最近优先」读取历史。 */
    fun load(): List<String> {
        val raw = prefs?.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_SIZE)
    }

    /**
     * 记录一次搜索。
     *
     * 重复项（忽略大小写）会被提到最前，而不是产生第二条；
     * 超出 [MAX_SIZE] 的尾部条目直接丢弃。返回更新后的列表。
     */
    fun add(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return load()
        val next = (listOf(q) + load().filterNot { it.equals(q, ignoreCase = true) })
            .take(MAX_SIZE)
        prefs?.edit()?.putString(KEY_HISTORY, next.joinToString(SEPARATOR))?.apply()
        return next
    }

    /** 清空历史，返回空列表方便直接赋给 UI 状态。 */
    fun clear(): List<String> {
        prefs?.edit()?.remove(KEY_HISTORY)?.apply()
        return emptyList()
    }
}
