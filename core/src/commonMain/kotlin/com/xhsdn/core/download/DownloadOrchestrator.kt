package com.xhsdn.core.download

import com.xhsdn.core.http.DownloadCallback
import com.xhsdn.core.http.HttpFetcher
import com.xhsdn.core.http.SharedHttpClient
import com.xhsdn.core.naming.NamingFormat
import com.xhsdn.core.naming.TemplateApplier
import com.xhsdn.core.parse.MediaUrlExtractor
import com.xhsdn.core.parse.NoteDetailsParser
import com.xhsdn.core.platform.FileStorage
import com.xhsdn.core.platform.LivePhotoWriter
import com.xhsdn.core.url.LinkExtractor
import com.xhsdn.core.url.PostIdExtractor
import com.xhsdn.core.url.UrlTransformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * 下载编排核心。
 * 跨平台版本，封装自原 [XHSDownloader.java] 的 [XHSDownloader.downloadContent] 流程：
 *   1. 提取/解析短链 → 真 URL
 *   2. fetchPostDetails 拿 HTML
 *   3. NoteDetailsParser 解析出媒体 URLs + Live Photo 配对
 *   4. 对每个媒体 URL：构建候选列表 → 多次重试 → 写入 [FileStorage]
 *   5. Live Photo: 调 [LivePhotoWriter.create]，失败 fallback 为分别保存
 *
 * 与原 Java 实现的差异：
 * - 取消检测改用 Kotlin 协程 [kotlinx.coroutines.ensureActive]，等价的 [checkForStop] 行为
 * - OkHttp Call 引用由 [HttpFetcher] 管理；orchestrator 只负责 cancel 标志位
 */
