package com.xhsdn.core.model

/**
 * 下载任务。
 *
 * 与 Android 端原 [app/.../data/DownloadTask.kt] 字段保持一致，方便将来对接时类型直接互通。
 */
data class DownloadTask(
    val id: Long,
    val noteUrl: String,
    val noteTitle: String?,
    val noteType: NoteType,
    val totalFiles: Int,
    val completedFiles: Int = 0,
    val failedFiles: Int = 0,
    val currentFileProgress: Float = 0f,
    val status: TaskStatus,
    val createdAt: Long,
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val filePaths: List<String> = emptyList(),
    val noteContent: String? = null,
) {
    val progress: Float
        get() = if (totalFiles > 0) {
            ((completedFiles + currentFileProgress) / totalFiles.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val isActive: Boolean
        get() = status == TaskStatus.QUEUED ||
            status == TaskStatus.DOWNLOADING ||
            status == TaskStatus.WAITING_FOR_USER

    val isCompleted: Boolean
        get() = status == TaskStatus.COMPLETED
}
