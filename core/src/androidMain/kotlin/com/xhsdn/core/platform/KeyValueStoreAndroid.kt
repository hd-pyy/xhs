package com.xhsdn.core.platform

import android.content.Context
import android.content.SharedPreferences

/**
 * Android 端 [KeyValueStore] 实现。
 * 复刻原 [XHSDownloader.shouldCreateLivePhotos] 等处用到的 SharedPreferences 访问。
 */
class AndroidKeyValueStore(appContext: Context) : KeyValueStore {
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("XHSDownloaderPrefs", Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun getAll(): Map<String, String?> {
        @Suppress("UNCHECKED_CAST")
        return prefs.all as Map<String, String?>
    }
}
