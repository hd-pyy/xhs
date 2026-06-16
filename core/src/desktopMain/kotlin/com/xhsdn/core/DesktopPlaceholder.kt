package com.xhsdn.core

/** 阶段 1 占位（desktopMain）。阶段 5 起会加入 FileStorage / LivePhotoWriter / KeyValueStore / AppNotifier / ClipboardAccess 等 actual 实现。 */
internal actual fun platformName(): String = "Desktop"
