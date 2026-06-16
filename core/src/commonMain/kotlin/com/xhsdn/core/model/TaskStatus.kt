package com.xhsdn.core.model

/** 下载任务状态 */
enum class TaskStatus {
    QUEUED,            // 排队中
    DOWNLOADING,       // 下载中
    COMPLETED,         // 下载完成
    FAILED,            // 下载失败
    WAITING_FOR_USER,  // 等待用户操作 (如视频选择)
}

/** 笔记类型 */
enum class NoteType {
    IMAGE,   // 图文笔记
    VIDEO,   // 视频笔记
    UNKNOWN, // 未知
}

/** 媒体项（UI 渲染用） */
data class MediaItem(
    val path: String,
    val type: MediaType,
)

enum class MediaType { IMAGE, VIDEO, OTHER }
