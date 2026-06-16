package com.xhsdn.core.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 在 `window.__INITIAL_STATE__` 根对象中查找 note 对象。
 * 复刻 [XHSDownloader.findNoteObjects]（java:924-1052）。
 *
 * 已知结构（按优先级）：
 * 1. `root.note.noteDetailMap[*].note`
 * 2. `root.note.feed.items[*]`
 * 3. `root.note` 直接是 note
 * 4. `root.feed.items[*]`
 * 5. `root.noteData.data.noteData` (新结构)
 * 6. 上述都不命中时做深搜兜底
 */
object NoteFinder {

    fun findNoteObjects(root: JsonObject): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        val seenIds = mutableSetOf<String>()

        fun addCandidate(note: JsonObject?) {
            if (note == null) return
            if (note.isEmpty()) return
            val noteId = (note["noteId"] as? JsonPrimitive)?.contentOrNullSafe()
            if (!noteId.isNullOrEmpty()) {
                if (!seenIds.add(noteId)) return
            }
            out += note
        }

        // 1) root.note.*
        val noteRoot = root["note"] as? JsonObject
        if (noteRoot != null) {
            val noteDetailMap = noteRoot["noteDetailMap"] as? JsonObject
            if (noteDetailMap != null) {
                for ((_, value) in noteDetailMap) {
                    val noteData = value as? JsonObject
                    val note = noteData?.get("note") as? JsonObject
                    addCandidate(note)
                }
            } else {
                val innerNote = noteRoot["note"] as? JsonObject
                if (innerNote != null) {
                    addCandidate(innerNote)
                } else {
                    val feed = noteRoot["feed"] as? JsonObject
                    val items = feed?.get("items") as? JsonArray
                    if (items != null) {
                        for (item in items) {
                            addCandidate(item as? JsonObject)
                        }
                    } else {
                        addCandidate(noteRoot)
                    }
                }
            }
        }

        // 2) root.feed.items
        val rootFeed = root["feed"] as? JsonObject
        val rootItems = rootFeed?.get("items") as? JsonArray
        if (rootItems != null) {
            for (item in rootItems) {
                addCandidate(item as? JsonObject)
            }
        }

        // 3) root.noteData.data.noteData
        val noteDataRoot = root["noteData"] as? JsonObject
        if (noteDataRoot != null) {
            val data = noteDataRoot["data"] as? JsonObject
            if (data != null) {
                val note = (data["noteData"] ?: data["note"]) as? JsonObject
                addCandidate(note)
            }
        }

        // 4) 兜底深搜
        val hasLikely = out.any { isLikelyNoteObject(it) }
        if (out.isEmpty() || !hasLikely) {
            deepScan(root, out, seenIds) { addCandidate(it) }
        }

        return out
    }

    private fun isLikelyNoteObject(obj: JsonObject): Boolean {
        val imageList = obj["imageList"] as? JsonArray
        if (imageList != null && imageList.isNotEmpty()) {
            val first = imageList.first() as? JsonObject ?: return false
            return first.containsKey("urlDefault") || first.containsKey("url") ||
                first.containsKey("traceId") || first.containsKey("infoList")
        }
        val images = obj["images"] as? JsonArray
        if (images != null && images.isNotEmpty()) {
            val first = images.first() as? JsonObject ?: return false
            return first.containsKey("urlDefault") || first.containsKey("url") ||
                first.containsKey("traceId") || first.containsKey("infoList")
        }
        val video = obj["video"] as? JsonObject
        if (video != null) {
            return video.containsKey("consumer") || video.containsKey("media")
        }
        return false
    }

    private fun deepScan(
        root: JsonElement,
        out: MutableList<JsonObject>,
        seenIds: MutableSet<String>,
        addCandidate: (JsonObject?) -> Unit,
    ) {
        val stack = ArrayDeque<JsonElement>()
        stack.addLast(root)
        var visited = 0
        val maxVisited = 50_000
        val maxNotes = 5
        while (stack.isNotEmpty() && visited < maxVisited && out.size < maxNotes) {
            val current = stack.removeLast()
            visited++
            when (current) {
                is JsonObject -> {
                    val innerNote = current["note"] as? JsonObject
                    if (innerNote != null) stack.addLast(innerNote)
                    if (isLikelyNoteObject(current)) {
                        // 兼容原 addNoteCandidate 行为：去重后加入
                        val noteId = (current["noteId"] as? JsonPrimitive)?.contentOrNullSafe()
                        if (noteId.isNullOrEmpty() || seenIds.add(noteId)) {
                            out += current
                            if (out.size >= maxNotes) break
                        }
                    }
                    for ((_, v) in current) {
                        if (v is JsonObject || v is JsonArray) stack.addLast(v)
                    }
                }
                is JsonArray -> {
                    for (v in current) {
                        if (v is JsonObject || v is JsonArray) stack.addLast(v)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else null
}
