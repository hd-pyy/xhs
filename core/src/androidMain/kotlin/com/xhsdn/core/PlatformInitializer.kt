package com.xhsdn.core

import android.content.Context
import com.xhsdn.core.platform.AndroidAppNotifier
import com.xhsdn.core.platform.AndroidClipboardAccess
import com.xhsdn.core.platform.AndroidFileStorage
import com.xhsdn.core.platform.AndroidKeyValueStore
import com.xhsdn.core.platform.AndroidLivePhotoWriter
import com.xhsdn.core.platform.PlatformContext

/**
 * Android 端 [PlatformContext] 初始化入口。
 * 在 [com.neoruaa.xhsdn.XHSApplication.onCreate] 中调用一次。
 */
object PlatformInitializer {
    fun init(appContext: Context) {
        val ctx = appContext.applicationContext
        PlatformContext.set(
            fileStorage = AndroidFileStorage(ctx),
            livePhotoWriter = AndroidLivePhotoWriter(ctx),
            keyValueStore = AndroidKeyValueStore(ctx),
            appNotifier = AndroidAppNotifier(ctx),
            clipboardAccess = AndroidClipboardAccess(ctx),
        )
    }
}
