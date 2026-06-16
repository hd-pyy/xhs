package com.xhsdn.core.http

import okhttp3.Call
import okhttp3.Request

/**
 * HTTP 抓取层。
 * 复刻 [XHSDownloader.fetchPostDetails]（java:697-720）+ [resolveShortUrl]（java:2210-2235）。
 */
class HttpFetcher(
    private val userAgent: String = DEFAULT_UA,
) {
    @Volatile
    private var activeCall: Call? = null

    /** 抓取笔记详情页 HTML。 */
    fun fetchPostDetails(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", userAgent)
            .addHeader(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=1.0,image/avif,image/webp,image/apng,*/*;q=1.0",
            )
            .build()
        return executeForString(request)
    }

    /**
     * 短链 follow redirect，返回最终 URL。失败返回 null。
     */
    fun resolveShortUrl(shortUrl: String): String? {
        val request = Request.Builder()
            .url(shortUrl)
            .addHeader("User-Agent", userAgent)
            .build()
        return try {
            val call = SharedHttpClient.instance.newCall(request)
            activeCall = call
            call.execute().use { response ->
                if (response.isSuccessful) response.request.url.toString() else null
            }
        } catch (e: Exception) {
            null
        } finally {
            activeCall = null
        }
    }

    /** 取消进行中的请求 */
    fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }

    private fun executeForString(request: Request): String? {
        return try {
            val call = SharedHttpClient.instance.newCall(request)
            activeCall = call
            call.execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    response.body!!.string()
                } else null
            }
        } catch (e: Exception) {
            null
        } finally {
            activeCall = null
        }
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36 xiaohongshu"
    }
}
