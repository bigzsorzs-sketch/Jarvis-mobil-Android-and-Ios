package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAiAttachmentsTest {
    @Test fun acceptOnlySupportedMultimodalMimeTypesForExplicitCloudSend() {
        for (mime in listOf("image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf", "text/plain", "text/markdown",
            "text/csv", "application/json", "application/xml", "text/html")) {
            assertTrue(mime, JarvisAiAttachments.acceptedMime(mime))
        }
        for (mime in listOf("application/zip", "application/vnd.microsoft.portable-executable",
            "video/mp4", "audio/mpeg", "image/heic", "application/octet-stream")) {
            assertFalse(mime, JarvisAiAttachments.acceptedMime(mime))
        }
    }
    @Test fun stagingLimitIsNotFalselyPresentedAsCloudUploadLimit() {
        assertEquals(20, JarvisAiAttachments.MAX_ITEMS)
        assertTrue(JarvisAiAttachments.MAX_SEND_BYTES < JarvisAttachments.MAX_TOTAL_BYTES)
        assertTrue(JarvisAiAttachments.MAX_FILE_BYTES < JarvisAiAttachments.MAX_SEND_BYTES)
    }
}
