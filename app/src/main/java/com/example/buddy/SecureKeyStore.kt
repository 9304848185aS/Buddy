package com.example.buddy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the provider API key with a non-exportable Android Keystore AES key.
 * The encrypted value and IV are stored in app-private preferences.
 */
object SecureKeyStore {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "buddy_api_key_aes"
    private const val PREFS = "buddy_secure_key"
    private const val CIPHER_TEXT = "cipher_text"
    private const val IV = "iv"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    @Synchronized
    fun get(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(CIPHER_TEXT, null)
        val ivText = prefs.getString(IV, null)
        if (!encrypted.isNullOrBlank() && !ivText.isNullOrBlank()) {
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
                )
                String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }

        // Migrate any API key saved by older Buddy builds, then remove the plaintext copy.
        val legacyPrefs = context.applicationContext.getSharedPreferences("buddy_ai", Context.MODE_PRIVATE)
        val legacy = legacyPrefs.getString("api_key", "").orEmpty()
        if (legacy.isNotBlank() && set(context, legacy)) {
            legacyPrefs.edit().remove("api_key").apply()
            return legacy
        }
        return ""
    }

    @Synchronized
    fun set(context: Context, value: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value.isBlank()) {
            prefs.edit().remove(CIPHER_TEXT).remove(IV).commit()
            context.applicationContext.getSharedPreferences("buddy_ai", Context.MODE_PRIVATE)
                .edit().remove("api_key").commit()
            return true
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(CIPHER_TEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
