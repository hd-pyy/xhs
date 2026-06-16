package com.xhsdn.core.platform

/**
 * 跨平台通知抽象。
 * Android 走 NotificationManager，Desktop 走 println / TrayIcon（MVP）。
 */
interface AppNotifier {
    fun notify(id: Int, title: String, text: String, progress: Int? = null)
    fun cancel(id: Int)
}
