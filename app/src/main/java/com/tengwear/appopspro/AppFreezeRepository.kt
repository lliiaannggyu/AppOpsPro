package com.tengwear.appopspro

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

/**
 * 应用冻结 / 解冻。
 *
 * ## 实现方式
 *
 * 走 `pm disable-user --user 0 <pkg>` / `pm enable --user 0 <pkg>`（经 [ShizukuShell] 以 shell 身份执行），
 * 效果等同于系统设置里的「停用应用」：
 * - 冻结后应用图标消失、进程被杀、无法被拉起、不再接收广播
 * - 解冻后恢复原状，数据不丢
 *
 * 之所以不直接调 `IPackageManager.setApplicationEnabledSetting`：
 * `dev.rikka.hidden:stub` 里只带了 35 个方法，**没有** `setApplicationEnabledSetting`
 * （实测 `javap` 确认），要调它就得手拼 Parcel + 反射取 transaction code。
 * 而 `pm` 是官方 CLI，跨版本稳定、错误信息明确，复用已有的命令执行通道即可。
 *
 * ## 安全护栏
 *
 * 冻结系统关键应用（SystemUI / Launcher / Settings 等）会让手表直接不可用，
 * 且因为界面没了，用户**没法再解冻**。所以 [isProtected] 会硬拦这些包。
 */
object AppFreezeRepository {

    private const val TAG = "AppOpsPro"

    /**
     * 绝对不能冻结的包特征（小写匹配）。
     *
     * 这些应用一旦被停用，桌面/状态栏/设置会直接消失，用户将无法自行恢复。
     */
    private val PROTECTED_KEYWORDS = listOf(
        "systemui",
        "launcher",
        "home",
        "settings",
        "companion",
        "watchface",
        "wearable.app",
    )

    /** 冻结状态的判定结果。 */
    enum class State { FROZEN, ACTIVE }

    data class FreezeResult(
        val success: Boolean,
        val message: String
    )

    /**
     * 该包是否被硬保护（不允许冻结）。
     *
     * @param selfPackage 本应用自己的包名 —— 冻结自己等于把工具本身弄没了，必须拦。
     */
    fun isProtected(packageName: String, selfPackage: String): Boolean {
        if (packageName == selfPackage) return true
        if (packageName == "android") return true
        val lower = packageName.lowercase()
        return PROTECTED_KEYWORDS.any { lower.contains(it) }
    }

    /** 读取当前冻结状态。读不到时按「正常」处理，避免误报。 */
    fun stateOf(context: Context, packageName: String): State = try {
        val info = context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.MATCH_DISABLED_COMPONENTS
        )
        if (info.enabled) State.ACTIVE else State.FROZEN
    } catch (e: Exception) {
        Log.w(TAG, "stateOf failed: $packageName", e)
        State.ACTIVE
    }

    /**
     * 冻结 / 解冻应用，并**回读校验**（`pm` 返回 0 不代表一定生效）。
     */
    suspend fun setFrozen(
        context: Context,
        packageName: String,
        selfPackage: String,
        frozen: Boolean
    ): FreezeResult {
        if (isProtected(packageName, selfPackage)) {
            return FreezeResult(false, "该系统组件受保护，不能冻结")
        }

        val command = if (frozen) {
            "pm disable-user --user 0 $packageName"
        } else {
            "pm enable --user 0 $packageName"
        }

        val result = ShizukuShell.run(command)
        if (result.error != null) {
            return FreezeResult(false, result.error)
        }
        if (result.exitCode != 0) {
            val detail = result.displayText.lines().firstOrNull { it.isNotBlank() }.orEmpty()
            return FreezeResult(false, "命令失败（exit=${result.exitCode}）$detail".trim())
        }

        // 回读校验：pm 的返回码不可全信，以系统里真实的 enabled 状态为准
        val actual = stateOf(context, packageName)
        val expected = if (frozen) State.FROZEN else State.ACTIVE
        return if (actual == expected) {
            FreezeResult(true, if (frozen) "已冻结" else "已解冻")
        } else {
            FreezeResult(false, if (frozen) "冻结未生效（系统拒绝）" else "解冻未生效（系统拒绝）")
        }
    }

    /** 供 UI 展示：该应用是否属于「系统应用」。 */
    fun isSystemApp(context: Context, packageName: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(
            packageName,
            PackageManager.MATCH_DISABLED_COMPONENTS
        )
        (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    } catch (e: Exception) {
        false
    }
}
