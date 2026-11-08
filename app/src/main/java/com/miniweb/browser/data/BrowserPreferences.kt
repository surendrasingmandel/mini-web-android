package com.miniweb.browser.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mini_web_preferences")

enum class BrowserTheme { SYSTEM, LIGHT, DARK, AMOLED }
enum class ProtectionLevel { STANDARD, AGGRESSIVE, CUSTOM }

data class BrowserSettings(
    val theme: BrowserTheme = BrowserTheme.SYSTEM,
    val accent: String = "#22B8D6",
    val searchEngine: String = "DuckDuckGo",
    val adBlocking: Boolean = true,
    val trackerBlocking: Boolean = true,
    val thirdPartyCookies: Boolean = true,
    val suggestions: Boolean = true,
    val saveHistory: Boolean = true,
    val protection: ProtectionLevel = ProtectionLevel.STANDARD,
    val desktopMode: Boolean = false,
    val toolbarBottom: Boolean = true,
    val homepage: String = ""
)

class BrowserPreferences(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val engine = stringPreferencesKey("search_engine")
        val ads = booleanPreferencesKey("ad_blocking")
        val trackers = booleanPreferencesKey("tracker_blocking")
        val cookies = booleanPreferencesKey("third_party_cookies")
        val suggestions = booleanPreferencesKey("suggestions")
        val history = booleanPreferencesKey("save_history")
        val protection = stringPreferencesKey("protection")
        val desktop = booleanPreferencesKey("desktop_mode")
        val toolbar = booleanPreferencesKey("toolbar_bottom")
        val homepage = stringPreferencesKey("homepage")
    }
    val settings = context.dataStore.data.map { p ->
        BrowserSettings(
            theme = runCatching { BrowserTheme.valueOf(p[Keys.theme] ?: "SYSTEM") }.getOrDefault(BrowserTheme.SYSTEM),
            accent = p[Keys.accent] ?: "#22B8D6", searchEngine = p[Keys.engine] ?: "DuckDuckGo",
            adBlocking = p[Keys.ads] ?: true, trackerBlocking = p[Keys.trackers] ?: true,
            thirdPartyCookies = p[Keys.cookies] ?: true, suggestions = p[Keys.suggestions] ?: true,
            saveHistory = p[Keys.history] ?: true,
            protection = runCatching { ProtectionLevel.valueOf(p[Keys.protection] ?: "STANDARD") }.getOrDefault(ProtectionLevel.STANDARD),
            desktopMode = p[Keys.desktop] ?: false, toolbarBottom = p[Keys.toolbar] ?: true, homepage = p[Keys.homepage] ?: ""
        )
    }
    suspend fun update(block: (MutablePreferences) -> Unit) { context.dataStore.updateData { old -> old.toMutablePreferences().also(block) } }
    suspend fun setTheme(v: BrowserTheme) = update { it[Keys.theme] = v.name }
    suspend fun setEngine(v: String) = update { it[Keys.engine] = v }
    suspend fun setAds(v: Boolean) = update { it[Keys.ads] = v }
    suspend fun setTrackers(v: Boolean) = update { it[Keys.trackers] = v }
    suspend fun setCookies(v: Boolean) = update { it[Keys.cookies] = v }
    suspend fun setSuggestions(v: Boolean) = update { it[Keys.suggestions] = v }
    suspend fun setHistory(v: Boolean) = update { it[Keys.history] = v }
    suspend fun setProtection(v: ProtectionLevel) = update { it[Keys.protection] = v.name }
    suspend fun setDesktop(v: Boolean) = update { it[Keys.desktop] = v }
    suspend fun setToolbarBottom(v: Boolean) = update { it[Keys.toolbar] = v }
    suspend fun setHomepage(v: String) = update { it[Keys.homepage] = v }
}