class DownloadOrchestrator(
    private val fileStorage: FileStorage,
    private val livePhotoWriter: LivePhotoWriter,
    private val callback: DownloadCallback? = null,
    private val customNamingEnabled: Boolean = false,
    private val customFormatTemplate: String = NamingFormat.DEFAULT_TEMPLATE,
) {
    @Volatile
    private var shouldStop: Boolean = false

    @Volatile
    private var videosDetected: Boolean = false

    @Volatile
    private var videoWarningShown: Boolean = true

    private val httpFetcher = HttpFetcher()

    fun stop() {
        shouldStop = true
        httpFetcher.cancel()
    }

    /**
     * 拉取并下载 [inputUrl] 指向的笔记。返回是否至少有一个文件成功。
     */
    suspend fun downloadContent(inputUrl: String): Boolean = withContext(Dispatchers.IO) {
        shouldStop = false
        videosDetected = false
        var successful = 0
        var hasErrors = false
        try {
            coroutineContext.ensureActive()
            val urls = LinkExtractor.extractLinks(inputUrl)
            if (urls.isEmpty()) return@withContext false

            // 短链 follow redirect
            val resolved = urls.map { url ->
                if (url.contains("xhslink.com")) {
                    httpFetcher.resolveShortUrl(url) ?: url
                } else url
            }

            val sessionTs = SimpleDateFormat("yyMMdd", Locale.getDefault()).format(Date())
            val sessionEpoch = System.currentTimeMillis() / 1000L

            for (url in resolved) {
                coroutineContext.ensureActive()
                val postId = PostIdExtractor.extractPostId(url) ?: continue

                val html = httpFetcher.fetchPostDetails(url)
                if (html.isNullOrEmpty()) {
                    callback?.onDownloadError("Failed to fetch post details: $url", url)
                    hasErrors = true
                    continue
                }

                val parseResult = NoteDetailsParser.parse(html) { videosDetected = true }
                val mediaUrls = parseResult.mediaUrls
                if (mediaUrls.isEmpty()) {
                    callback?.onDownloadError("No media URLs found in post: $postId", url)
                    hasErrors = true
                    continue
                }

                // Live Photo 配对处理
                val hasLivePhoto = parseResult.livePhotoPairs.isNotEmpty()
                if (hasLivePhoto && parseResult.livePhotoPairs.any { shouldCreateLivePhotos() && it.imageUrl != null && it.videoUrl != null }) {
                    val ok = createLivePhotos(
                        postId = postId,
                        pairs = parseResult.livePhotoPairs,
                        allMediaUrls = mediaUrls,
                        timestamp = sessionTs,
                        sessionEpoch = sessionEpoch,
                    )
                    if (!ok) hasErrors = true
                    successful++ // Live Photo 至少创建了一次即算成功
                    continue
                }

                // 普通下载：4 并发
                val results = downloadMediaBatch(
                    postId = postId,
                    mediaUrls = mediaUrls,
                    timestamp = sessionTs,
                    sessionEpoch = sessionEpoch,
                )
                if (results.any { it }) successful++
                if (results.any { !it }) hasErrors = true
            }

            successful > 0
        } catch (e: kotlinx.coroutines.CancellationException) {
            false
        } catch (e: Exception) {
            callback?.onDownloadError("Exception: ${e.message}", inputUrl)
            false
        }
    }

    private fun shouldCreateLivePhotos(): Boolean = true // 平台层偏好，默认开启

    private suspend fun downloadMediaBatch(
        postId: String,
        mediaUrls: List<String>,
        timestamp: String,
        sessionEpoch: Long,
    ): List<Boolean> = coroutineScope {
        mediaUrls.mapIndexed { idx, url ->
            async(Dispatchers.IO) {
                val baseName = buildFileBaseName(postId, idx + 1, sessionEpoch)
                val ext = MediaExtensionUtil.determineFileExtension(url)
                val name = "$baseName.$ext"
                downloadWithRetries(url, name, timestamp, isVideo = MediaExtensionUtil.isVideoUrl(url))
            }
        }.awaitAll()
    }

    private suspend fun downloadWithRetries(
        mediaUrl: String,
        fileName: String,
        timestamp: String,
        isVideo: Boolean,
    ): Boolean {
        val candidates = buildDownloadCandidateUrls(mediaUrl, mediaUrl)
        for (candidate in candidates) {
            for (attempt in 1..MAX_ATTEMPTS) {
                coroutineContext.ensureActive()
                if (callback?.isCancelled() == true) return false
                val ok = downloadSingle(candidate, fileName, timestamp, isVideo)
                if (ok) return true
            }
        }
        callback?.onDownloadError("Failed after retries: $mediaUrl", mediaUrl)
        return false
    }

    private suspend fun downloadSingle(
        url: String,
        fileName: String,
        @Suppress("UNUSED_PARAMETER") timestamp: String,
        isVideo: Boolean,
    ): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            val call = SharedHttpClient.instance.newCall(request)
            val response = call.execute()
            if (!response.isSuccessful) {
                response.close()
                return false
            }
            val body = response.body ?: run { response.close(); return false }
            val bytes = body.bytes()
            response.close()
            val savedPath = if (isVideo) {
                fileStorage.saveVideo(bytes, fileName, "application/octet-stream")
            } else {
                fileStorage.saveImage(bytes, fileName, "image/jpeg")
            }
            callback?.onFileDownloaded(savedPath)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildDownloadCandidateUrls(mediaUrl: String, originalUrl: String): List<String> {
        val set = linkedSetOf<String>()
        val transformed = UrlTransformer.transformXhsCdnUrl(mediaUrl)
        if (isLikelyMediaUrl(transformed) && transformed != mediaUrl) set += transformed
        if (isLikelyMediaUrl(mediaUrl)) set += mediaUrl
        if (isLikelyMediaUrl(originalUrl)) {
            set += originalUrl
            val tOrig = UrlTransformer.transformXhsCdnUrl(originalUrl)
            if (isLikelyMediaUrl(tOrig)) set += tOrig
        }
        return set.toList()
    }

    private fun isLikelyMediaUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val lower = url.lowercase()
        return lower.contains("xhscdn.com") || lower.contains("ci.xiaohongshu.com") ||
            lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") ||
            lower.contains(".webp") || lower.contains(".gif") || lower.contains(".mp4") ||
            lower.contains(".mov") || lower.contains("imageview2") || lower.contains("sns-video") ||
            lower.contains("/spectrum/")
    }

    private fun buildFileBaseName(
        fallbackPostId: String,
        mediaIndex: Int,
        sessionEpoch: Long,
    ): String {
        val ctx = TemplateApplier.Context(
            fallbackPostId = fallbackPostId,
            mediaIndex = mediaIndex,
            downloadEpochSeconds = sessionEpoch,
        )
        return TemplateApplier.buildFileBaseName(ctx, customNamingEnabled, customFormatTemplate)
    }

    private suspend fun createLivePhotos(
        postId: String,
        pairs: List<MediaUrlExtractor.MediaPair>,
        allMediaUrls: List<String>,
        timestamp: String,
        sessionEpoch: Long,
    ): Boolean {
        var hasErrors = false
        val cacheDir = File(System.getProperty("java.io.tmpdir") ?: ".", "xhsdn-cache-$postId")
        cacheDir.mkdirs()

        for ((idx, pair) in pairs.withIndex()) {
            val imgUrl = pair.imageUrl ?: continue
            val vidUrl = pair.videoUrl ?: continue
            val baseName = buildFileBaseName(postId, idx + 1, sessionEpoch)
            val imgName = "${baseName}_img.${MediaExtensionUtil.determineFileExtension(imgUrl)}"
            val vidName = "${baseName}_vid.${MediaExtensionUtil.determineFileExtension(vidUrl)}"

            val imgOk = downloadToCache(imgUrl, imgName, cacheDir)
            val vidOk = downloadToCache(vidUrl, vidName, cacheDir)
            if (!imgOk || !vidOk) { hasErrors = true; continue }

            val imgFile = File(cacheDir, imgName)
            val vidFile = File(cacheDir, vidName)
            val outName = "${baseName}_live.jpg"
            val outputPath = fileStorage.picturesDir() + File.separator + outName

            val created = livePhotoWriter.create(imgFile.absolutePath, vidFile.absolutePath, outputPath)
            if (created) {
                callback?.onFileDownloaded(outputPath)
            } else {
                // fallback：分别保存
                val imgPath = fileStorage.saveImage(imgFile.readBytes(), imgName, "image/jpeg")
                val vidPath = fileStorage.saveVideo(vidFile.readBytes(), vidName, "video/mp4")
                callback?.onFileDownloaded(imgPath)
                callback?.onFileDownloaded(vidPath)
            }
            imgFile.delete(); vidFile.delete()
        }
        cacheDir.delete()
        return !hasErrors
    }

    private fun downloadToCache(url: String, fileName: String, cacheDir: File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            val call = SharedHttpClient.instance.newCall(request)
            val response = call.execute()
            if (!response.isSuccessful) { response.close(); return false }
            val body = response.body ?: run { response.close(); return false }
            val out = File(cacheDir, fileName)
            body.byteStream().use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            response.close()
            true
        } catch (e: Exception) { false }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
