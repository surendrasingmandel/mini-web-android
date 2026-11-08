package com.miniweb.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.miniweb.browser.data.BrowserTheme

private val DarkScheme = darkColorScheme(primary = Color(0xFF22B8D6), secondary = Color(0xFF70D7E8), background = Color(0xFF101820), surface = Color(0xFF17232D))
private val LightScheme = lightColorScheme(primary = Color(0xFF087F98), secondary = Color(0xFF16A6C1), background = Color(0xFFF5F8FA), surface = Color.White)

@Composable
fun MiniWebTheme(theme: BrowserTheme, accent: String, content: @Composable () -> Unit) {
    val dark = when (theme) { BrowserTheme.LIGHT -> false; BrowserTheme.DARK, BrowserTheme.AMOLED -> true; BrowserTheme.SYSTEM -> isSystemInDarkTheme() }
    val parsed = runCatching { Color(android.graphics.Color.parseColor(accent)) }.getOrDefault(Color(0xFF22B8D6))
    val base = if (dark) DarkScheme else LightScheme
    val scheme = base.copy(primary = parsed, secondary = parsed, background = if (theme == BrowserTheme.AMOLED) Color.Black else base.background, surface = if (theme == BrowserTheme.AMOLED) Color.Black else base.surface)
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
