package com.xhsdn.core.naming

/**
 * 文件名模板应用器。把 `{token}` 替换为实际值，并做安全化。
 * 复刻 [XHSDownloader.applyCustomTemplate] + [buildFileBaseName] + [safeTokenValue]（java:1766-1965）。
 */
object TemplateApplier {

    /** 占位符上下文字。 */
    data class Context(
        val fallbackPostId: String,
        val mediaIndex: Int,
        val userName: String? = null,
        val userId: String? = null,
        val title: String? = null,
        val publishTime: String? = null,
        val downloadEpochSeconds: Long = 0L,
    ) {
        val indexPadded: String
            get() = String.format("%02d", maxOf(mediaIndex, 1))
    }

    private val NAMING_PLACEHOLDER_PATTERN = Regex("\\{([^}]+)\\}")

    fun buildFileBaseName(
        context: Context,
        customNamingEnabled: Boolean,
        customFormatTemplate: String?,
    ): String {
        val indexPart = context.indexPadded
        if (customNamingEnabled && !customFormatTemplate.isNullOrEmpty()) {
            val custom = applyCustomTemplate(customFormatTemplate, context, indexPart)
            if (!custom.isNullOrEmpty()) return custom
        }
        return "${context.fallbackPostId}_$indexPart"
    }

    private fun applyCustomTemplate(
        template: String,
        context: Context,
        indexPart: String,
    ): String? {
        val containsIndex = template.contains(NamingFormat.buildPlaceholder(NamingFormat.TOKEN_INDEX)) ||
            template.contains(NamingFormat.buildPlaceholder(NamingFormat.TOKEN_INDEX_PADDED))
        val containsTitle = template.contains(NamingFormat.buildPlaceholder(NamingFormat.TOKEN_TITLE))

        val result = if (containsTitle) {
            // 限制 title 长度，保证 255 字节文件名上限
            val titleText = context.title ?: ""
            val titleValue = safeTokenValue(titleText, 50)
            val withTitle = template.replace(
                NamingFormat.buildPlaceholder(NamingFormat.TOKEN_TITLE),
                titleValue ?: "",
            )
            applyTokens(withTitle, context, indexPart)
        } else {
            applyTokens(template, context, indexPart)
        }

        val sanitized = sanitizeForFilename(result, 0)
        if (sanitized.isNullOrEmpty()) return null
        return if (containsIndex) sanitized else "${sanitized}_$indexPart"
    }

    private fun applyTokens(template: String, context: Context, indexPart: String): String {
        val sb = StringBuilder()
        val matcher = NAMING_PLACEHOLDER_PATTERN.findAll(template)
        var lastEnd = 0
        for (m in matcher) {
            sb.append(template, lastEnd, m.range.first)
            val key = m.groupValues[1]
            val replacement = resolveTemplateValue(key, context, indexPart) ?: ""
            sb.append(replacement)
            lastEnd = m.range.last + 1
        }
        sb.append(template, lastEnd, template.length)
        return sb.toString()
    }

    private fun resolveTemplateValue(key: String, context: Context, indexPart: String): String? = when (key) {
        NamingFormat.TOKEN_USERNAME -> safeTokenValue(context.userName, 60)
        NamingFormat.TOKEN_USER_ID -> safeTokenValue(context.userId, 60)
        NamingFormat.TOKEN_TITLE -> safeTokenValue(context.title, 80)
        NamingFormat.TOKEN_POST_ID -> safeTokenValue(context.fallbackPostId, 60)
        NamingFormat.TOKEN_PUBLISH_TIME -> safeTokenValue(context.publishTime, 60)
        NamingFormat.TOKEN_INDEX -> maxOf(context.mediaIndex, 1).toString()
        NamingFormat.TOKEN_INDEX_PADDED -> indexPart
        NamingFormat.TOKEN_DOWNLOAD_TIMESTAMP -> {
            val epoch = if (context.downloadEpochSeconds > 0) context.downloadEpochSeconds
            else System.currentTimeMillis() / 1000L
            epoch.toString()
        }
        else -> ""
    }

    private fun safeTokenValue(value: String?, maxLength: Int): String? {
        if (value.isNullOrEmpty()) return null
        val sanitized = sanitizeForFilename(value, 0) ?: return null
        if (maxLength > 0 && sanitized.length > maxLength) {
            val available = (maxLength - 3).coerceAtLeast(0)
            return if (available > 0) sanitized.substring(0, available) + "..."
            else sanitized.substring(0, maxLength)
        }
        return sanitized
    }

    /** 把非法字符替换为 `_`，连续空白压成一个，限制长度。 */
    fun sanitizeForFilename(value: String?, maxLength: Int): String? {
        if (value.isNullOrEmpty()) return null
        var s = value.replace(Regex("""[\\/:*?"<>|]"""), "_")
        s = s.replace(Regex("\\p{Cntrl}"), "")
        s = s.trim()
        s = s.replace(Regex("\\s+"), "_")
        s = s.replace(Regex("_+"), "_")
        s = s.replace(Regex("^_+"), "")
        s = s.replace(Regex("_+$"), "")
        if (maxLength > 0 && s.length > maxLength) s = s.substring(0, maxLength)
        return s.ifEmpty { null }
    }
}
