package com.xhsdn.core

/** 阶段 1 占位（androidMain）。阶段 4 起会加入 FileStorage / LivePhotoWriter 等 actual 实现。 */
internal actual fun platformName(): String = "Android"
