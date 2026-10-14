package ai.jarvis.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class JarvisConversationStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun interleavedConversationsStaySeparateAndSearchPreservesTheirIds() {
        val name = "conversation-test-${UUID.randomUUID()}.db"
        try {
            JarvisChatStore(context, name).use { store ->
                store.append("First private title", "one", "a")
                store.append("Second title", "two", "b")
                store.append("Follow-up", "three", "a")
                assertEquals(listOf("a", "b"), store.conversations().map { it.id })
                assertEquals("First private title", store.conversations().first().title)
                val newest = store.recent(1, "a").single()
                assertEquals("Follow-up", newest.request)
                assertEquals(listOf("First private title"), store.before(newest.id, 50, "a").map { it.request })
                assertEquals("b", store.search("Second").single().conversationId)
                assertTrue(store.recent(50, "empty-new-chat").isEmpty())
            }
            JarvisChatStore(context, name).use { reopened ->
                assertEquals(2, reopened.conversations().size)
                assertEquals(2, reopened.recent(50, "a").size)
            }
            assertFalse(context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1)
                .contains("First private title"))
        } finally { context.deleteDatabase(name) }
    }

    @Test fun versionOneMigrationPreservesEveryEncryptedRow() {
        val name = "migration-test-${UUID.randomUUID()}.db"
        try {
            JarvisChatStore(context, name).use { store ->
                store.append("old question", "old answer")
                store.append("second question", "second answer")
            }
            val path = context.getDatabasePath(name).path
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                // Reconstruct the exact v1 schema using real Keystore ciphertext.
                db.execSQL("CREATE TABLE old_chat (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, request BLOB NOT NULL, response BLOB NOT NULL)")
                db.execSQL("INSERT INTO old_chat SELECT id, created_at, request, response FROM chat")
                db.execSQL("DROP TABLE chat")
                db.execSQL("ALTER TABLE old_chat RENAME TO chat")
                db.execSQL("CREATE INDEX idx_chat_when ON chat(created_at)")
                db.version = 1
            }
            JarvisChatStore(context, name).use { migrated ->
                assertEquals(2L, migrated.count())
                assertEquals(listOf("old answer", "second answer"), migrated.recent(50, "legacy").map { it.answer })
                assertEquals(listOf("legacy"), migrated.conversations().map { it.id })
                migrated.append("new question", "new answer", "new")
                assertEquals(2, migrated.recent(50, "legacy").size)
                assertEquals(1, migrated.recent(50, "new").size)
            }
        } finally { context.deleteDatabase(name) }
    }
}
