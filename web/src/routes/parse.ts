import { Router } from 'express';

import { extractLinks } from '../core/url/LinkExtractor';
import { extractPostId } from '../core/url/PostIdExtractor';
import { parse as parseNoteDetails } from '../core/parse/NoteDetailsParser';
import { fetchHtml, resolveShortUrl } from '../http/xhsHttp';

export const parseRouter = Router();

interface ParseRequest {
  text?: string;
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
      const html = await fetchHtml(url);
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
