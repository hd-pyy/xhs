package com.xhsdn.desktop.tray

import java.awt.Image
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

/**
 * 桌面端系统托盘通知。
 * 通过 AWT [TrayIcon] 实现（不需要 JNA 也能在 Windows 弹气泡通知）。
 */
object TrayNotifier {

    private var trayIcon: TrayIcon? = null

    fun init() {
        if (!SystemTray.isSupported()) {
            println("[tray] SystemTray not supported on this platform")
            return
        }
        val tray = SystemTray.getSystemTray()
        val image: Image? = runCatching {
            Toolkit.getDefaultToolkit().createImage("icon.png")
        }.getOrNull()
        val icon = TrayIcon(image ?: createEmptyImage(), "XHS Downloader")
        icon.isImageAutoSize = true
        icon.toolTip = "XHS Downloader"
        tray.add(icon)
        trayIcon = icon
    }

    fun show(title: String, text: String) {
        val icon = trayIcon ?: return
        icon.displayMessage(title, text, TrayIcon.MessageType.INFO)
    }

    private fun createEmptyImage(): Image =
        java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB)
}
