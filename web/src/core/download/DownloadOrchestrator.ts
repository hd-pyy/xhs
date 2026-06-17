import { request } from 'undici';
import * as path from 'path';
import * as os from 'os';
import * as fs from 'fs/promises';

import { extractLinks } from '../url/LinkExtractor';
import { extractPostId } from '../url/PostIdExtractor';
import { transformXhsCdnUrl } from '../url/UrlTransformer';
import { parse as parseNoteDetails } from '../parse/NoteDetailsParser';
import { MediaPair } from '../parse/MediaUrlExtractor';
import { determineFileExtension, isVideoUrl } from './MediaExtensionUtil';
import { buildFileBaseName, TemplateContext } from '../naming/TemplateApplier';
import { DEFAULT_TEMPLATE } from '../naming/NamingFormat';

import { FileStorage } from '../../platform/FileStorage';
import { LivePhotoWriter } from '../../platform/LivePhotoWriter';
import { DownloadCallback } from '../../platform/DownloadCallback';

/**
 * 下载编排核心。
 *
 * 1:1 翻译自 `core/src/commonMain/kotlin/com/xhsdn/core/download/DownloadOrchestrator.kt`:
 *   1. 提取/解析短链 → 真 URL
 *   2. fetchPostDetails 拿 HTML
 *   3. NoteDetailsParser 解析出媒体 URLs + Live Photo 配对
 *   4. 对每个媒体 URL:构建候选列表 → 多次重试 → 写入 FileStorage
 *   5. Live Photo:调 LivePhotoWriter.create,失败 fallback 为分别保存
 *
 * Kotlin 协程被换成 Node async/await;并发度由 `p-limit` 简化版内联实现(4 并发)。
 */

const DEFAULT_UA =
  'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36 xiaohongshu';

const MAX_ATTEMPTS = 3;
const CONCURRENCY = 4;

export interface DownloadOrchestratorOptions {
  fileStorage: FileStorage;
  livePhotoWriter: LivePhotoWriter;
  callback?: DownloadCallback;
  customNamingEnabled?: boolean;
  customFormatTemplate?: string;
  baseUrl?: string; // 用于把 fileStorage 的绝对路径转成可访问的 HTTP URL
}

export interface DownloadResult {
  ok: boolean;
  savedFiles: SavedFile[];
}

export interface SavedFile {
  absPath: string;
  publicUrl: string;
  fileName: string;
  isVideo: boolean;
  isLivePhoto: boolean;
  mimeType: string;
}

function isLikelyMediaUrl(url: string | null | undefined): boolean {
  if (!url) return false;
  const lower = url.toLowerCase();
  return (
    lower.includes('xhscdn.com') ||
    lower.includes('ci.xiaohongshu.com') ||
    lower.includes('.jpg') ||
    lower.includes('.jpeg') ||
    lower.includes('.png') ||
    lower.includes('.webp') ||
    lower.includes('.gif') ||
    lower.includes('.mp4') ||
    lower.includes('.mov') ||
    lower.includes('imageview2') ||
    lower.includes('sns-video') ||
    lower.includes('/spectrum/')
  );
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
      const body = await res.body.text();
      return body;
    }
    return null;
  } catch {
    return null;
  }
}

async function resolveShortUrl(shortUrl: string): Promise<string | null> {
  try {
    const res = await request(shortUrl, {
      method: 'GET',
      headers: { 'User-Agent': DEFAULT_UA },
      maxRedirections: 10,
    });
    if (res.statusCode >= 200 && res.statusCode < 300) {
      // undici 跟进 redirect 后,res.url 已经是最终 URL
      const finalUrl = (res as unknown as { url?: string }).url ?? shortUrl;
      // 释放 body
      await res.body.dump();
      return finalUrl;
    }
    return null;
  } catch {
    return null;
  }
}

async function fetchBytes(url: string): Promise<Buffer | null> {
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      const res = await request(url, {
        method: 'GET',
        headers: { 'User-Agent': DEFAULT_UA },
        maxRedirections: 5,
      });
      if (res.statusCode >= 200 && res.statusCode < 300) {
        const buf = Buffer.from(await res.body.arrayBuffer());
        return buf;
      }
      await res.body.dump();
    } catch {
      // 网络错误,继续重试
    }
  }
  return null;
}

function buildDownloadCandidateUrls(mediaUrl: string, originalUrl: string): string[] {
  const set: string[] = [];
  const seen = new Set<string>();
  const add = (u: string | null | undefined) => {
    if (!u) return;
    if (!isLikelyMediaUrl(u)) return;
    if (!seen.has(u)) {
      seen.add(u);
      set.push(u);
    }
  };
  const transformed = transformXhsCdnUrl(mediaUrl);
  if (transformed && transformed !== mediaUrl) add(transformed);
  add(mediaUrl);
  if (originalUrl) {
    add(originalUrl);
    add(transformXhsCdnUrl(originalUrl));
  }
  return set;
}

