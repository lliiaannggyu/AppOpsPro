package com.tengwear.appopspro

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分辨率与显示密度（DPI）修改。
 *
 * 底层就是 `wm size` / `wm density` 两条命令，经 [ShizukuShell] 以 shell 身份执行，
 * 等价于 `adb shell wm ...`。两条命令从 API 17 起就存在，`reset` 子命令从 API 19 起，
 * 项目 minSdk 已是 25，无兼容问题。
 *
 * - 分辨率：`wm size <W>x<H>` 设置覆盖分辨率，`wm size reset` 清除（回到物理分辨率）
 * - 密度：`wm density <dpi>` 设置覆盖 DPI，`wm density reset` 清除（回到物理密度）
 *
 * 覆盖值（Override）优先于物理值（Physical）；读取时把两者都解析出来，
 * 界面展示「当前生效值 = 覆盖优先」。
 */
object DisplayRepository {

    private const val TAG = "AppOpsPro"

    /** 当前显示状态（字段为 null 表示该项读不到，通常是命令失败 / 未就绪）。 */
    data class DisplayState(
        /** 物理分辨率，如 `454x454`。 */
        val physicalSize: String?,
        /** 覆盖分辨率；null 表示未设置覆盖。 */
        val overrideSize: String?,
        /** 物理 DPI，如 `320`。 */
        val physicalDensity: String?,
        /** 覆盖 DPI；null 表示未设置覆盖。 */
        val overrideDensity: String?
    ) {
        /** 当前生效的分辨率（覆盖优先，否则物理）。 */
        val currentSize: String? get() = overrideSize ?: physicalSize
        /** 当前生效的 DPI（覆盖优先，否则物理）。 */
        val currentDensity: String? get() = overrideDensity ?: physicalDensity
    }

    /**
     * 读取当前状态。
     * @return 解析出的 state，以及（若有）任一条命令的错误结果
     */
    suspend fun read(): Pair<DisplayState, ShizukuShell.Result?> = withContext(Dispatchers.IO) {
        if (!ShizukuShell.isReady()) {
            return@withContext DisplayState(null, null, null, null) to null
        }
        val sizeRes = ShizukuShell.run("wm size")
        val densityRes = ShizukuShell.run("wm density")
        val state = parse(sizeRes.stdout, densityRes.stdout)
        val err = buildList {
            if (sizeRes.error != null) add(sizeRes)
            if (densityRes.error != null) add(densityRes)
        }.firstOrNull()
        Log.d(TAG, "display read: size=${state.currentSize} density=${state.currentDensity}")
        state to err
    }

    /** 设置 DPI（覆盖）。 */
    suspend fun setDensity(dpi: Int): ShizukuShell.Result =
        ShizukuShell.run("wm density $dpi")

    /** 清除 DPI 覆盖（回到物理密度）。 */
    suspend fun resetDensity(): ShizukuShell.Result =
        ShizukuShell.run("wm density reset")

    /** 设置分辨率（覆盖），格式 `WxH`。 */
    suspend fun setSize(width: Int, height: Int): ShizukuShell.Result =
        ShizukuShell.run("wm size ${width}x${height}")

    /** 清除分辨率覆盖（回到物理分辨率）。 */
    suspend fun resetSize(): ShizukuShell.Result =
        ShizukuShell.run("wm size reset")

    /**
     * 把 `wm size` / `wm density` 的文本输出解析成 [DisplayState]。
     *
     * 标准输出形如：
     * ```
     * Physical size: 454x454
     * Override size: 360x360      （未设置覆盖时这行不存在）
     * ```
     * 密度同理（`Physical density` / `Override density`）。
     */
    private fun parse(sizeOut: String, densityOut: String): DisplayState {
        val physicalSize = sizeOut.valueAfter("Physical size")
        val overrideSize = sizeOut.valueAfter("Override size")
        val physicalDensity = densityOut.valueAfter("Physical density")
        val overrideDensity = densityOut.valueAfter("Override density")
        return DisplayState(physicalSize, overrideSize, physicalDensity, overrideDensity)
    }

    /** 取某行冒号后的非空内容（如 `Physical size: 454x454` → `454x454`）。 */
    private fun String.valueAfter(prefix: String): String? {
        val line = lineSequence().firstOrNull { it.contains(prefix) } ?: return null
        return line.substringAfter(':').trim().takeIf { it.isNotEmpty() }
    }
}
