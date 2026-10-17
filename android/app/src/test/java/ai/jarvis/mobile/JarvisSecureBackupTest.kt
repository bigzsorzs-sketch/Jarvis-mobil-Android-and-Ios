package ai.jarvis.mobile

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JarvisSecureBackupTest {
    @Test fun encryptedArchiveRoundTripsWithPassword() {
        val password = "test-password-unique-2026".toCharArray()
        val raw = """{"version":1,"fields":{"notes":"[]"},"chats":[]}"""
            .toByteArray(Charsets.UTF_8)
        val encrypted = JarvisSecureBackup.encrypt(raw, password)
        assertFalse(raw.contentEquals(encrypted))
        assertArrayEquals(raw, JarvisSecureBackup.decrypt(encrypted, password))
        assertFalse(encrypted.contentEquals(JarvisSecureBackup.encrypt(raw, password)))
    }

    @Test fun wrongPasswordOrModifiedCiphertextMustFailClosed() {
        val encrypted = JarvisSecureBackup.encrypt(
            "health notes, private conversation".toByteArray(),
            "test-password-unique-2026".toCharArray()
        )
        assertThrows(Exception::class.java) {
            JarvisSecureBackup.decrypt(encrypted, "wrong-password-long".toCharArray())
        }
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) {
            JarvisSecureBackup.decrypt(tampered, "test-password-unique-2026".toCharArray())
        }
    }

    @Test fun credentialsCanNeverAppearInBackupAllowlist() {
        val fields = JarvisSecureBackup.DATA_FIELDS
        assertTrue(fields.containsAll(listOf("readings", "notes", "billing_v1")))
        assertFalse(fields.any {
            it.contains("password") || it.contains("token") ||
                it.contains("api") || it.contains("secret") || it.contains("key")
        })
    }

    @Test fun previouslyCreatedLargeNotesArchiveCanBeRestored() {
        val password = "test-password-unique-2026".toCharArray()
        val notes = "n".repeat(2_100_000)
        val plaintext = JSONObject()
            .put("version", 1)
            .put("fields", JSONObject().put("notes", notes))
            .put("chats", JSONArray())
            .toString().toByteArray(Charsets.UTF_8)
        val archive = JarvisSecureBackup.encrypt(plaintext, password)
        assertTrue(archive.size <= JarvisSecureBackup.MAX_ARCHIVE)
        val recovered = JarvisSecureBackup.inspect(archive, password)
        assertEquals(notes, recovered.fields["notes"])
        assertTrue(recovered.conversations.isEmpty())
    }

    @Test fun malformedArchiveNeverReachesRestore() {
        val password = "test-password-unique-2026".toCharArray()
        val plaintext = JSONObject().put("version", 1)
            .put("fields", JSONObject().put("ai_gateway_access_token", "not-allowed"))
            .put("chats", JSONArray()).toString().toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            JarvisSecureBackup.inspect(JarvisSecureBackup.encrypt(plaintext, password), password)
        }
    }

    @Test fun shortBackupPasswordRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            JarvisSecureBackup.encrypt("private".toByteArray(), "short".toCharArray())
        }
    }
}
