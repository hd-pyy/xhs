/**
 * xhscdn.com URL → ci.xiaohongshu.com 转换器。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/url/UrlTransformer.kt`。
 * 视频 URL(包含 `video` 或 `sns-video`)保持原样不转换。
 */
export function transformXhsCdnUrl(originalUrl: string | null | undefined): string {
  if (!originalUrl) return originalUrl ?? '';
  if (!originalUrl.includes('xhscdn.com')) return originalUrl;
  if (originalUrl.includes('video') || originalUrl.includes('sns-video')) return originalUrl;

  const parts = originalUrl.split('/');
  if (parts.length <= 5) return originalUrl;

  const tokens: string[] = [];
  for (let i = 5; i < parts.length; i++) {
    tokens.push(parts[i]);
  }
  const fullToken = tokens.join('/');
  // Kotlin: split("[!?]".toRegex()).firstOrNull()
  const token = fullToken.split(/[!?]/).find((s) => s.length > 0);
  if (!token) return originalUrl;
  return `https://ci.xiaohongshu.com/${token}`;
}
