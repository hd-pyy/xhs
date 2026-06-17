/**
 * Node 版 FileStorage。复刻 Desktop 版 `core/src/desktopMain/kotlin/com/xhsdn/core/platform/FileStorageDesktop.kt` 的语义:
 * - 默认写文件到 `<root>/storage/pictures/xhsdn/` 与 `<root>/storage/videos/xhsdn/`
 * - 同名文件自动加 `(1)`, `(2)` 后缀
 * - 返回绝对路径,前端再用 baseUrl 转成 HTTP URL
 */

import * as path from 'path';
import * as fs from 'fs/promises';

export interface FileStorage {
  rootDir(): string;
  picturesDir(): string;
  videosDir(): string;
  saveImage(bytes: Buffer, fileName: string, mimeType: string): Promise<string>;
  saveVideo(bytes: Buffer, fileName: string, mimeType: string): Promise<string>;
}

export class WebFileStorage implements FileStorage {
  private root: string;
  private picturesBaseDir: string;
  private videosBaseDir: string;

  constructor(rootDir: string) {
    this.root = path.resolve(rootDir);
    this.picturesBaseDir = path.join(this.root, 'storage', 'pictures');
    this.videosBaseDir = path.join(this.root, 'storage', 'videos');
  }

  rootDir(): string {
    return this.root;
  }

  /** 每次调用都重新解析,与 Kotlin 版本语义一致。 */
  private resolvePicturesDir(): string {
    return path.join(this.picturesBaseDir, 'xhsdn');
  }

  private resolveVideosDir(): string {
    return path.join(this.videosBaseDir, 'xhsdn');
  }

  picturesDir(): string {
    return this.resolvePicturesDir();
  }

  videosDir(): string {
    return this.resolveVideosDir();
  }

  private async ensureDir(dir: string): Promise<void> {
    await fs.mkdir(dir, { recursive: true });
  }

  private uniqueFile(dir: string, name: string): string {
    // 同步检查 → 在保存前计算最终路径,避免覆盖
    return path.join(dir, name);
  }

  async saveImage(bytes: Buffer, fileName: string, _mimeType: string): Promise<string> {
    const dir = this.resolvePicturesDir();
    await this.ensureDir(dir);
    const target = await this.uniqueFileAsync(dir, fileName);
    await fs.writeFile(target, bytes);
    return target;
  }

  async saveVideo(bytes: Buffer, fileName: string, _mimeType: string): Promise<string> {
    const dir = this.resolveVideosDir();
    await this.ensureDir(dir);
    const target = await this.uniqueFileAsync(dir, fileName);
    await fs.writeFile(target, bytes);
    return target;
  }

  /** 同名文件自动加 `(1)`, `(2)` 后缀。1:1 翻译自 DesktopFileStorage.uniqueFile。 */
  private async uniqueFileAsync(dir: string, name: string): Promise<string> {
    const target = path.join(dir, name);
    try {
      await fs.access(target);
    } catch {
      return target;
    }
    const dotIdx = name.lastIndexOf('.');
    const base = dotIdx > 0 ? name.substring(0, dotIdx) : name;
    const ext = dotIdx > 0 ? '.' + name.substring(dotIdx + 1) : '';
    let i = 1;
    while (true) {
      const candidate = path.join(dir, `${base}_(${i})${ext}`);
      try {
        await fs.access(candidate);
      } catch {
        return candidate;
      }
      i++;
    }
  }
}