export class DownloadOrchestrator {
  private fileStorage: FileStorage;
  private livePhotoWriter: LivePhotoWriter;
  private callback?: DownloadCallback;
  private customNamingEnabled: boolean;
  private customFormatTemplate: string;
  private baseUrl: string;

  constructor(opts: DownloadOrchestratorOptions) {
    this.fileStorage = opts.fileStorage;
    this.livePhotoWriter = opts.livePhotoWriter;
    this.callback = opts.callback;
    this.customNamingEnabled = opts.customNamingEnabled ?? false;
    this.customFormatTemplate = opts.customFormatTemplate ?? DEFAULT_TEMPLATE;
    this.baseUrl = (opts.baseUrl ?? '').replace(/\/$/, '');
  }

  private absPathToPublicUrl(absPath: string): string {
    const root = this.fileStorage.rootDir();
    const rel = absPath.startsWith(root) ? absPath.substring(root.length).replace(/\\/g, '/') : absPath;
    const trimmed = rel.replace(/^\/+/, '');
    return this.baseUrl ? `${this.baseUrl}/media/${trimmed}` : `/media/${trimmed}`;
  }

  private buildFileBaseName(fallbackPostId: string, mediaIndex: number, sessionEpoch: number): string {
    const ctx: TemplateContext = {
      fallbackPostId,
      mediaIndex,
      downloadEpochSeconds: sessionEpoch,
    };
    return buildFileBaseName(ctx, this.customNamingEnabled, this.customFormatTemplate);
  }

