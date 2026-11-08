
package com.miniweb.browser.ui

import androidx.compose.foundation.layout.statusBarsPadding
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.miniweb.browser.data.*
import com.miniweb.browser.engine.AdBlockEngine
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

private data class BrowserTab(
    val id: Int,
    var url: String = "",
    var title: String = "New tab",
    val privateMode: Boolean = false
)

private enum class Panel {
    NONE, TABS, BOOKMARKS, HISTORY, SETTINGS, PRIVACY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    settings: BrowserSettings,
    preferences: BrowserPreferences,
    dao: BrowserDao
) {
    val context = LocalContext.current
    val browserSettings = settings
    val scope = rememberCoroutineScope()

    val adBlocker = remember { AdBlockEngine() }

    val tabs = remember {
        mutableStateListOf(BrowserTab(1))
    }

    var activeId by remember { mutableIntStateOf(1) }

    val activeTab = tabs.firstOrNull {
        it.id == activeId
    } ?: tabs.first()

    val latestSettings by rememberUpdatedState(settings)
    val latestActiveId by rememberUpdatedState(activeId)
    val latestPrivateMode by rememberUpdatedState(activeTab.privateMode)

    var webView by remember {
        mutableStateOf<WebView?>(null)
    }

    var address by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("Mini Web") }
    var currentUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    var panel by remember { mutableStateOf(Panel.NONE) }
    var blockedCount by remember { mutableIntStateOf(0) }

    val blockedCounter = remember { AtomicInteger(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var showSuggestions by remember { mutableStateOf(false) }

    val bookmarks by dao.observeBookmarks()
        .collectAsState(initial = emptyList())

    val history by dao.observeHistory()
        .collectAsState(initial = emptyList())

    fun navigate(input: String) {
        val raw = input.trim()

        if (raw.isEmpty()) return

        val url = when {
            raw.matches(
                Regex("(?i)^[a-z][a-z0-9+.-]*://.*")
            ) -> raw

            raw.contains(".") && !raw.contains(" ") ->
                "https://$raw"

            else -> {
                val q = URLEncoder.encode(raw, "UTF-8")

                when (settings.searchEngine) {
                    "Google" ->
                        "https://www.google.com/search?q=$q"

                    "Bing" ->
                        "https://www.bing.com/search?q=$q"

                    "Yahoo" ->
                        "https://search.yahoo.com/search?p=$q"

                    "Brave Search" ->
                        "https://search.brave.com/search?q=$q"

                    "Ecosia" ->
                        "https://www.ecosia.org/search?q=$q"

                    "Startpage" ->
                        "https://www.startpage.com/sp/search?query=$q"

                    else ->
                        "https://duckduckgo.com/?q=$q"
                }
            }
        }

        val uri = runCatching {
            Uri.parse(url)
        }.getOrNull()

        if (uri == null || uri.scheme !in listOf("http", "https")) {
            return
        }

        tabs.firstOrNull {
            it.id == activeId
        }?.url = url

        address = url
        webView?.loadUrl(url)
        showSuggestions = false
    }

    BackHandler(
        enabled = panel != Panel.NONE ||
                (webView?.canGoBack() == true)
    ) {
        if (panel != Panel.NONE) {
            panel = Panel.NONE
        } else {
            webView?.goBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,

        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (loading) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth()
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = .12f
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "M",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            showSuggestions = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        placeholder = {
                            Text("Search or enter address")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (address.isNotEmpty()) {
                                IconButton(
                                    onClick = { address = "" }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Clear"
                                    )
                                }
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onGo = { navigate(address) }
                        )
                    )

                    IconButton(
                        onClick = { panel = Panel.TABS }
                    ) {
                        Icon(Icons.Default.Tab, "Tabs")
                    }
                }

                if (
                    showSuggestions &&
                    settings.suggestions &&
                    address.isNotBlank()
                ) {
                    val matches = history.filter {
                        it.url.contains(address, true) ||
                                it.title.contains(address, true)
                    }.take(4)

                    matches.forEach { entry ->
                        Text(
                            entry.title.ifBlank { entry.url },
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navigate(entry.url)
                                }
                                .padding(
                                    horizontal = 22.dp,
                                    vertical = 8.dp
                                ),
                            maxLines = 1
                        )
                    }
                }
            }
        },

        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        webView?.goBack()
                    },
                    icon = {
                        Icon(Icons.Default.ArrowBack, "Back")
                    },
                    enabled = canGoBack
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {
                        webView?.goForward()
                    },
                    icon = {
                        Icon(Icons.Default.ArrowForward, "Forward")
                    },
                    enabled = canGoForward
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {
                        if (currentUrl.isEmpty()) {
                            navigate(
                                settings.homepage.ifBlank {
                                    "https://duckduckgo.com"
                                }
                            )
                        } else {
                            webView?.reload()
                        }
                    },
                    icon = {
                        Icon(Icons.Default.Refresh, "Reload")
                    }
                )

                NavigationBarItem(
                    selected = false,
                    onClick = {
                        if (currentUrl.isNotBlank()) {
                            scope.launch {
                                dao.addBookmark(
                                    Bookmark(
                                        title = pageTitle,
                                        url = currentUrl
                                    )
                                )
                            }
                        }
                    },
                    icon = {
                        Icon(Icons.Default.Bookmark, "Bookmark")
                    }
                )

                NavigationBarItem(
                    selected = panel == Panel.SETTINGS,
                    onClick = {
                        panel = Panel.SETTINGS
                    },
                    icon = {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                )
            }
        }
    ) { padding ->

        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),

                factory = { ctx ->

                    WebView(ctx).apply {

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        // FIX: Explicitly access WebView settings.
                        this.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadsImagesAutomatically = true

                            allowFileAccess = false
                            allowContentAccess = false

                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)

                            mixedContentMode =
                                WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            cacheMode = WebSettings.LOAD_DEFAULT

                            userAgentString = userAgentFor(
                                ctx,
                                latestSettings.desktopMode
                            )
                        }

                        CookieManager.getInstance()
                            .setAcceptCookie(true)

                        CookieManager.getInstance()
                            .setAcceptThirdPartyCookies(
                                this,
                                !latestSettings.thirdPartyCookies
                            )

                        webChromeClient = WebChromeClient()

                        webViewClient = object : WebViewClient() {

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {

                                val req = request ?: return null

                                val host = runCatching {
                                    Uri.parse(view?.url ?: "").host
                                }.getOrNull()

                                if (
                                    adBlocker.isBlocked(
                                        req.url.toString(),
                                        host,
                                        latestSettings.adBlocking,
                                        latestSettings.trackerBlocking
                                    )
                                ) {
                                    val total =
                                        blockedCounter.incrementAndGet()

                                    mainHandler.post {
                                        blockedCount = total
                                    }

                                    return WebResourceResponse(
                                        "text/plain",
                                        "utf-8",
                                        204,
                                        "Blocked",
                                        emptyMap(),
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                return super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                loading = true

                                url?.let {
                                    currentUrl = it
                                    address = it
                                }

                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                            }

                            override fun onPageFinished(
                                view: WebView?,
                                url: String?
                            ) {
                                loading = false

                                pageTitle = view?.title ?: "Mini Web"

                                currentUrl = url ?: currentUrl
                                address = currentUrl

                                tabs.firstOrNull {
                                    it.id == latestActiveId
                                }?.let {
                                    it.url = currentUrl
                                    it.title = pageTitle
                                }

                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true

                                if (
                                    latestSettings.saveHistory &&
                                    !latestPrivateMode &&
                                    currentUrl.startsWith("https://")
                                ) {
                                    scope.launch {
                                        dao.addHistory(
                                            HistoryEntry(
                                                title = pageTitle,
                                                url = currentUrl
                                            )
                                        )
                                    }
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

                                val uri = request?.url ?: return false

                                return uri.scheme !in listOf(
                                    "http",
                                    "https"
                                )
                            }
                        }

                        setDownloadListener {
                                url,
                                userAgent,
                                contentDisposition,
                                mimeType,
                                _ ->

                            runCatching {
                                val request =
                                    DownloadManager.Request(Uri.parse(url)).apply {

                                        setMimeType(mimeType)

                                        addRequestHeader(
                                            "User-Agent",
                                            userAgent
                                        )

                                        setNotificationVisibility(
                                            DownloadManager.Request
                                                .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                                        )

                                        setDestinationInExternalFilesDir(
                                            ctx,
                                            android.os.Environment.DIRECTORY_DOWNLOADS,
                                            URLUtil.guessFileName(
                                                url,
                                                contentDisposition,
                                                mimeType
                                            )
                                        )
                                    }

                                (
                                    ctx.getSystemService(
                                        Context.DOWNLOAD_SERVICE
                                    ) as DownloadManager
                                ).enqueue(request)
                            }
                        }

                        webView = this

                        val initial =
                            browserSettings.homepage.ifBlank {
                                "https://duckduckgo.com"
                            }

                        loadUrl(initial)
                    }
                },

                update = { view ->

                    webView = view

                    // Correct WebView settings access.
                    view.settings.userAgentString = userAgentFor(
                        context,
                        latestSettings.desktopMode
                    )

                    CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(
                            view,
                            !latestSettings.thirdPartyCookies
                        )
                }
            )

            if (panel != Panel.NONE) {

                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    when (panel) {

                        Panel.TABS -> TabsPanel(
                            tabs = tabs,
                            activeId = activeId,

                            onSelect = { id ->
                                activeId = id

                                val tab = tabs.first {
                                    it.id == id
                                }

                                if (tab.url.isNotBlank()) {
                                    navigate(tab.url)
                                }

                                panel = Panel.NONE
                            },

                            onNew = {
                                val id =
                                    (tabs.maxOfOrNull { it.id } ?: 0) + 1

                                tabs.add(BrowserTab(id))
                                activeId = id

                                navigate("https://duckduckgo.com")
                                panel = Panel.NONE
                            },

                            onClose = { id ->

                                if (tabs.size > 1) {

                                    tabs.removeAll {
                                        it.id == id
                                    }

                                    if (activeId == id) {
                                        activeId = tabs.last().id

                                        navigate(
                                            tabs.last().url.ifBlank {
                                                "https://duckduckgo.com"
                                            }
                                        )
                                    }

                                } else {

                                    tabs[0] = BrowserTab(1)
                                    activeId = 1

                                    navigate("https://duckduckgo.com")
                                }
                            },

                            onDismiss = {
                                panel = Panel.NONE
                            }
                        )

                        Panel.BOOKMARKS -> SimpleListPanel(
                            title = "Bookmarks",
                            rows = bookmarks.map {
                                it.title to it.url
                            },
                            onOpen = {
                                navigate(it)
                                panel = Panel.NONE
                            },
                            onDismiss = {
                                panel = Panel.NONE
                            }
                        )

                        Panel.HISTORY -> SimpleListPanel(
                            title = "History",
                            rows = history.map {
                                it.title to it.url
                            },
                            onOpen = {
                                navigate(it)
                                panel = Panel.NONE
                            },
                            onDismiss = {
                                panel = Panel.NONE
                            },
                            onClear = {
                                scope.launch {
                                    dao.clearHistory()
                                }
                            }
                        )

                        Panel.PRIVACY -> PrivacyPanel(
                            blocked = blockedCount,
                            settings = settings,
                            onDismiss = {
                                panel = Panel.NONE
                            }
                        )

                        Panel.SETTINGS -> SettingsPanel(
                            settings,
                            preferences,
                            onDismiss = {
                                panel = Panel.NONE
                            },
                            onHistory = {
                                panel = Panel.HISTORY
                            },
                            onBookmarks = {
                                panel = Panel.BOOKMARKS
                            },
                            onPrivacy = {
                                panel = Panel.PRIVACY
                            }
                        )

                        Panel.NONE -> Unit
                    }
                }
            }
        }
    }
}

