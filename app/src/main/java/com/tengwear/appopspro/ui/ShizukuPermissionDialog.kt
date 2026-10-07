package com.tengwear.appopspro.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * Shizuku 权限申请弹窗（Wear M3E AlertDialog）。
 *
 * 依据 Wear M3E 的 AlertDialog 签名：
 *   - title: @Composable () -> Unit        （必填）
 *   - text:  @Composable (() -> Unit)?     （可选）
 *   - confirmButton / dismissButton: @Composable RowScope.() -> Unit
 * 尾随 content 是 ScalingLazyListScope.() -> Unit，非 @Composable，不使用。
 */
@Composable
fun ShizukuPermissionDialog(
    visible: Boolean,
    binderAvailable: Boolean,
    permissionGranted: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val title: String
    val message: String
    val confirmText: String

    when {
        !binderAvailable -> {
            title = "Shizuku 未运行"
            message = "请先打开 Shizuku 应用并启动服务，然后回到本应用重新尝试。"
            confirmText = "重试"
        }
        !permissionGranted -> {
            title = "需要 Shizuku 授权"
            message = "本应用需要通过 Shizuku 调用系统 AppOps 接口来管理其他应用的权限。"
            confirmText = "去授权"
        }
        else -> {
            title = "已授权"
            message = "Shizuku 权限已获得，可以开始使用。"
            confirmText = "开始使用"
        }
    }

    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("取消")
            }
        }
    )
}