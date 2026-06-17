# XHS Downloader · Web 版

> 版本:`0.1.0-web`
> 分支:`feat/web`
> 发版 tag 命名:`v*.*.*-web`(如 `v0.1.0-web`)

## 这是什么

把 Kotlin KMP 项目里的 `:core` 模块(解析 + 下载 XHS 笔记的算法)1:1 翻译成 TypeScript,
用 Node + Express + undici 重新实现一遍。前端是一个单 HTML 页面。

完整复用策略:

- `:core/src/commonMain/.../url/LinkExtractor.ts` ← 翻译自 `url/LinkExtractor.kt`
- `:core/src/commonMain/.../url/UrlUtils.ts` ← `url/UrlUtils.kt`
- `:core/src/commonMain/.../url/PostIdExtractor.ts` ← `url/PostIdExtractor.kt`
- `:core/src/commonMain/.../url/UrlTransformer.ts` ← `url/UrlTransformer.kt`
- `:core/src/commonMain/.../parse/JsLiteralExtractor.ts` ← `parse/JsLiteralExtractor.kt`(状态机 1:1 翻译)
- `:core/src/commonMain/.../parse/InitialStateParser.ts` ← `parse/InitialStateParser.kt`
- `:core/src/commonMain/.../parse/NoteFinder.ts` ← `parse/NoteFinder.kt`(6 个优先路径)
- `:core/src/commonMain/.../parse/MediaUrlExtractor.ts` ← `parse/MediaUrlExtractor.kt`
- `:core/src/commonMain/.../parse/NoteDetailsParser.ts` ← `parse/NoteDetailsParser.kt`
- `:core/src/commonMain/.../naming/NamingFormat.ts` ← `naming/NamingFormat.kt`
- `:core/src/commonMain/.../naming/TemplateApplier.ts` ← `naming/TemplateApplier.kt`
- `:core/src/commonMain/.../download/MediaExtensionUtil.ts` ← `download/MediaExtensionUtil.kt`
- `:core/src/commonMain/.../download/DownloadOrchestrator.ts` ← `download/DownloadOrchestrator.kt`(OkHttp → undici, File → fs/promises, 协程 → async/await, 4 并发内联)

平台层:

- `platform/FileStorage.ts` ← `core/desktopMain/.../platform/FileStorageDesktop.kt`(默认 `Pictures/xhsdn`、`Videos/xhsdn` 目录 + `(1)` 防重名)
- `platform/LivePhotoWriter.ts` ← Web 版先返回 false,fallback 拆 jpg + mp4
- `platform/DownloadCallback.ts` ← 控制台日志占位

> ⚠️ 不要尝试把 `:core` 的 Kotlin 代码直接 require() — 它依赖 OkHttp + `java.io.File`,
> `:core` 当前只配置了 `android` + `jvm("desktop")` 两个 target,没有 js/native target。

## 跑起来

需要 Node 18+。

```bash
cd web
npm install
npm run build     # tsc → dist/
npm start         # 启 Express 在 :3000
# 或开发热重载:
npm run dev
```

打开 http://localhost:3000

## API

- `POST /api/parse` body `{ text: string }` → `{ results: [{ postId, originalUrl, mediaUrls, livePhotoPairs, error? }] }`
- `POST /api/download` body `{ text: string }` → `{ ok, savedFiles: [{ absPath, publicUrl, fileName, isVideo, isLivePhoto, mimeType }], picturesDir, videosDir }`
- `GET /api/history` → `{ items: [{ fileName, absPath, publicUrl, isVideo, size, mtime }] }`
- `GET /api/health` → `{ ok, version, ts }`
- `GET /media/pictures/xhsdn/<file>` 静态文件(图片)
- `GET /media/videos/xhsdn/<file>` 静态文件(视频)

## 文件落盘

```
web/
├── storage/
│   ├── pictures/xhsdn/    # 图片、Live Photo fallback 出来的 jpg
│   └── videos/xhsdn/      # 视频
└── public/                # 前端单页
```

`storage/` 已经在 `.gitignore` 里,不入库。

## 版本管理(发版流程)

Web 版与 Android/Desktop 是相互独立的发布单元。

- Android: `main` 分支
- Desktop (Compose): `main` + `fix/desktop-jpackage-staging`
- Web (本目录): `feat/web` 分支,tag 用 `v*.*.*-web`

发版时:

```bash
git checkout feat/web
git pull
# 改 web/package.json 里的 version,如 0.2.0-web
npm version 0.2.0-web -m "chore(web): bump to 0.2.0-web"
git push origin feat/web
git tag -a v0.2.0-web -m "Web 版 0.2.0"
git push origin v0.2.0-web
```

## 已知限制

- **Live Photo 不会合成 mov**。Web 版 `LivePhotoWriter.create()` 永远返回 false,
  fallback 路径会同时保存 `.jpg` + `.mp4`(命名 `<baseName>_img.jpg` / `<baseName>_vid.mp4`)。
  后续要合成可接 ffmpeg。
- **无任务管理**。Web 版用 `/api/history` 列出 storage/ 目录的文件,没有持久化元数据。
- **下载是同步阻塞的**。`/api/download` 会一直 hold 到所有文件下载完才返回;
  对长任务/多图笔记可能耗时 30s+。前端目前没有进度条,后续可接 SSE / WebSocket。
