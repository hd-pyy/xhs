/**
 * 文件扩展名与视频 URL 识别。
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/download/MediaExtensionUtil.kt`。
 */

/** 决定文件扩展名(不含点)。 */
export function determineFileExtension(url: string | null | undefined): string {
  if (!url) return 'jpg';
  const lower = url.toLowerCase();
  if (lower.includes('.jpg') || lower.includes('.jpeg')) return 'jpg';
  if (lower.includes('.png')) return 'png';
  if (lower.includes('.gif')) return 'gif';
  if (lower.includes('.webp')) return 'webp';
  if (
    lower.includes('.mp4') ||
    lower.includes('video') ||
    lower.includes('masterurl') ||
    lower.includes('stream')
  ) {
    return 'mp4';
  }
  if (url.includes('xhscdn.com')) {
    return url.includes('h264') || url.includes('stream') ? 'mp4' : 'jpg';
  }
  return 'jpg';
}

/** 是否视频 URL(用于文件类型分发)。 */
export function isVideoUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  const lower = url.toLowerCase();
  return (
    lower.includes('.mp4') ||
    lower.includes('.mov') ||
    lower.includes('.avi') ||
    lower.includes('.webm') ||
    lower.includes('video') ||
    lower.includes('masterurl') ||
    lower.includes('stream') ||
    lower.includes('sns-video') ||
    lower.includes('/spectrum/')
  );
}

/** 是否主帖视频(非 Live Photo 配对里的视频流)。 */
export function isMainPostVideoUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  return url.includes('sns-video-bd') && (url.includes('pre_post') || url.includes('originVideoKey'));
}
