package com.xhsdn.core.url

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlTransformerTest {

    @Test
    fun transformsImageCdnUrl() {
        val original = "http://sns-webpic-qc.xhscdn.com/202404121854/a7e6fa93538d17fa5da39ed6195557d7/abc123!nd_dft_wlteh_webp_3"
        val transformed = UrlTransformer.transformXhsCdnUrl(original)
        assertTrue(transformed.startsWith("https://ci.xiaohongshu.com/"))
        assertTrue(transformed.contains("abc123"))
    }

    @Test
    fun leavesVideoUrlAlone() {
        val video = "https://sns-video-bd.xhscdn.com/something"
        assertEquals(video, UrlTransformer.transformXhsCdnUrl(video))
    }

    @Test
    fun leavesNonCdnUrlAlone() {
        val url = "https://example.com/foo.jpg"
        assertEquals(url, UrlTransformer.transformXhsCdnUrl(url))
    }
}
