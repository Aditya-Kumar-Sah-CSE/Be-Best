package com.focuslock.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Handles security PIN management using PBKDF2WithHmacSHA256 with per-user salt.
 * Ensures PIN is never stored in plaintext and resists brute-force / timing attacks.
 */
class PinSecurityManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasPin(): Boolean {
        return prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun setPin(pin: String): Boolean {
        if (pin.length < 4) return false

        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)

        val hash = hashPin(pin.toCharArray(), salt) ?: return false

        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()

        return true
    }

    fun verifyPin(pin: String): Boolean {
        val saltBase64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedHashBase64 = prefs.getString(KEY_PIN_HASH, null) ?: return false

        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)

        val computedHash = hashPin(pin.toCharArray(), salt) ?: return false

        return constantTimeEquals(storedHash, computedHash)
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply()
    }

    // Hardcore Mode Cooldown Lock
    fun setHardcoreUnlockRequestTime(timeMillis: Long) {
        prefs.edit().putLong(KEY_HARDCORE_UNLOCK_REQUEST, timeMillis).apply()
    }

    fun getHardcoreUnlockRequestTime(): Long {
        return prefs.getLong(KEY_HARDCORE_UNLOCK_REQUEST, 0L)
    }

    fun isHardcoreModeActive(): Boolean {
        return prefs.getBoolean(KEY_HARDCORE_ENABLED, false)
    }

    fun setHardcoreMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDCORE_ENABLED, enabled).apply()
    }

    private fun hashPin(pin: CharArray, salt: ByteArray): ByteArray? {
        val spec = PBEKeySpec(pin, salt, ITERATION_COUNT, KEY_LENGTH)
        return try {
            val skf = SecretKeyFactory.getInstance(ALGORITHM)
            skf.generateSecret(spec).encoded
        } catch (e: NoSuchAlgorithmException) {
            null
        } catch (e: InvalidKeySpecException) {
            null
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    companion object {
        private const val PREFS_NAME = "focuslock_security_prefs"
        private const val KEY_PIN_HASH = "key_pin_hash"
        private const val KEY_PIN_SALT = "key_pin_salt"
        private const val KEY_HARDCORE_ENABLED = "key_hardcore_enabled"
        private const val KEY_HARDCORE_UNLOCK_REQUEST = "key_hardcore_unlock_req"

        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATION_COUNT = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_BYTES = 16
    }
}
