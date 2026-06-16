package com.xhsdn.core.platform

/**
 * Desktop 端 [AppNotifier] 实现。
 * - 始终 println（统一日志出口）
 * - 托盘通知由 :desktop 模块在初始化时通过 [setTrayHook] 注入回调
 */
object DesktopAppNotifier : AppNotifier {

    /** 托盘回调：args = (title, text)。:desktop 模块在 PlatformInitializer.init() 后调用。 */
    @Volatile
    private var trayHook: ((String, String) -> Unit)? = null

    fun setTrayHook(hook: (String, String) -> Unit) { trayHook = hook }

    override fun notify(id: Int, title: String, text: String, progress: Int?) {
        val line = "[notify] ($id) $title - $text" + (progress?.let { " [$it%]" } ?: "")
        println(line)
        runCatching { trayHook?.invoke(title, text) }
    }

    override fun cancel(id: Int) {
        println("[notify] cancel $id")
    }
}
