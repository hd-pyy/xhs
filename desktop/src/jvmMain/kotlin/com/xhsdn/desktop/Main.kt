package com.xhsdn.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.xhsdn.core.PlatformInitializer
import com.xhsdn.core.platform.DesktopAppNotifier
import com.xhsdn.desktop.tray.TrayNotifier
import kotlinx.coroutines.runBlocking

/**
 * Windows 桌面版入口。
 *
 * - 调用 [PlatformInitializer.init] 注入 actual 平台实现
 * - 注册 AWT 托盘，把 [TrayNotifier.show] 桥接到 [DesktopAppNotifier]
 * - 启动一个 Compose 窗口，托管 [App] 内容
 */
fun main() = runBlocking {
    PlatformInitializer.init()
    PlatformInitializer.taskManager.init()

    runCatching { TrayNotifier.init() }
    DesktopAppNotifier.setTrayHook { title, text -> TrayNotifier.show(title, text) }

    application {
        val windowState = rememberWindowState(size = DpSize(960.dp, 720.dp))
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "XHS Downloader",
        ) {
            App()
        }
    }
}
