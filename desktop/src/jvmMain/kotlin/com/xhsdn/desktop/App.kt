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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.xhsdn.desktop.screen.HistoryScreen
import com.xhsdn.desktop.screen.HomeScreen
import com.xhsdn.desktop.screen.SettingsScreen
import com.xhsdn.desktop.ui.theme.XhsTheme
import com.xhsdn.desktop.viewmodel.NavTab
import com.xhsdn.desktop.viewmodel.rememberDesktopTaskViewModel

/**
 * 桌面端 App 顶层。用 Material3 替代 Android 端 Miuix。
 *
 * 三页：Home / History / Settings，对应 [docs/小红书去水印工具迭代计划.md] 提出的 Tab 拆分。
 *
 * Tab 选中态放在 [com.xhsdn.desktop.viewmodel.DesktopTaskViewModel] 而不是本地 remember,
 * 是为了让 HistoryScreen 的「重新解析」按钮能切回 HomeScreen 并自动下载。
 */
@Composable
fun App() {
    XhsTheme {
        val viewModel = rememberDesktopTaskViewModel()
        val tab by viewModel.selectedTab.collectAsState()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == NavTab.HOME,
                        onClick = { viewModel.selectTab(NavTab.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("下载") },
                    )
                    NavigationBarItem(
                        selected = tab == NavTab.HISTORY,
                        onClick = { viewModel.selectTab(NavTab.HISTORY) },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("任务") },
                    )
                    NavigationBarItem(
                        selected = tab == NavTab.SETTINGS,
                        onClick = { viewModel.selectTab(NavTab.SETTINGS) },
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
                    NavTab.HOME -> HomeScreen()
                    NavTab.HISTORY -> HistoryScreen()
                    NavTab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}
