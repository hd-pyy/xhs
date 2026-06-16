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
}
