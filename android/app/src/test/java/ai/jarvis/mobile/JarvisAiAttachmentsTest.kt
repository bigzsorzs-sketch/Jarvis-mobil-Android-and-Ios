package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAiAttachmentsTest {
    @Test fun acceptOnlySupportedMultimodalMimeTypesForExplicitCloudSend() {
        for (mime in listOf("image/jpeg", "image/png", "image/webp", "image/gif",
            "application/pdf", "text/plain", "text/markdown",
            "text/csv", "application/json", "application/xml", "text/html",
            "video/mp4", "video/webm", "video/quicktime")) {
            assertTrue(mime, JarvisAiAttachments.acceptedMime(mime))
        }
        for (mime in listOf("application/zip", "application/vnd.microsoft.portable-executable",
            "video/x-matroska", "audio/mpeg", "image/heic", "application/octet-stream")) {
            assertFalse(mime, JarvisAiAttachments.acceptedMime(mime))
        }
    }
    @Test fun stagingLimitIsNotFalselyPresentedAsCloudUploadLimit() {
        assertEquals(20, JarvisAiAttachments.MAX_ITEMS)
        assertTrue(JarvisAiAttachments.MAX_SEND_BYTES < JarvisAttachments.MAX_TOTAL_BYTES)
        assertTrue(JarvisAiAttachments.MAX_FILE_BYTES < JarvisAiAttachments.MAX_SEND_BYTES)
    }

    @Test fun aiUploadPreflightRejectsUnsupportedFilesWithoutDeletingStagedItems() {
        fun item(name: String, mime: String, bytes: Long) =
            JarvisAttachment(name, name, java.io.File("/tmp/does-not-need-to-exist"), mime, bytes, mime.startsWith("image/"))
        assertNull(JarvisAiAttachments.rejectionReason(listOf(item("ok.pdf", "application/pdf", 500_000))))
        // Only bounded, explicitly selected still frames will be sent:
        // the original MP4 bytes and audio are not part of the AI payload.
        assertNull(JarvisAiAttachments.rejectionReason(listOf(
            item("clip.mp4", "video/mp4", 100_000))))
        assertNull(JarvisAiAttachments.rejectionReason(listOf(
            item("clip.webm", "video/webm", 120_000))))
        assertTrue(JarvisAiAttachments.rejectionReason(listOf(
            item("too_large.mp4", "video/mp4", 129L * 1024 * 1024)))!!
            .contains("128 MB"))
        assertTrue(JarvisAiAttachments.rejectionReason(listOf(
            item("a.mp4", "video/mp4", 100_000),
            item("b.mp4", "video/mp4", 100_000)))!!.contains("egy videó"))
        assertTrue(JarvisAiAttachments.rejectionReason(listOf(
            item("program.exe", "application/vnd.microsoft.portable-executable", 500_000)))!!.contains("nem küldhető"))
        assertTrue(JarvisAiAttachments.rejectionReason(listOf(
            item("large.pdf", "application/pdf", 6L * 1024 * 1024)))!!.contains("5 MB"))
        assertTrue(JarvisAiAttachments.rejectionReason(listOf(
            item("long.txt", "text/plain", 300_000)))!!.contains("256 KB"))
        assertTrue(JarvisAiAttachments.rejectionReason(
            (0..4).map { item("picture$it.jpg", "image/jpeg", 5L * 1024 * 1024) }
        )!!.contains("20 MB"))
    }
}
