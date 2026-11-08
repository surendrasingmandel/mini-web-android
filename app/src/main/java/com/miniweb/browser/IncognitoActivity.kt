
package com.miniweb.browser

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.miniweb.browser.data.BrowserTheme
import com.miniweb.browser.ui.theme.MiniWebTheme
import java.net.URLEncoder

/** Runs in its own Android process for separate WebView storage. */
class IncognitoActivity : ComponentActivity() {

    companion object {
        @Volatile
        private var suffixConfigured = false
    }

    private var privateWebView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        // Configure separate WebView data directory before creating WebView.
        synchronized(IncognitoActivity::class.java) {
            if (!suffixConfigured) {
                WebView.setDataDirectorySuffix("miniweb_incognito")
                suffixConfigured = true
            }
        }

        super.onCreate(savedInstanceState)

        setContent {
            MiniWebTheme(BrowserTheme.DARK, "#22B8D6") {

                var address by remember {
                    mutableStateOf("https://duckduckgo.com")
                }

                val viewState = remember {
                    mutableStateOf<WebView?>(null)
                }

                fun open(raw: String) {
                    val value = raw.trim()
                    if (value.isEmpty()) return

                    val target = when {
                        value.matches(
                            Regex("(?i)^[a-z][a-z0-9+.-]*://.*")
                        ) -> value

                        value.contains('.') && !value.contains(' ') ->
                            "https://$value"

                        else ->
                            "https://duckduckgo.com/?q=${
                                URLEncoder.encode(value, "UTF-8")
                            }"
                    }

                    val uri = runCatching {
                        Uri.parse(target)
                    }.getOrNull() ?: return

                    if (uri.scheme !in listOf("http", "https")) return

                    address = target
                    viewState.value?.loadUrl(target)
                }

                BackHandler(
                    enabled = viewState.value?.canGoBack() == true
                ) {
                    viewState.value?.goBack()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // FIX: Keep the complete private browser UI below
                    // the Android status bar.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = 6.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {

                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Private browsing",
                                tint = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = address,
                                onValueChange = {
                                    address = it
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = {
                                    Text("Search privately or enter URL")
                                },
                                keyboardOptions =
                                    androidx.compose.foundation.text.KeyboardOptions(
                                        imeAction =
                                            androidx.compose.ui.text.input.ImeAction.Go
                                    ),
                                keyboardActions =
                                    androidx.compose.foundation.text.KeyboardActions(
                                        onGo = {
                                            open(address)
                                        }
                                    )
                            )

                            IconButton(
                                onClick = {
                                    open(address)
                                }
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Go"
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewState.value?.reload()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reload"
                                )
                            }

                            IconButton(
                                onClick = {
                                    finish()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close private mode"
                                )
                            }
                        }

                        Text(
                            text = "Private browsing · data is cleared when you close this window",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 4.dp
                            )
                        )

                        AndroidView(
                            modifier = Modifier.weight(1f).fillMaxWidth(),

                            factory = { ctx ->

                                WebView(ctx).apply {

                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )

                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false

                                    settings.mixedContentMode =
                                        android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                                    CookieManager.getInstance().setAcceptCookie(true)

                                    CookieManager.getInstance()
                                        .setAcceptThirdPartyCookies(this, false)

                                    webChromeClient = WebChromeClient()

                                    webViewClient = object : WebViewClient() {

                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: android.graphics.Bitmap?
                                        ) {
                                            if (!url.isNullOrBlank()) {
                                                address = url
                                            }
                                        }

                                        override fun onReceivedSslError(
                                            view: WebView?,
                                            handler: SslErrorHandler?,
                                            error: android.net.http.SslError?
                                        ) {
                                            handler?.cancel()
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val scheme =
                                                request?.url?.scheme ?: return false

                                            return scheme !in listOf(
                                                "http",
                                                "https"
                                            )
                                        }
                                    }

                                    viewState.value = this
                                    privateWebView = this

                                    loadUrl("https://duckduckgo.com")
                                }
                            },

                            update = {
                                viewState.value = it
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {

        privateWebView?.let { view ->
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.clearCache(true)
                view.clearFormData()
                view.removeAllViews()
                view.destroy()
            }
        }

        privateWebView = null

        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
        }

        WebStorage.getInstance().deleteAllData()

        super.onDestroy()
    }
}
