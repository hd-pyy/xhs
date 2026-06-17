import { Router } from 'express';
import * as path from 'path';
import * as fs from 'fs';

import { WebFileStorage } from '../platform/FileStorage';
import { WebLivePhotoWriter } from '../platform/LivePhotoWriter';
import { ConsoleDownloadCallback } from '../platform/DownloadCallback';
import { DownloadOrchestrator } from '../core/download/DownloadOrchestrator';

export interface DownloadDeps {
  rootDir: string;
  publicBaseUrl: string;
}

export function buildDownloadRouter(deps: DownloadDeps): Router {
  const router = Router();
  const fileStorage = new WebFileStorage(deps.rootDir);
  const livePhotoWriter = new WebLivePhotoWriter();
  const callback = new ConsoleDownloadCallback();

  router.post('/download', async (req, res) => {
    const text = req.body?.text;
    if (!text || typeof text !== 'string') {
      return res.status(400).json({ error: 'text 不能为空' });
    }

    try {
      const orchestrator = new DownloadOrchestrator({
        fileStorage,
        livePhotoWriter,
        callback,
        baseUrl: deps.publicBaseUrl,
      });
      const result = await orchestrator.downloadContent(text);
      return res.json({
        ok: result.ok,
        savedFiles: result.savedFiles,
        picturesDir: fileStorage.picturesDir(),
        videosDir: fileStorage.videosDir(),
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      return res.status(500).json({ error: `下载异常: ${msg}` });
    }
  });

  router.get('/history', async (_req, res) => {
    // 简单列出 storage 目录下的文件,前端展示历史
    const picDir = fileStorage.picturesDir();
    const vidDir = fileStorage.videosDir();
    const items: Array<{
      fileName: string;
      absPath: string;
      publicUrl: string;
      isVideo: boolean;
      size: number;
      mtime: number;
    }> = [];

    async function walk(dir: string, isVideo: boolean) {
      try {
        const entries = await fs.promises.readdir(dir, { withFileTypes: true });
        for (const e of entries) {
          if (!e.isFile()) continue;
          const abs = path.join(dir, e.name);
          const stat = await fs.promises.stat(abs);
          const rel = abs.substring(fileStorage.rootDir().length).replace(/\\/g, '/').replace(/^\/+/, '');
          items.push({
            fileName: e.name,
            absPath: abs,
            publicUrl: `${deps.publicBaseUrl}/media/${rel}`,
            isVideo,
            size: stat.size,
            mtime: stat.mtimeMs,
          });
        }
      } catch {
        // 目录不存在时静默跳过
      }
    }

    await walk(picDir, false);
    await walk(vidDir, true);
    items.sort((a, b) => b.mtime - a.mtime);
    return res.json({ items });
  });

  return router;
}
