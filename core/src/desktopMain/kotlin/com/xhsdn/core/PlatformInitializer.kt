package com.xhsdn.core

import com.xhsdn.core.platform.DesktopAppNotifier
import com.xhsdn.core.platform.DesktopClipboardAccess
import com.xhsdn.core.platform.DesktopFileStorage
import com.xhsdn.core.platform.DesktopKeyValueStore
import com.xhsdn.core.platform.DesktopLivePhotoWriter
import com.xhsdn.core.platform.PlatformContext
import com.xhsdn.core.task.TaskManager
import com.xhsdn.core.task.TaskPersistence
import java.io.File

/**
 * Desktop 端启动初始化。
 * 在 [com.xhsdn.desktop.MainKt.main] 最前面调用一次。
 */
object PlatformInitializer {

    lateinit var taskManager: TaskManager
        private set

    fun init() {
        PlatformContext.set(
            fileStorage = DesktopFileStorage(),
            livePhotoWriter = DesktopLivePhotoWriter,
            keyValueStore = DesktopKeyValueStore(),
            appNotifier = DesktopAppNotifier,
            clipboardAccess = DesktopClipboardAccess,
        )

        val appDataDir = File(System.getProperty("user.home") ?: ".", ".xhsdn").also { it.mkdirs() }
        val persistence = TaskPersistence(File(appDataDir, "history.json"))
        taskManager = TaskManager(persistence)
    }
}
