package com.xhsdn.core.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsLiteralExtractorTest {

    @Test
    fun extractFirstJsObjectLiteral_balanced() {
        val js = "var x = {\"a\": 1, \"b\": {\"c\": 2}}; foo();"
        val extracted = JsLiteralExtractor.extractFirstJsObjectLiteral(js)
        assertEquals("{\"a\": 1, \"b\": {\"c\": 2}}", extracted)
    }

    @Test
    fun extractFirstJsObjectLiteral_skipsBracesInStrings() {
        val js = "var x = {\"a\": \"{not real}\", \"b\": 1};"
        val extracted = JsLiteralExtractor.extractFirstJsObjectLiteral(js)
        assertEquals("{\"a\": \"{not real}\", \"b\": 1}", extracted)
    }

    @Test
    fun extractFirstJsObjectLiteral_returnsNullOnNullOrEmpty() {
        assertNull(JsLiteralExtractor.extractFirstJsObjectLiteral(null))
        assertNull(JsLiteralExtractor.extractFirstJsObjectLiteral(""))
    }

    @Test
    fun replaceJsUndefinedWithNull_works() {
        val input = "{\"a\": undefined, \"b\": \"undefined is fine\", \"c\": 1}"
        val output = JsLiteralExtractor.replaceJsUndefinedWithNull(input)
        assertTrue(output.contains("\"a\": null"))
        assertTrue(output.contains("\"b\": \"undefined is fine\""))
    }

    @Test
    fun replaceJsUndefinedWithNull_keepsIdentifiersContainingUndefined() {
        val input = "{\"myUndefined\": 1}"
        val output = JsLiteralExtractor.replaceJsUndefinedWithNull(input)
        assertTrue(output.contains("\"myUndefined\": 1"))
    }
}
