package com.xhsdn.core.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 从 note JSON 中提取媒体 URL 与 Live Photo 配对。
 * 复刻 [XHSDownloader.extractMediaUrlsFromNote]（java:1143-1331）的核心逻辑。
 */
object MediaUrlExtractor {

    /**
     * 单张图 / 视频配对。`isLivePhoto == true` 时 [videoUrl] 不为空。
     */
    data class MediaPair(
        var originalImageUrl: String?,
        var originalVideoUrl: String?,
        var imageUrl: String?,
        var videoUrl: String?,
        var isLivePhoto: Boolean,
    )

    /** 视频回调：用于触发 "检测到视频" 通知。 */
    fun interface OnVideoDetected {
        fun onVideoDetected()
    }

    /**
     * 抽取媒体 URL 与 Live Photo 配对。
     * @return 媒体 URL 列表（图/视频混合）
     */
    fun extract(
        note: JsonObject,
        mediaPairs: MutableList<MediaPair>,
        onVideoDetected: OnVideoDetected? = null,
    ): List<String> {
        val mediaUrls = mutableListOf<String>()
        var videosDetected = false

        // 1. 视频主帖
        val video = note["video"] as? JsonObject
        if (video != null) {
            val consumer = video["consumer"] as? JsonObject
            val originKey = consumer?.get("originVideoKey")?.jsonPrimitiveOrNull()
            if (originKey != null) {
                val videoUrl = "https://sns-video-bd.xhscdn.com/$originKey"
                mediaUrls += videoUrl
                videosDetected = true
                onVideoDetected?.onVideoDetected()
            } else {
                val media = video["media"] as? JsonObject
                val stream = media?.get("stream") as? JsonObject
                val h265 = stream?.get("h265") as? JsonArray
                if (h265 != null) {
                    for (item in h265) {
                        val url = when (item) {
                            is kotlinx.serialization.json.JsonPrimitive -> {
                                val s = item.content
                                if (s.startsWith("http")) s else null
                            }
                            is JsonObject -> {
                                item["url"]?.let { it.jsonPrimitiveOrNull() }
                                    ?: item["masterUrl"]?.let { it.jsonPrimitiveOrNull() }
                            }
                            else -> null
                        }
                        if (url != null) {
                            mediaUrls += url
                            videosDetected = true
                            onVideoDetected?.onVideoDetected()
                        }
                    }
                }
            }
        }

        // 2. 图片列表（图集 / Live Photo）
        val imageList: JsonArray? = when {
            note["imageList"] is JsonArray -> (note["imageList"] as JsonArray)
            note["images"] is JsonArray -> (note["images"] as JsonArray)
            note["image"] is JsonObject -> {
                val single = note["image"] as JsonObject
                JsonArray(listOf(single))
            }
            else -> null
        }

        if (imageList != null) {
            for (element in imageList) {
                val image = element as? JsonObject ?: continue
                val imageUrl = pickString(image, listOf("urlDefault", "url"))
                    ?: run {
                        val traceId = image["traceId"]?.let { it.jsonPrimitiveOrNull() }
                        if (traceId != null) "https://sns-img-qc.xhscdn.com/$traceId" else null
                    }
                    ?: run {
                        val infoList = image["infoList"] as? JsonArray
                        infoList?.firstOrNull()?.let { (it as? JsonObject)?.get("url")?.let { u -> u.jsonPrimitiveOrNull() } }
                    }

                // Live Photo: image.stream.h264[0].masterUrl / url
                val livePhotoVideoUrl = run {
                    val stream = image["stream"] as? JsonObject
                    val h264 = stream?.get("h264") as? JsonArray
                    val first = h264?.firstOrNull()
                    when (first) {
                        is JsonObject -> {
                            first["masterUrl"]?.let { it.jsonPrimitiveOrNull() }
                                ?: first["url"]?.let { it.jsonPrimitiveOrNull() }
                        }
                        else -> null
                    }
                }

                if (imageUrl != null) {
                    if (livePhotoVideoUrl != null) {
                        mediaPairs += MediaPair(imageUrl, livePhotoVideoUrl, imageUrl, livePhotoVideoUrl, true)
                        videosDetected = true
                        onVideoDetected?.onVideoDetected()
                    } else {
                        mediaPairs += MediaPair(imageUrl, null, imageUrl, null, false)
                    }
                }
            }
        }

        return mediaUrls
    }

    private fun pickString(obj: JsonObject, keys: List<String>): String? {
        for (k in keys) {
            val v = obj[k]?.let { it.jsonPrimitiveOrNull() }
            if (v != null) return v
        }
        return null
    }

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): String? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.let { if (it.isString) it.content else null }
}
