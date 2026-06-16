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

    // 用户自定义保存目录在 Windows 注册表里的 key
    private const val PREF_PICTURES_DIR = "save.pictures.dir"
    private const val PREF_VIDEOS_DIR = "save.videos.dir"

    fun init() {
        val kv = DesktopKeyValueStore()
        val fileStorage = DesktopFileStorage().apply {
            // 启动时恢复用户之前选过的目录（如果有）
            kv.getString(PREF_PICTURES_DIR, null)?.let { setPicturesBaseDir(File(it)) }
            kv.getString(PREF_VIDEOS_DIR, null)?.let { setVideosBaseDir(File(it)) }
        }

        PlatformContext.set(
            fileStorage = fileStorage,
            livePhotoWriter = DesktopLivePhotoWriter,
            keyValueStore = kv,
            appNotifier = DesktopAppNotifier,
            clipboardAccess = DesktopClipboardAccess,
        )

        val appDataDir = File(System.getProperty("user.home") ?: ".", ".xhsdn").also { it.mkdirs() }
        val persistence = TaskPersistence(File(appDataDir, "history.json"))
        taskManager = TaskManager(persistence)
    }
}
