
package com.miniweb.browser

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.lifecycleScope
import com.miniweb.browser.data.BrowserDatabase
import com.miniweb.browser.data.BrowserPreferences
import com.miniweb.browser.ui.BrowserScreen
import com.miniweb.browser.ui.theme.MiniWebTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var preferences: BrowserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                9001
            )
        }

        enableEdgeToEdge()

        preferences = BrowserPreferences(applicationContext)

        val database = BrowserDatabase.get(applicationContext)

        setContent {

            val settings by preferences.settings.collectAsState(
                initial = com.miniweb.browser.data.BrowserSettings()
            )

            var showSplash by remember {
                mutableStateOf(true)
            }

            LaunchedEffect(Unit) {
                delay(2000)
                showSplash = false
            }

            MiniWebTheme(
                theme = settings.theme,
                accent = settings.accent
            ) {

                if (showSplash) {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {

                        Image(
                            painter = painterResource(
                                id = R.drawable.splash_branding
                            ),
                            contentDescription = "Mini Web Splash Screen",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                } else {

                    BrowserScreen(
                        settings = settings,
                        preferences = preferences,
                        dao = database.browserDao()
                    )
                }
            }
        }
    }
}
