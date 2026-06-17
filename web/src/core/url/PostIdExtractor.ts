/**
 * 从 XHS URL 中提取 noteId(帖子 ID)。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/url/PostIdExtractor.kt`。
 * 处理 `explore/...`、`item/...`、短链 `xhslink.com/...` 三种情况。
 */
const ID_PATTERN = /(?:explore|item)\/([a-zA-Z0-9_-]+)\/?(?:\?|$)/;
const ID_USER_PATTERN = /user\/profile\/[a-z0-9]+\/([a-zA-Z0-9_-]+)\/?(?:\?|$)/;

export function extractPostId(url: string | null | undefined): string | null {
  if (!url) return null;

  const m1 = url.match(ID_PATTERN);
  if (m1 && m1[1]) return m1[1];

  const m2 = url.match(ID_USER_PATTERN);
  if (m2 && m2[1]) return m2[1];

  // 短链兜底:xhslink.com/路径,ID 通常在最后一段
  if (url.includes('xhslink.com/')) {
    const parts = url.split('/');
    if (parts.length > 0) {
      let last = parts[parts.length - 1];
      if (last.includes('?')) last = last.substring(0, last.indexOf('?'));
      if (last && last !== 'o') return last;
      if (parts.length > 1) {
        let second = parts[parts.length - 2];
        if (second.includes('?')) second = second.substring(0, second.indexOf('?'));
        if (second) return second;
      }
    }
  }
  return null;
}
