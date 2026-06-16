package com.xhsdn.core.naming

/**
 * 命名模板占位符常量与默认模板。
 * 与 [app/.../NamingFormat.java] 保持一致。
 */
object NamingFormat {
    const val TOKEN_USERNAME = "username"
    const val TOKEN_USER_ID = "user_id"
    const val TOKEN_TITLE = "title"
    const val TOKEN_POST_ID = "post_id"
    const val TOKEN_PUBLISH_TIME = "publish_time"
    const val TOKEN_INDEX = "index"
    const val TOKEN_INDEX_PADDED = "index_padded"
    const val TOKEN_DOWNLOAD_TIMESTAMP = "download_timestamp"

    const val DEFAULT_TEMPLATE = "{post_id}_{index_padded}"

    fun buildPlaceholder(token: String): String = "{$token}"
}
