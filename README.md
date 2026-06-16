# 小红书下载器 (XHS Downloader)

支持 Android 与 Windows 桌面两端：
- **Android 端**：原项目所有功能（Miuix UI + MediaStore）
- **Windows 桌面端**：新增，由 Kotlin Multiplatform + Jetpack Compose Desktop 实现

iOS 版本仓库➡️ [点我](https://github.com/NEORUAA/XHS_Downloader_iOS)

## 主要功能

- **图片下载**: 自动提取小红书笔记中的图片并获取其原始文件 cdn 地址
- **视频下载**: 自动提取小红书笔记中的视频（至多 1080p）
- **文案提取**: 自动提取小红书笔记中的文案并复制至剪切板
- **Live Photo 合成**: 智能识别并合成动态照片（仅 Android 端支持 MIUI/OPPO/Google 动态照片格式；Windows 端会分别保存为 .jpg + .mp4）
- **网页版提取**: Android 端支持内置 WebView 爬取（Windows 端首批不包含）
- **剪贴板监听**: 自动识别复制的小红书链接

## 仓库结构

```
XHS_Downloader_Android/
├── app/                    # Android UI 模块（Miuix，原项目保留）
├── core/                   # ★ 新增 KMP 共享模块
│   └── src/
│       ├── commonMain/     # 跨平台业务（HTTP 抓取 / JSON 解析 / URL 转换 / 命名模板 / 下载编排）
│       ├── androidMain/    # Android 平台 actual（FileStorage/LivePhotoWriter/...）
│       └── desktopMain/    # Windows 平台 actual
└── desktop/                # ★ 新增 Compose Desktop GUI 模块
    └── src/jvmMain/        # Material3 UI + 托盘通知
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

```
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
   - Windows：直接保存为图+视频两个文件
3. **找不到文件**：
   - Android：保存在 `Pictures/xhsdn` 与 `Movies/xhsdn`
   - Windows：保存在 `%USERPROFILE%\Pictures\xhsdn` 与 `%USERPROFILE%\Videos\xhsdn`

## 桌面端构建要求

- **JDK 11+**（建议 17 或 21 LTS）
- **Gradle 9.x**（项目自带 wrapper）
- **Windows 10+**（jpackage 需要 WiX 3.x 来构建 .msi；.exe 不需要）

`gradle.properties` 已开启 `org.gradle.java.installations.auto-detect=true`，配合项目根 `settings.gradle` 里的 foojay resolver，无需手装 JDK 也能跑 Gradle toolchain。

## 开源许可

项目使用 AGPL-3.0 许可协议，要求分发和修改的同时也公开源码，且使用相同的许可协议。

## 反馈与支持

如遇到问题或有功能建议，欢迎提交 issues 或 PR。
