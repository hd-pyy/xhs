package com.xhsdn.core.url

/**
 * xhscdn.com URL → ci.xiaohongshu.com 转换器。
 * 复刻 [XHSDownloader.transformXhsCdnUrl]（java:2245-2274）。
 * 视频 URL（包含 `video` 或 `sns-video`）保持原样不转换。
 */
object UrlTransformer {
    fun transformXhsCdnUrl(originalUrl: String?): String {
        if (originalUrl.isNullOrEmpty()) return originalUrl ?: ""
        if (!originalUrl.contains("xhscdn.com")) return originalUrl
        if (originalUrl.contains("video") || originalUrl.contains("sns-video")) return originalUrl

        val parts = originalUrl.split("/")
        if (parts.size <= 5) return originalUrl
        val tokenBuilder = StringBuilder()
        for (i in 5 until parts.size) {
            if (i > 5) tokenBuilder.append("/")
            tokenBuilder.append(parts[i])
        }
        val fullToken = tokenBuilder.toString()
        val token = fullToken.split("[!?]".toRegex()).firstOrNull()?.takeIf { it.isNotEmpty() }
            ?: return originalUrl
        return "https://ci.xiaohongshu.com/$token"
    }
}
