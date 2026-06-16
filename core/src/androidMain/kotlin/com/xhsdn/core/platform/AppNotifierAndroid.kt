package com.xhsdn.core.platform

import android.content.Context

/**
 * Android 端 [AppNotifier] 实现。
 * 委托给现有 [com.neoruaa.xhsdn.utils.NotificationHelper]。
 */
class AndroidAppNotifier(private val appContext: Context) : AppNotifier {
    override fun notify(id: Int, title: String, text: String, progress: Int?) {
        com.neoruaa.xhsdn.utils.NotificationHelper.showDownloadNotification(
            appContext, id, title, text, false,
        )
    }

    override fun cancel(id: Int) {
        com.neoruaa.xhsdn.utils.NotificationHelper.cancelNotification(appContext, id)
    }
}
