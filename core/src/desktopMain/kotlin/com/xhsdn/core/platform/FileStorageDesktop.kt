package com.xhsdn.core.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop 端 [FileStorage] 实现。
 * 写文件到 `%USERPROFILE%/Pictures/xhsdn` 与 `%USERPROFILE%/Videos/xhsdn`。
 * 不依赖 Android MediaStore，因此 Windows 资源管理器不会"按应用分组"，但文件存在路径稳定。
 */
class DesktopFileStorage : FileStorage {

    private val picturesDir: File by lazy {
        File(homeDir(), "Pictures/xhsdn").also { it.mkdirs() }
    }

    private val videosDir: File by lazy {
        File(homeDir(), "Videos/xhsdn").also { it.mkdirs() }
    }

    private fun homeDir(): String = System.getProperty("user.home") ?: "."

    override suspend fun saveImage(bytes: ByteArray, fileName: String, mimeType: String): String {
        val out = uniqueFile(picturesDir, fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    override suspend fun saveVideo(bytes: ByteArray, fileName: String, mimeType: String): String {
        val out = uniqueFile(videosDir, fileName)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    override suspend fun copyCacheToTarget(cache: File, fileName: String, mimeType: String, isVideo: Boolean): String {
        val target = uniqueFile(if (isVideo) videosDir else picturesDir, fileName)
        Files.move(cache.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return target.absolutePath
    }

    override fun picturesDir(): String = picturesDir.absolutePath
    override fun videosDir(): String = videosDir.absolutePath

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
}
