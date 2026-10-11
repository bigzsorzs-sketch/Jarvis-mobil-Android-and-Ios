package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisAttachmentsTest {
    @Test fun twentyImageHardLimit() {
        assertTrue(JarvisAttachments.withinLimits(19, 0, true, 1024))
        assertFalse(JarvisAttachments.withinLimits(20, 0, true, 1024))
        assertTrue(JarvisAttachments.withinLimits(20, 0, false, 1024))
    }
    @Test fun aggregateOneGibibyteHardLimit() {
        val limit = JarvisAttachments.MAX_TOTAL_BYTES
        assertTrue(JarvisAttachments.withinLimits(1, limit - 1, false, 1))
        assertFalse(JarvisAttachments.withinLimits(1, limit - 1, false, 2))
        assertFalse(JarvisAttachments.withinLimits(0, 0, false, limit + 1))
        assertFalse(JarvisAttachments.withinLimits(0, 0, true, 0))
    }
}
