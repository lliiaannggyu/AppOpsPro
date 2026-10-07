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
 * 应用列表权限引导弹窗。
 * 当 QUERY_ALL_PACKAGES 被系统限制导致应用列表为空时显示。
 */
@Composable
fun AppListPermissionDialog(
    visible: Boolean,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "需要应用列表权限",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "本应用需要读取已安装应用列表的权限，" +
                        "才能管理其他应用的权限。\n\n" +
                        "请点击下方按钮前往设置检查权限。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        // ⚠️ 不要给 Wear M3E AlertDialog 的按钮加 Modifier.fillMaxWidth()：
        // 它把 confirmButton / dismissButton 放在**同一行**，各自 fillMaxWidth 会让行宽
        // 变成 2 倍屏宽，第二个按钮被挤出屏幕（实测右边界超出屏宽）。
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text("前往设置")
            }
        },
        dismissButton = {
            Button(onClick = onRetry) {
                Text("重新检查")
            }
        }
    )
}