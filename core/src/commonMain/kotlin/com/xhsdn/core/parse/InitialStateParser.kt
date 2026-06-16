package com.xhsdn.core.parse

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 从 HTML 中提取 `window.__INITIAL_STATE__ = {...}` 并解析为 JsonObject。
 * 复刻 [XHSDownloader.parseInitialStateRootFromHtml]（java:839-878）。
 */
object InitialStateParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(html: String?): JsonObject? {
        if (html.isNullOrEmpty()) return null
        val startIndex = html.indexOf("window.__INITIAL_STATE__")
        if (startIndex < 0) return null
        val endIndex = html.indexOf("</script>", startIndex)
        if (endIndex < 0) return null
        val scriptContent = html.substring(startIndex, endIndex)
        val equalsIndex = scriptContent.indexOf('=')
        if (equalsIndex < 0) return null

        val afterEquals = scriptContent.substring(equalsIndex + 1).trim()
        val jsObject = JsLiteralExtractor.extractFirstJsObjectLiteral(afterEquals) ?: afterEquals
        val cleaned = jsObject.trim().trimEnd(';').let { JsLiteralExtractor.replaceJsUndefinedWithNull(it) }

        return runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull()
    }
}
