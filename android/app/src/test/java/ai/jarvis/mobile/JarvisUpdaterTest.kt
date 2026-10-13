package ai.jarvis.mobile

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class JarvisUpdaterTest {
    private fun release(tag: String, url: String, digest: String = "a".repeat(64)) =
        JSONObject().put("tag_name", tag).put("draft", false).put("assets",
            JSONArray().put(JSONObject().put("name", "Jarvis-Mobile-Android-test.apk")
                .put("browser_download_url", url).put("size", 10_000_000)
                .put("digest", "sha256:" + digest)))

    private fun asset(tag: String): String =
        "https://github.com/bigzsorzs-sketch/Jarvis-mobil-Android-and-Ios/releases/download/" +
            tag + "/Jarvis-Mobile-Android-test.apk"

    @Test fun comparesVersionNumbersAndFiltersUntrustedAssets() {
        val a = "v0.2.17-dev-test.1"
        val b = "v0.2.18-signed-test.1"
        val releases = JSONArray()
            .put(release(a, asset(a))).put(release(b, asset(b)))
            .put(release("v9.9.9-dev-test.1", "https://attacker.invalid/fake.apk"))
        val selected = JarvisUpdateRules.choose(releases.toString(), "0.2.16-dev")
        assertEquals(b, selected?.tag)
        assertNull(JarvisUpdateRules.choose(releases.toString(), "0.2.19-dev"))
        assertNotNull(JarvisUpdateRules.version("v0.2.17-dev-test.1"))
        assertNull(JarvisUpdateRules.version("v0.2.17-other.1"))
    }

    @Test fun rejectsWrongDomainDowngradeAndMissingDigest() {
        val tag = "v0.2.17-dev-test.1"
        assertTrue(JarvisUpdateRules.allowedDownloadUrl(asset(tag), tag))
        assertFalse(JarvisUpdateRules.allowedDownloadUrl("http://github.com/file.apk", tag))
        assertFalse(JarvisUpdateRules.allowedDownloadUrl("https://evil.github.com/file.apk", tag))
        assertFalse(JarvisUpdateRules.allowedDownloadUrl(asset(tag) + "?token=untrusted", tag))
        assertFalse(JarvisUpdateRules.allowedDownloadUrl(
            asset(tag).replace("0.2.17", "0.2.18"), tag))
        assertFalse(JarvisUpdateRules.allowedRedirect("http://release-assets.githubusercontent.com/xx"))
        assertTrue(JarvisUpdateRules.allowedRedirect("https://release-assets.githubusercontent.com/xx"))
        assertNull(JarvisUpdateRules.digest("sha256:nope"))
        assertEquals("a".repeat(64), JarvisUpdateRules.digest("sha256:" + "a".repeat(64)))
    }

    @Test fun acceptsOnlyExactlyMatchingSigners() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 4)
        assertTrue(JarvisUpdateRules.sameSigner(listOf(a), listOf(a)))
        assertFalse(JarvisUpdateRules.sameSigner(listOf(a), listOf(b)))
        assertFalse(JarvisUpdateRules.sameSigner(listOf(a), emptyList()))
        assertFalse(JarvisUpdateRules.sameSigner(emptyList(), emptyList()))
    }

    @Test fun spokenUpdateCommandsInSevenLanguagesAndNoSilentInstall() {
        val commands = listOf(
            "Jarvis, ellenőrizd a frissítéseket",
            "Jarvis check for updates",
            "Jarvis prüfe Updates",
            "Jarvis vérifie les mises à jour",
            "Jarvis comprobar actualizaciones",
            "Jarvis controlla aggiornamenti",
            "Jarvis sprawdź aktualizacje"
        )
        commands.forEach { assertTrue(it, JarvisUpdateVoice.isRequest(it)) }
        assertTrue(JarvisUpdateVoice.shouldInstall("Jarvis, frissítsd magad"))
        assertTrue(JarvisUpdateVoice.shouldInstall("Jarvis install update"))
        assertFalse(JarvisUpdateVoice.shouldInstall("Jarvis, ellenőrizd a frissítéseket"))
        assertFalse(JarvisUpdateVoice.isRequest("Mennyibe kerül az autó?"))
    }

    @Test fun allLabelsLocalizedWithoutGaps() {
        assertEquals(7, JarvisLanguage.entries.size)
        assertTrue(JarvisUpdateTexts.valid())
        JarvisLanguage.entries.forEach { language ->
            listOf("heading", "check", "download", "signatureMismatch",
                "installerPermission", "voiceCheck", "voiceInstall", "explanation"
            ).forEach { key -> assertTrue(JarvisUpdateTexts.get(language, key).isNotBlank()) }
        }
    }
}
