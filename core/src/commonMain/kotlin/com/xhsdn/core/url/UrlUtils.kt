package com.xhsdn.core.url

/**
 * 通用 URL 工具。从 [app/.../utils/UrlUtils.kt] 迁出，
 * 保持与 Android 端原 [com.neoruaa.xhsdn.utils.UrlUtils] 同名 API，调用方零改动。
 */
object UrlUtils {
    private val URL_REGEX = Regex("(?:https?|ftp)://[^\\s]+")

    /** 从一段文本中抽取第一个看起来像 URL 的字符串。 */
    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val match = URL_REGEX.find(text) ?: return null
        return match.value
    }

    /** 是否为 XHS 链接（任何已知格式）。 */
    fun isXhsLink(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return LinkExtractor.extractLinks(url).isNotEmpty() ||
            url.contains("xiaohongshu.com") ||
            url.contains("xhslink.com")
    }
}
