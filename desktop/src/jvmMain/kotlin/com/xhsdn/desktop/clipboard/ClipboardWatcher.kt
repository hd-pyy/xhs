package com.xhsdn.desktop.clipboard

import com.xhsdn.core.platform.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 桌面端剪贴板监听器。
 *
 * MVP 实现：800ms 轮询 `PlatformContext.current.clipboardAccess.getText()`。
 * 检测到变化时通过 [events] SharedFlow 发射。
 *
 * TODO: 阶段 7 后可替换为 Win32 `AddClipboardFormatListener` via JNA。
 */
class ClipboardWatcher(private val intervalMs: Long = 800L) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var last: String? = null

    private val _events = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            val clip = PlatformContext.current.clipboardAccess
            while (isActive) {
                runCatching {
                    val current = clip.getText()
                    if (current != null && current != last) {
                        last = current
                        _events.emit(current)
                    }
                }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
