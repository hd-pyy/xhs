# 小红书下载器 (XHS Downloader)

三端同源,共享 `:core` 解析/下载算法:

- **Android 端** —— 原项目所有功能（Miuix UI + MediaStore）
- **Windows 桌面端** —— Kotlin Multiplatform + Jetpack Compose Desktop
- **Web 版** —— Node + Express + undici (TypeScript), 1:1 翻译自 `:core/commonMain`

iOS 版本仓库➡️ [点我](https://github.com/NEORUAA/XHS_Downloader_iOS)

## 主要功能

- **图片下载**: 自动提取小红书笔记中的图片并获取其原始文件 cdn 地址
- **视频下载**: 自动提取小红书笔记中的视频（至多 1080p）
- **文案提取**: 自动提取小红书笔记中的文案并复制至剪切板
- **Live Photo 合成**: 智能识别并合成动态照片（仅 Android 端支持 MIUI/OPPO/Google 动态照片格式；Windows / Web 端会分别保存为 .jpg + .mp4）
- **网页版提取**: Android 端支持内置 WebView 爬取（Windows / Web 端首批不包含）
- **剪贴板监听**: Android 端自动识别复制的小红书链接；Web 端在输入框粘贴即可

## 仓库结构

```text
XHS_Downloader_Android/
├── app/                    # Android UI 模块（Miuix，原项目保留）
├── core/                   # ★ KMP 共享模块
│   └── src/
│       ├── commonMain/     # 跨平台业务（HTTP 抓取 / JSON 解析 / URL 转换 / 命名模板 / 下载编排）
│       ├── androidMain/    # Android 平台 actual（FileStorage/LivePhotoWriter/...）
│       └── desktopMain/    # Windows 平台 actual
├── desktop/                # ★ Compose Desktop GUI 模块
│   └── src/jvmMain/        # Material3 UI + 托盘通知
└── web/                    # ★ Web 版（Node + Express + TypeScript）
    ├── src/
    │   ├── core/           # 1:1 翻译自 :core/commonMain
    │   ├── platform/       # FileStorage / LivePhotoWriter / DownloadCallback
    │   ├── routes/         # /api/parse, /api/download, /api/fetch
    │   └── server.ts
    └── public/             # 单页前端
```

## 桌面端使用

### 前置条件

- 本仓库的 Gradle 配置假设系统里有 **JDK 17 或 21**（Kotlin 2.3 + Compose 1.7.3 工具链）。
  Windows 上最方便的做法是装 [Android Studio](https://developer.android.com/studio)，它自带
  `C:\Program Files (x86)\Android\openjdk\jdk-17.0.8.101-hotspot`。
  本仓库 `gradle.properties` 已指向这个路径，`foojay-resolver-convention` 也会在缺失时自动从网络下载。

**⚠️ 不要直接跑 `./gradlew.bat`！** 你的系统 `JAVA_HOME` 指向 JDK 11，
Gradle 9 起不来。**用仓库根目录的 `run-desktop.ps1`**：

### 开发模式（启动 GUI）

```powershell
.\run-desktop.ps1 run
```

### 打包 .exe

```powershell
.\run-desktop.ps1 dist
```

产物路径：

```text
desktop\build\compose\binaries\main\app\xhsdn\xhsdn.exe
```

### 其它任务

```powershell
.\run-desktop.ps1 test          # 跑 commonMain 单元测试
.\run-desktop.ps1 core-desktop  # 只编译 :core 的 desktop target（验证代码最快）
```

### `run-desktop.ps1` 做了什么

脚本每次执行时先：
1. 强制设 `JAVA_HOME = C:\Program Files (x86)\Android\openjdk\jdk-17.0.8.101-hotspot`
2. 把 JDK 17 放到 `PATH` 最前面
3. `cd` 到仓库根目录
4. 再调 `gradlew.bat --no-daemon`

这样不管系统默认 `JAVA_HOME` 是什么，Gradle daemon 都能起来。

**Compose Desktop 1.7.3 打包 bug 说明**：内置 `createDistributable` 会清空
`build/compose/tmp/createDistributable/libs/` 但 jpackage 又从同一目录读 jar，
导致 `Input length = 1` 失败。`desktop/build.gradle.kts` 末尾的 `xhsPackageExe`
Exec task 已接管这步（直接调 `jpackage.exe`），你只需跑上面脚本即可。

### 桌面端默认路径
- **图片**：`%USERPROFILE%\Pictures\xhsdn\`
- **视频**：`%USERPROFILE%\Videos\xhsdn\`
- **任务历史 JSON**：`%USERPROFILE%\.xhsdn\history.json`
- **设置项**：`HKCU\Software\JavaSoft\Prefs\com\xhsdn\desktop`（Windows 注册表）

### 桌面端目前做不到的

1. **Live Photo 合成**：仅 Android 支持。Windows 端把图与视频分别保存为 .jpg + .mp4
2. **WebView 登录辅助**：暂未迁移
3. **代码签名**：未签名，Windows SmartScreen 会弹"未知发布者"
4. **macOS / Linux**：本仓库配置支持，但未经测试

## Web 版使用

需要 Node 18+。

```bash
cd web
npm install
npm run build     # tsc → dist/
npm start         # 启 Express 在 :3000
# 开发热重载:
npm run dev
```

打开 <http://localhost:3000> 即可在浏览器粘贴小红书分享链接、下载图片/视频。

### Web API

| 路由 | 说明 |
| --- | --- |
| `POST /api/parse` | body `{ text }` → `{ results: [{ postId, originalUrl, mediaUrls, livePhotoPairs, error? }] }` |
| `POST /api/download` | body `{ text }` → `{ ok, savedFiles, picturesDir, videosDir }` |
| `POST /api/fetch` | 代理转发 XHS 请求（绕开浏览器 CORS） |
| `GET /api/history` | 返回 `storage/` 已下载文件列表 |
| `GET /api/health` | `{ ok, version, ts }` |
| `GET /media/pictures/xhsdn/<file>` | 已下载图片静态托管 |
| `GET /media/videos/xhsdn/<file>` | 已下载视频静态托管 |

### Web 版落盘

```text
web/
├── storage/
│   ├── pictures/xhsdn/    # 图片、Live Photo fallback 出来的 jpg
│   └── videos/xhsdn/      # 视频
└── public/                # 前端单页
```

`web/storage/` 已在 `.gitignore` 内，不会入库。

### Web 版做不到的

1. **Live Photo 合成**：返回 false，自动 fallback 拆 jpg + mp4
2. **登录 cookie 注入**：当前只能抓公开笔记
3. **持久化任务历史**：仅靠 `storage/` 文件枚举，无 SQLite

> ⚠️ `web/src/core` 是手工翻译，**不要尝试** `require()` Kotlin `:core` —— 它依赖 OkHttp + `java.io.File`，
> 目前只配置了 `android` + `jvm("desktop")` 两个 KMP target，没有 js/native target。

## Android 端使用

照原 README：

1. **复制链接**：在小红书 App 中复制笔记分享链接
2. **粘贴链接**：在本应用中粘贴到输入框
3. **开始下载**：点击下载按钮，等待处理完成
4. **查看内容**：下载完成后可在系统相册中查看

## 支持的链接类型

- `https://www.xiaohongshu.com/explore/...`
- `https://xhslink.com/...`
- `https://www.xiaohongshu.com/discovery/item/...`

## 使用须知

- 请仅下载自己需要的内容，遵守相关法律法规
- 请尊重笔记原作者的版权，合理使用下载内容
- 本应用仅供个人学习和研究使用，下载后请于 24H 内删除

## 常见问题

1. **下载失败**：请确认填入的分享链接在浏览器中能被查看
2. **Live Photo 无效**：
   - Android：合成失败会自动 fallback 成普通视频来下载
   - Windows / Web：直接保存为图+视频两个文件
3. **找不到文件**：
   - Android：保存在 `Pictures/xhsdn` 与 `Movies/xhsdn`
   - Windows：保存在 `%USERPROFILE%\Pictures\xhsdn` 与 `%USERPROFILE%\Videos\xhsdn`
   - Web：保存在 `web/storage/pictures/xhsdn` 与 `web/storage/videos/xhsdn`

## 桌面端构建要求

- **JDK 11+**（建议 17 或 21 LTS）
- **Gradle 9.x**（项目自带 wrapper）
- **Windows 10+**（jpackage 需要 WiX 3.x 来构建 .msi；.exe 不需要）

`gradle.properties` 已开启 `org.gradle.java.installations.auto-detect=true`，配合项目根 `settings.gradle` 里的 foojay resolver，无需手装 JDK 也能跑 Gradle toolchain。

## 版本与发版

| 模块 | 当前版本 | 备注 |
| --- | --- | --- |
| Android | 沿原 app `versionName` | 见 `app/build.gradle` |
| Desktop | `1.0.0` | 见 `desktop/build.gradle.kts` 的 `packageVersion` |
| Web | `0.1.0-web` | 见 `web/package.json` |

三端独立发版，Web 版 tag 命名 `v*.*.*-web`。

## 开源许可

项目使用 AGPL-3.0 许可协议，要求分发和修改的同时也公开源码，且使用相同的许可协议。

## 反馈与支持

如遇到问题或有功能建议，欢迎提交 issues 或 PR。
