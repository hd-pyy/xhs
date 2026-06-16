package com.xhsdn.core.platform

import android.content.Context
import com.neoruaa.xhsdn.LivePhotoCreator
import java.io.File

/**
 * Android 端 [LivePhotoWriter] 实现。
 * 直接复用现有 [LivePhotoCreator]（MIUI/OPPO/Google 三家 XMP 实现）。
 */
class AndroidLivePhotoWriter(private val appContext: Context) : LivePhotoWriter {
    override suspend fun create(imagePath: String, videoPath: String, outputPath: String): Boolean {
        return try {
            val imageFile = File(imagePath)
            val videoFile = File(videoPath)
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()
            LivePhotoCreator.createLivePhoto(imageFile, videoFile, outputFile, appContext)
        } catch (e: Throwable) {
            false
        }
    }
}
