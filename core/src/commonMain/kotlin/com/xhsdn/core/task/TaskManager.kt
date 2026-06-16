package com.xhsdn.core.task

import com.xhsdn.core.model.DownloadTask
import com.xhsdn.core.model.NoteType
import com.xhsdn.core.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 任务状态管理。跨平台版，使用 [TaskPersistence] 持久化到 JSON 文件。
 *
 * 与 Android 端 [app/.../data/TaskManager.kt] 行为一致，但底层存储改为 JSON 文件。
 * UI 端通过 [tasks] StateFlow 订阅变更。
 */
class TaskManager(private val persistence: TaskPersistence) {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val idGen = AtomicLong(System.currentTimeMillis())

    suspend fun init() {
        val existing = persistence.load()
        _tasks.value = existing
        existing.maxOfOrNull { it.id }?.let { idGen.set(it + 1) }
    }

    suspend fun createTask(
        noteUrl: String,
        noteTitle: String? = null,
        noteType: NoteType = NoteType.UNKNOWN,
        totalFiles: Int = 0,
    ): DownloadTask {
        val task = DownloadTask(
            id = idGen.getAndIncrement(),
            noteUrl = noteUrl,
            noteTitle = noteTitle,
            noteType = noteType,
            totalFiles = totalFiles,
            status = TaskStatus.QUEUED,
            createdAt = System.currentTimeMillis(),
        )
        updateList { it + task }
        return task
    }

    suspend fun updateTask(task: DownloadTask) = updateList { list ->
        list.map { if (it.id == task.id) task else it }
    }

    suspend fun updateStatus(taskId: Long, status: TaskStatus) = updateList { list ->
        list.map { if (it.id == taskId) it.copy(status = status) else it }
    }

    suspend fun deleteTask(taskId: Long) = updateList { list -> list.filterNot { it.id == taskId } }

    suspend fun clearHistory() = updateList { list -> list.filterNot { it.isCompleted } }

    private suspend inline fun updateList(crossinline transform: (List<DownloadTask>) -> List<DownloadTask>) {
        val current = _tasks.value
        val updated = transform(current)
        _tasks.value = updated
        persistence.save(updated)
    }
}
