package com.xhsdn.desktop.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xhsdn.core.url.UrlUtils
import com.xhsdn.desktop.clipboard.ClipboardWatcher
import com.xhsdn.desktop.ui.DirectoryPicker
import com.xhsdn.desktop.viewmodel.rememberDesktopTaskViewModel
import kotlinx.coroutines.flow.collectLatest

/**
 * Home 页：粘贴链接 → 触发下载。Material3 替代 Miuix。
 *
 * 监听剪贴板变化，如果发现 XHS 链接就自动填入（可设置中关闭）。
 */
@Composable
fun HomeScreen() {
    val viewModel = rememberDesktopTaskViewModel()
    val urlInput by viewModel.urlInput.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    var detectedLink by remember { mutableStateOf<String?>(null) }
    // 当前保存目录（首页 + 设置页共用同一份）。用户在这里改 → 即时影响下一次下载。
    var savePath by remember {
        mutableStateOf(
            com.xhsdn.core.platform.PlatformContext.current.fileStorage.picturesDir()
        )
    }
    val watcher = remember { ClipboardWatcher() }

    LaunchedEffect(Unit) {
        watcher.start()
        watcher.events.collectLatest { text ->
            // 直接从分享文案里抽干净的 URL（兼容"✨xxx http://xhslink.com/..."这类前后带中文/换行的格式）。
            // 显示给用户和传给下载器的都应当是干净的 URL，下游不再处理混合文案。
            val firstUrl = UrlUtils.extractFirstUrl(text)
            if (firstUrl != null && UrlUtils.isXhsLink(firstUrl)) {
                detectedLink = firstUrl
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "粘贴小红书链接",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "支持 https://www.xiaohongshu.com/explore/... 与 https://xhslink.com/... 等格式",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 链接输入框 + 右侧的解析/下载按钮（替代之前的大尺寸整行按钮）。
        // 输入框内默认提示「请粘贴小红书分享文案或链接」，与用户从 App 复制分享文案的习惯一致。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = urlInput,
                onValueChange = { viewModel.setUrl(it) },
                placeholder = { Text("请粘贴小红书分享文案或链接…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isDownloading,
            )
            FilledIconButton(
                onClick = { viewModel.startDownload() },
                enabled = !isDownloading && urlInput.isNotBlank(),
                modifier = Modifier.height(56.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.Download, contentDescription = "解析并下载")
                }
            }
        }

        // 保存路径快捷入口。让用户不用切到设置页就能改目录。
        SavePathRow(
            currentPath = savePath,
            onChoose = {
                val chosen = DirectoryPicker.pickDirectory(savePath)
                if (chosen != null) {
                    // 图片和视频共用同一父目录，符合"快速下载到指定文件夹"的心智模型
                    com.xhsdn.core.platform.PlatformContext.current.keyValueStore
                        .putString(PREF_PICTURES_DIR, chosen.absolutePath)
                    com.xhsdn.core.platform.PlatformContext.current.keyValueStore
                        .putString(PREF_VIDEOS_DIR, chosen.absolutePath)
                    com.xhsdn.core.platform.PlatformContext.current.fileStorage
                        .setBaseDirs(chosen.absolutePath, chosen.absolutePath)
                    savePath = com.xhsdn.core.platform.PlatformContext.current.fileStorage.picturesDir()
                }
            },
            onReset = {
                com.xhsdn.core.platform.PlatformContext.current.keyValueStore.remove(PREF_PICTURES_DIR)
                com.xhsdn.core.platform.PlatformContext.current.keyValueStore.remove(PREF_VIDEOS_DIR)
                com.xhsdn.core.platform.PlatformContext.current.fileStorage
                    .setBaseDirs(picturesRoot = null, videosRoot = null)
                savePath = com.xhsdn.core.platform.PlatformContext.current.fileStorage.picturesDir()
            },
        )

        if (detectedLink != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("检测到剪贴板内 XHS 链接：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(detectedLink.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        viewModel.setUrl(detectedLink.orEmpty())
                        detectedLink = null
                    }) { Text("填入并下载") }
                }
            }
        }
    }
}

@Composable
private fun SavePathRow(
    currentPath: String,
    onChoose: () -> Unit,
    onReset: () -> Unit,
) {
    Column {
        Text("保存到", style = MaterialTheme.typography.bodyMedium)
        Text(
            currentPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onChoose) { Text("更改…") }
            OutlinedButton(onClick = onReset) { Text("恢复默认") }
        }
    }
}

internal const val PREF_PICTURES_DIR = "save.pictures.dir"
internal const val PREF_VIDEOS_DIR = "save.videos.dir"
