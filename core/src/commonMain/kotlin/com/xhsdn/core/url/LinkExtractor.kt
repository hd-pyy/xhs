package com.xhsdn.core.url

/**
 * 从用户输入（剪贴板、粘贴、手动输入）中提取 XHS 链接。
 * 复刻 [XHSDownloader.extractLinks] 逻辑（java:592-649），改为纯 Kotlin。
 */
object LinkExtractor {
    private val XHS_LINK_PATTERN = Regex("(?:https?://)?www\\.xiaohongshu\\.com/explore/\\S+")
    private val XHS_USER_PATTERN = Regex("(?:https?://)?www\\.xiaohongshu\\.com/user/profile/[a-z0-9]+/\\S+")
    private val XHS_SHARE_PATTERN = Regex("(?:https?://)?www\\.xiaohongshu\\.com/discovery/item/\\S+")
    private val XHS_SHORT_PATTERN = Regex("(?:https?://)?xhslink\\.com/[^\\s\\\"<>\\\\\\^`{|}，。；！？、【】《》]+")

    fun extractLinks(input: String?): List<String> {
        if (input.isNullOrEmpty()) return emptyList()
        val urls = mutableListOf<String>()
        val parts = input.split("\\s+".toRegex())
        for (part in parts) {
            if (part.isBlank()) continue
            val shortMatcher = XHS_SHORT_PATTERN.find(part)
            if (shortMatcher != null) {
                val shortUrl = part.substring(shortMatcher.range.first, shortMatcher.range.last + 1)
                urls += shortUrl // 短链需要 [ShortLinkResolver] 再 follow redirect 后再决定
                continue
            }
            val shareMatcher = XHS_SHARE_PATTERN.find(part)
            if (shareMatcher != null) {
                urls += part.substring(shareMatcher.range.first, shareMatcher.range.last + 1)
                continue
            }
            val linkMatcher = XHS_LINK_PATTERN.find(part)
            if (linkMatcher != null) {
                urls += part.substring(linkMatcher.range.first, linkMatcher.range.last + 1)
                continue
            }
            val userMatcher = XHS_USER_PATTERN.find(part)
            if (userMatcher != null) {
                urls += part.substring(userMatcher.range.first, userMatcher.range.last + 1)
            }
        }
        return urls
    }
}
