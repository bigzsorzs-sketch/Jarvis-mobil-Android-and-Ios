package ai.jarvis.mobile

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisSharedCommandsInstrumentedTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun ready() {
        val prefs = ui.activity.getSharedPreferences("jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false))
            ui.onNodeWithText("Folytatás").performScrollTo().performClick()
        ui.runOnIdle {
            JarvisLanguageSettings.save(ui.activity, JarvisLanguage.HUNGARIAN)
            JarvisSpeechLanguageEvents.detected = JarvisLanguage.HUNGARIAN
        }
        ui.waitForIdle()
    }
    private fun voice(command: String): String {
        var answer: String? = null
        ui.runOnIdle {
            assertTrue("Foreground voice handler must be registered", JarvisForegroundCommands.submit(command) { answer = it })
        }
        ui.waitUntil(5_000) { answer != null }
        ui.waitForIdle()
        return answer!!
    }
    @Test fun spokenFormEditAndTypedSaveUseTheActualNotesUiAndPersist() {
        ready()
        voice("Nyisd meg a Jegyzetek oldalt")
        voice("Írd az Új jegyzet mezőbe: Egységes hangos és írott teszt")
        ui.onNodeWithText("Egységes hangos és írott teszt").assertExists()
        ui.onNodeWithTag("jarvis-command-panel").performClick()
        ui.onNodeWithTag("jarvis-page-command-input").performTextInput("Nyomd meg a Mentés gombot")
        ui.onNodeWithTag("jarvis-page-command-input").performImeAction()
        ui.waitUntil(5_000) {
            SecurePrefs(ui.activity).read("notes", "[]").contains("Egységes hangos és írott teszt")
        }
        ui.onNodeWithText("Bezárás").performClick()
        voice("vissza")
        voice("Nyisd meg a Jegyzetek oldalt")
        ui.onNodeWithText("Egységes hangos és írott teszt").assertExists()
    }
    @Test fun voiceCanOperateBillingTabsAndClearStaleCallAcrossInputModes() {
        ready()
        ui.onNodeWithTag("jarvis-home-input").performTextInput("Hívd fel a telefonszámot")
        ui.onNodeWithTag("jarvis-home-input").performImeAction()
        voice("Nyisd meg a Számlázás oldalt")
        val clients = BillingStrings.get(JarvisLanguage.HUNGARIAN, "clients")
        voice("Nyomd meg az első $clients gombot")
        voice("Írd az " + BillingStrings.get(JarvisLanguage.HUNGARIAN, "customer") + " mezőbe: Parancs Teszt")
        ui.onNodeWithTag("billing-client-name").assertTextContains("Parancs Teszt")
        assertFalse(voice("sorold fel az oldal vezérlőit").contains("telefonszámot hívjam"))
    }
    @Test fun everyPageUsesTheSameActualVoiceExecutor() {
        ready()
        // Native phone-app routes such as Calendar leave Jarvis by design;
        // UI page assertions apply only to screens rendered by Jarvis Compose.
        for (tool in (JarvisToolCatalog.all + JarvisToolCatalog.subpages)
            .filter { JarvisToolCatalog.phoneAppFor(it.route) == null }) {
            voice("Nyisd meg a ${tool.name} oldalt")
            if (tool.route == "Főoldal") ui.onNodeWithTag("jarvis-home-input").assertExists()
            else ui.onNodeWithTag("jarvis-subpage-header").assertTextContains(tool.route)
        }
    }
}
