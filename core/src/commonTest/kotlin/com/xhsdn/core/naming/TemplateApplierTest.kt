package com.xhsdn.core.naming

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateApplierTest {

    @Test
    fun buildFileBaseName_defaultTemplate() {
        val ctx = TemplateApplier.Context(fallbackPostId = "abc123", mediaIndex = 1)
        val base = TemplateApplier.buildFileBaseName(ctx, customNamingEnabled = false, customFormatTemplate = null)
        assertEquals("abc123_01", base)
    }

    @Test
    fun buildFileBaseName_withCustomTitle() {
        val ctx = TemplateApplier.Context(
            fallbackPostId = "abc123",
            mediaIndex = 2,
            title = "好戏开场",
        )
        val base = TemplateApplier.buildFileBaseName(
            ctx,
            customNamingEnabled = true,
            customFormatTemplate = "{title}_{post_id}_{index_padded}",
        )
        assertEquals("好戏开场_abc123_02", base)
    }

    @Test
    fun buildFileBaseName_sanitizesPathChars() {
        val ctx = TemplateApplier.Context(
            fallbackPostId = "abc",
            mediaIndex = 1,
            title = "test/with\\bad:chars*?",
        )
        val base = TemplateApplier.buildFileBaseName(
            ctx,
            customNamingEnabled = true,
            customFormatTemplate = "{title}_{index}",
        )
        // 非法字符被替换为 _
        assertTrue(base.contains("test_with_bad_chars"))
    }

    @Test
    fun sanitizeForFilename_handlesEmpty() {
        assertEquals(null, TemplateApplier.sanitizeForFilename("", 10))
        assertEquals(null, TemplateApplier.sanitizeForFilename("///", 10))
    }

    @Test
    fun sanitizeForFilename_truncatesAtMax() {
        val s = "a".repeat(100)
        assertEquals(50, TemplateApplier.sanitizeForFilename(s, 50)?.length)
    }
}
