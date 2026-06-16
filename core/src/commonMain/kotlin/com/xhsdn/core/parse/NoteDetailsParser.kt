package com.xhsdn.core.parse

import com.xhsdn.core.url.UrlTransformer
import kotlinx.serialization.json.JsonObject

/**
 * 把一个 note 解析为媒体 URL 列表 + Live Photo 配对。
 * 复刻 [XHSDownloader.parsePostDetails]（java:1054-1135）的核心去重 + CDN 转换逻辑。
 */
object NoteDetailsParser {

    data class ParseResult(
        val mediaUrls: List<String>,
        val livePhotoPairs: List<MediaUrlExtractor.MediaPair>,
    )

    fun parse(html: String?, onVideoDetected: MediaUrlExtractor.OnVideoDetected? = null): ParseResult {
        val root = InitialStateParser.parse(html)
            ?: return ParseResult(emptyList(), emptyList())

        val notes = NoteFinder.findNoteObjects(root)
        val allMediaPairs = mutableListOf<MediaUrlExtractor.MediaPair>()
        val allMediaUrls = mutableListOf<String>()

        for (note in notes) {
            val urls = MediaUrlExtractor.extract(note, allMediaPairs, onVideoDetected)
            allMediaUrls += urls
        }

        // 对 Live Photo 配对做 CDN URL 转换
        for (pair in allMediaPairs) {
            pair.originalImageUrl?.let { pair.imageUrl = UrlTransformer.transformXhsCdnUrl(it) }
            pair.originalVideoUrl?.let { pair.videoUrl = UrlTransformer.transformXhsCdnUrl(it) }
        }

        // 重组 mediaUrls：Live Photo 的图 + 视频按顺序排在前，其余 mediaUrls 去重追加
        val ordered = mutableListOf<String>()
        val livePhotoPairs = allMediaPairs.filter { it.isLivePhoto }
        for (pair in livePhotoPairs) {
            pair.imageUrl?.let { ordered += it }
            pair.videoUrl?.let { ordered += it }
        }
        for (url in allMediaUrls) {
            if (url !in ordered) ordered += url
        }

        return ParseResult(ordered, livePhotoPairs)
    }

    /** 从纯 URL 列表的解析（无 HTML）— 用于选择性下载的二次处理。 */
    fun parseNoteDirectly(note: JsonObject, onVideoDetected: MediaUrlExtractor.OnVideoDetected? = null): ParseResult {
        val pairs = mutableListOf<MediaUrlExtractor.MediaPair>()
        val urls = MediaUrlExtractor.extract(note, pairs, onVideoDetected)
        for (pair in pairs) {
            pair.originalImageUrl?.let { pair.imageUrl = UrlTransformer.transformXhsCdnUrl(it) }
            pair.originalVideoUrl?.let { pair.videoUrl = UrlTransformer.transformXhsCdnUrl(it) }
        }
        return ParseResult(urls, pairs.filter { it.isLivePhoto })
    }
}
