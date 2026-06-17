import express from 'express';
import * as path from 'path';
import * as fs from 'fs';

import { parseRouter } from './routes/parse';
import { fetchRouter } from './routes/fetch';
import { buildDownloadRouter } from './routes/download';

const PORT = Number(process.env.PORT ?? 3000);
const ROOT_DIR = path.resolve(__dirname, '..');
const PUBLIC_DIR = path.join(ROOT_DIR, 'public');
const STORAGE_DIR = path.join(ROOT_DIR, 'storage');

const app = express();
app.use(express.json({ limit: '1mb' }));

// 简单访问日志
app.use((req, _res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.url}`);
  next();
});

// 静态前端
app.use(express.static(PUBLIC_DIR));

// 把下载出来的文件通过 /media/<path> 暴露给浏览器
// 注意:这里把 ROOT_DIR/storage 整个挂上去,实际暴露的是
//   <ROOT_DIR>/storage/pictures/xhsdn/* 与 <ROOT_DIR>/storage/videos/xhsdn/*
const MEDIA_ROOT = path.join(STORAGE_DIR, 'pictures');
const MEDIA_ROOT_VIDEOS = path.join(STORAGE_DIR, 'videos');
fs.mkdirSync(path.join(MEDIA_ROOT, 'xhsdn'), { recursive: true });
fs.mkdirSync(path.join(MEDIA_ROOT_VIDEOS, 'xhsdn'), { recursive: true });
app.use(
  '/media',
  express.static(MEDIA_ROOT, {
    fallthrough: true,
    index: false,
    setHeaders: (res) => res.setHeader('Cache-Control', 'public, max-age=3600'),
  }),
);
// videos 子目录单独挂一份,让 /media/videos/xhsdn/xxx.mp4 也能访问
app.use(
  '/media/videos',
  express.static(MEDIA_ROOT_VIDEOS, {
    fallthrough: true,
    index: false,
    setHeaders: (res) => res.setHeader('Cache-Control', 'public, max-age=3600'),
  }),
);

// API
app.use('/api', parseRouter);
app.use('/api', fetchRouter);
app.use(
  '/api',
  buildDownloadRouter({
    rootDir: ROOT_DIR,
    publicBaseUrl: '', // 同源部署,前端直接用 /media/...
  }),
);

// health
app.get('/api/health', (_req, res) => {
  res.json({ ok: true, version: '0.1.0-web', ts: Date.now() });
});

app.listen(PORT, () => {
  console.log(`xhsdn-web listening on http://localhost:${PORT}`);
  console.log(`storage root: ${STORAGE_DIR}`);
});
