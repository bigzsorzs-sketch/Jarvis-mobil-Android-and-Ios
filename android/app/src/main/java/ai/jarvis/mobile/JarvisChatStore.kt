package ai.jarvis.mobile

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class JarvisChatEntry(
    val createdAtMs: Long, val request: String, val answer: String,
    val id: Long = 0L
)

/**
 * Local-only chat history, encrypted row-by-row with a nonexportable device key.
 * The SQLite index stores no conversation plaintext, provider key or contact data.
 * No implicit backup or cross-device decrypt: migration needs a future explicit,
 * user-encrypted export and recovery passphrase, not the Android Keystore key.
 */
internal class JarvisChatStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext, "jarvis_conversation_v1.db", null, 1
) {
    private val alias = "jarvis_mobile_conversation_aes_v1"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE chat (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, request BLOB NOT NULL, response BLOB NOT NULL)")
        db.execSQL("CREATE INDEX idx_chat_when ON chat(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations must preserve every encrypted row.
        throw IllegalStateException("No chat schema migration available")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return generator.generateKey()
    }

    private fun encrypt(text: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(bytes: ByteArray): String {
        require(bytes.size >= 28) { "Invalid conversation record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    @Synchronized fun append(request: String, answer: String) {
        require(request.isNotBlank() && request.length <= 8000 && answer.length <= 100_000)
        val stmt = writableDatabase.compileStatement(
            "INSERT INTO chat(created_at, request, response) VALUES(?, ?, ?)")
        stmt.use {
            it.bindLong(1, System.currentTimeMillis())
            it.bindBlob(2, encrypt(request))
            it.bindBlob(3, encrypt(answer))
            it.executeInsert()
        }
    }

    @Synchronized fun recent(limit: Int = 50): List<JarvisChatEntry> {
        require(limit in 1..200)
        val result = ArrayList<JarvisChatEntry>()
        readableDatabase.rawQuery(
            "SELECT id, created_at, request, response FROM chat ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(JarvisChatEntry(c.getLong(1),
                    decrypt(c.getBlob(2)), decrypt(c.getBlob(3)), c.getLong(0)))
            }
        }
        return result.asReversed()
    }

    /** Load older rows using a stable database cursor. New chat messages can be
     * appended concurrently without shifting the pagination window.
     * Only decrypted in memory on the user's own device.
     */
    @Synchronized fun before(idExclusive: Long, limit: Int = 40): List<JarvisChatEntry> {
        require(idExclusive > 0L && limit in 1..200)
        val result = ArrayList<JarvisChatEntry>()
        readableDatabase.rawQuery(
            "SELECT id, created_at, request, response FROM chat " +
                "WHERE id < ? ORDER BY id DESC LIMIT ?",
            arrayOf(idExclusive.toString(), limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(JarvisChatEntry(
                cursor.getLong(1), decrypt(cursor.getBlob(2)),
                decrypt(cursor.getBlob(3)), cursor.getLong(0)
            ))
        }
        return result.asReversed()
    }

    /** Search is fully offline: decrypt records locally, never upload their text. */
    @Synchronized fun search(term: String, limit: Int = 60): List<JarvisChatEntry> {
        require(limit in 1..200)
        val needle = term.trim()
        if (needle.isEmpty()) return recent(limit).asReversed()
        val result = ArrayList<JarvisChatEntry>()
        readableDatabase.rawQuery(
            "SELECT created_at, request, response FROM chat ORDER BY id DESC", null
        ).use { c ->
            while (c.moveToNext() && result.size < limit) {
                val question = decrypt(c.getBlob(1))
                val answer = decrypt(c.getBlob(2))
                if (question.contains(needle, ignoreCase = true) ||
                    answer.contains(needle, ignoreCase = true)) {
                    result.add(JarvisChatEntry(c.getLong(0), question, answer))
                }
            }
        }
        return result
    }

    @Synchronized fun count(): Long =
        android.database.DatabaseUtils.longForQuery(readableDatabase,
            "SELECT COUNT(*) FROM chat", null)
}
