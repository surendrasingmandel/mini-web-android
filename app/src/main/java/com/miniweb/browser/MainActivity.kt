package com.miniweb.browser

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.miniweb.browser.data.BrowserDatabase
import com.miniweb.browser.data.BrowserPreferences
import com.miniweb.browser.ui.BrowserScreen
import com.miniweb.browser.ui.theme.MiniWebTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var preferences: BrowserPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
        }
        enableEdgeToEdge()
        preferences = BrowserPreferences(applicationContext)
        val database = BrowserDatabase.get(applicationContext)
        setContent {
            val settings by preferences.settings.collectAsState(initial = com.miniweb.browser.data.BrowserSettings())
            MiniWebTheme(theme = settings.theme, accent = settings.accent) {
                BrowserScreen(settings = settings, preferences = preferences, dao = database.browserDao())
            }
        }
    }
}
