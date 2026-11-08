package com.miniweb.browser

import com.miniweb.browser.engine.AdBlockEngine
import org.junit.Assert.*
import org.junit.Test

class AdBlockEngineTest {
    private val engine = AdBlockEngine()
    @Test fun blocksKnownAdHost() { assertTrue(engine.isBlocked("https://doubleclick.net/ad.js", "example.com", true, false)) }
    @Test fun allowsFirstPartyRequests() { assertFalse(engine.isBlocked("https://example.com/script.js", "example.com", true, true)) }
    @Test fun disabledFilterAllowsRequest() { assertFalse(engine.isBlocked("https://doubleclick.net/ad.js", "example.com", false, false)) }
    @Test fun allowsNonListedHost() { assertFalse(engine.isBlocked("https://cdn.example.org/app.js", "example.com", true, true)) }
}
