package ai.jarvis.mobile

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisUpdateUiInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun settingsUpdateButtonAndLanguageSwitchesDoNotStartInstallation() {
        val prefs = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("jarvis-home-input")
            .performTextInput("Nyisd meg a Beállítások oldalt")
        composeRule.onNodeWithTag("jarvis-home-input").performImeAction()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("jarvis-update-check"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("jarvis-update-check").assertExists()
        // No installed APK is touched unless the user explicitly downloads it.
        composeRule.onNodeWithTag("jarvis-update-install").assertDoesNotExist()
        JarvisLanguage.entries.forEach { language ->
            composeRule.onNodeWithTag("jarvis-language-switcher").performClick()
            composeRule.onNodeWithTag("jarvis-language-" + language.tag).performClick()
            composeRule.onNodeWithText(JarvisUpdateTexts.get(language, "check")).assertExists()
        }
    }
}
