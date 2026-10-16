package ai.jarvis.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Explicit user-exported offline backup. Destination is selected by Android's
 * Storage Access Framework: Drive, OneDrive or a local folder. We never read
 * the user's Google/Microsoft credentials or upload an unencrypted archive.
 *
 * Never export API keys, provider tokens, Gmail credentials, auth cookies,
 * Keystore keys or cloud-consent decisions.
 */
internal object JarvisSecureBackup {
    private val MAGIC = "JARVIS-ENCRYPTED-BACKUP-1\n".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_ITERATIONS = 250_000
    private const val MAX_PLAINTEXT = 16_000_000
    private const val MAX_ARCHIVE = MAX_PLAINTEXT + 1024
    internal const val MAX_CHAT_RECORDS = 10_000

    internal val DATA_FIELDS = listOf(
        "readings", "notes", "invoices", "billing_v1", "home_destination",
        "saved_places_import_v1", "trusted_people_v1"
    )
    private val random = SecureRandom()

    internal data class Snapshot(
        val fields: Map<String, String>,
        val conversations: List<JarvisChatEntry>
    )

    private fun validatedPassword(password: CharArray) {
        require(password.size in 12..256) {
            "A mentési jelszónak legalább 12 karakterből kell állnia."
        }
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, KEY_ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    /** Pure byte-level envelope with authenticated header; safe to unit-test. */
    internal fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        validatedPassword(password)
        require(plaintext.size in 1..MAX_PLAINTEXT) { "Túl nagy a biztonsági mentés." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plaintext)
    }

    internal fun decrypt(archive: ByteArray, password: CharArray): ByteArray {
        validatedPassword(password)
        require(archive.size in (MAGIC.size + SALT_BYTES + IV_BYTES + 16)..MAX_ARCHIVE &&
            archive.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Nem támogatott vagy sérült Jarvis-mentés."
        }
        val salt = archive.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = archive.copyOfRange(MAGIC.size + SALT_BYTES, MAGIC.size + SALT_BYTES + IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(archive, MAGIC.size + SALT_BYTES + IV_BYTES,
            archive.size - MAGIC.size - SALT_BYTES - IV_BYTES)
    }

    fun create(context: Context, password: CharArray): ByteArray {
        val prefs = SecurePrefs(context.applicationContext)
        val fields = JSONObject()
        DATA_FIELDS.forEach { field ->
            val value = prefs.read(field, "")
            if (value.isNotBlank()) fields.put(field, value)
        }
        val chats = JSONArray()
        JarvisChatStore(context).use { store ->
            store.backupEntries(MAX_CHAT_RECORDS).forEach { entry ->
                chats.put(JSONObject()
                    .put("timestamp", entry.createdAtMs)
                    .put("conversation", entry.conversationId)
                    .put("question", entry.request)
                    .put("answer", entry.answer)
                    .put("aiContext", entry.aiContext))
            }
        }
        val doc = JSONObject()
            .put("version", 1)
            .put("fields", fields)
            .put("chats", chats)
        return encrypt(doc.toString().toByteArray(Charsets.UTF_8), password)
    }

    /** Parse and validate everything before any local data is changed. */
    fun inspect(archive: ByteArray, password: CharArray): Snapshot {
        val doc = JSONObject(decrypt(archive, password).toString(Charsets.UTF_8))
        require(doc.getInt("version") == 1) { "Ismeretlen mentési verzió." }
        val obj = doc.getJSONObject("fields")
        val fields = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            require(name in DATA_FIELDS) { "Ismeretlen mező a mentésben." }
            val value = obj.getString(name)
            require(value.length <= 2_000_000) { "Túl nagy adatmező." }
            fields[name] = value
        }
        val arr = doc.getJSONArray("chats")
        require(arr.length() <= MAX_CHAT_RECORDS) { "Túl sok beszélgetési bejegyzés." }
        val chats = ArrayList<JarvisChatEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val conversation = item.getString("conversation")
            val question = item.getString("question")
            val answer = item.getString("answer")
            val stamp = item.getLong("timestamp")
            require(conversation.isNotBlank() && conversation.length <= 64 &&
                question.isNotBlank() && question.length <= 8000 &&
                answer.isNotBlank() && answer.length <= JarvisNetworkSafety.MAX_AI_TEXT_CHARACTERS &&
                stamp > 0L && stamp < System.currentTimeMillis() + 86_400_000L) {
                "Érvénytelen beszélgetési adat."
            }
            chats.add(JarvisChatEntry(
                createdAtMs = stamp, request = question, answer = answer,
                conversationId = conversation, aiContext = item.getBoolean("aiContext")
            ))
        }
        return Snapshot(fields.toMap(), chats)
    }

    /** Caller must explicitly confirm overwrite of the listed local records. */
    fun restore(context: Context, snapshot: Snapshot) {
        // Chat restore runs as one transaction; imported threads receive new IDs
        // and never delete any existing conversation rows.
        JarvisChatStore(context).use { it.importBackupEntries(snapshot.conversations) }
        val prefs = SecurePrefs(context.applicationContext)
        snapshot.fields.forEach { (name, value) ->
            require(name in DATA_FIELDS)
            prefs.write(name, value)
        }
    }
}
