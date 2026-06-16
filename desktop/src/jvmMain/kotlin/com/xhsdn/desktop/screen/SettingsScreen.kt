package com.xhsdn.desktop.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xhsdn.core.platform.PlatformContext
import com.xhsdn.desktop.viewmodel.rememberDesktopTaskViewModel

/**
 * 设置页。MVP 展示 KV 存储里的偏好项和图片/视频存储路径。
 */
@Composable
fun SettingsScreen() {
    val viewModel = rememberDesktopTaskViewModel()
    val tasks by viewModel.tasks.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        SettingRow("图片保存目录", PlatformContext.current.fileStorage.picturesDir())
        SettingRow("视频保存目录", PlatformContext.current.fileStorage.videosDir())
        SettingRow("任务历史条数", tasks.size.toString())

        Spacer(Modifier.height(16.dp))
        Text(
            "注：Desktop 端暂不支持动态照片（Live Photo）合成；将分别保存为 .jpg + .mp4。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
}
