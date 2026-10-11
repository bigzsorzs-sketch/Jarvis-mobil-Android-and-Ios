package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAttachmentsTest {
    @Test fun twentyImageHardLimit() {
        assertTrue(JarvisAttachments.withinLimits(19, 0, true, 1024))
        assertFalse(JarvisAttachments.withinLimits(20, 0, true, 1024))
        assertTrue(JarvisAttachments.withinLimits(20, 0, false, 1024))
    }
    @Test fun commonDocumentsArchivesAndMediaAreRecognized() {
        val samples = mapOf(
            "report.pdf" to "application/pdf",
            "contract.docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "table.xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "slides.pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "names.csv" to "text/csv",
            "data.json" to "application/json",
            "book.epub" to "application/epub+zip",
            "photos.zip" to "application/zip",
            "archive.rar" to "application/vnd.rar",
            "archive.7z" to "application/x-7z-compressed",
            "song.mp3" to "audio/mpeg",
            "recording.m4a" to "audio/mp4",
            "movie.mp4" to "video/mp4",
            "clip.mkv" to "video/x-matroska"
        )
        samples.forEach { (name, expected) ->
            assertEquals(expected, JarvisAttachments.determineType(name,
                "application/octet-stream").first)
        }
        assertEquals("application/x-custom",
            JarvisAttachments.determineType("custom.myformat",
                "application/x-custom").first)
    }
    @Test fun imagesCountEvenThroughGenericFilesPicker() {
        val images = listOf("IMG.HEIC", "photo.jpg", "diagram.png", "picture.webp",
            "animation.gif", "scan.TIFF", "drawing.svg")
        images.forEach { name ->
            assertTrue(name, JarvisAttachments.determineType(name,
                "application/octet-stream").second)
        }
        assertTrue(JarvisAttachments.determineType("any_name",
            "image/png").second)
        assertFalse(JarvisAttachments.determineType("report.pdf",
            "application/octet-stream").second)
    }
    @Test fun aggregateOneGibibyteHardLimit() {
        val limit = JarvisAttachments.MAX_TOTAL_BYTES
        assertTrue(JarvisAttachments.withinLimits(1, limit - 1, false, 1))
        assertFalse(JarvisAttachments.withinLimits(1, limit - 1, false, 2))
        assertFalse(JarvisAttachments.withinLimits(0, 0, false, limit + 1))
        assertFalse(JarvisAttachments.withinLimits(0, 0, true, 0))
    }
}
