# XHS Downloader 桌面端 KMP 化迁移记录

> 本文档归档 2026-06 完成的"Kotlin Multiplatform + Compose Desktop"改造方案。完整计划见 [hashed-mapping-feather.md](https://example.local/hashed-mapping-feather.md) 计划文件。

## 背景

项目原本只有 Android 端。需求：在同一仓库内加 Windows 桌面 GUI 版（Kotlin Multiplatform + Compose Desktop），最终产物可双击运行 `.exe`，且与 Android 共享同一份核心业务（HTTP 抓取、JSON 解析、URL 转换、命名模板、媒体配对）。

## 方案要点

### 1. 模块拆分
- `:app` — Android UI（Miuix 不动）
- `:core` — KMP 共享模块（业务核心）
  - `commonMain`：纯 Kotlin/Java 业务
  - `androidMain`：MediaStore 写入、SharedPreferences、LivePhotoCreator 桥接
  - `desktopMain`：Pictures/Videos 目录、Preferences（注册表）、AWT 剪贴板
- `:desktop` — Compose Desktop GUI（Material3 替代 Miuix）

### 2. 跨平台抽象（`expect/actual`）
`core/commonMain/.../platform/` 下 5 个接口：
- `FileStorage` — 平台存储抽象
- `LivePhotoWriter` — Live Photo 合成
- `KeyValueStore` — 小 KV
- `AppNotifier` — 通知
- `ClipboardAccess` — 剪贴板

`PlatformContext` 单例在启动时由入口模块注入 actual 实现，业务代码通过 `PlatformContext.current.xxx` 取用。

### 3. 业务迁移映射

| 原 Android 文件 | 迁移到 core | 备注 |
| --- | --- | --- |
| `XHSDownloader.java` HTTP 抓取 | `core/commonMain/.../http/HttpFetcher.kt` | 纯 OkHttp |
| `XHSDownloader.java` URL 提取/转换 | `core/commonMain/.../url/*` | 纯函数 |
| `XHSDownloader.java` JSON 解析 | `core/commonMain/.../parse/*` | `kotlinx.serialization.json` 替代 `org.json` |
| `XHSDownloader.java` 命名模板 | `core/commonMain/.../naming/TemplateApplier.kt` | 纯函数 |
| `XHSDownloader.java` 下载主循环 | `core/commonMain/.../download/DownloadOrchestrator.kt` | 用 Kotlin 协程 `ensureActive()` 替代 Java `Thread.interrupt()` |
| `FileDownloader.java` MediaStore 写入 | `core/androidMain/.../FileStorageAndroid.kt` | 实际类 |
| `FileDownloader.java` 共享 OkHttp 配置 | `core/commonMain/.../http/SharedHttpClient.kt` | Dispatcher / 超时 / HTTP/2 |
| `LivePhotoCreator.java` | `core/androidMain/.../LivePhotoWriterAndroid.kt` | 委托给原 Java |
| `DownloadTask.kt` 数据类 | `core/commonMain/.../model/DownloadTask.kt` | 同字段、同名 |
| `TaskManager.kt` 任务管理 | `core/commonMain/.../task/TaskManager.kt` + `TaskPersistence.kt` | 改用 JSON 文件 |

### 4. Desktop 端实现

| 抽象 | Desktop actual | 备注 |
| --- | --- | --- |
| `FileStorage` | `DesktopFileStorage` | 写 `%USERPROFILE%\Pictures\xhsdn` 与 `...\Videos\xhsdn` |
| `LivePhotoWriter` | `DesktopLivePhotoWriter` | 永远返回 false，触发 fallback 分别保存 |
| `KeyValueStore` | `DesktopKeyValueStore` | `Preferences.userRoot()`（Windows 注册表） |
| `AppNotifier` | `DesktopAppNotifier` | println + AWT `TrayIcon` 托盘 |
| `ClipboardAccess` | `DesktopClipboardAccess` | `java.awt.Toolkit.systemClipboard` |

任务历史不走 `Preferences`（单 key ~8KB 上限），改用 `TaskPersistence` 写 `%USERPROFILE%\.xhsdn\history.json`（`kotlinx.serialization`）。

### 5. UI 替换（Miuix → Material3）
Miuix 仅有 `-android` artifact，Desktop 端用 Material3。**仅 `:desktop` 替换，`:app` 的 Miuix 保持不动**。

| 原 Miuix | Material3 |
| --- | --- |
| `Scaffold` | `androidx.compose.material3.Scaffold` |
| `TopAppBar` | `TopAppBar` |
| `Button` / `Card` / `TextField` | 同名 Material3 |
| `WindowBottomSheet` | `ModalBottomSheet` |
| `WindowDialog` | `AlertDialog` |
| `LinearProgressIndicator` | 同名 Material3 |
| `MiuixIcons` | `androidx.compose.material.icons.Icons.Default.*` |
| `MiuixTheme` | 自定义 `MaterialTheme` colorScheme（XHS 红 `#FF2442`） |

### 6. 剪贴板监听
`ClipboardWatcher` 用 800ms 轮询 `PlatformContext.current.clipboardAccess.getText()`。MVP 简单可靠，未来可换 JNA + Win32 `AddClipboardFormatListener`。

### 7. 打包 .exe
`desktop/build.gradle.kts` 用 Compose Desktop `application` plugin + `nativeDistributions`：

```kotlin
compose.desktop {
    application {
        mainClass = "com.xhsdn.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "xhsdn"
            packageVersion = "1.0.0"
            modules("jdk.crypto.ec", "jdk.httpserver")
        }
    }
}
```

构建命令：
```bash
./gradlew :desktop:run                # 开发模式
./gradlew :desktop:createDistributable # 产物 .exe / .msi
```

输出位置：
- `desktop/build/compose/binaries/main/exe/XHS Downloader.exe`
- `desktop/build/compose/binaries/main/msi/XHS Downloader-1.0.0.msi`

## 验证清单

| 步骤 | 命令 | 期望 |
| --- | --- | --- |
| 三模块可识别 | `./gradlew projects` | 列出 `:app :core :desktop` |
| 单元测试 | `./gradlew :core:jvmTest` | 全绿 |
| 桌面运行 | `./gradlew :desktop:run` | 弹出窗口 |
| 端到端 | 粘贴 → 解析 → 进度 → 完成 → 历史 | 整链路通 |
| 打包 | `./gradlew :desktop:createDistributable` | .exe 生成 |

## 风险与边界

1. **Live Photo 合成**仅 Android 端支持（依赖 MIUI/ColorOS 私有 XMP）。Windows 端会分别保存为 .jpg + .mp4
2. **WebView 登录辅助** Windows 端未迁移，首批隐藏入口
3. **代码签名** 未配置，SmartScreen 会弹"未知发布者"
4. **打包体积** jpackage 输出 80-120 MB（含 JRE）

## 后续可做

- 迁移 `WebViewActivity` 到 Desktop（用 JCEF / WebView2）
- 替换剪贴板轮询为 Win32 `AddClipboardFormatListener` via JNA
- 任务详情页（参考 Android `DetailActivity`）
- 选择性下载（参考 Android `SelectiveDownloadSheet`）
- macOS / Linux 打包（`TargetFormat.Dmg` / `Deb` 已声明，未测试）
- 代码签名 + 自动更新
