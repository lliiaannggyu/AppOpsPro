package com.tengwear.appopspro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 日志抓取的全局状态：**后台服务写，UI 读**。
 *
 * 用 Compose 快照状态而不是页面内的 `remember`：抓取在 [LogCaptureService] 里持续跑，
 * 界面离开 / 重进后结果仍在，状态一变 UI 自动刷新。
 */
object LogCaptureState {

    /**
     * 缓冲上限。实时日志是无限增长的，全留着会 OOM；超过就把最旧的丢掉。
     * [total] 仍记录累计匹配行数，所以「丢了多少」看得出来。
     */
    private const val MAX_LINES = 5000

    /** 日志行（实时追加）。用 SnapshotStateList 便于增量更新。 */
    val lines = mutableStateListOf<String>()

    /** 是否正在抓取。 */
    var isRunning by mutableStateOf(false)
        private set

    /** 状态提示（进度 / 结果 / 错误）。 */
    var status by mutableStateOf<String?>(null)
        private set

    /** 上次结果是否为错误（决定提示颜色）。 */
    var isError by mutableStateOf(false)
        private set

    /** 累计匹配到的行数（可能大于 [lines] 的 size —— 超上限会丢最旧的）。 */
    var total by mutableStateOf(0)
        private set

    /** 服务开始抓取。 */
    fun start() {
        lines.clear()
        total = 0
        isRunning = true
        isError = false
        status = "实时抓取中…"
    }

    /**
     * 追加一批行。
     *
     * ⚠️ 按批而不是逐行：logcat 每秒可能几百行，逐行写状态会让 UI 每行重组一次。
     * 服务端每 ~250ms flush 一批。
     */
    fun append(batch: List<String>) {
        if (batch.isEmpty()) return
        lines.addAll(batch)
        total += batch.size
        if (lines.size > MAX_LINES) {
            lines.removeRange(0, lines.size - MAX_LINES)
        }
        status = "实时抓取中 · 已 $total 行"
    }

    /** 正常停止。 */
    fun stop() {
        isRunning = false
        status = "已停止 · 共 $total 行"
    }

    /** 抓取抛异常。 */
    fun fail(e: Exception) {
        isRunning = false
        isError = true
        status = "抓取异常：${e.javaClass.simpleName}"
    }

    /** 清空结果与提示。 */
    fun clear() {
        lines.clear()
        total = 0
        status = null
        isError = false
    }
}
