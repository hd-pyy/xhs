package com.xhsdn.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.xhsdn.desktop.screen.HistoryScreen
import com.xhsdn.desktop.screen.HomeScreen
import com.xhsdn.desktop.screen.SettingsScreen
import com.xhsdn.desktop.ui.theme.XhsTheme

/**
 * 桌面端 App 顶层。用 Material3 替代 Android 端 Miuix。
 *
 * 三页：Home / History / Settings，对应 [docs/小红书去水印工具迭代计划.md] 提出的 Tab 拆分。
 */
@Composable
fun App() {
    XhsTheme {
        var tab by remember { mutableStateOf(Tab.Home) }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Home,
                        onClick = { tab = Tab.Home },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("下载") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.History,
                        onClick = { tab = Tab.History },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("任务") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Settings,
                        onClick = { tab = Tab.Settings },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("设置") },
                    )
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (tab) {
                    Tab.Home -> HomeScreen()
                    Tab.History -> HistoryScreen()
                    Tab.Settings -> SettingsScreen()
                }
            }
        }
    }
}

private enum class Tab { Home, History, Settings }