  private async downloadWithRetries(
    mediaUrl: string,
    fileName: string,
    isVideo: boolean,
  ): Promise<boolean> {
    const candidates = buildDownloadCandidateUrls(mediaUrl, mediaUrl);
    for (const candidate of candidates) {
      for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        const bytes = await fetchBytes(candidate);
        if (bytes) {
          const savedPath = isVideo
            ? await this.fileStorage.saveVideo(bytes, fileName, 'application/octet-stream')
            : await this.fileStorage.saveImage(bytes, fileName, 'image/jpeg');
          if (this.callback) this.callback.onFileDownloaded(savedPath);
          return true;
        }
      }
    }
    if (this.callback) this.callback.onDownloadError(`Failed after retries: ${mediaUrl}`, mediaUrl);
    return false;
  }

  private async downloadMediaBatch(
    postId: string,
    mediaUrls: string[],
    sessionEpoch: number,
  ): Promise<{ results: boolean[]; savedFiles: SavedFile[] }> {
    const results: boolean[] = [];
    const savedFiles: SavedFile[] = [];
    let cursor = 0;

    async function worker(self: DownloadOrchestrator) {
      const my: { ok: boolean; saved?: SavedFile }[] = [];
      while (true) {
        const idx = cursor++;
        if (idx >= mediaUrls.length) break;
        const url = mediaUrls[idx];
        const ext = determineFileExtension(url);
        const name = `${self.buildFileBaseName(postId, idx + 1, sessionEpoch)}.${ext}`;
        const isVideo = isVideoUrl(url);
        const ok = await self.downloadWithRetries(url, name, isVideo);
        my.push({ ok });
        if (ok) {
          const absPath = isVideo
            ? path.join(self.fileStorage.videosDir(), name)
            : path.join(self.fileStorage.picturesDir(), name);
          savedFiles.push({
            absPath,
            publicUrl: self.absPathToPublicUrl(absPath),
            fileName: name,
            isVideo,
            isLivePhoto: false,
            mimeType: isVideo ? 'video/mp4' : 'image/jpeg',
          });
        }
      }
      return my;
    }

    const workers = Array.from({ length: Math.min(CONCURRENCY, mediaUrls.length) }, () =>
      worker(this),
    );
    const settled = await Promise.all(workers);
    for (const arr of settled) for (const r of arr) results.push(r.ok);
    return { results, savedFiles };
  }

  private async createLivePhotos(
    postId: string,
    pairs: MediaPair[],
    sessionEpoch: number,
  ): Promise<SavedFile[]> {
    const savedFiles: SavedFile[] = [];
    const cacheDir = path.join(os.tmpdir(), `xhsdn-cache-${postId}-${Date.now()}`);
    await fs.mkdir(cacheDir, { recursive: true });

    for (let idx = 0; idx < pairs.length; idx++) {
      const pair = pairs[idx];
      const imgUrl = pair.imageUrl;
      const vidUrl = pair.videoUrl;
      if (!imgUrl || !vidUrl) continue;

      const baseName = this.buildFileBaseName(postId, idx + 1, sessionEpoch);
      const imgExt = determineFileExtension(imgUrl);
      const vidExt = determineFileExtension(vidUrl);
      const imgName = `${baseName}_img.${imgExt}`;
      const vidName = `${baseName}_vid.${vidExt}`;

      const imgBuf = await fetchBytes(imgUrl);
      const vidBuf = await fetchBytes(vidUrl);
      if (!imgBuf || !vidBuf) continue;

      const imgCache = path.join(cacheDir, imgName);
      const vidCache = path.join(cacheDir, vidName);
      await fs.writeFile(imgCache, imgBuf);
      await fs.writeFile(vidCache, vidBuf);

      const outName = `${baseName}_live.jpg`;
      const outputPath = path.join(this.fileStorage.picturesDir(), outName);

      const created = await this.livePhotoWriter.create(imgCache, vidCache, outputPath);
      if (created) {
        if (this.callback) this.callback.onFileDownloaded(outputPath);
        savedFiles.push({
          absPath: outputPath,
          publicUrl: this.absPathToPublicUrl(outputPath),
          fileName: outName,
          isVideo: false,
          isLivePhoto: true,
          mimeType: 'image/jpeg',
        });
      } else {
        // fallback:分别保存为 jpg + mp4
        const imgSaved = await this.fileStorage.saveImage(imgBuf, imgName, 'image/jpeg');
        const vidSaved = await this.fileStorage.saveVideo(vidBuf, vidName, 'video/mp4');
        if (this.callback) {
          this.callback.onFileDownloaded(imgSaved);
          this.callback.onFileDownloaded(vidSaved);
        }
        savedFiles.push({
          absPath: imgSaved,
          publicUrl: this.absPathToPublicUrl(imgSaved),
          fileName: imgName,
          isVideo: false,
          isLivePhoto: false,
          mimeType: 'image/jpeg',
        });
        savedFiles.push({
          absPath: vidSaved,
          publicUrl: this.absPathToPublicUrl(vidSaved),
          fileName: vidName,
          isVideo: true,
          isLivePhoto: false,
          mimeType: 'video/mp4',
        });
      }

      await fs.unlink(imgCache).catch(() => {});
      await fs.unlink(vidCache).catch(() => {});
    }

    await fs.rm(cacheDir, { recursive: true, force: true }).catch(() => {});
    return savedFiles;
  }

  async downloadContent(inputUrl: string): Promise<DownloadResult> {
    const allSaved: SavedFile[] = [];
    let successful = 0;
    let hasErrors = false;
    try {
      const urls = extractLinks(inputUrl);
      if (urls.length === 0) return { ok: false, savedFiles: [] };

      // 短链 follow redirect
      const resolved: string[] = [];
      for (const u of urls) {
        if (u.includes('xhslink.com')) {
          const r = await resolveShortUrl(u);
          resolved.push(r ?? u);
        } else {
          resolved.push(u);
        }
      }

      const sessionEpoch = Math.floor(Date.now() / 1000);

      for (const url of resolved) {
        const postId = extractPostId(url);
        if (!postId) continue;

        const html = await fetchText(url);
        if (!html) {
          if (this.callback) this.callback.onDownloadError(`Failed to fetch post details: ${url}`, url);
          hasErrors = true;
          continue;
        }

        const parseResult = parseNoteDetails(html);
        const mediaUrls = parseResult.mediaUrls;
        if (mediaUrls.length === 0) {
          if (this.callback) this.callback.onDownloadError(`No media URLs found in post: ${postId}`, url);
          hasErrors = true;
          continue;
        }

        // Live Photo 配对处理
        const hasLivePhoto = parseResult.livePhotoPairs.length > 0;
        const canCreateLive = parseResult.livePhotoPairs.some((p) => p.imageUrl && p.videoUrl);
        if (hasLivePhoto && canCreateLive) {
          const saved = await this.createLivePhotos(postId, parseResult.livePhotoPairs, sessionEpoch);
          if (saved.length === 0) {
            hasErrors = true;
          } else {
            successful++;
            allSaved.push(...saved);
          }
          continue;
        }

        // 普通下载:4 并发
        const { results, savedFiles } = await this.downloadMediaBatch(postId, mediaUrls, sessionEpoch);
        if (results.some((r) => r)) successful++;
        if (results.some((r) => !r)) hasErrors = true;
        allSaved.push(...savedFiles);
      }

      return { ok: successful > 0, savedFiles: allSaved };
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (this.callback) this.callback.onDownloadError(`Exception: ${msg}`, inputUrl);
      return { ok: false, savedFiles: allSaved };
    }
  }
}
