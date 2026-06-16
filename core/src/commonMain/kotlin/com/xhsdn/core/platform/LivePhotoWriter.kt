package com.xhsdn.core.platform

/**
 * 跨平台 Live Photo 合成。
 * Desktop 端 always-返回 false（不合成，触发 orchestrator 的 fallback 分支）。
 */
interface LivePhotoWriter {
    /** 返回 true 表示成功；false 表示失败或不支持，调用方应走 fallback。 */
    suspend fun create(imagePath: String, videoPath: String, outputPath: String): Boolean
}
