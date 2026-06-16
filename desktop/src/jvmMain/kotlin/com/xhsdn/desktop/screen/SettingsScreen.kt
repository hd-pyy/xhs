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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xhsdn.core.platform.PlatformContext
import com.xhsdn.desktop.ui.DirectoryPicker
import com.xhsdn.desktop.viewmodel.rememberDesktopTaskViewModel

/**
 * 设置页。MVP 展示 KV 存储里的偏好项和图片/视频存储路径。
 * 用户可以为图片/视频分别选择自定义保存目录，路径持久化到注册表（重启后保留）。
 */
@Composable
fun SettingsScreen() {
    val viewModel = rememberDesktopTaskViewModel()
    val tasks by viewModel.tasks.collectAsState()

    // 用 state 让 UI 在用户选择目录后即时刷新
    var picturesDir by remember { mutableStateOf(PlatformContext.current.fileStorage.picturesDir()) }
    var videosDir by remember { mutableStateOf(PlatformContext.current.fileStorage.videosDir()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))

        DirectoryRow(
            label = "图片保存目录",
            currentPath = picturesDir,
            onChoose = {
                val chosen = pickDirectory(picturesDir)
                if (chosen != null) {
                    PlatformContext.current.keyValueStore.putString(PREF_PICTURES_DIR, chosen.absolutePath)
                    PlatformContext.current.fileStorage.setBaseDirs(picturesRoot = chosen.absolutePath, videosRoot = null)
                    picturesDir = PlatformContext.current.fileStorage.picturesDir()
                }
            },
            onReset = {
                PlatformContext.current.keyValueStore.remove(PREF_PICTURES_DIR)
                PlatformContext.current.fileStorage.setBaseDirs(picturesRoot = null, videosRoot = null)
                picturesDir = PlatformContext.current.fileStorage.picturesDir()
            },
        )

        DirectoryRow(
            label = "视频保存目录",
            currentPath = videosDir,
            onChoose = {
                val chosen = pickDirectory(videosDir)
                if (chosen != null) {
                    PlatformContext.current.keyValueStore.putString(PREF_VIDEOS_DIR, chosen.absolutePath)
                    PlatformContext.current.fileStorage.setBaseDirs(picturesRoot = null, videosRoot = chosen.absolutePath)
                    videosDir = PlatformContext.current.fileStorage.videosDir()
                }
            },
            onReset = {
                PlatformContext.current.keyValueStore.remove(PREF_VIDEOS_DIR)
                PlatformContext.current.fileStorage.setBaseDirs(picturesRoot = null, videosRoot = null)
                videosDir = PlatformContext.current.fileStorage.videosDir()
            },
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "任务历史条数：${tasks.size}",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "注：已下载的文件不会自动迁移；修改保存目录后，新下载的文件会保存到新位置。\nDesktop 端暂不支持动态照片（Live Photo）合成；将分别保存为 .jpg + .mp4。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DirectoryRow(
    label: String,
    currentPath: String,
    onChoose: () -> Unit,
    onReset: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
            Button(onClick = onChoose) { Text("选择…") }
            OutlinedButton(onClick = onReset) { Text("恢复默认") }
        }
    }
}

/**
 * 弹出系统目录选择对话框；返回用户选中的目录或 null。
 */
private fun pickDirectory(initial: String) = DirectoryPicker.pickDirectory(initial)
