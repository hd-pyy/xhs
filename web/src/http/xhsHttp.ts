import { request } from 'undici';

/**
 * XHS HTTP 公共 helper。Android 风格 UA、短链 follow redirect、抓 HTML。
 * 抽出来给 /api/parse 与 /api/stream 共用,避免两处复制。
 */

export const DEFAULT_UA =
  'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36 xiaohongshu';

/** 跟随短链重定向,返回最终 URL;失败返回 null。 */
export async function resolveShortUrl(shortUrl: string): Promise<string | null> {
  try {
    const res = await request(shortUrl, {
      method: 'GET',
      headers: { 'User-Agent': DEFAULT_UA },
      maxRedirections: 10,
    });
    if (res.statusCode >= 200 && res.statusCode < 300) {
      const finalUrl = (res as unknown as { url?: string }).url ?? shortUrl;
      await res.body.dump();
      return finalUrl;
    }
    return null;
  } catch {
    return null;
  }
}

/** 抓笔记详情页 HTML;失败返回 null。 */
export async function fetchHtml(url: string): Promise<string | null> {
  try {
    const res = await request(url, {
      method: 'GET',
      headers: {
        'User-Agent': DEFAULT_UA,
        Accept:
          'text/html,application/xhtml+xml,application/xml;q=1.0,image/avif,image/webp,image/apng,*/*;q=1.0',
      },
      maxRedirections: 5,
    });
    if (res.statusCode >= 200 && res.statusCode < 300) {
      return await res.body.text();
    }
    return null;
  } catch {
    return null;
  }
}

/** 抓二进制(图片/视频);失败返回 null。带简单重试。 */
export async function fetchBytes(
  url: string,
  maxAttempts = 3,
): Promise<{ bytes: Buffer; contentType: string } | null> {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const res = await request(url, {
        method: 'GET',
        headers: { 'User-Agent': DEFAULT_UA },
        maxRedirections: 5,
      });
      if (res.statusCode >= 200 && res.statusCode < 300) {
        const bytes = Buffer.from(await res.body.arrayBuffer());
        const contentType =
          (res.headers['content-type'] as string | undefined) ?? 'application/octet-stream';
        return { bytes, contentType };
      }
      await res.body.dump();
    } catch {
      // 网络错误,继续重试
    }
  }
  return null;
}
