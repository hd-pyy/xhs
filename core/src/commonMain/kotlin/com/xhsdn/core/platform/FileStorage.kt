package com.xhsdn.core.platform

import java.io.File

/**
 * 跨平台文件存储抽象。
 * Android 端走 MediaStore 写公共目录，Desktop 端走用户 Pictures/Videos/xhsdn 目录。
 */
interface FileStorage {

    /** 写图片。返回最终保存的绝对路径。 */
    suspend fun saveImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String

    /** 写视频。返回最终保存的绝对路径。 */
    suspend fun saveVideo(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String

    /** 把缓存文件移动/复制到最终位置。 */
    suspend fun copyCacheToTarget(
        cache: File,
        fileName: String,
        mimeType: String,
        isVideo: Boolean,
    ): String

    /** App 的"图"目录。 */
    fun picturesDir(): String

    /** App 的"视频"目录。 */
    fun videosDir(): String

    /**
     * 设置图片/视频的根保存目录。null 表示恢复为平台默认。
     * 仅支持支持用户自定义目录的平台（Desktop）；不支持时返回 false。
     */
    fun setBaseDirs(picturesRoot: String?, videosRoot: String?): Boolean = false
}
