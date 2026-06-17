/**
 * 从用户输入(剪贴板、粘贴、手动输入)中提取 XHS 链接。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/url/LinkExtractor.kt`。
 * Kotlin `Regex.find(part)` 等价于 TS `regex.exec(part)` —— 返回 null 时即未命中。
 */
const XHS_LINK_PATTERN = /(?:https?:\/\/)?www\.xiaohongshu\.com\/explore\/\S+/;
const XHS_USER_PATTERN = /(?:https?:\/\/)?www\.xiaohongshu\.com\/user\/profile\/[a-z0-9]+\/\S+/;
const XHS_SHARE_PATTERN = /(?:https?:\/\/)?www\.xiaohongshu\.com\/discovery\/item\/\S+/;
const XHS_SHORT_PATTERN = /(?:https?:\/\/)?xhslink\.com\/[^\s"<>\\^`{|}，。；！？、【】《》]+/;

export function extractLinks(input: string | null | undefined): string[] {
  if (!input) return [];
  const urls: string[] = [];
  // Kotlin "\\s+" 等价于 JS /\s+/
  const parts = input.split(/\s+/);
  for (const part of parts) {
    if (!part) continue;

    const shortMatch = part.match(XHS_SHORT_PATTERN);
    if (shortMatch) {
      urls.push(shortMatch[0]);
      continue;
    }
    const shareMatch = part.match(XHS_SHARE_PATTERN);
    if (shareMatch) {
      urls.push(shareMatch[0]);
      continue;
    }
    const linkMatch = part.match(XHS_LINK_PATTERN);
    if (linkMatch) {
      urls.push(linkMatch[0]);
      continue;
    }
    const userMatch = part.match(XHS_USER_PATTERN);
    if (userMatch) {
      urls.push(userMatch[0]);
    }
  }
  return urls;
}