private fun userAgentFor(
    context: Context,
    desktop: Boolean
): String {

    val defaultAgent = WebSettings.getDefaultUserAgent(context)

    return if (desktop) {
        defaultAgent
            .replace("; wv", "")
            .replace(
                Regex("\\bAndroid [0-9.]+;"),
                "X11; Linux x86_64;"
            )
            .replace("Mobile Safari", "Safari")
    } else {
        defaultAgent
    }
}

@Composable
private fun TabsPanel(
    tabs: List<BrowserTab>,
    activeId: Int,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
    onClose: (Int) -> Unit,
    onDismiss: () -> Unit
) {

    Column(
        Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                "Tabs (${tabs.size})",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close")
            }

            IconButton(onClick = onNew) {
                Icon(Icons.Default.Add, "New tab")
            }
        }

        LazyColumn {

            items(tabs, key = { it.id }) { tab ->

                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable {
                            onSelect(tab.id)
                        }
                ) {

                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Column(
                            Modifier.weight(1f)
                        ) {

                            Text(
                                tab.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )

                            Text(
                                tab.url.ifBlank { "New tab" },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }

                        if (tab.id == activeId) {
                            Text(
                                "Active",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                onClose(tab.id)
                            }
                        ) {
                            Icon(Icons.Default.Close, "Close tab")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleListPanel(
    title: String,
    rows: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null
) {

    Column(
        Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            if (onClear != null) {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close")
            }
        }

        LazyColumn {

            items(rows) { (name, url) ->

                ListItem(
                    headlineContent = {
                        Text(
                            name.ifBlank { url },
                            maxLines = 1
                        )
                    },
                    supportingContent = {
                        Text(url, maxLines = 1)
                    },
                    modifier = Modifier.clickable {
                        onOpen(url)
                    }
                )
            }
        }
    }
}

@Composable
private fun PrivacyPanel(
    blocked: Int,
    settings: BrowserSettings,
    onDismiss: () -> Unit
) {

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                "Privacy dashboard",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close")
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "$blocked",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text("Requests blocked in this browsing session")

        Spacer(Modifier.height(20.dp))

        ListItem(
            headlineContent = {
                Text("Ad blocking")
            },
            supportingContent = {
                Text(if (settings.adBlocking) "Enabled" else "Disabled")
            },
            leadingContent = {
                Icon(Icons.Default.Shield, null)
            }
        )

        ListItem(
            headlineContent = {
                Text("Tracker blocking")
            },
            supportingContent = {
                Text(
                    if (settings.trackerBlocking) "Enabled"
                    else "Disabled"
                )
            },
            leadingContent = {
                Icon(Icons.Default.VisibilityOff, null)
            }
        )

        ListItem(
            headlineContent = {
                Text("Third-party cookies")
            },
            supportingContent = {
                Text(
                    if (settings.thirdPartyCookies) "Blocked"
                    else "Allowed"
                )
            },
            leadingContent = {
                Icon(Icons.Default.Lock, null)
            }
        )

        Text(
            "Protection uses a built-in host blocklist. " +
                    "It is not a full EasyList-compatible engine " +
                    "and does not guarantee anonymity.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsPanel(
    s: BrowserSettings,
    prefs: BrowserPreferences,
    onDismiss: () -> Unit,
    onHistory: () -> Unit,
    onBookmarks: () -> Unit,
    onPrivacy: () -> Unit
) {

    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close")
            }
        }

        LazyColumn {

            item {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )
            }

            item {

                Text("Theme", style = MaterialTheme.typography.bodyMedium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {

                    BrowserTheme.entries.forEach { theme ->

                        FilterChip(
                            selected = s.theme == theme,
                            onClick = {
                                scope.launch {
                                    prefs.setTheme(theme)
                                }
                            },
                            label = {
                                Text(
                                    theme.name.lowercase()
                                        .replaceFirstChar {
                                            it.uppercase()
                                        }
                                )
                            }
                        )
                    }
                }
            }

            item {

                Text(
                    "Search engine",
                    style = MaterialTheme.typography.bodyMedium
                )

                var expanded by remember {
                    mutableStateOf(false)
                }

                Box {

                    OutlinedButton(
                        onClick = {
                            expanded = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(s.searchEngine)

                        Spacer(Modifier.weight(1f))

                        Icon(
                            Icons.Default.ArrowDropDown,
                            null
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {

                        listOf(
                            "DuckDuckGo",
                            "Google",
                            "Bing",
                            "Yahoo",
                            "Brave Search",
                            "Ecosia",
                            "Startpage"
                        ).forEach { engine ->

                            DropdownMenuItem(
                                text = {
                                    Text(engine)
                                },
                                onClick = {
                                    scope.launch {
                                        prefs.setEngine(engine)
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {

                HorizontalDivider(
                    Modifier.padding(vertical = 10.dp)
                )

                Text(
                    "Privacy & security",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                SettingSwitch(
                    "Block ads",
                    s.adBlocking
                ) {
                    scope.launch {
                        prefs.setAds(it)
                    }
                }
            }

            item {
                SettingSwitch(
                    "Block trackers",
                    s.trackerBlocking
                ) {
                    scope.launch {
                        prefs.setTrackers(it)
                    }
                }
            }

            item {
                SettingSwitch(
                    "Block third-party cookies",
                    s.thirdPartyCookies
                ) {
                    scope.launch {
                        prefs.setCookies(it)
                    }
                }
            }

            item {
                SettingSwitch(
                    "Search suggestions",
                    s.suggestions
                ) {
                    scope.launch {
                        prefs.setSuggestions(it)
                    }
                }
            }

            item {
                SettingSwitch(
                    "Save browsing history",
                    s.saveHistory
                ) {
                    scope.launch {
                        prefs.setHistory(it)
                    }
                }
            }

            item {
                SettingSwitch(
                    "Desktop site by default",
                    s.desktopMode
                ) {
                    scope.launch {
                        prefs.setDesktop(it)
                    }
                }
            }

            item {
                TextButton(onClick = onPrivacy) {
                    Icon(Icons.Default.Shield, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy dashboard")
                }
            }

            item {
                TextButton(onClick = onBookmarks) {
                    Icon(Icons.Default.Bookmarks, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Bookmarks")
                }
            }

            item {
                TextButton(onClick = onHistory) {
                    Icon(Icons.Default.History, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Browsing history")
                }
            }

            item {
                Text(
                    "Mini Web 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        top = 18.dp,
                        bottom = 20.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                onChange(!checked)
            }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            label,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}
