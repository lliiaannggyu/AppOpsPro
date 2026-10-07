package com.tengwear.appopspro.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * 剪贴板读写 + Toast 的共用扩展。
 *
 * 命令行页（粘贴命令、复制输出）和关于页（复制交流群号）都要用，
 * 抽出来避免两处各写一份。
 */

/** 读系统剪贴板首个条目的纯文本；为空或不可读时返回 null。 */
fun Context.readClipboardText(): String? = try {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = manager?.primaryClip
    if (clip == null || clip.itemCount == 0) {
        null
    } else {
        clip.getItemAt(0).coerceToText(this)?.toString()
    }
} catch (e: Exception) {
    null
}

/** 写入系统剪贴板。 */
fun Context.copyToClipboard(label: String, text: String) {
    try {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Exception) {
    }
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
