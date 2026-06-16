package com.xhsdn.desktop.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.xhsdn.core.PlatformInitializer
import com.xhsdn.core.download.DownloadOrchestrator
import com.xhsdn.core.http.DownloadCallback
import com.xhsdn.core.model.DownloadTask
import com.xhsdn.core.model.NoteType
import com.xhsdn.core.model.TaskStatus
import com.xhsdn.core.platform.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 桌面端任务视图模型。
 * 替代 Android 端 [com.neoruaa.xhsdn.viewmodels.MainViewModel]，但只保留核心下载/任务能力。
 */
class DesktopTaskViewModel {

    private val taskManager = PlatformInitializer.taskManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tasks: StateFlow<List<DownloadTask>> = taskManager.tasks

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun setUrl(url: String) {
        _urlInput.value = url
    }

    fun startDownload() {
        val url = _urlInput.value
        if (url.isBlank() || _isDownloading.value) return
        scope.launch {
            val task = taskManager.createTask(noteUrl = url)
            taskManager.updateStatus(task.id, TaskStatus.DOWNLOADING)
            _isDownloading.value = true
            val callback = createCallback(task.id)
            val orchestrator = DownloadOrchestrator(
                fileStorage = PlatformContext.current.fileStorage,
                livePhotoWriter = PlatformContext.current.livePhotoWriter,
                callback = callback,
            )
            val ok = orchestrator.downloadContent(url)
            taskManager.updateStatus(
                task.id,
                if (ok) TaskStatus.COMPLETED else TaskStatus.FAILED,
            )
            _isDownloading.value = false
        }
    }

    fun clearHistory() {
        scope.launch { taskManager.clearHistory() }
    }

    private fun createCallback(@Suppress("UNUSED_PARAMETER") taskId: Long): DownloadCallback =
        object : DownloadCallback {
            override fun onFileDownloaded(filePath: String) {
                // TODO: 阶段 7 接 notifier
            }
            override fun onDownloadError(status: String, originalUrl: String) {
                println("[err] $status ($originalUrl)")
            }
            override fun onDownloadProgress(status: String) {
                println("[progress] $status")
            }
            override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                // no-op for now
            }
            override fun onVideoDetected() {
                println("[video] 检测到视频")
            }
            override fun isCancelled(): Boolean = false
        }
}

@Composable
fun rememberDesktopTaskViewModel(): DesktopTaskViewModel = remember { DesktopTaskViewModel() }
