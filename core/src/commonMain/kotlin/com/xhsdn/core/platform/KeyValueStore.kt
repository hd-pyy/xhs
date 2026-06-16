package com.xhsdn.core.platform

/**
 * 跨平台 KV 存储。
 * Android 用 SharedPreferences，Desktop 用 java.util.prefs.Preferences（Windows 注册表）。
 */
interface KeyValueStore {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)
    fun remove(key: String)
    fun getAll(): Map<String, String?>
}
