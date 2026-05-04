package com.neoruaa.xhsdn.viewmodels

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import com.neoruaa.xhsdn.data.TaskManager
import com.neoruaa.xhsdn.data.TaskStatus
import com.neoruaa.xhsdn.data.NoteType
import kotlinx.coroutines.CancellationException
import android.util.Log
import com.neoruaa.xhsdn.DownloadCallback
import com.neoruaa.xhsdn.R
import com.neoruaa.xhsdn.XHSDownloader
import com.neoruaa.xhsdn.data.DownloadTask
import com.neoruaa.xhsdn.utils.NotificationHelper


data class MediaItem(val path: String, val type: MediaType)

enum class MediaType {
    IMAGE, VIDEO, OTHER
}

data class MainUiState(
    val urlInput: String = "",
    val status: List<String> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList(),
    val isDownloading: Boolean = false,
    val progressLabel: String = "",
    val progress: Float = 0f,
    val downloadProgressText: String = "0%｜0kb/s", // Format: "XX%｜XXXkb/s"
    val showWebCrawl: Boolean = false,
    val showVideoWarning: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private var totalMediaCount = 0
    private var downloadedCount = 0
    private val displayedFiles = mutableSetOf<String>()
    private var currentUrl: String? = null
    private var hasUserContinuedAfterVideoWarning = false
    private var downloadJob: Job? = null
    private var currentDownloader: XHSDownloader? = null

    // Track individual file progress for more accurate overall progress
    private val fileProgressMap = mutableMapOf<String, Float>() // Maps file path to progress (0.0 to 1.0)
    private var currentFileProgress = 0f // Progress of the currently downloading file (0.0 to 1.0)
    private var lastOverallProgress = 0f // Track the last overall progress to prevent regression

    // Fields to track download progress and speed for the first callback
    private var currentDownloadStartTime: Long = 0
    private var currentDownloadStartBytes: Long = 0
    private var currentDownloadTotalBytes: Long = 0
    private var currentDownloadedBytes: Long = 0
    private var lastSpeedCalculationTime: Long = 0
    private var lastCalculatedSpeed = "0kb/s"

    // Task tracking for history
    var currentTaskId: Long = 0
        private set
    private var taskCompletedFiles: Int = 0
    private var taskFailedFiles: Int = 0
    private var taskCurrentFileProgress: Float = 0f
    private var maxTaskProgress: Float = 0f

    // Throttling for task progress updates to reduce database writes
    private var lastTaskProgressUpdateTime = 0L
    private val TASK_PROGRESS_UPDATE_INTERVAL = 100L // 100ms interval between updates
    private val debugNotificationImportantKeywords = listOf(
        "开始",
        "完成",
        "失败",
        "错误",
        "出错",
        "取消",
        "停止",
        "检测到视频",
        "网页转存",
        "提示"
    )

    fun cancelCurrentDownload() {
        if (downloadJob?.isActive == true) {
            // Signal the downloader to stop at the next checkpoint
            currentDownloader?.stopDownload()
            downloadJob?.cancel()
            _uiState.update { it.copy(isDownloading = false, progressLabel = "已取消") }
            if (currentTaskId > 0) {
                TaskManager.completeTask(currentTaskId, false, "用户取消")
            }
        }
    }

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> { // >= 1 MB/s
                val mbps = bytesPerSecond / (1024 * 1024)
                "${String.format("%.2f", mbps)}MB/s"
            }
            bytesPerSecond >= 1024 -> { // >= 1 KB/s
                val kbps = bytesPerSecond / 1024
                "${String.format("%.1f", kbps)}KB/s"
            }
            else -> {
                "${bytesPerSecond.toInt()}B/s"
            }
        }
    }

    private fun resetDownloadTracking() {
        currentDownloadStartTime = System.currentTimeMillis()
        currentDownloadStartBytes = 0
        currentDownloadTotalBytes = 0
        currentDownloadedBytes = 0
        lastSpeedCalculationTime = 0
        lastCalculatedSpeed = "0KB/s"
        lastOverallProgress = 0f // Reset the last overall progress when starting a new download

        // Update UI to show initial state
        _uiState.update { currentState ->
            currentState.copy(downloadProgressText = "0.0%｜0KB/s")
        }
    }

    private fun isTerminalDownloadError(status: String): Boolean {
        val normalized = status.lowercase(Locale.ROOT)
        return normalized.contains("failed to download after") ||
            normalized.contains("exception downloading") ||
            normalized.contains("download failed") ||
            normalized.contains("io error downloading file") ||
            normalized.contains("security exception while downloading file") ||
            normalized.contains("non-media response received") ||
            normalized.contains("both image and video failed to download separately")
    }

    fun updateUrl(value: String) {
        _uiState.update { it.copy(urlInput = value, showWebCrawl = false) }
    }

    fun pasteLinkFromClipboard() {
        viewModelScope.launch {
            try {
                val clipboardManager = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipboardText = clipData.getItemAt(0).text?.toString() ?: ""
                    if (clipboardText.isNotEmpty()) {
                        _uiState.update { it.copy(urlInput = clipboardText) }
                        appendStatus("已从剪贴板粘贴链接")
                    } else {
                        appendStatus("剪贴板内容为空")
                    }
                } else {
                    appendStatus("剪贴板无内容")
                }
            } catch (e: Exception) {
                appendStatus("读取剪贴板失败: ${e.message}")
            }
        }
    }

    fun startDownload(onError: (String) -> Unit) {
        val targetUrl = _uiState.value.urlInput.trim()
        if (targetUrl.isEmpty()) {
            onError("请输入链接")
            return
        }
        // Do NOT cancel any existing download job — let the old task complete in the background
        currentUrl = targetUrl
        downloadedCount = 0
        totalMediaCount = 0
        displayedFiles.clear()

        // Reset download tracking variables
        resetDownloadTracking()

        _uiState.update {
            it.copy(
                isDownloading = true,
                status = listOf("处理中：$targetUrl"),
                mediaItems = emptyList(),
                progressLabel = "",
                progress = 0f,
                showWebCrawl = false
            )
        }

        // 在协程外部立即创建新任务并设置currentTaskId，以便外部可以立即访问
        val initialTaskId = TaskManager.createTask(
            noteUrl = targetUrl,
            noteTitle = extractTitleFromUrl(targetUrl),
            noteType = NoteType.IMAGE,
            totalFiles = 1 // We'll update this later after getting the count
        )
        TaskManager.startTask(initialTaskId)

        currentTaskId = initialTaskId
        // 重置任务跟踪计数器
        taskCompletedFiles = 0
        taskFailedFiles = 0

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            totalMediaCount = runCatching { XHSDownloader(getApplication()).getMediaCount(targetUrl) }
                .getOrElse { 0 }
            updateProgress()

            // Update the task with the actual total file count
            if (totalMediaCount > 0) {
                TaskManager.updateTask(initialTaskId) { task ->
                    task.copy(totalFiles = totalMediaCount)
                }
            }

            val myTaskId = initialTaskId // Capture taskId locally for this coroutine

            // Track completed/failed files for this task (shared between callback and completion logic)
            val localCompletedFiles = java.util.concurrent.atomic.AtomicInteger(0)
            val localFailedFiles = java.util.concurrent.atomic.AtomicInteger(0)

            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {

                    override fun onFileDownloaded(filePath: String) {
                        val completed = localCompletedFiles.incrementAndGet()
                        // Always update TaskManager with the captured myTaskId
                        TaskManager.updateProgress(myTaskId, completed, localFailedFiles.get(), 0f)
                        TaskManager.addFilePath(myTaskId, filePath)

                        // Only update UI if this is still the active task
                        if (myTaskId == currentTaskId) {
                            viewModelScope.launch(Dispatchers.Main) {
                                if (displayedFiles.add(filePath)) {
                                    addMedia(filePath)
                                    downloadedCount++
                                    taskCompletedFiles++
                                    currentFileProgress = 0f
                                    taskCurrentFileProgress = 0f
                                    updateProgress()
                                }
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        if (myTaskId == currentTaskId) {
                            appendStatus(status)
                        }
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                        // Calculate individual file progress for TaskManager
                        val fileProgress = if (total > 0 && downloaded >= 0) {
                            if (downloaded <= total) downloaded.toFloat() / total.toFloat() else 1.0f
                        } else 0f

                        // Always update TaskManager with the captured myTaskId
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTaskProgressUpdateTime >= TASK_PROGRESS_UPDATE_INTERVAL) {
                            TaskManager.updateProgress(
                                myTaskId,
                                localCompletedFiles.get(),
                                localFailedFiles.get(),
                                fileProgress
                            )
                            lastTaskProgressUpdateTime = currentTime
                        }

                        // Only update UI if this is still the active task
                        if (myTaskId != currentTaskId) return

                        val progressPercent = if (total > 0) {
                            (downloaded.toDouble() / total.toDouble() * 100).toFloat()
                        } else 0f

                        currentFileProgress = fileProgress
                        taskCurrentFileProgress = currentFileProgress

                        // Calculate download speed
                        val deltaTime = currentTime - lastSpeedCalculationTime
                        if (deltaTime > 500) {
                            val deltaBytes = if (downloaded >= currentDownloadedBytes) {
                                downloaded - currentDownloadedBytes
                            } else {
                                downloaded
                            }
                            val deltaTimeSec = deltaTime.toDouble() / 1000.0
                            val speedBps = if (deltaTimeSec > 0) deltaBytes.toDouble() / deltaTimeSec else 0.0
                            lastCalculatedSpeed = formatSpeed(speedBps)
                            lastSpeedCalculationTime = currentTime
                        }

                        currentDownloadedBytes = downloaded
                        currentDownloadTotalBytes = total
                        updateProgress()

                        val progressText =
                            "${String.format("%.1f", progressPercent)}%｜$lastCalculatedSpeed"
                        _uiState.update { currentState ->
                            currentState.copy(downloadProgressText = progressText)
                        }
                    }

                    override fun onDownloadError(status: String, originalUrl: String) {
                        if (isTerminalDownloadError(status)) {
                            val failed = localFailedFiles.incrementAndGet()
                            TaskManager.updateProgress(myTaskId, localCompletedFiles.get(), failed, 0f)
                        }
                        // Only update UI if this is still the active task
                        if (myTaskId == currentTaskId) {
                            appendStatus("错误：$status ($originalUrl)")
                            if (status.contains("No media URLs found", true)
                                || status.contains("Failed to fetch post details", true)
                                || status.contains("Could not extract post ID", true)
                            ) {
                                _uiState.update { it.copy(showWebCrawl = true) }
                            }
                        }
                    }

                    override fun onVideoDetected() {
                        // Correctly mark as VIDEO type in DB
                        // Note: Live Photos don't trigger this, so they stay as IMAGE (correct)
                        TaskManager.updateTaskType(myTaskId, NoteType.VIDEO)

                        // Only show the warning and stop the download if the user hasn't already chosen to continue
                        if (!hasUserContinuedAfterVideoWarning) {
                            _uiState.update { it.copy(showVideoWarning = true) }

                            // Update DB status to WAITING_FOR_USER so UI can show choice buttons
                            TaskManager.updateTaskStatus(
                                myTaskId,
                                TaskStatus.WAITING_FOR_USER,
                                "检测到视频，请选择下载方式"
                            )

                            // Cancel the download job to stop the download immediately
                            // We use a specific flag to know this was an intentional stop for user input
                            downloadJob?.cancel(CancellationException("WAITING_FOR_USER"))

                            appendStatus("下载因检测到视频而停止")

                            // Update isDownloading to false when download is stopped
                            _uiState.update { it.copy(isDownloading = false) }

                            appendStatus("提示：检测到视频，请选择坚持下载(720P)或网页爬取")
                        }
                        // If user has already continued, just log that video was detected but don't stop
                        else {
                            appendStatus("检测到视频文件，继续下载...")
                        }
                    }
                }
            )

            // Store reference so cancelCurrentDownload() can signal this downloader
            currentDownloader = downloader

            // If user has continued after video warning, don't stop on video detection
            if (hasUserContinuedAfterVideoWarning) {
                downloader.setShouldStopOnVideo(false)
            } else {
                downloader.setShouldStopOnVideo(true)
            }
            // Run download with cancellation check, passing THIS job
            val currentJob = coroutineContext[Job]
            try {
                val success = runDownloadWithCancellationCheck(downloader, targetUrl, currentJob)

                // Always update TaskManager with the final result
                val finalCompleted = localCompletedFiles.get()
                val finalFailed = localFailedFiles.get()
                
                // A task is only truly successful if success is true AND there are no failed files
                val isStrictSuccess = success && finalFailed == 0 && finalCompleted > 0
                
                // Generate a descriptive error message if not strictly successful
                val errorMsg = when {
                    isStrictSuccess -> null
                    !success -> "解析或下载中断"
                    finalCompleted == 0 -> "未发现可下载资源"
                    finalFailed > 0 -> "部分下载失败 ($finalFailed)"
                    else -> "下载过程异常"
                }
                
                TaskManager.completeTask(myTaskId, isStrictSuccess, errorMsg)

                // Only update UI if this is still the active task
                if (myTaskId == currentTaskId) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isDownloading = false) }
                        val uiStatus = when {
                            isStrictSuccess -> "✅ 下载完成"
                            finalFailed > 0 && finalCompleted > 0 -> "⚠️ 部分下载失败 ($finalFailed)"
                            else -> "❌ 下载失败"
                        }
                        appendStatus(uiStatus)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                     // Check if this cancellation was for WAITING_FOR_USER
                     if (e.message == "WAITING_FOR_USER") {
                         Log.d("MainViewModel", "Download cancelled for user input")
                         // Do NOT complete task as failed. Leave it as WAITING_FOR_USER.
                     } else {
                        appendStatus("⏹️ 下载已取消")
                        TaskManager.completeTask(myTaskId, false, "下载已取消")
                     }
                } else {
                    appendStatus("❌ 下载出错: ${e.message}")
                    TaskManager.completeTask(myTaskId, false, e.message ?: "未知错误")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Only reset UI-related state if this is still the active task
                    if (currentTaskId == myTaskId) {
                        resetDownloadTracking()
                        currentTaskId = 0
                        taskCompletedFiles = 0
                        taskFailedFiles = 0
                        hasUserContinuedAfterVideoWarning = false
                        currentDownloader = null
                    }
                }
            }
        }
    }

    /**
     * 重试失败的任务（复用已有 taskId）
     */
    fun retryTask(task: DownloadTask, onError: (String) -> Unit) {
        // 重置任务状态
        TaskManager.resetTask(task.id)
        currentTaskId = task.id

        val targetUrl = task.noteUrl
        currentUrl = targetUrl
        updateUrl(targetUrl)

        // Reset tracking
        downloadedCount = 0
        totalMediaCount = 0
        taskCompletedFiles = 0
        taskFailedFiles = 0
        displayedFiles.clear()
        resetDownloadTracking()

        _uiState.update {
            it.copy(
                isDownloading = true,
                progressLabel = "",
                progress = 0f,
                showWebCrawl = false,
                showVideoWarning = false,
                mediaItems = emptyList()
            )
        }

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            totalMediaCount = runCatching { XHSDownloader(getApplication()).getMediaCount(targetUrl) }
                .getOrElse { 0 }
            updateProgress()

            val myTaskId = task.id

            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (displayedFiles.add(filePath)) {
                                addMedia(filePath)
                                downloadedCount++
                                taskCompletedFiles++
                                currentFileProgress = 0f
                                updateProgress()
                                TaskManager.updateProgress(
                                    myTaskId,
                                    taskCompletedFiles,
                                    taskFailedFiles,
                                    taskCurrentFileProgress
                                )
                                TaskManager.addFilePath(myTaskId, filePath)
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        appendStatus(status)
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                        val progressPercent = if (total > 0) {
                            (downloaded.toDouble() / total.toDouble() * 100).toFloat()
                        } else 0f
                        currentFileProgress =
                            if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                        taskCurrentFileProgress = currentFileProgress
                        updateProgress()

                        // Update task progress with current file progress, with throttling
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTaskProgressUpdateTime >= TASK_PROGRESS_UPDATE_INTERVAL) {
                            TaskManager.updateProgress(
                                myTaskId,
                                taskCompletedFiles,
                                taskFailedFiles,
                                taskCurrentFileProgress
                            )
                            lastTaskProgressUpdateTime = currentTime
                        }

                        val progressText =
                            "${String.format("%.1f", progressPercent)}%｜$lastCalculatedSpeed"
                        _uiState.update { currentState ->
                            currentState.copy(downloadProgressText = progressText)
                        }
                    }

                    override fun onDownloadError(status: String, originalUrl: String) {
                        appendStatus("错误：$status ($originalUrl)")
                        if (isTerminalDownloadError(status)) {
                            taskFailedFiles++
                            taskCurrentFileProgress = 0f
                            TaskManager.updateProgress(
                                myTaskId,
                                taskCompletedFiles,
                                taskFailedFiles,
                                taskCurrentFileProgress
                            )
                        }
                    }

                    override fun onVideoDetected() {
                        _uiState.update { it.copy(showVideoWarning = true) }
                        TaskManager.updateTaskType(myTaskId, NoteType.VIDEO)
                    }
                }
            )

            downloader.setShouldStopOnVideo(true)
            downloader.resetStopDownload()

            try {
                val success = downloader.downloadContent(targetUrl)
                withContext(Dispatchers.Main) {
                    val isStrictSuccess = success && taskCompletedFiles > 0 && taskFailedFiles == 0
                    
                    val errorMsg = when {
                        isStrictSuccess -> null
                        !success -> "解析或下载中断"
                        taskCompletedFiles == 0 -> "未发现可下载资源"
                        taskFailedFiles > 0 -> "部分下载失败 ($taskFailedFiles)"
                        else -> "下载过程异常"
                    }
                    
                    TaskManager.completeTask(myTaskId, isStrictSuccess, errorMsg)
                    
                    if (myTaskId == currentTaskId) {
                        val uiStatus = when {
                            isStrictSuccess -> "✅ 下载完成"
                            taskFailedFiles > 0 && taskCompletedFiles > 0 -> "⚠️ 部分下载失败 ($taskFailedFiles)"
                            else -> "❌ 下载失败"
                        }
                        appendStatus(uiStatus)
                    }
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    if (taskCompletedFiles == 0) {
                        TaskManager.completeTask(myTaskId, false, "下载已取消")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    TaskManager.completeTask(myTaskId, false, e.message ?: "未知错误")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDownloading = false, showVideoWarning = false) }
                    resetDownloadTracking()
                    if (currentTaskId == myTaskId) {
                        currentTaskId = 0
                    }
                    taskCompletedFiles = 0
                    taskFailedFiles = 0
                    hasUserContinuedAfterVideoWarning = false
                }
            }
        }
    }

    fun copyDescription(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val targetUrl = _uiState.value.urlInput.trim()
        if (targetUrl.isEmpty()) {
            onError("请输入链接")
            return
        }
        appendStatus("获取笔记文案中…")
        viewModelScope.launch(Dispatchers.IO) {
            val desc = XHSDownloader(getApplication(), null).getNoteDescription(targetUrl)
            withContext(Dispatchers.Main) {
                if (!desc.isNullOrEmpty()) {
//                    copyToClipboard(desc)
                    appendStatus("已提取文案：\n$desc")

                    // 如果当前有任务ID，则更新任务的笔记内容
                    if (currentTaskId > 0) {
                        val currentTask = TaskManager.getTaskById(currentTaskId)
                        if (currentTask != null) {
                            // 创建更新后的任务，保留原有数据但更新笔记内容
                            val updatedTask = currentTask.copy(noteContent = desc)

                            // 更新TaskManager中的任务
                            TaskManager.updateTask(currentTaskId) { updatedTask }
                        }
                    }

                    onResult(desc)
                } else {
                    appendStatus("未获取到文案")
                    onError("未获取到文案")
                }
            }
        }
    }

    fun onWebCrawlResult(urls: List<String>, content: String?, taskId: Long? = null) {
        appendStatus("onWebCrawlResult 被调用，URL数量: ${urls.size}")

        if (urls.isEmpty()) {
            appendStatus("网页未发现可下载的资源")
            return
        }

        appendStatus("接收到 ${urls.size} 个原始URL")

        // Filter duplicate videos logic
        // 1. Separate videos and images
        // Helper to check if URL is a video
        fun isVideo(url: String): Boolean {
            return url.contains(".mp4") ||
                   url.contains("sns-video") ||
                   url.contains("blob:")
        }

        val (videoUrls, imageUrls) = urls.partition { isVideo(it) }

        appendStatus("分离后: 视频 ${videoUrls.size} 个, 图片 ${imageUrls.size} 个")

        // 2. Deduplicate videos (Prioritize HD from sns-video-bd.xhscdn.com)
        // Since xhs_extractor.js pushes the main video (originVideoKey) first,
        // we can safely prioritize the first video that matches our quality criteria.
        val finalVideoUrls = if (videoUrls.size > 1) {
            val hdVideos = videoUrls.filter { it.contains("sns-video") } // Broadened check
            if (hdVideos.isNotEmpty()) {
                // Determine valid HD videos
                val distinctHd = hdVideos.distinct()

                // User requirement: Keep only the highest quality one.
                // Assuming the first one (from originVideoKey) is the best.
                listOf(distinctHd.first())
            } else {
                // No HD videos found, keep the first available video to avoid duplicates
                listOf(videoUrls.distinct().first())
            }
        } else {
            videoUrls // 0 or 1 video, just keep it
        }

        // 3. Combine and deduplicate everything
        val finalUrls = (imageUrls + finalVideoUrls).distinct()

        appendStatus("去重后最终URL数量: ${finalUrls.size}")

        if (finalUrls.isEmpty()) {
             appendStatus("过滤后未发现有效资源")
             return
        }

        downloadedCount = 0
        totalMediaCount = finalUrls.size

        // Update task status to DOWNLOADING if taskId is provided
        taskId?.let { id ->
            TaskManager.updateTaskStatus(id, TaskStatus.DOWNLOADING)
        }

        // Reset download tracking variables for web crawl
        resetDownloadTracking()
        var webCrawlFailedFiles = 0

        _uiState.update {
            it.copy(
                isDownloading = true,
                progressLabel = "$downloadedCount/$totalMediaCount",
                progress = 0f,
                showWebCrawl = false,
                showVideoWarning = false
        )
        }
        updateProgress()
        appendStatus("开始爬取，请等待任务完成")
        viewModelScope.launch(Dispatchers.IO) {
            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (displayedFiles.add(filePath)) {
                                addMedia(filePath)
                                downloadedCount++
                                currentFileProgress =
                                    0f // Reset current file progress when file is completed
                                updateProgress()
                                // If we have a taskId, update the task progress
                                taskId?.let { id ->
                                    TaskManager.addFilePath(id, filePath)
                                    // Update the completed files count in the task manager
                                    TaskManager.updateProgress(id, downloadedCount, webCrawlFailedFiles, taskCurrentFileProgress)
                                }
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        appendStatus(status)
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                        // Calculate progress percentage - always update this
                        val progressPercent = if (total > 0) {
                            (downloaded.toDouble() / total.toDouble() * 100).toFloat()
                        } else 0f

                        // Calculate individual file progress (0.0 to 1.0)
                        currentFileProgress = if (total > 0 && downloaded >= 0) {
                            if (downloaded <= total) {
                                // Normal case: downloaded is less than or equal to total
                                downloaded.toFloat() / total.toFloat()
                            } else {
                                // Edge case: downloaded exceeds total (could happen with dynamic content)
                                // Cap at 1.0 to prevent progress > 100%
                                1.0f
                            }
                        } else {
                            0f
                        }

                        // Update task's current file progress
                        taskCurrentFileProgress = currentFileProgress

                        // Calculate download speed more responsively
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = currentTime - lastSpeedCalculationTime

                        // Update speed calculation more frequently for better responsiveness
                        if (deltaTime > 500) { // Update every 0.5 seconds instead of 1 second
                            // Prevent negative deltaBytes by ensuring downloaded is greater than or equal to currentDownloadedBytes
                            val deltaBytes = if (downloaded >= currentDownloadedBytes) {
                                downloaded - currentDownloadedBytes
                            } else {
                                // If downloaded is less than currentDownloadedBytes, it means we're tracking a different file
                                // In this case, just use the current downloaded amount as the basis
                                downloaded
                            }

                            val deltaTimeSec = deltaTime.toDouble() / 1000.0 // Convert to seconds

                            val speedBps = if (deltaTimeSec > 0) {
                                deltaBytes.toDouble() / deltaTimeSec
                            } else 0.0

                            // Format speed with appropriate units (KB/s or MB/s)
                            lastCalculatedSpeed = formatSpeed(speedBps)
                            lastSpeedCalculationTime = currentTime
                        }

                        // Update the current download stats
                        currentDownloadedBytes = downloaded
                        currentDownloadTotalBytes = total

                        // Update overall progress
                        updateProgress()

                        // Update task progress with current file progress, with throttling
                        // For web crawl, use the taskId parameter if available
                        taskId?.let { id ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTaskProgressUpdateTime >= TASK_PROGRESS_UPDATE_INTERVAL) {
                                TaskManager.updateProgress(
                                    id,
                                    downloadedCount, // For web crawl, use downloadedCount as completed files
                                    webCrawlFailedFiles,
                                    taskCurrentFileProgress
                                )
                                lastTaskProgressUpdateTime = currentTime
                            }
                        }

                        // Always update UI state with current progress and speed
                        val progressText =
                            "${String.format("%.1f", progressPercent)}%｜$lastCalculatedSpeed"
                        _uiState.update { currentState ->
                            currentState.copy(downloadProgressText = progressText)
                        }
                    }

                    override fun onDownloadError(status: String, originalUrl: String) {
                        appendStatus("错误：$status ($originalUrl)")
                        if (isTerminalDownloadError(status)) {
                            webCrawlFailedFiles++
                            taskCurrentFileProgress = 0f
                            taskId?.let { id ->
                                TaskManager.updateProgress(id, downloadedCount, webCrawlFailedFiles, taskCurrentFileProgress)
                            }
                        }
                    }

                    override fun onVideoDetected() {
                        // For web crawl, we don't show the video warning since it's already in web mode
                        // Just update the state to reflect that videos were detected
                        _uiState.update { it.copy(showVideoWarning = true) }
                    }
                }
            )

            // If user has continued after video warning, don't stop on video detection
            if (hasUserContinuedAfterVideoWarning) {
                downloader.setShouldStopOnVideo(false)
            } else {
                downloader.setShouldStopOnVideo(true)
            }
            // Reset the stop flag for new download
            downloader.resetStopDownload()

            // For web crawl, we already have the URLs from the WebView, so we don't need to extract them again
            // Use the finalUrls that were passed to this function
            val postIdTemp = currentDownloadStartTime.toString()
            val postId = "webview_$postIdTemp"

            try {
                appendStatus("开始下载 ${finalUrls.size} 个文件")

                finalUrls.forEachIndexed { index, rawUrl ->
                    appendStatus("正在下载第 ${index + 1}/${finalUrls.size} 个文件: $rawUrl")
                    val transformed = downloader.transformXhsCdnUrl(rawUrl).takeUnless { it.isNullOrEmpty() } ?: rawUrl
                    val extension = determineFileExtension(transformed)
                    val fileName = "${postId}_${index + 1}.$extension"
                    appendStatus("准备下载文件: $fileName, URL: $transformed")
                    val success = downloader.downloadFile(rawUrl, fileName)
                    appendStatus(
                        if (success) "完成下载第 ${index + 1} 个文件"
                        else "第 ${index + 1} 个文件下载失败"
                    )
                }
                withContext(Dispatchers.Main) {
                    updateProgress()
                    appendStatus("网页转存完成")
                    // Mark task as complete if taskId was provided
                    taskId?.let { id ->
                        if (downloadedCount > 0 && webCrawlFailedFiles == 0) {
                            TaskManager.completeTask(id, true)
                        } else {
                            val errorMessage = if (downloadedCount > 0 && webCrawlFailedFiles > 0) {
                                "部分文件下载失败"
                            } else {
                                "网页转存出错"
                            }
                            TaskManager.completeTask(id, false, errorMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendStatus("网页转存出错: ${e.message}")
                    e.printStackTrace() // Print stack trace for debugging
                    // Mark task as failed if taskId was provided
                    taskId?.let { id ->
                        TaskManager.completeTask(id, false, "网页转存出错: ${e.message}")
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Reset download tracking when download completes (success or failure)
                    resetDownloadTracking()
                    _uiState.update { it.copy(showWebCrawl = false, isDownloading = false) }
                    // Reset the flag after download completes (whether successful or not)
                    hasUserContinuedAfterVideoWarning = false
                }
            }
        }
    }

    fun resetWebCrawlFlag() {
        _uiState.update { it.copy(showWebCrawl = false) }
    }

    fun notifyWebCrawlSuggestion() {
        _uiState.update { it.copy(showWebCrawl = true) }
    }

    fun continueAfterVideoWarning() {
        hasUserContinuedAfterVideoWarning = true
        _uiState.update { it.copy(showVideoWarning = false) }
        // Restart the download with the same URL to continue after video warning
        currentUrl?.let { url ->
            startDownload { msg -> appendStatus("错误: $msg") }
        }
    }

    fun continueTask(task: DownloadTask) {
        updateUrl(task.noteUrl)
        continueAfterVideoWarning()
    }

    fun resetVideoWarning() {
        _uiState.update { it.copy(showVideoWarning = false) }
        hasUserContinuedAfterVideoWarning = false
    }

    private fun addMedia(filePath: String) {
        val type = detectMediaType(filePath)
        _uiState.update { state ->
            state.copy(mediaItems = state.mediaItems + MediaItem(filePath, type))
        }
    }

    fun removeMediaItem(mediaItem: MediaItem) {
        _uiState.update { state ->
            state.copy(mediaItems = state.mediaItems.filter { it.path != mediaItem.path })
        }
    }

    private fun runDownloadWithCancellationCheck(downloader: XHSDownloader, targetUrl: String, job: Job?): Boolean {
        // Create a thread to run the download
        var result = false
        val thread = Thread {
            result = downloader.downloadContent(targetUrl)
        }
        thread.start()

        // Periodically check if the SPECIFIC job was cancelled
        while (thread.isAlive) {
            if (job?.isActive == false) {
                // Interrupt the thread
                thread.interrupt()
                // Wait a bit for the thread to respond to interruption
                Thread.sleep(100)
                break
            }
            Thread.sleep(100) // Check every 100ms
        }

        // Wait for thread to finish gracefully
        try {
            thread.join(1000) // Wait up to 1 second for graceful shutdown
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // If the job was cancelled, throw a CancellationException to jump to the catch block.
        // This prevents the caller from incorrectly marking the task as "FAILED" with a generic error message.
        // Using ensureActive() is the standard way to check for cancellation and throw the exception.
        job?.ensureActive()

        return result
    }

    private fun detectMediaType(filePath: String): MediaType {
        val lower = filePath.lowercase(Locale.getDefault())
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") ||
                lower.endsWith(".mkv") || lower.contains("sns-video") || lower.contains("video") -> MediaType.VIDEO

            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".gif") || lower.endsWith(".webp") -> MediaType.IMAGE

            else -> MediaType.OTHER
        }
    }

    private fun determineFileExtension(url: String?): String {
        val lower = url?.lowercase(Locale.getDefault()) ?: return "jpg"
        return when {
            lower.contains(".mp4") -> "mp4"
            lower.contains(".mov") -> "mov"
            lower.contains(".avi") -> "avi"
            lower.contains(".mkv") -> "mkv"
            lower.contains(".webm") -> "webm"
            lower.contains(".png") -> "png"
            lower.contains(".gif") -> "gif"
            lower.contains(".webp") -> "webp"
            lower.contains("video") || lower.contains("sns-video") -> "mp4"
            else -> "jpg"
        }
    }

    private fun updateProgress() {
        val label = if (totalMediaCount > 0) {
            "$downloadedCount/$totalMediaCount"
        } else {
            "$downloadedCount/?"
        }
        val calculatedProgress = if (totalMediaCount > 0) {
            // Calculate progress as (completed files + current file progress) / total files
            (downloadedCount + currentFileProgress) / totalMediaCount.toFloat()
        } else {
            0f
        }

        // Ensure progress doesn't regress (go backwards)
        val overallProgress = maxOf(calculatedProgress, lastOverallProgress)
        lastOverallProgress = overallProgress

        _uiState.update { it.copy(progressLabel = label, progress = overallProgress) }

        // Also update the corresponding task in TaskManager if it exists
        if (currentTaskId > 0) {
            // Calculate the new task progress based on taskCompletedFiles and taskCurrentFileProgress
            val newTaskProgress = if (totalMediaCount > 0) {
                (taskCompletedFiles + taskCurrentFileProgress) / totalMediaCount.toFloat()
            } else {
                0f
            }

            // Ensure task progress doesn't regress by using maxTaskProgress
            maxTaskProgress = maxOf(maxTaskProgress, newTaskProgress)

            // Update task progress with throttling
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTaskProgressUpdateTime >= TASK_PROGRESS_UPDATE_INTERVAL) {
                TaskManager.updateProgress(
                    currentTaskId,
                    taskCompletedFiles,
                    taskFailedFiles,
                    taskCurrentFileProgress
                )
                lastTaskProgressUpdateTime = currentTime
            }
        }
    }

    private fun appendStatus(message: String) {
        _uiState.update { it.copy(status = it.status + message) }
        Log.d("XHSDownloader", message)
        showDebugNotification(message)
    }

    private fun showDebugNotification(message: String) {
        val appContext = getApplication<Application>()
        val title = appContext.getString(R.string.debug_notifications)
        val important = isImportantStatusMessage(message)
        NotificationHelper.showOrUpdateDebugNotification(appContext, title, message, important)
    }

    private fun isImportantStatusMessage(message: String): Boolean {
        if (message.contains("✅") || message.contains("❌") || message.contains("⏹️")) {
            return true
        }
        return debugNotificationImportantKeywords.any { keyword -> message.contains(keyword) }
    }

    private fun extractTitleFromUrl(url: String): String? {
        // Extract title from URL if possible, otherwise return null
        return null // For now, return null - the actual title extraction would happen elsewhere
    }

    fun clearHistory() {
        viewModelScope.launch {
            TaskManager.clearAllTasks()
            appendStatus("历史记录已清除")
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("xhsdn", text))
    }
}
