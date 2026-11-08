package com.miniweb.browser.engine

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/** Lightweight host-based request filter. This is intentionally not presented as a full filter-list parser. */
class AdBlockEngine {
    private val adHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "adnxs.com", "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com", "pubmatic.com", "openx.net"
    )
    private val trackerHosts = setOf(
        "scorecardresearch.com", "quantserve.com", "pixel.facebook.com", "connect.facebook.net",
        "analytics.tiktok.com", "branch.io", "app-measurement.com"
    )
    private val allowedHosts = ConcurrentHashMap.newKeySet<String>()
    fun allow(host: String) { allowedHosts.add(host.lowercase()) }

    fun isBlocked(rawUrl: String, firstPartyHost: String?, blockAds: Boolean, blockTrackers: Boolean): Boolean {
        if (!blockAds && !blockTrackers) return false
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val first = firstPartyHost?.lowercase()
        if (first != null && (host == first || host.endsWith(".$first"))) return false
        if (allowedHosts.any { host == it || host.endsWith(".$it") }) return false
        return (blockAds && adHosts.any { host == it || host.endsWith(".$it") }) ||
            (blockTrackers && trackerHosts.any { host == it || host.endsWith(".$it") })
    }
}
