package com.xhsdn.core.platform

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Android 端 [FileStorage] 实现。
 * 走 MediaStore 把图片/视频写入 `Pictures/xhsdn` 与 `Movies/xhsdn`。
 */
class AndroidFileStorage(private val appContext: Context) : FileStorage {

    override suspend fun saveImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String = saveToMediaStore(bytes, fileName, mimeType, isVideo = false)

    override suspend fun saveVideo(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): String = saveToMediaStore(bytes, fileName, mimeType, isVideo = true)

    override suspend fun copyCacheToTarget(
        cache: File,
        fileName: String,
        mimeType: String,
        isVideo: Boolean,
    ): String {
        val bytes = cache.readBytes()
        return saveToMediaStore(bytes, fileName, mimeType, isVideo)
    }

    override fun picturesDir(): String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/xhsdn"

    override fun videosDir(): String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath + "/xhsdn"

    private fun saveToMediaStore(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        isVideo: Boolean,
    ): String {
        val resolver = appContext.contentResolver
        val collection = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) Environment.DIRECTORY_MOVIES + "/xhsdn" else Environment.DIRECTORY_PICTURES + "/xhsdn")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri: Uri = resolver.insert(collection, values) ?: return fileName
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri.toString()
    }
}

/** 在私有目录写临时文件（用于 Live Photo 暂存等场景）。 */
fun writeBytesToPrivateDir(context: Context, name: String, bytes: ByteArray): File {
    val dir = context.getExternalFilesDir(null) ?: context.filesDir
    if (!dir.exists()) dir.mkdirs()
    val out = File(dir, name)
    FileOutputStream(out).use { it.write(bytes) }
    return out
}
