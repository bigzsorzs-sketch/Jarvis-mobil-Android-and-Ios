package ai.jarvis.mobile

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class JarvisConversationContextInstrumentedTest {
    @Test fun onlyAiTurnsFromTheChosenChatRemainEligibleAfterReopening() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "context-${UUID.randomUUID()}.db"
        try {
            JarvisChatStore(context, database).use { store ->
                store.append("Remember Focus", "I will compare it.", "one", aiContext = true)
                store.append("Nézd meg a cukrom", "Helyi mérés: 6.1", "one")
                store.append("A different private conversation", "Another answer", "two", aiContext = true)
            }
            JarvisChatStore(context, database).use { store ->
                val eligible = store.recent(50, "one").filter { it.aiContext }
                assertEquals(1, eligible.size)
                assertEquals("Remember Focus", eligible.single().request)
                assertFalse(store.search("Helyi mérés").single().aiContext)
                assertEquals(1, store.recent(50, "two").size)
            }
        } finally { context.deleteDatabase(database) }
    }

    @Test fun versionTwoMigrationPreservesCiphertextAndKeepsLegacyRowsLocal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = "migration-${UUID.randomUUID()}.db"
        try {
            JarvisChatStore(context, database).use {
                it.append("Old private result", "Legacy answer", "old")
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(database).path, null,
                SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("CREATE TABLE old_chat (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, request BLOB NOT NULL, response BLOB NOT NULL, conversation_id TEXT NOT NULL DEFAULT 'legacy')")
                db.execSQL("INSERT INTO old_chat SELECT id, created_at, request, response, conversation_id FROM chat")
                db.execSQL("DROP TABLE chat")
                db.execSQL("ALTER TABLE old_chat RENAME TO chat")
                db.version = 2
            }
            JarvisChatStore(context, database).use { store ->
                val old = store.recent(50, "old").single()
                assertEquals("Legacy answer", old.answer)
                assertFalse(old.aiContext)
                store.append("New AI question", "New answer", "new", aiContext = true)
                assertTrue(store.recent(50, "new").single().aiContext)
            }
        } finally { context.deleteDatabase(database) }
    }
}
