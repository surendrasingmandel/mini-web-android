package com.miniweb.browser

import android.os.Bundle
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
