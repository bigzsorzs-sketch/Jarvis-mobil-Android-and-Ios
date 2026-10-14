package ai.jarvis.mobile

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisLanguageSwitchInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun everyFlagChangesHomeDrawerAndSubpageWithoutChangingRouteId() {
        val prefs = composeRule.activity.getSharedPreferences("jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        try {
            for (language in JarvisLanguage.entries) {
                composeRule.onNodeWithTag("jarvis-language-switcher").performClick()
                composeRule.onNodeWithTag("jarvis-language-" + language.tag).performClick()
                composeRule.onNodeWithText(
                    JarvisTranslations.text("Kérdezd meg Jarvist", language)).assertExists()
                assertEquals(language, JarvisLanguageSettings.load(composeRule.activity))
                composeRule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
                composeRule.onNodeWithTag("jarvis-drawer-settings").performClick()
                composeRule.onNodeWithTag("jarvis-subpage-header").assert(
                    hasText(JarvisTranslations.text("Beállítások", language)))
                composeRule.onNodeWithTag("jarvis-subpage-home-button").performClick()
            }
        } finally {
            composeRule.runOnIdle {
                JarvisLanguageSettings.save(composeRule.activity, JarvisLanguage.HUNGARIAN)
            }
        }
    }

    @Test fun languageFlagTranslatesHomeDrawerAndNestedSettingsAndPersists() {
        val prefs = composeRule.activity.getSharedPreferences("jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        try {
            composeRule.onNodeWithTag("jarvis-language-switcher").performClick()
            composeRule.onNodeWithTag("jarvis-language-en-GB").performClick()
            composeRule.onNodeWithText("Ask Jarvis").assertExists()
            composeRule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
            composeRule.onNodeWithTag("jarvis-drawer-settings").performClick()
            composeRule.onNodeWithTag("jarvis-subpage-header").assert(hasText("Settings"))
            composeRule.onNodeWithText("General").assertExists()
            assertEquals("en-GB", JarvisLanguageSettings.load(composeRule.activity).tag)
        } finally {
            // Restore the default so other legacy Hungarian command tests do not depend on order.
            composeRule.runOnIdle {
                JarvisLanguageSettings.save(composeRule.activity, JarvisLanguage.HUNGARIAN)
            }
        }
    }
}
