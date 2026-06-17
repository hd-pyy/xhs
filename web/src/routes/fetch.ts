import { Router } from 'express';

import { determineFileExtension } from '../core/download/MediaExtensionUtil';
import { fetchBytes } from '../http/xhsHttp';

/**
 * 单文件代理下载 —— 不落盘,服务端 fetch XHS CDN URL 后原样 stream 回浏览器。
 *
 * 浏览器收到 `Content-Disposition: attachment` 后会弹原生下载框,文件落到用户本机。
 * 多文件场景由前端循环 N 次请求,每次拿到一个独立文件。
 *
 * Query:
 *   url      必填,XHS CDN 链接
 *   name     可选,自定义下载文件名;缺省时从 url 推断扩展名
 */
export const fetchRouter = Router();

fetchRouter.get('/fetch', async (req, res) => {
  const url = typeof req.query.url === 'string' ? req.query.url : '';
  const customName = typeof req.query.name === 'string' ? req.query.name : '';
  if (!url) {
    return res.status(400).json({ error: 'url 不能为空' });
  }
  // 只放行 http(s),防止 SSRF
  if (!/^https?:\/\//.test(url)) {
    return res.status(400).json({ error: 'url 必须以 http(s):// 开头' });
  }

  const result = await fetchBytes(url);
  if (!result) {
    return res.status(502).json({ error: `fetch 失败: ${url}` });
  }

  // 文件名:优先用前端传过来的,否则用 url hash + 扩展名
  const ext = determineFileExtension(url);
  const fileName = customName
    ? customName.replace(/[\\/:*?"<>|]/g, '_')
    : `xhsdn-${Date.now()}.${ext}`;

  res.setHeader('Content-Type', result.contentType);
  res.setHeader('Content-Length', String(result.bytes.length));
  res.setHeader(
    'Content-Disposition',
    `attachment; filename*=UTF-8''${encodeURIComponent(fileName)}`,
  );
  res.setHeader('Cache-Control', 'no-store');
  res.end(result.bytes);
});
