package com.tengwear.appopspro

import android.content.pm.IPackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku 权限管理器。
 */
object ShizukuManager {

    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    @Volatile
    var isBinderAvailable = false
        private set

    @Volatile
    var isPermissionGranted = false
        private set

    var onPermissionStateChanged: ((granted: Boolean) -> Unit)? = null

    // ── Binder 监听 ────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        isBinderAvailable = true
        requestPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isBinderAvailable = false
        isPermissionGranted = false
        onPermissionStateChanged?.invoke(false)
    }

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                isPermissionGranted =
                    grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                onPermissionStateChanged?.invoke(isPermissionGranted)
            }
        }

    // ── 初始化 ─────────────────────────────────────────

    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        if (Shizuku.pingBinder()) {
            isBinderAvailable = true
        }
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    // ── 权限请求 ───────────────────────────────────────

    /**
     * 显式请求 Shizuku 权限。
     * 调用前先通过 isBinderAvailable 判断 Shizuku 服务是否在运行。
     */
    fun requestPermission() {
        if (!isBinderAvailable) {
            return
        }
        try {
            if (Shizuku.isPreV11()) return

            if (Shizuku.checkSelfPermission() ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                isPermissionGranted = true
                onPermissionStateChanged?.invoke(true)
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: IllegalStateException) {
            // Binder 尚未就绪，忽略
        }
    }

    // ── 获取系统服务 ───────────────────────────────────

    fun getActivityManager(): android.app.IActivityManager {
        val binder: IBinder = SystemServiceHelper.getSystemService("activity")
        return android.app.IActivityManager.Stub.asInterface(ShizukuBinderWrapper(binder))
    }

    fun getPackageManager(): IPackageManager {
        val binder: IBinder = SystemServiceHelper.getSystemService("package")
        return IPackageManager.Stub.asInterface(ShizukuBinderWrapper(binder))
    }

    // ── 状态检查 ───────────────────────────────────────

    fun isReady(): Boolean = isBinderAvailable && isPermissionGranted

    fun isAdbMode(): Boolean = try {
        Shizuku.getUid() == 2000
    } catch (e: Exception) {
        false
    }
}