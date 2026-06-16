package com.xhsdn.core.download

/**
 * 文件扩展名与视频 URL 识别。
 * 复刻 [XHSDownloader.determineFileExtension] + [XHSDownloader.isVideoUrl]（java:561-590 / 2189-2194）。
 */
object MediaExtensionUtil {

    /**
     * 决定文件扩展名（不含点）。
     * 对 xhscdn.com 默认 jpg，除非包含视频特征（h264 / stream / masterUrl / video）。
     */
    fun determineFileExtension(url: String?): String {
        if (url.isNullOrEmpty()) return "jpg"
        val lower = url.lowercase()
        return when {
            lower.contains(".jpg") || lower.contains(".jpeg") -> "jpg"
            lower.contains(".png") -> "png"
            lower.contains(".gif") -> "gif"
            lower.contains(".webp") -> "webp"
            lower.contains(".mp4") || lower.contains("video") ||
                lower.contains("masterurl") || lower.contains("stream") -> "mp4"
            url.contains("xhscdn.com") ->
                if (url.contains("h264") || url.contains("stream")) "mp4" else "jpg"
            else -> "jpg"
        }
    }

    /** 是否视频 URL（用于文件类型分发） */
    fun isVideoUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val lower = url.lowercase()
        return lower.contains(".mp4") || lower.contains(".mov") || lower.contains(".avi") ||
            lower.contains(".webm") || lower.contains("video") ||
            lower.contains("masterurl") || lower.contains("stream") ||
            lower.contains("sns-video") || lower.contains("/spectrum/")
    }

    /** 是否主帖视频（非 Live Photo 配对里的视频流） */
    fun isMainPostVideoUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.contains("sns-video-bd") &&
            (url.contains("pre_post") || url.contains("originVideoKey"))
    }
}
