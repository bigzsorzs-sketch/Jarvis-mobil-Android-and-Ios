package ai.jarvis.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** On-device smoke: actual Android Keystore, SQLite and offline search. */
@RunWith(AndroidJUnit4::class)
class JarvisChatEncryptionInstrumentedTest {
    @Test fun olderConversationPagesAreStableAndChronological() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = JarvisChatStore(context)
        val prefix = "older-chat-" + UUID.randomUUID()
        for (i in 1..6) store.append("$" + "prefix-$" + "i", "válasz " + i)
        val latest = store.recent(3)
        assertEquals(3, latest.size)
        assertTrue(latest[0].id < latest[1].id)
        assertTrue(latest[1].id < latest[2].id)
        val older = store.before(latest.first().id, 3)
        assertEquals(3, older.size)
        assertTrue(older.last().id < latest.first().id)
        assertTrue(older[0].id < older[1].id)
        assertTrue(older[1].id < older[2].id)
        assertEquals(emptyList<JarvisChatEntry>(), store.before(older.first().id, 3)
            .filter { it.request.startsWith(prefix) })
        store.close()
    }

    @Test fun chatSurvivesReopeningDatabaseWithoutPlaintextOnDisk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val marker = "jarvis-private-" + UUID.randomUUID().toString()
        val store = JarvisChatStore(context)
        store.append(marker, "Jarvis-válasz a telefonon")
        assertTrue(store.search(marker).any { it.request == marker })
        assertTrue(store.count() > 0L)
        store.close()
        val reopened = JarvisChatStore(context)
        assertTrue(reopened.search(marker).any { it.answer == "Jarvis-válasz a telefonon" })
        reopened.close()
        val rawBytes = context.getDatabasePath("jarvis_conversation_v1.db").readBytes()
        assertFalse("Conversation plaintext must not be written to SQLite",
            rawBytes.toString(Charsets.ISO_8859_1).contains(marker))
    }
}
