import { extract, MediaPair, OnVideoDetected } from './MediaUrlExtractor';
import { findNoteObjects } from './NoteFinder';
import { parse as parseInitialState } from './InitialStateParser';
import { transformXhsCdnUrl } from '../url/UrlTransformer';

/**
 * 把一个 note 解析为媒体 URL 列表 + Live Photo 配对。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/parse/NoteDetailsParser.kt`。
 */

export interface ParseResult {
  mediaUrls: string[];
  livePhotoPairs: MediaPair[];
}

export function parse(
  html: string | null | undefined,
  onVideoDetected?: OnVideoDetected,
): ParseResult {
  const root = parseInitialState(html);
  if (!root || typeof root !== 'object') {
    return { mediaUrls: [], livePhotoPairs: [] };
  }

  // InitialStateParser 返回 Record<string, unknown>,findNoteObjects 接受相同形状
  const notes = findNoteObjects(root);
  const allMediaPairs: MediaPair[] = [];
  const allMediaUrls: string[] = [];

  for (const note of notes) {
    const urls = extract(note, allMediaPairs, onVideoDetected);
    allMediaUrls.push(...urls);
  }

  // 对 Live Photo 配对做 CDN URL 转换
  for (const pair of allMediaPairs) {
    if (pair.originalImageUrl) pair.imageUrl = transformXhsCdnUrl(pair.originalImageUrl);
    if (pair.originalVideoUrl) pair.videoUrl = transformXhsCdnUrl(pair.originalVideoUrl);
  }

  // 重组 mediaUrls:Live Photo 的图 + 视频按顺序排在前,其余 mediaUrls 去重追加
  const ordered: string[] = [];
  const livePhotoPairs = allMediaPairs.filter((p) => p.isLivePhoto);
  for (const pair of livePhotoPairs) {
    if (pair.imageUrl) ordered.push(pair.imageUrl);
    if (pair.videoUrl) ordered.push(pair.videoUrl);
  }
  const seen = new Set(ordered);
  for (const url of allMediaUrls) {
    if (!seen.has(url)) {
      ordered.push(url);
      seen.add(url);
    }
  }

  return { mediaUrls: ordered, livePhotoPairs };
}
