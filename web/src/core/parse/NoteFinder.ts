/**
 * 在 `window.__INITIAL_STATE__` 根对象中查找 note 对象。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/parse/NoteFinder.kt`。
 *
 * 已知结构(按优先级):
 * 1. `root.note.noteDetailMap[*].note`
 * 2. `root.note.feed.items[*]`
 * 3. `root.note` 直接是 note
 * 4. `root.feed.items[*]`
 * 5. `root.noteData.data.noteData` (新结构)
 * 6. 上述都不命中时做深搜兜底
 */

type Json = null | boolean | number | string | Json[] | { [k: string]: Json };
type JsonObject = { [k: string]: Json };
type JsonArray = Json[];

function isObject(v: unknown): v is JsonObject {
  return !!v && typeof v === 'object' && !Array.isArray(v);
}
function isArray(v: unknown): v is JsonArray {
  return Array.isArray(v);
}
function primitiveString(v: unknown): string | null {
  if (typeof v === 'string') return v;
  return null;
}

function isLikelyNoteObject(obj: JsonObject): boolean {
  const imageList = obj['imageList'];
  if (isArray(imageList) && imageList.length > 0) {
    const first = imageList[0];
    if (!isObject(first)) return false;
    return (
      'urlDefault' in first ||
      'url' in first ||
      'traceId' in first ||
      'infoList' in first
    );
  }
  const images = obj['images'];
  if (isArray(images) && images.length > 0) {
    const first = images[0];
    if (!isObject(first)) return false;
    return (
      'urlDefault' in first ||
      'url' in first ||
      'traceId' in first ||
      'infoList' in first
    );
  }
  const video = obj['video'];
  if (isObject(video)) {
    return 'consumer' in video || 'media' in video;
  }
  return false;
}

function deepScan(
  root: Json,
  out: JsonObject[],
  seenIds: Set<string>,
): void {
  const stack: Json[] = [root];
  let visited = 0;
  const maxVisited = 50_000;
  const maxNotes = 5;
  while (stack.length > 0 && visited < maxVisited && out.length < maxNotes) {
    const current = stack.pop()!;
    visited++;
    if (isObject(current)) {
      const innerNote = current['note'];
      if (isObject(innerNote)) stack.push(innerNote);
      if (isLikelyNoteObject(current)) {
        const noteId = primitiveString(current['noteId']);
        if (!noteId || seenIds.add(noteId)) {
          out.push(current);
          if (out.length >= maxNotes) break;
        }
      }
      for (const v of Object.values(current)) {
        if (isObject(v) || isArray(v)) stack.push(v);
      }
    } else if (isArray(current)) {
      for (const v of current) {
        if (isObject(v) || isArray(v)) stack.push(v);
      }
    }
  }
}

export function findNoteObjects(rootInput: unknown): JsonObject[] {
  if (!isObject(rootInput)) return [];
  const root = rootInput as JsonObject;
  const out: JsonObject[] = [];
  const seenIds = new Set<string>();

  function addCandidate(note: JsonObject | null | undefined) {
    if (!note || Object.keys(note).length === 0) return;
    const noteId = primitiveString(note['noteId']);
    if (noteId) {
      if (!seenIds.add(noteId)) return;
    }
    out.push(note);
  }

  // 1) root.note.*
  const noteRoot = root['note'];
  if (isObject(noteRoot)) {
    const noteDetailMap = noteRoot['noteDetailMap'];
    if (isObject(noteDetailMap)) {
      for (const value of Object.values(noteDetailMap)) {
        if (!isObject(value)) continue;
        const note = value['note'];
        addCandidate(isObject(note) ? note : null);
      }
    } else {
      const innerNote = noteRoot['note'];
      if (isObject(innerNote)) {
        addCandidate(innerNote);
      } else {
        const feed = noteRoot['feed'];
        if (isObject(feed)) {
          const items = feed['items'];
          if (isArray(items)) {
            for (const item of items) {
              addCandidate(isObject(item) ? item : null);
            }
          } else {
            addCandidate(noteRoot);
          }
        } else {
          addCandidate(noteRoot);
        }
      }
    }
  }

  // 2) root.feed.items
  const rootFeed = root['feed'];
  if (isObject(rootFeed)) {
    const rootItems = rootFeed['items'];
    if (isArray(rootItems)) {
      for (const item of rootItems) {
        addCandidate(isObject(item) ? item : null);
      }
    }
  }

  // 3) root.noteData.data.noteData
  const noteDataRoot = root['noteData'];
  if (isObject(noteDataRoot)) {
    const data = noteDataRoot['data'];
    if (isObject(data)) {
      const note = data['noteData'] ?? data['note'];
      addCandidate(isObject(note) ? note : null);
    }
  }

  // 4) 兜底深搜
  const hasLikely = out.some(isLikelyNoteObject);
  if (out.length === 0 || !hasLikely) {
    deepScan(root, out, seenIds);
  }

  return out;
}
