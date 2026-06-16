package com.xhsdn.core.url

/**
 * 从 XHS URL 中提取 noteId（帖子 ID）。
 * 复刻 [XHSDownloader.extractPostId]（java:651-695），处理 `explore/...`、`item/...`、短链 `xhslink.com/...` 三种情况。
 */
object PostIdExtractor {
    private val ID_PATTERN = Regex("(?:explore|item)/([a-zA-Z0-9_\\-]+)/?(?:\\?|$)")
    private val ID_USER_PATTERN = Regex("user/profile/[a-z0-9]+/([a-zA-Z0-9_\\-]+)/?(?:\\?|$)")

    fun extractPostId(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        ID_PATTERN.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        ID_USER_PATTERN.find(url)?.groupValues?.getOrNull(1)?.let { return it }

        // 短链兜底：xhslink.com/路径，ID 通常在最后一段
        if (url.contains("xhslink.com/")) {
            val parts = url.split("/")
            if (parts.isNotEmpty()) {
                var last = parts.last()
                if (last.contains("?")) last = last.substringBefore("?")
                if (last.isNotEmpty() && last != "o") return last
                if (parts.size > 1) {
                    var second = parts[parts.size - 2]
                    if (second.contains("?")) second = second.substringBefore("?")
                    if (second.isNotEmpty()) return second
                }
            }
        }
        return null
    }
}
