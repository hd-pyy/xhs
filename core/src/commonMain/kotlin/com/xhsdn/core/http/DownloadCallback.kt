package com.xhsdn.core.http

/**
 * 下载进度回调。
 * 对应 [app/.../DownloadCallback.java]，行为完全一致。
 */
interface DownloadCallback {
    /** 文件下载成功 */
    fun onFileDownloaded(filePath: String)
    /** 单个文件下载失败 */
    fun onDownloadError(status: String, originalUrl: String)
    /** 文本进度（如 "Live photo 合成失败"） */
    fun onDownloadProgress(status: String)
    /** 字节级进度 */
    fun onDownloadProgressUpdate(downloaded: Long, total: Long)
    /** 检测到视频（用于触发 "是否继续" 提示） */
    fun onVideoDetected()
    /** 是否已取消 */
    fun isCancelled(): Boolean
}
