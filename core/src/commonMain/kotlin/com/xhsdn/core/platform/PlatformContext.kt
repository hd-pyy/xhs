package com.xhsdn.core.platform

/**
 * 平台上下文聚合。入口模块（:app / :desktop）在启动时调用 [set] 注入 actual 实现。
 * 业务代码通过 [PlatformContext.current.xxx] 访问，避免到处传。
 */
object PlatformContext {
    private var _fileStorage: FileStorage? = null
    private var _livePhotoWriter: LivePhotoWriter? = null
    private var _keyValueStore: KeyValueStore? = null
    private var _appNotifier: AppNotifier? = null
    private var _clipboardAccess: ClipboardAccess? = null

    fun set(
        fileStorage: FileStorage,
        livePhotoWriter: LivePhotoWriter,
        keyValueStore: KeyValueStore,
        appNotifier: AppNotifier,
        clipboardAccess: ClipboardAccess,
    ) {
        _fileStorage = fileStorage
        _livePhotoWriter = livePhotoWriter
        _keyValueStore = keyValueStore
        _appNotifier = appNotifier
        _clipboardAccess = clipboardAccess
    }

    val current: Current
        get() = Current(
            fileStorage = requireNotNull(_fileStorage) { "PlatformContext not initialized" },
            livePhotoWriter = requireNotNull(_livePhotoWriter) { "PlatformContext not initialized" },
            keyValueStore = requireNotNull(_keyValueStore) { "PlatformContext not initialized" },
            appNotifier = requireNotNull(_appNotifier) { "PlatformContext not initialized" },
            clipboardAccess = requireNotNull(_clipboardAccess) { "PlatformContext not initialized" },
        )

    data class Current(
        val fileStorage: FileStorage,
        val livePhotoWriter: LivePhotoWriter,
        val keyValueStore: KeyValueStore,
        val appNotifier: AppNotifier,
        val clipboardAccess: ClipboardAccess,
    )
}
