/**
 * 下载回调。Web 版默认是 no-op + console.log。
 * 后续要接 SSE / WebSocket 给前端实时进度时,改这里的实现即可。
 */
export interface DownloadCallback {
  onFileDownloaded(filePath: string): void;
  onDownloadError(status: string, originalUrl: string): void;
  onDownloadProgress(status: string): void;
  onDownloadProgressUpdate(downloaded: number, total: number): void;
  onVideoDetected(): void;
  isCancelled(): boolean;
}

export class ConsoleDownloadCallback implements DownloadCallback {
  onFileDownloaded(filePath: string): void {
    console.log(`[file] ${filePath}`);
  }
  onDownloadError(status: string, originalUrl: string): void {
    console.log(`[err] ${status} (${originalUrl})`);
  }
  onDownloadProgress(status: string): void {
    console.log(`[progress] ${status}`);
  }
  onDownloadProgressUpdate(downloaded: number, total: number): void {
    // no-op
  }
  onVideoDetected(): void {
    console.log('[video] 检测到视频');
  }
  isCancelled(): boolean {
    return false;
  }
}
