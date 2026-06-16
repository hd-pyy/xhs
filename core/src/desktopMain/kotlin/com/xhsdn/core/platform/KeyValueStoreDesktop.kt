package com.xhsdn.core.platform

import java.util.prefs.Preferences

/**
 * Desktop 端 [KeyValueStore] 实现。
 * 写 Windows 注册表 `HKCU\Software\JavaSoft\Prefs\com\xhsdn\desktop`。
 * 单 key 上限 ~8KB；大列表（任务历史）走 [com.xhsdn.core.task.TaskPersistence] JSON 文件。
 */
class DesktopKeyValueStore : KeyValueStore {

    private val prefs: Preferences = Preferences.userRoot().node("com/xhsdn/desktop")

    override fun getString(key: String, default: String?): String? = prefs.get(key, default)

    override fun putString(key: String, value: String?) {
        if (value == null) prefs.remove(key) else prefs.put(key, value)
    }

    override fun remove(key: String) {
        prefs.remove(key)
    }

    override fun getAll(): Map<String, String?> {
        val keys = prefs.keys() ?: emptyArray<String>()
        return keys.associateWith { prefs.get(it, null) }
    }
}
