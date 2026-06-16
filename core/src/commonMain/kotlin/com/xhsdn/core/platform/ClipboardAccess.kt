package com.xhsdn.core.platform

/**
 * 跨平台剪贴板访问。
 * Android 用 ClipboardManager，Desktop 用 java.awt.Toolkit。
 */
interface ClipboardAccess {
    fun getText(): String?
    fun setText(text: String)
}
