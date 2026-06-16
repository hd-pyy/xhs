package com.xhsdn.core.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkExtractorTest {

    @Test
    fun extractLinks_fromExploreUrl() {
        val text = "https://www.xiaohongshu.com/explore/abc123?xsec_token=foo"
        val links = LinkExtractor.extractLinks(text)
        assertTrue(links.isNotEmpty(), "应当识别 explore 链接")
        assertTrue(links.first().contains("explore/abc123"))
    }

    @Test
    fun extractLinks_fromShortUrl() {
        val text = "http://xhslink.com/o/abc123"
        val links = LinkExtractor.extractLinks(text)
        assertTrue(links.isNotEmpty(), "应当识别短链")
        assertTrue(links.first().contains("xhslink.com"))
    }

    @Test
    fun extractLinks_fromShareUrl() {
        val text = "https://www.xiaohongshu.com/discovery/item/xyz789"
        val links = LinkExtractor.extractLinks(text)
        assertTrue(links.isNotEmpty(), "应当识别 discovery/item 链接")
    }

    @Test
    fun extractLinks_emptyInput() {
        assertEquals(emptyList(), LinkExtractor.extractLinks(""))
        assertEquals(emptyList(), LinkExtractor.extractLinks(null))
    }

    @Test
    fun extractPostId_explore() {
        assertEquals("abc123", PostIdExtractor.extractPostId("https://www.xiaohongshu.com/explore/abc123"))
    }

    @Test
    fun extractPostId_shortLink() {
        assertEquals("abc123", PostIdExtractor.extractPostId("http://xhslink.com/o/abc123"))
    }

    @Test
    fun extractPostId_item() {
        assertEquals("xyz789", PostIdExtractor.extractPostId("https://www.xiaohongshu.com/discovery/item/xyz789"))
    }

    @Test
    fun urlUtils_isXhsLink() {
        assertTrue(UrlUtils.isXhsLink("https://www.xiaohongshu.com/explore/abc"))
        assertTrue(UrlUtils.isXhsLink("https://xhslink.com/o/abc"))
        assertEquals(false, UrlUtils.isXhsLink("https://example.com"))
        assertEquals(false, UrlUtils.isXhsLink(null))
    }
}
