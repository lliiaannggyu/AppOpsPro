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
 * 「一键授予全部权限」的二次确认弹窗。
 *
 * 这是一次性写入上百条 AppOps 的高影响操作，且**不可一键撤销**
 * （要恢复只能逐条改回，或 `adb shell cmd appops reset <pkg>`），
 * 因此必须二次确认，并在文案里说清后果与不可写条数。
 *
 * @param opCount 实际会尝试写入的条数（已扣除受系统保护的 op）
 */
@Composable
fun GrantAllConfirmDialog(
    visible: Boolean,
    appLabel: String,
    opCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "授予全部权限？",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "将把「$appLabel」的 $opCount 项权限全部置为「允许」。\n" +
                        "影响较大，无法一键撤销，受保护项会被跳过。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        // ⚠️ 不要给 Wear M3E AlertDialog 的按钮加 Modifier.fillMaxWidth()：
        // 它把 confirmButton / dismissButton 放在**同一行**里，各自 fillMaxWidth 会让
        // 行宽变成 2 倍屏宽，第二个按钮被挤出屏幕（实测 bounds 右边界 454 > 屏宽 454）。
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("授予")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
