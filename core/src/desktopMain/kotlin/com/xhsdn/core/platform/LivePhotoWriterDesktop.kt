package com.xhsdn.core.platform

/**
 * Desktop 端 [LivePhotoWriter] 实现。
 * 不合成 iPhone/MIUI 动态照片容器；始终返回 false，触发 orchestrator 的 fallback 分支分别保存图与视频。
 */
object DesktopLivePhotoWriter : LivePhotoWriter {
    override suspend fun create(imagePath: String, videoPath: String, outputPath: String): Boolean = false
}
