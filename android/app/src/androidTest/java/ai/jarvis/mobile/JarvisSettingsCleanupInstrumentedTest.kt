package ai.jarvis.mobile

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisSettingsCleanupInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun legacyAssistantAndAiKeyTilesAreHiddenButConsentRemainsAccessible() {
        val prefs = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        try {
            composeRule.onNodeWithTag("jarvis-language-switcher").performClick()
            composeRule.onNodeWithTag("jarvis-language-en-GB").performClick()
            composeRule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
            composeRule.onNodeWithTag("jarvis-drawer-settings").performClick()

            composeRule.onNodeWithText("AI brain").assertDoesNotExist()
            composeRule.onNodeWithText("Default assistant").assertDoesNotExist()
            composeRule.onNodeWithText("AI privacy and access").assertExists()
            composeRule.onNodeWithText("Smart Home").assertExists()
            composeRule.onNodeWithTag("jarvis-update-check").assertExists()

            // Consent withdrawal/connection recovery must stay reachable,
            // even though the legacy top-level AI-key setup tile is hidden.
            composeRule.onNodeWithText("AI privacy and access")
                .performScrollTo().performClick()
            composeRule.onNodeWithTag("jarvis-subpage-header").assert(hasText("AI brain"))
        } finally {
            composeRule.runOnIdle {
                JarvisLanguageSettings.save(composeRule.activity, JarvisLanguage.HUNGARIAN)
            }
        }
    }
}
