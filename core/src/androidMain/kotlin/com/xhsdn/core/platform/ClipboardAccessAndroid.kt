package com.xhsdn.core.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Android 端 [ClipboardAccess] 实现。
 */
class AndroidClipboardAccess(appContext: Context) : ClipboardAccess {
    private val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun getText(): String? {
        return runCatching {
            val clip = cm.primaryClip ?: return@runCatching null
            if (clip.itemCount == 0) return@runCatching null
            clip.getItemAt(0).text?.toString()
        }.getOrNull()
    }

    override fun setText(text: String) {
        cm.setPrimaryClip(ClipData.newPlainText("xhsdn", text))
    }
}
