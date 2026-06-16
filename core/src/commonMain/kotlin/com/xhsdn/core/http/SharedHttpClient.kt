package com.xhsdn.core.http

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.Arrays
import java.util.concurrent.TimeUnit

/**
 * 共享 OkHttp 客户端。
 * 复刻 [FileDownloader.createSharedHttpClient]（java:41-57）配置：
 * - connectTimeout 20s, readTimeout 45s, writeTimeout 30s
 * - 最多 12 个并发请求 / 8 个同主机
 * - HTTP/2 + HTTP/1.1 双协议
 */
object SharedHttpClient {
    val instance: OkHttpClient by lazy { build() }

    private fun build(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 12
            maxRequestsPerHost = 8
        }
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(12, 10, TimeUnit.MINUTES))
            .dispatcher(dispatcher)
            .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }
}
