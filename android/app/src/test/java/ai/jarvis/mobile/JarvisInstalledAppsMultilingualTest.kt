package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test

class JarvisInstalledAppsMultilingualTest {
    private val spotify = JarvisInstalledApp("Spotify", "com.spotify.music", "Main")
    private val youtube = JarvisInstalledApp("YouTube", "com.google.android.youtube", "Main")
    private val whatsapp = JarvisInstalledApp("WhatsApp", "com.whatsapp", "Main")

    @Test fun sevenLanguagesRecognizeOnlyExplicitExactAppNames() {
        val requests = listOf(
            "Nyisd meg a Spotifyt",
            "Open the Spotify app",
            "Öffne die Spotify-App",
            "Ouvre l’application Spotify",
            "Abre la aplicación Spotify",
            "Apri l'app Spotify",
            "Otwórz aplikację Spotify"
        )
        for (request in requests) {
            assertTrue(request, JarvisInstalledApps.choose(
                request, listOf(spotify, youtube, whatsapp)) is AppSelection.Found)
            val app = JarvisInstalledApps.choose(request, listOf(spotify)) as AppSelection.Found
            assertEquals(request, spotify.packageName, app.app.packageName)
        }
        assertTrue(JarvisInstalledApps.choose(
            "Jarvis, kérlek nyisd meg a YouTube-ot", listOf(youtube)) is AppSelection.Found)
        assertTrue(JarvisInstalledApps.choose(
            "Uruchom WhatsApp", listOf(whatsapp)) is AppSelection.Found)
        assertTrue(JarvisInstalledApps.choose(
            "Starte Spotify", listOf(spotify)) is AppSelection.Found)
        assertTrue(JarvisInstalledApps.choose(
            "Lance WhatsApp", listOf(whatsapp)) is AppSelection.Found)
        assertTrue(JarvisInstalledApps.choose(
            "Avvia Spotify", listOf(spotify)) is AppSelection.Found)
    }

    @Test fun negativeCompoundAndQuestionPromptsDoNotLaunch() {
        val unsafe = listOf(
            "Ne nyisd meg a Spotifyt",
            "Do not open Spotify", "Don't open Spotify",
            "Öffne Spotify nicht", "N'ouvre pas Spotify",
            "No abras Spotify", "Non aprire Spotify", "Nie otwieraj Spotify",
            "Open Spotify and send an email", "Ouvre Spotify puis supprime les fichiers",
            "Öffne Spotify und sende Nachrichten", "Nyisd meg a Spotifyt majd töröld a fájlt",
            "Can you open Spotify?", "Czy możesz otworzyć Spotify?"
        )
        for (raw in unsafe) {
            assertEquals(raw, AppSelection.NotARequest,
                JarvisInstalledApps.choose(raw, listOf(spotify)))
        }
    }

    @Test fun noSubstringFallbackForSimilarAppNames() {
        val apps = listOf(youtube,
            JarvisInstalledApp("YouTube Music", "com.google.android.apps.youtube.music", "Main"))
        assertEquals(youtube.packageName,
            (JarvisInstalledApps.choose("Open YouTube", apps) as AppSelection.Found).app.packageName)
        assertTrue(JarvisInstalledApps.choose("Open You", apps) is AppSelection.Missing)
    }
}
