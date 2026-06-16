package com.xhsdn.desktop.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xhsdn.core.url.UrlUtils
import com.xhsdn.desktop.clipboard.ClipboardWatcher
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
    val watcher = remember { ClipboardWatcher() }

    LaunchedEffect(Unit) {
        watcher.start()
        watcher.events.collectLatest { text ->
            val firstUrl = UrlUtils.extractFirstUrl(text)
            if (firstUrl != null && UrlUtils.isXhsLink(firstUrl)) {
                detectedLink = text
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

        // 大型下载按钮（迭代计划文档里的设计）
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDownloading)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primary,
            ),
            onClick = { viewModel.startDownload() },
            enabled = !isDownloading,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isDownloading) "下载中…" else "点击下载（自动读取输入框）",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        OutlinedTextField(
            value = urlInput,
            onValueChange = { viewModel.setUrl(it) },
            label = { Text("或手动输入链接") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
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
