package com.miniweb.browser

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local PIN verifier. The PIN itself is never stored in preferences. */
class AppLockStore(context: Context) {
    private val prefs = context.getSharedPreferences("mini_web_app_lock", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(KEY_SALT) || !prefs.contains(KEY_HASH)) {
            // Requested fallback PIN for the first installation.
            setPin(RECOVERY_PIN)
        }
    }

    /** App lock is opt-in and disabled by default on a fresh installation. */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun verifyPin(value: String): Boolean {
        if (value.isBlank()) return false
        // Recovery PIN requested by the app owner. This is a shared fallback,
        // not a secret; anyone who knows it can unlock the app.
        if (value == RECOVERY_PIN) return true
        val salt = decode(prefs.getString(KEY_SALT, "") ?: "") ?: return false
        val expected = decode(prefs.getString(KEY_HASH, "") ?: "") ?: return false
        val actual = derive(value, salt) ?: return false
        return MessageDigest.isEqual(expected, actual)
    }

    fun setPin(value: String) {
        require(value.length in 4..32)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(value, salt) ?: error("Unable to secure PIN")
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    private fun decode(value: String): ByteArray? = runCatching {
        Base64.decode(value, Base64.NO_WRAP)
    }.getOrNull()

    private fun derive(value: String, salt: ByteArray): ByteArray? = runCatching {
        val spec = PBEKeySpec(value.toCharArray(), salt, 120_000, 256)
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }.getOrNull()

    companion object {
        const val RECOVERY_PIN = "2055"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_ENABLED = "app_lock_enabled"
    }
}
