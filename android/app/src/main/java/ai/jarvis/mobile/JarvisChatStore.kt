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
    val id: Long = 0L, val conversationId: String = "legacy",
    val aiContext: Boolean = false
)

internal data class JarvisChatSummary(val id: String, val title: String, val updatedAtMs: Long)

/**
 * Local-only chat history, encrypted row-by-row with a nonexportable device key.
 * The SQLite index stores no conversation plaintext, provider key or contact data.
 * No implicit backup or cross-device decrypt: migration needs a future explicit,
 * user-encrypted export and recovery passphrase, not the Android Keystore key.
 */
internal class JarvisChatStore(context: Context, databaseName: String = "jarvis_conversation_v1.db") : SQLiteOpenHelper(
    context.applicationContext, databaseName, null, 3
) {
    private val alias = "jarvis_mobile_conversation_aes_v1"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE chat (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, request BLOB NOT NULL, response BLOB NOT NULL, conversation_id TEXT NOT NULL DEFAULT 'legacy', ai_context INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_chat_when ON chat(created_at)")
        db.execSQL("CREATE INDEX idx_chat_conversation ON chat(conversation_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Earlier versions had one timeline, with no reliable session boundaries.
            // Preserve every ciphertext and expose that timeline as one older chat.
            db.execSQL("ALTER TABLE chat ADD COLUMN conversation_id TEXT NOT NULL DEFAULT 'legacy'")
            db.execSQL("CREATE INDEX idx_chat_conversation ON chat(conversation_id, id)")
        }
        if (oldVersion < 3) {
            // Legacy rows may contain device-private results. They are NOT AI context.
            db.execSQL("ALTER TABLE chat ADD COLUMN ai_context INTEGER NOT NULL DEFAULT 0")
        }
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

    @Synchronized fun append(request: String, answer: String, conversationId: String = "legacy", aiContext: Boolean = false) {
        require(request.isNotBlank() && request.length <= 8000 && answer.length <= JarvisNetworkSafety.MAX_AI_TEXT_CHARACTERS)
        require(conversationId.isNotBlank() && conversationId.length <= 64)
        val stmt = writableDatabase.compileStatement(
            "INSERT INTO chat(created_at, request, response, conversation_id, ai_context) VALUES(?, ?, ?, ?, ?)")
        stmt.use {
            it.bindLong(1, System.currentTimeMillis())
            it.bindBlob(2, encrypt(request))
            it.bindBlob(3, encrypt(answer))
            it.bindString(4, conversationId)
            it.bindLong(5, if (aiContext) 1L else 0L)
            it.executeInsert()
        }
    }

    @Synchronized fun recent(limit: Int = 50, conversationId: String? = null): List<JarvisChatEntry> {
        require(limit in 1..200)
        val result = ArrayList<JarvisChatEntry>()
        readableDatabase.rawQuery(
            "SELECT id, created_at, request, response, conversation_id, ai_context FROM chat " +
                (if (conversationId == null) "" else "WHERE conversation_id = ? ") +
                "ORDER BY id DESC LIMIT ?",
            if (conversationId == null) arrayOf(limit.toString()) else arrayOf(conversationId, limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(JarvisChatEntry(c.getLong(1),
                    decrypt(c.getBlob(2)), decrypt(c.getBlob(3)), c.getLong(0), c.getString(4), c.getInt(5) == 1))
            }
        }
        return result.asReversed()
    }

    /** Load older rows using a stable database cursor. New chat messages can be
     * appended concurrently without shifting the pagination window.
     * Only decrypted in memory on the user's own device.
     */
    @Synchronized fun before(idExclusive: Long, limit: Int = 40, conversationId: String? = null): List<JarvisChatEntry> {
        require(idExclusive > 0L && limit in 1..200)
        val result = ArrayList<JarvisChatEntry>()
        readableDatabase.rawQuery(
            "SELECT id, created_at, request, response, conversation_id, ai_context FROM chat " +
                "WHERE id < ? " + (if (conversationId == null) "" else "AND conversation_id = ? ") +
                "ORDER BY id DESC LIMIT ?",
            if (conversationId == null) arrayOf(idExclusive.toString(), limit.toString())
            else arrayOf(idExclusive.toString(), conversationId, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(JarvisChatEntry(
                cursor.getLong(1), decrypt(cursor.getBlob(2)),
                decrypt(cursor.getBlob(3)), cursor.getLong(0), cursor.getString(4), cursor.getInt(5) == 1
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
            "SELECT created_at, request, response, id, conversation_id, ai_context FROM chat ORDER BY id DESC", null
        ).use { c ->
            while (c.moveToNext() && result.size < limit) {
                val question = decrypt(c.getBlob(1))
                val answer = decrypt(c.getBlob(2))
                if (question.contains(needle, ignoreCase = true) ||
                    answer.contains(needle, ignoreCase = true)) {
                    result.add(JarvisChatEntry(c.getLong(0), question, answer, c.getLong(3), c.getString(4), c.getInt(5) == 1))
                }
            }
        }
        return result
    }

    /** Titles are derived from encrypted first messages, never stored in plaintext. */
    @Synchronized fun conversations(): List<JarvisChatSummary> {
        val result = ArrayList<JarvisChatSummary>()
        readableDatabase.rawQuery(
            "SELECT first.conversation_id, first.request, last.created_at FROM " +
                "(SELECT MIN(id) first_id, MAX(id) last_id FROM chat GROUP BY conversation_id) grouped_chats " +
                "JOIN chat first ON first.id = grouped_chats.first_id " +
                "JOIN chat last ON last.id = grouped_chats.last_id ORDER BY last.id DESC", null
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(JarvisChatSummary(cursor.getString(0),
                decrypt(cursor.getBlob(1)).replace(Regex("\\s+"), " ").trim().take(100), cursor.getLong(2)))
        }
        return result
    }

    /** Decrypt locally only after an explicit, password-protected backup request. */
    @Synchronized fun backupEntries(limit: Int): List<JarvisChatEntry> {
        require(limit in 1..10_000)
        require(count() <= limit) {
            "Túl sok beszélgetés a mentéshez; semmit nem mentettünk részlegesen."
        }
        val result = ArrayList<JarvisChatEntry>()
        readableDatabase.rawQuery(
            "SELECT created_at, request, response, conversation_id, ai_context FROM chat ORDER BY id ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(JarvisChatEntry(
                    createdAtMs = cursor.getLong(0),
                    request = decrypt(cursor.getBlob(1)),
                    answer = decrypt(cursor.getBlob(2)),
                    conversationId = cursor.getString(3),
                    aiContext = cursor.getInt(4) == 1
                ))
            }
        }
        return result
    }

    /** Append restored conversations atomically without deleting existing ones. */
    @Synchronized fun importBackupEntries(entries: List<JarvisChatEntry>, beforeCommit: () -> Unit = {}) {
        require(entries.size <= 10_000)
        entries.forEach {
            require(it.request.isNotBlank() && it.request.length <= 8000 &&
                it.answer.length <= JarvisNetworkSafety.MAX_AI_TEXT_CHARACTERS &&
                it.conversationId.isNotBlank() && it.conversationId.length <= 64 &&
                it.createdAtMs > 0)
        }
        val mappedIds = mutableMapOf<String, String>()
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (entry in entries) {
                val targetId = mappedIds.getOrPut(entry.conversationId) {
                    java.util.UUID.randomUUID().toString()
                }
                val values = android.content.ContentValues().apply {
                    put("created_at", entry.createdAtMs)
                    put("request", encrypt(entry.request))
                    put("response", encrypt(entry.answer))
                    put("conversation_id", targetId)
                    put("ai_context", if (entry.aiContext) 1 else 0)
                }
                db.insertOrThrow("chat", null, values)
            }
            // If this fails, SQLite rolls back imported chats as well.
            beforeCommit()
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized fun count(): Long =
        android.database.DatabaseUtils.longForQuery(readableDatabase,
            "SELECT COUNT(*) FROM chat", null)
}
