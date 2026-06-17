import { transformXhsCdnUrl } from '../url/UrlTransformer';

/**
 * 从 note JSON 中提取媒体 URL 与 Live Photo 配对。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/parse/MediaUrlExtractor.kt`。
 */

type Json = null | boolean | number | string | Json[] | { [k: string]: Json };
type JsonObject = { [k: string]: Json };
type JsonArray = Json[];

export interface MediaPair {
  originalImageUrl: string | null;
  originalVideoUrl: string | null;
  imageUrl: string | null;
  videoUrl: string | null;
  isLivePhoto: boolean;
}

export type OnVideoDetected = () => void;

function isObject(v: Json | undefined): v is JsonObject {
  return !!v && typeof v === 'object' && !Array.isArray(v);
}
function isArray(v: Json | undefined): v is JsonArray {
  return Array.isArray(v);
}
function asString(v: Json | undefined): string | null {
  return typeof v === 'string' ? v : null;
}

function pickString(obj: JsonObject, keys: string[]): string | null {
  for (const k of keys) {
    const v = asString(obj[k]);
    if (v !== null) return v;
  }
  return null;
}

export function extract(
  note: JsonObject,
  mediaPairs: MediaPair[],
  onVideoDetected?: OnVideoDetected,
): string[] {
  const mediaUrls: string[] = [];
  let videosDetected = false;

  // 1. 视频主帖
  const video = note['video'];
  if (isObject(video)) {
    const consumer = video['consumer'];
    if (isObject(consumer)) {
      const originKey = asString(consumer['originVideoKey']);
      if (originKey) {
        const videoUrl = `https://sns-video-bd.xhscdn.com/${originKey}`;
        mediaUrls.push(videoUrl);
        videosDetected = true;
        if (onVideoDetected) onVideoDetected();
      }
    } else {
      const media = video['media'];
      if (isObject(media)) {
        const stream = media['stream'];
        if (isObject(stream)) {
          const h265 = stream['h265'];
          if (isArray(h265)) {
            for (const item of h265) {
              let url: string | null = null;
              if (typeof item === 'string') {
                url = item.startsWith('http') ? item : null;
              } else if (isObject(item)) {
                url = asString(item['url']) ?? asString(item['masterUrl']);
              }
              if (url) {
                mediaUrls.push(url);
                videosDetected = true;
                if (onVideoDetected) onVideoDetected();
              }
            }
          }
        }
      }
    }
  }

  // 2. 图片列表(图集 / Live Photo)
  let imageList: JsonArray | null = null;
  if (isArray(note['imageList'])) {
    imageList = note['imageList'] as JsonArray;
  } else if (isArray(note['images'])) {
    imageList = note['images'] as JsonArray;
  } else if (isObject(note['image'])) {
    imageList = [note['image'] as JsonObject];
  }

  if (imageList) {
    for (const element of imageList) {
      if (!isObject(element)) continue;
      const image = element;
      const imageUrl =
        pickString(image, ['urlDefault', 'url']) ??
        (() => {
          const traceId = asString(image['traceId']);
          return traceId ? `https://sns-img-qc.xhscdn.com/${traceId}` : null;
        })() ??
        (() => {
          const infoList = image['infoList'];
          if (isArray(infoList)) {
            const first = infoList[0];
            if (isObject(first)) return asString(first['url']);
          }
          return null;
        })();

      // Live Photo: image.stream.h264[0].masterUrl / url
      let livePhotoVideoUrl: string | null = null;
      const stream = image['stream'];
      if (isObject(stream)) {
        const h264 = stream['h264'];
        if (isArray(h264) && h264.length > 0) {
          const first = h264[0];
          if (isObject(first)) {
            livePhotoVideoUrl = asString(first['masterUrl']) ?? asString(first['url']);
          }
        }
      }

      if (imageUrl) {
        if (livePhotoVideoUrl) {
          mediaPairs.push({
            originalImageUrl: imageUrl,
            originalVideoUrl: livePhotoVideoUrl,
            imageUrl,
            videoUrl: livePhotoVideoUrl,
            isLivePhoto: true,
          });
          videosDetected = true;
          if (onVideoDetected) onVideoDetected();
        } else {
          mediaPairs.push({
            originalImageUrl: imageUrl,
            originalVideoUrl: null,
            imageUrl,
            videoUrl: null,
            isLivePhoto: false,
          });
        }
        // 修复 Java 版的 bug:图片 URL 也要进返回值
        mediaUrls.push(imageUrl);
      }
    }
  }

  return mediaUrls;
}

// 抑制 unused warning: videosDetected 在 Kotlin 内部用于 shouldCreateLivePhotos 决策,
// 这里仅作为副作用占位,TS 侧由上层判定。
export const _videosDetectedPlaceholder = (b: boolean) => b;
