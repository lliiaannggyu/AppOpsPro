package com.tengwear.appopspro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 单应用实时性能 / 资源指标采集。
 *
 * 全部走 [ShizukuShell.run]（shell 身份，等价于 `adb shell`），不依赖任何特殊 SDK。
 *
 * ## 各指标的可靠性（Android 限制）
 * - **CPU%**：实时，按应用进程采样 `/proc/<pid>/stat` 的 utime+stime，跨 1s 算占用率。
 *   需要应用**正在运行**（有 PID）；未运行则为 null。
 * - **运存 (PSS)**：`dumpsys meminfo <pkg>` 读 TOTAL PSS（KB），刷新读取。需要应用运行。
 * - **存储**：`du` 应用数据目录（/data/data + Android/data + obb）。**注意**：Shizuku 以 shell
 *   身份运行时，/data/data 对其他应用常无读权限，可能读不到（返回 null）——这是系统限制，非 bug。
 * - **电量 (全局)**：`dumpsys battery` 的 level，始终可用（设备级，不限应用）。
 * - **功耗 (单应用累计)**：`dumpsys batterystats <pkg>` 解析「Estimated power use」段里该 Uid 的
 *   累计 mAh。**这是自上次充电以来的累计估算值，不是实时瞬时功率**——Android 不提供单应用瞬时功率。
 *
 * ⚠️ 已**移除 GPU**：Android 不暴露单应用 GPU 占用；全局 GPU 占用仅部分机型（kgsl）有 sysfs，
 * 绝大多数设备读不到，留着只会长期显示「系统不支持」，无实用价值。
 */
data class AppStats(
    /** CPU 占用率（按单核口径，0~N*100）。null = 应用未运行 / 不可用。 */
    val cpuPercent: Double? = null,
    /** 内存 PSS（KB）。null = 不可用。 */
    val pssKb: Long? = null,
    /** 应用数据目录占用（KB）。null = 读不到（多为 shell 权限限制）。 */
    val storageKb: Long? = null,
    /** 全局电池剩余（%）。始终有值（设备级）。 */
    val batteryPercent: Int? = null,
    /** 该应用自上次充电累计估算耗电（mAh）。null = 不可用。 */
    val powerMah: Double? = null
)

object AppStatsRepository {

    /** 取应用当前 PID（`pidof`）；多进程只取第一个；未运行返回 null。 */
    suspend fun resolvePid(pkg: String): Int? {
        if (pkg.isBlank()) return null
        val r = ShizukuShell.run("pidof $pkg")
        return r.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
    }

    /**
     * 采样一次 CPU 占用率（内部两次读取间隔约 1s）。
     * 需要应用正在运行（有 PID）；进程消失返回 null。
     */
    suspend fun sampleCpu(pid: Int): Double? = withContext(Dispatchers.IO) {
        val first = readProcCpuTicks(pid) ?: return@withContext null
        delay(1000L)
        val second = readProcCpuTicks(pid) ?: return@withContext null
        // /proc/pid/stat 的 utime/stime 单位是时钟Tick，Linux 上 CLK_TCK = 100。
        val deltaTicks = (second - first).toDouble()
        // 采样窗口约 1s；top 的 %CPU 也是「单核算口」，故不乘核数。
        val pct = deltaTicks / (1.0 * 100) * 100.0
        if (pct < 0) null else pct.coerceAtMost(100.0 * 8) // 上限放宽到 8 核，避免极端值
    }

    /** 读 `/proc/<pid>/stat` 的 utime+stime（时钟Tick）。comm 字段可能含空格/括号，从最后一个 ')' 后解析。 */
    private suspend fun readProcCpuTicks(pid: Int): Long? {
        val r = ShizukuShell.run("cat /proc/$pid/stat")
        val line = r.stdout.lines().firstOrNull { it.isNotBlank() } ?: return null
        val idx = line.lastIndexOf(')')
        if (idx < 0) return null
        val rest = line.substring(idx + 1).trim().split(Regex("\\s+"))
        // ')' 之后顺序：state ppid pgrp session tty_nr tpgid flags
        //            minflt cminflt majflt cmajflt utime stime
        val utime = rest.getOrNull(11)?.toLongOrNull() ?: return null
        val stime = rest.getOrNull(12)?.toLongOrNull() ?: 0
        return utime + stime
    }

    /** 内存 PSS（KB），via `dumpsys meminfo <pkg>`。 */
    suspend fun readMemoryKb(pkg: String): Long? {
        val r = ShizukuShell.run("dumpsys meminfo $pkg")
        if (r.error != null) return null
        r.stdout.lineSequence().forEach { l ->
            val t = l.trim()
            // 形如: "TOTAL  12345  6789  23456  ..."（PSS, swapPss, ...）
            if (t.startsWith("TOTAL") && !t.contains("TOTAL PSS")) {
                val nums = t.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                if (nums.isNotEmpty()) return nums[0]
            }
        }
        return null
    }

    /** 应用数据目录占用（KB）：/data/data + /sdcard/Android/data + obb。 */
    suspend fun readStorageKb(pkg: String): Long? {
        val r = ShizukuShell.run(
            "du -s -k /data/data/$pkg " +
                "/storage/emulated/0/Android/data/$pkg " +
                "/storage/emulated/0/Android/obb/$pkg 2>/dev/null"
        )
        if (r.error != null) return null
        var sum = 0L
        r.stdout.lines().forEach { l ->
            val parts = l.trim().split(Regex("\\s+"))
            parts.firstOrNull()?.toLongOrNull()?.let { sum += it }
        }
        return if (sum > 0) sum else null
    }

    /** 全局电池剩余（%）。 */
    suspend fun readBatteryPercent(): Int? {
        val r = ShizukuShell.run("dumpsys battery")
        r.stdout.lineSequence().forEach { l ->
            val t = l.trim()
            if (t.startsWith("level:")) {
                return t.substringAfter(":").trim().toIntOrNull()
            }
        }
        return null
    }

    /** 该应用自上次充电累计估算耗电（mAh），解析 `dumpsys batterystats`。 */
    suspend fun readPowerMah(pkg: String): Double? {
        val r = ShizukuShell.run("dumpsys batterystats $pkg 2>/dev/null")
        if (r.error != null) return null
        val lines = r.stdout.lines()
        var inEstimated = false
        for (l in lines) {
            val t = l.trim()
            if (t.contains("Estimated power use")) {
                inEstimated = true
                continue
            }
            if (inEstimated && t.startsWith("Uid")) {
                // 形如: "Uid 10123: 12.3 (cpu=.. screen=.. )"
                val colon = t.indexOf(':')
                if (colon >= 0) {
                    val valPart = t.substring(colon + 1).trim()
                    val mah = valPart.split(Regex("\\s+")).firstOrNull()
                        ?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
                    if (mah != null && mah > 0) return mah
                }
            }
        }
        return null
    }

    /** 一次性拉取全部指标（应用未运行则 cpu/pss 为 null）。 */
    suspend fun snapshot(pkg: String): AppStats = withContext(Dispatchers.IO) {
        val pid = resolvePid(pkg)
        val cpu = pid?.let { sampleCpu(it) }
        val mem = readMemoryKb(pkg)
        val storage = readStorageKb(pkg)
        val battery = readBatteryPercent()
        val power = readPowerMah(pkg)
        AppStats(
            cpuPercent = cpu,
            pssKb = mem,
            storageKb = storage,
            batteryPercent = battery,
            powerMah = power
        )
    }
}
