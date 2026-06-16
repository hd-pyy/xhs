package com.xhsdn.core.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Desktop 端 [ClipboardAccess] 实现。
 * 跨平台 [java.awt.Toolkit] 提供 system clipboard。
 */
object DesktopClipboardAccess : ClipboardAccess {

    private val clipboard: java.awt.datatransfer.Clipboard = Toolkit.getDefaultToolkit().systemClipboard

    override fun getText(): String? = runCatching {
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else null
    }.getOrNull()

    override fun setText(text: String) {
        clipboard.setContents(StringSelection(text), null)
    }
}
