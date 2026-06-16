package com.xhsdn.core.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop 端 [FileStorage] 实现。
 * 默认写文件到 `%USERPROFILE%/Pictures/xhsdn` 与 `%USERPROFILE%/Videos/xhsdn`，
 * 也可通过 [setPicturesBaseDir] / [setVideosBaseDir] 由用户在设置页修改。
 * 不依赖 Android MediaStore，因此 Windows 资源管理器不会"按应用分组"，但文件存在路径稳定。
 */
class DesktopFileStorage(
    initialPicturesBaseDir: File = defaultPicturesBaseDir(),
    initialVideosBaseDir: File = defaultVideosBaseDir(),
) : FileStorage {

    @Volatile private var picturesBaseDir: File = initialPicturesBaseDir
    @Volatile private var videosBaseDir: File = initialVideosBaseDir

    /** 设置图片保存的根目录。`xhsdn/` 子目录会自动创建在该目录下。 */
    fun setPicturesBaseDir(dir: File) {
        picturesBaseDir = dir
    }

    /** 设置视频保存的根目录。`xhsdn/` 子目录会自动创建在该目录下。 */
    fun setVideosBaseDir(dir: File) {
        videosBaseDir = dir
    }

    /** 每次调用都重新解析——这样保存位置变更后下一次保存就生效。 */
    private fun resolvePicturesDir(): File =
        File(picturesBaseDir, "xhsdn").also { it.mkdirs() }

    private fun resolveVideosDir(): File =
        File(videosBaseDir, "xhsdn").also { it.mkdirs() }

    override suspend fun saveImage(bytes: ByteArray, fileName: String, mimeType: String): String {
        val out = uniqueFile(resolvePicturesDir(), fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    override suspend fun saveVideo(bytes: ByteArray, fileName: String, mimeType: String): String {
        val out = uniqueFile(resolveVideosDir(), fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    override suspend fun copyCacheToTarget(cache: File, fileName: String, mimeType: String, isVideo: Boolean): String {
        val target = uniqueFile(if (isVideo) resolveVideosDir() else resolvePicturesDir(), fileName)
        Files.move(cache.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target.absolutePath
    }

    override fun picturesDir(): String = resolvePicturesDir().absolutePath
    override fun videosDir(): String = resolveVideosDir().absolutePath

    override fun setBaseDirs(picturesRoot: String?, videosRoot: String?): Boolean {
        picturesRoot?.let { picturesBaseDir = File(it) }
        videosRoot?.let { videosBaseDir = File(it) }
        return true
    }

    /** 同名文件自动加 `(1)`, `(2)` 后缀。 */
    private fun uniqueFile(dir: File, name: String): File {
        val target = File(dir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (true) {
            val candidate = File(dir, "${base}_($i)$ext")
            if (!candidate.exists()) return candidate
            i++
        }
    }

    companion object {
        private fun defaultPicturesBaseDir(): File =
            File(System.getProperty("user.home") ?: ".", "Pictures")
        private fun defaultVideosBaseDir(): File =
            File(System.getProperty("user.home") ?: ".", "Videos")
    }
}
