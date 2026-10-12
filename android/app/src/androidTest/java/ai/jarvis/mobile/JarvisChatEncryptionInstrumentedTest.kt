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
