package ai.jarvis.mobile

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local encrypted persistence for sensitive readings, notes and invoice drafts.
 * The AES key is non-exportable from Android Keystore. Existing 0.1.x test
 * data is migrated only after it has been encrypted successfully.
 * No insecure fallback is used if encryption fails.
 */
internal class SecurePrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "jarvis_basic_v1", Context.MODE_PRIVATE
    )
    private val alias = "jarvis_mobile_basic_v1_aes"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    @Synchronized fun write(field: String, value: String) {
        writeAll(mapOf(field to value))
    }

    /** Prepare all ciphertexts before atomically committing the preference set. */
    @Synchronized fun writeAll(values: Map<String, String>) {
        val prepared = values.mapValues { (_, value) ->
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            "v1:" + Base64.encodeToString(
                cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP
            )
        }
        val edit = prefs.edit()
        prepared.forEach { (field, payload) ->
            edit.putString("encrypted_$field", payload).remove(field)
        }
        check(edit.commit()) { "Encrypted local data could not be saved" }
    }

    @Synchronized fun read(field: String, default: String): String {
        val payload = prefs.getString("encrypted_$field", null)
        if (payload == null) {
            val legacy = prefs.getString(field, null) ?: return default
            // Migration is intentionally fail-closed: do not remove plaintext if save fails.
            write(field, legacy)
            return legacy
        }
        require(payload.startsWith("v1:")) { "Unsupported encrypted data version" }
        val data = Base64.decode(payload.removePrefix("v1:"), Base64.DEFAULT)
        require(data.size > 12) { "Invalid encrypted data" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
    }
}
