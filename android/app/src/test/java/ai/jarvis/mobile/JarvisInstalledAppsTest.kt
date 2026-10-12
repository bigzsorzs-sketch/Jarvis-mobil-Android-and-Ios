package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisInstalledAppsTest {
    private val apps = listOf(
        JarvisInstalledApp("YouTube", "com.google.android.youtube", "Main"),
        JarvisInstalledApp("Spotify", "com.spotify.music", "Launcher"),
        JarvisInstalledApp("WhatsApp", "com.whatsapp", "Main"),
        JarvisInstalledApp("YouTube Music", "com.google.android.music", "Main")
    )

    @Test fun recognizesOnlyOneUserNamedApplication() {
        assertEquals("youtube ot", JarvisInstalledApps.requestedLabel("Jarvis, kérlek nyisd meg a YouTube-ot"))
        assertEquals("spotifyt", JarvisInstalledApps.requestedLabel("Nyisd meg a Spotifyt"))
        assertEquals("whatsapp", JarvisInstalledApps.requestedLabel("Open WhatsApp"))
        assertNull(JarvisInstalledApps.requestedLabel("Mit tudsz a YouTube-ról?"))
        assertNull(JarvisInstalledApps.requestedLabel("Ne nyisd meg a YouTube-ot"))
        assertNull(JarvisInstalledApps.requestedLabel("Nyisd meg a YouTube-ot és hívj fel valakit"))
        assertNull(JarvisInstalledApps.requestedLabel("Nyisd meg a YouTube-ot majd küldd el"))
        assertNull(JarvisInstalledApps.requestedLabel("Nyisd meg a bank appot?"))
    }

    @Test fun matchesOnlyTheExactFullLabelIncludingHungarianCaseSuffix() {
        assertTrue(JarvisInstalledApps.matchesLabel("youtube ot", "YouTube"))
        assertTrue(JarvisInstalledApps.matchesLabel("spotifyt", "Spotify"))
        assertFalse(JarvisInstalledApps.matchesLabel("youtube ot", "YouTube Music"))
        assertFalse(JarvisInstalledApps.matchesLabel("you", "YouTube"))
        assertFalse(JarvisInstalledApps.matchesLabel("spotify hack", "Spotify"))
    }

    @Test fun neverMakesUpMissingAppsAndFailsClosedOnNameCollisions() {
        val ready = JarvisInstalledApps.choose("Nyisd meg a YouTube-ot", apps)
        assertTrue(ready is AppSelection.Found)
        assertEquals("com.google.android.youtube", (ready as AppSelection.Found).app.packageName)
        assertTrue(JarvisInstalledApps.choose("Nyisd meg a Netflixet", apps) is AppSelection.Missing)
        assertEquals(AppSelection.NotARequest,
            JarvisInstalledApps.choose("Tudsz YouTube videót keresni?", apps))
        val twins = apps + JarvisInstalledApp("YouTube", "example.fake.youtube", "Other")
        assertTrue(JarvisInstalledApps.choose("Open YouTube", twins) is AppSelection.Ambiguous)
    }

    @Test fun noModelProvidedPackageCanOverrideOriginalRequest() {
        assertTrue(JarvisInstalledApps.choose("Nyisd meg a Spotifyt", apps) is AppSelection.Found)
        assertFalse(JarvisInstalledApps.choose("Nyisd meg a Spotifyt", apps) ==
            AppSelection.Found(apps.first()))
    }
}
