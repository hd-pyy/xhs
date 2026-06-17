import { extractLinks } from './LinkExtractor';

/**
 * 通用 URL 工具。1:1 翻译自
 * `core/src/commonMain/kotlin/com/xhsdn/core/url/UrlUtils.kt`。
 */
const URL_REGEX = /(?:https?|ftp):\/\/[^\s]+/;

/** 从一段文本中抽取第一个看起来像 URL 的字符串。 */
export function extractFirstUrl(text: string | null | undefined): string | null {
  if (!text) return null;
  const match = text.match(URL_REGEX);
  return match ? match[0] : null;
}

/** 是否为 XHS 链接(任何已知格式)。 */
export function isXhsLink(url: string | null | undefined): boolean {
  if (!url) return false;
  if (extractLinks(url).length > 0) return true;
  return url.includes('xiaohongshu.com') || url.includes('xhslink.com');
}
