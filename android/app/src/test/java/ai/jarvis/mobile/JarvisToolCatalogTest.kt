package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisToolCatalogTest {
    @Test fun allMenuLabelsAreUniqueAndRoutesNonEmpty() {
        assertEquals(JarvisToolCatalog.all.size, JarvisToolCatalog.all.map { it.name }.distinct().size)
        assertTrue(JarvisToolCatalog.all.all { it.route.isNotBlank() })
    }
    @Test fun keyFeaturesRemainReachableFromTools() {
        val routes = JarvisToolCatalog.all.map { it.route }.toSet()
        assertTrue(routes.containsAll(setOf("Főoldal", "Vércukor", "Számlák",
            "Üzenetek", "Ismerősök", "Email", "Navigáció", "Beállítások", "Jegyzetek")))
        assertTrue(routes.intersect(setOf("Chat", "Térkép", "Fájlok", "Rendszerállapot")).isEmpty())
    }
}
