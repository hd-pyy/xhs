package com.xhsdn.core.task

import com.xhsdn.core.model.DownloadTask
import com.xhsdn.core.model.NoteType
import com.xhsdn.core.model.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 任务历史 JSON 持久化。
 * 避免使用 [java.util.prefs.Preferences] 存大列表（Windows 注册表单 key 上限 ~8KB）。
 *
 * 存储路径：%USERPROFILE%/.xhsdn/history.json（Android 走 app 私有目录）。
 */
class TaskPersistence(private val historyFile: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = kotlinx.coroutines.sync.Mutex()

    suspend fun load(): List<DownloadTask> = mutex.withLockSafe {
        if (!historyFile.exists()) return@withLockSafe emptyList()
        runCatching {
            val text = historyFile.readText()
            if (text.isBlank()) return@runCatching emptyList()
            val dto = json.decodeFromString(HistoryDto.serializer(), text)
            dto.tasks.map { it.toDomain() }
        }.getOrElse { emptyList() }
    }

    suspend fun save(tasks: List<DownloadTask>) = mutex.withLockSafe {
        val dto = HistoryDto(tasks.map { TaskDto.fromDomain(it) })
        runCatching {
            historyFile.parentFile?.mkdirs()
            val tmp = File(historyFile.parentFile, historyFile.name + ".tmp")
            tmp.writeText(json.encodeToString(HistoryDto.serializer(), dto))
            if (historyFile.exists()) historyFile.delete()
            tmp.renameTo(historyFile)
        }
    }

    @Serializable
    private data class HistoryDto(val tasks: List<TaskDto>)

    @Serializable
    private data class TaskDto(
        val id: Long,
        val noteUrl: String,
        val noteTitle: String? = null,
        val noteType: String = "UNKNOWN",
        val totalFiles: Int = 0,
        val completedFiles: Int = 0,
        val failedFiles: Int = 0,
        val currentFileProgress: Float = 0f,
        val status: String = "QUEUED",
        val createdAt: Long = System.currentTimeMillis(),
        val completedAt: Long? = null,
        val errorMessage: String? = null,
        val filePaths: List<String> = emptyList(),
        val noteContent: String? = null,
    ) {
        fun toDomain() = DownloadTask(
            id = id,
            noteUrl = noteUrl,
            noteTitle = noteTitle,
            noteType = runCatching { NoteType.valueOf(noteType) }.getOrDefault(NoteType.UNKNOWN),
            totalFiles = totalFiles,
            completedFiles = completedFiles,
            failedFiles = failedFiles,
            currentFileProgress = currentFileProgress,
            status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.QUEUED),
            createdAt = createdAt,
            completedAt = completedAt,
            errorMessage = errorMessage,
            filePaths = filePaths,
            noteContent = noteContent,
        )

        companion object {
            fun fromDomain(t: DownloadTask) = TaskDto(
                id = t.id,
                noteUrl = t.noteUrl,
                noteTitle = t.noteTitle,
                noteType = t.noteType.name,
                totalFiles = t.totalFiles,
                completedFiles = t.completedFiles,
                failedFiles = t.failedFiles,
                currentFileProgress = t.currentFileProgress,
                status = t.status.name,
                createdAt = t.createdAt,
                completedAt = t.completedAt,
                errorMessage = t.errorMessage,
                filePaths = t.filePaths,
                noteContent = t.noteContent,
            )
        }
    }
}

private suspend inline fun <T> kotlinx.coroutines.sync.Mutex.withLockSafe(crossinline block: suspend () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        lock()
        try { block() } finally { unlock() }
    }
