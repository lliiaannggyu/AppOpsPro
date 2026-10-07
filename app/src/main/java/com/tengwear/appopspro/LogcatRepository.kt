package com.tengwear.appopspro

/**
 * 日志级别过滤。
 *
 * [priority] 是 `logcat` 的优先级字母；`*:<priority>` 表示「所有 tag，优先级 ≥ 该值」，
 * 所以 [All]（V）等价于不过滤。
 */
enum class LogLevel(val label: String, val priority: String) {
    All("全部", "V"),
    Debug("D", "D"),
    Info("I", "I"),
    Warn("W", "W"),
    Error("E", "E")
}

/**
 * 日志抓取（**实时流式**）。
 *
 * 底层是 `logcat -v time '*:P'`（**不带 `-d`**，进程一直输出），经 [ShizukuShell.streamLines]
 * 逐行回调，等价 `adb shell logcat ...` 一直挂着。
 *
 * 两个关键取舍：
 * - **不加 `-d`**：`-d` 是 dump 完就退出（只抓一次快照）；要「一直抓」就得让 logcat 常驻输出。
 * - **不在 shell 里 grep**：管道中的 `grep` 默认块缓冲，日志得攒满一块才吐，实时性全丢。
 *   所以关键字过滤放到 Kotlin 侧**逐行**做（[stream] 里 `contains(ignoreCase=true)`）。
 */
object LogcatRepository {

    /**
     * 解析某个包名当前的进程 PID；应用没在跑 / `pidof` 不可用时返回 null。
     *
     * 多个 PID（多进程应用）时取第一个 —— `logcat --pid` 只接受一个。
     */
    suspend fun resolvePid(packageName: String): Int? {
        if (packageName.isBlank()) return null
        val r = ShizukuShell.run("pidof $packageName")
        return r.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
    }

    /**
     * 流式抓取：每匹配一行就回调一次 [onLine]，直到协程被取消。
     *
     * @param keyword 关键字（大小写不敏感，匹配整行；空则不过滤）
     * @param pid 指定进程 PID；非 null 时走 `logcat --pid=` 精确过滤
     */
    suspend fun stream(
        level: LogLevel,
        keyword: String,
        pid: Int?,
        onLine: suspend (String) -> Unit
    ): ShizukuShell.Result {
        val kw = keyword.trim()
        return ShizukuShell.streamLines(buildCommand(level, pid)) { line ->
            if (kw.isEmpty() || line.contains(kw, ignoreCase = true)) {
                onLine(line)
            }
        }
    }

    /** 构造抓取命令（拆出来便于单测 / 复用）。 */
    fun buildCommand(level: LogLevel, pid: Int?): String {
        val sb = StringBuilder("logcat -v time")
        if (pid != null) sb.append(" --pid=").append(pid)
        // 过滤器用单引号包住，避免 `*` 被 shell 当成通配符展开
        sb.append(" '*:").append(level.priority).append('\'')
        return sb.toString()
    }
}
