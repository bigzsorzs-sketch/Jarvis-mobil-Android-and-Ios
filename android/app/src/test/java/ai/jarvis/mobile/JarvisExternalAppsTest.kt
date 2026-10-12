package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisExternalAppsTest {
    @Test fun searchesUseFileFormatsWithoutPrivateFileNames() {
        val query = JarvisExternalApps.viewerQuery("George-vercukor-lelet.pdf", "application/pdf")
        assertEquals("PDF reader", query)
        assertFalse(query.contains("George"))
        assertFalse(query.contains("vercukor"))
        assertEquals("EXE file viewer", JarvisExternalApps.viewerQuery("setup.EXE",
            "application/vnd.microsoft.portable-executable"))
        assertEquals("ABC file viewer", JarvisExternalApps.viewerQuery("document.abc",
            "application/octet-stream"))
        assertEquals("file viewer", JarvisExternalApps.viewerQuery("no-extension",
            "application/octet-stream"))
    }

    @Test fun localFileSuffixesPreserveFormatsWithoutAllowingPaths() {
        assertEquals("exe", JarvisAttachments.localExtension("setup.EXE"))
        assertEquals("docx", JarvisAttachments.localExtension("../../private/report.docx"))
        assertEquals("bin", JarvisAttachments.localExtension("name.ext/path"))
        assertEquals("bin", JarvisAttachments.localExtension("file.abcdefghijklmnopqrstuvwxyz"))
        assertEquals("bin", JarvisAttachments.localExtension("file."))
    }

    @Test fun oemNotesAppsArePreferredWhenAvailable() {
        assertEquals("com.miui.notes", JarvisExternalApps.notesPackages("Xiaomi").first())
        assertEquals("com.samsung.android.app.notes", JarvisExternalApps.notesPackages("SAMSUNG").first())
        assertTrue(JarvisExternalApps.notesPackages("Google").contains("com.google.android.keep"))
    }
}
