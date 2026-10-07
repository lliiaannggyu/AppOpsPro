package com.tengwear.appopspro.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 单个 AppOp 权限条目。
 *
 * @param group 所属分组，取值见 [AppOpsCatalog.GROUP_*]，用于权限列表分节展示。
 * @param isPresent 该 op 在系统里是否有记录。false 表示按默认态合成展示（尚未被显式设置过）。
 * @param permission 该 op 对应的权限名；null 表示非权限类 op（如音频音量、后台运行等系统内部 op）。
 * @param permissionDeclared 目标应用清单里是否**声明**了 [permission]。
 *   `permission == null` 时恒为 false —— 非权限类 op 不属于「应用需要的权限」。
 * @param writable 该 op 能否真正写入系统。推导依据见
 *   [com.tengwear.appopspro.AppOpsRepository.resolveWritable]，并且写入后仍会回读校验。
 */
data class AppOpInfo(
    val opName: String,
    val opCode: Int,
    val label: String,
    val mode: Int,
    val group: String = AppOpsCatalog.GROUP_SYSTEM,
    val isPresent: Boolean = true,
    val permission: String? = null,
    val permissionDeclared: Boolean = false,
    val writable: Boolean = true
) {
    val modeLabel: String
        get() = when (mode) {
            MODE_ALLOWED -> "允许"
            MODE_IGNORED -> "拒绝"
            MODE_ASK -> "询问"
            else -> "默认"
        }

    val isAllowed: Boolean get() = mode == MODE_ALLOWED

    /** 是否已被**显式设置**（非系统默认态）。 */
    val isExplicit: Boolean get() = mode != MODE_DEFAULT

    /**
     * 是否属于「该应用需要的权限」，即权限列表默认展示的范围。
     *
     * 判定为「相关」的两种情形：
     * 1. 应用清单里声明了该 op 对应的权限（`permissionDeclared`）；
     * 2. 该项已被显式设置过（`isExplicit`）—— 包括用户此前在别处改过的，
     *    这类必须展示，否则用户看不到、也改不回来。
     *
     * 注意**不**把 `isPresent` 作为判据：`getOpsForPackage` 会为大量「从未被设置过」的
     * op 返回默认条目（实测 137/147），拿它当相关性会导致过滤形同虚设。
     */
    val relevant: Boolean get() = permissionDeclared || isExplicit

    companion object {
        const val MODE_ALLOWED = 0
        const val MODE_IGNORED = 1
        const val MODE_ASK = 2
        const val MODE_DEFAULT = 3
    }
}

/**
 * 已安装应用信息（精简）
 *
 * @param isSystem 是否为系统应用
 * @param isEnabled 应用是否处于启用状态。false = 被 `pm disable-user` 冻结（或系统停用）。
 *   冻结的应用仍会出现在列表里，否则用户解冻不了。
 * @param icon 应用图标（已解码为 [ImageBitmap]，可直接在 Compose 中绘制）；
 *             加载失败时为 null，UI 侧回退显示占位图标。
 */
data class InstalledApp(
    val packageName: String,
    val appLabel: String,
    val uid: Int,
    val isSystem: Boolean,
    val isEnabled: Boolean = true,
    val icon: ImageBitmap? = null
)
