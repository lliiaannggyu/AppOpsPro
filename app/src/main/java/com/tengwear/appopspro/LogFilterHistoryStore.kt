package com.tengwear.appopspro

import android.content.Context
import android.content.SharedPreferences

/**
 * 日志「过滤词」历史（SharedPreferences 持久化）。
 *
 * 与 [SearchHistoryStore] 同构，但**独立存储**（不和应用搜索历史混在一起）。
 * 只记录用户真正发起过抓取的关键字；最新在前，重复项提前而不是新增。
 */
object LogFilterHistoryStore {

    private const val PREF_NAME = "appopspro_log_filter"
    private const val KEY_HISTORY = "history"
    private const val SEPARATOR = "\n"

    /** 最多保留条数。 */
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

    /** 按「最近优先」读取。 */
    fun load(): List<String> {
        val raw = prefs?.getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_SIZE)
    }

    /** 记录一次过滤词；重复（忽略大小写）提前，返回更新后的列表。 */
    fun add(keyword: String): List<String> {
        val k = keyword.trim()
        if (k.isEmpty()) return load()
        val next = (listOf(k) + load().filterNot { it.equals(k, ignoreCase = true) })
            .take(MAX_SIZE)
        prefs?.edit()?.putString(KEY_HISTORY, next.joinToString(SEPARATOR))?.apply()
        return next
    }

    /** 清空历史。 */
    fun clear(): List<String> {
        prefs?.edit()?.remove(KEY_HISTORY)?.apply()
        return emptyList()
    }
}
