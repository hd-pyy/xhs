package com.xhsdn.desktop.ui

import java.awt.Component
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * 弹出系统目录选择对话框（Windows 上是 Win32 "浏览文件夹" 风格的 JFileChooser 渲染）。
 * 显式从 EDT 调用并尽量使用活动窗口作为 parent，避免被遮到后面或最小化。
 */
object DirectoryPicker {

    /**
     * @param initial 当前已选的目录路径，作为对话框的初始位置。
     * @return 用户选中的目录；取消或出错时返回 null。
     */
    fun pickDirectory(initial: String?): File? {
        return runCatching {
            // 必须从 EDT 调用 JFileChooser.showOpenDialog，否则在某些 swing 实现下会卡住
            val parent = findActiveFrame()
            if (SwingUtilities.isEventDispatchThread()) {
                doPick(initial, parent)
            } else {
                var result: File? = null
                SwingUtilities.invokeAndWait { result = doPick(initial, parent) }
                result
            }
        }.getOrNull()
    }

    private fun doPick(initial: String?, parent: Component?): File? {
        val chooser = JFileChooser(initial ?: System.getProperty("user.home")).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            dialogTitle = "选择保存目录"
        }
        return when (chooser.showOpenDialog(parent)) {
            JFileChooser.APPROVE_OPTION -> chooser.selectedFile
            else -> null
        }
    }

    private fun findActiveFrame(): Frame? {
        return Frame.getFrames().firstOrNull { it.isVisible && it.isActive }
            ?: Frame.getFrames().firstOrNull { it.isVisible }
    }
}
