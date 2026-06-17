import { Router } from 'express';
import { request } from 'undici';

import { extractLinks } from '../core/url/LinkExtractor';
import { extractPostId } from '../core/url/PostIdExtractor';
import { parse as parseNoteDetails } from '../core/parse/NoteDetailsParser';

const DEFAULT_UA =
  'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36 xiaohongshu';

export const parseRouter = Router();

interface ParseRequest {
  text?: string;
}

async function resolveShortUrl(shortUrl: string): Promise<string | null> {
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

async function fetchText(url: string): Promise<string | null> {
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
      const html = await res.body.text();
      return html;
    }
    return null;
  } catch {
    return null;
  }
}

parseRouter.post('/parse', async (req, res) => {
  const { text } = (req.body ?? {}) as ParseRequest;
  if (!text || typeof text !== 'string') {
    return res.status(400).json({ error: 'text 不能为空' });
  }

  try {
    const urls = extractLinks(text);
    if (urls.length === 0) {
      return res.status(400).json({ error: '未在输入中找到 XHS 链接' });
    }

    const resolved: string[] = [];
    for (const u of urls) {
      if (u.includes('xhslink.com')) {
        const r = await resolveShortUrl(u);
        resolved.push(r ?? u);
      } else {
        resolved.push(u);
      }
    }

    const out: Array<{
      originalUrl: string;
      postId: string | null;
      mediaUrls: string[];
      livePhotoPairs: Array<{
        imageUrl: string | null;
        videoUrl: string | null;
        isLivePhoto: boolean;
      }>;
      error?: string;
    }> = [];

    for (const url of resolved) {
      const postId = extractPostId(url);
      const html = await fetchText(url);
      if (!html) {
        out.push({ originalUrl: url, postId, mediaUrls: [], livePhotoPairs: [], error: 'fetch 失败' });
        continue;
      }
      const result = parseNoteDetails(html);
      out.push({
        originalUrl: url,
        postId,
        mediaUrls: result.mediaUrls,
        livePhotoPairs: result.livePhotoPairs.map((p) => ({
          imageUrl: p.imageUrl,
          videoUrl: p.videoUrl,
          isLivePhoto: p.isLivePhoto,
        })),
      });
    }

    return res.json({ results: out });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return res.status(500).json({ error: `解析异常: ${msg}` });
  }
});
