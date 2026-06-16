package com.xhsdn.desktop.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xhsdn.core.model.DownloadTask
import com.xhsdn.core.model.TaskStatus
import com.xhsdn.desktop.viewmodel.rememberDesktopTaskViewModel

/**
 * 任务历史页。展示 [com.xhsdn.core.task.TaskManager.tasks] StateFlow。
 */
@Composable
fun HistoryScreen() {
    val viewModel = rememberDesktopTaskViewModel()
    val tasks by viewModel.tasks.collectAsState()

    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("还没有下载任务", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Text("切到「下载」标签开始", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("历史（${tasks.size}）", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.clearHistory() }) { Text("清除已完成") }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tasks, key = { it.id }) { task -> TaskRow(task) }
        }
    }
}

@Composable
private fun TaskRow(task: DownloadTask) {
    val statusColor = when (task.status) {
        TaskStatus.QUEUED -> Color(0xFF9E9E9E)
        TaskStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        TaskStatus.COMPLETED -> Color(0xFF4CAF50)
        TaskStatus.FAILED -> Color(0xFFF44336)
        TaskStatus.WAITING_FOR_USER -> Color(0xFFFF9800)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = task.noteTitle ?: task.noteUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
                Text(
                    text = task.status.name,
                    color = statusColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (task.status == TaskStatus.DOWNLOADING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${task.completedFiles}/${task.totalFiles}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}
