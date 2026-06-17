/**
 * Node 版 LivePhotoWriter。
 *
 * Web 版目前不依赖 ffmpeg,所以 `create()` 永远返回 false,
 * 让 DownloadOrchestrator 走 fallback:分别保存为 jpg + mp4。
 * 后续可注入一个真正合成 mov 的实现。
 */
export interface LivePhotoWriter {
  create(imagePath: string, videoPath: string, outputPath: string): Promise<boolean>;
}

export class WebLivePhotoWriter implements LivePhotoWriter {
  async create(_imagePath: string, _videoPath: string, _outputPath: string): Promise<boolean> {
    return false;
  }
}
