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

    /**
     * 全局 Tab 选中态。让 HistoryScreen 能切换到 HomeScreen 去触发再次解析。
     * 状态在这里而不是 App 里,是因为 Compose 的 [rememberDesktopTaskViewModel]
     * 在多次 [remember] 时会拿到同一个实例(单 ViewModel = 共享 Tab 状态)。
     */
    private val _selectedTab = MutableStateFlow(NavTab.HOME)
    val selectedTab: StateFlow<NavTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: NavTab) {
        _selectedTab.value = tab
    }

    fun setUrl(url: String) {
        _urlInput.value = url
    }

    /**
     * 删除单条任务历史。仅移除记录，不会触碰已下载到磁盘的文件。
     */
    fun deleteTask(taskId: Long) {
        scope.launch { taskManager.deleteTask(taskId) }
    }

    /**
     * 从历史里取指定任务的 URL 后:
     *  1. 把 URL 写回 HomeScreen 输入框(用户能看到,方便对照)
     *  2. 切到 HomeScreen 标签
     *  3. 自动触发一次下载 — 用户从历史里点「重新解析」的本意就是要再下一遍。
     */
    fun retryFromHistory(taskId: Long) {
        scope.launch {
            val url = taskManager.tasks.value.firstOrNull { it.id == taskId }?.noteUrl
            if (!url.isNullOrBlank()) {
                _urlInput.value = url
                _selectedTab.value = NavTab.HOME
                startDownload()
            }
        }
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

/** 桌面端底部 Tab 枚举。提到 ViewModel 同包是为了跨 Screen 共享 Tab 状态。 */
enum class NavTab { HOME, HISTORY, SETTINGS }
