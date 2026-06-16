package com.xhsdn.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * XHS 主题色（小红书红）。Desktop 端用 Material3 替代 Android 端 Miuix。
 */
private val XhsRed = Color(0xFFFF2442)

private val LightColors = lightColorScheme(
    primary = XhsRed,
    secondary = XhsRed,
    tertiary = XhsRed,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = XhsRed,
    secondary = XhsRed,
    tertiary = XhsRed,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
)

@Composable
fun XhsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
