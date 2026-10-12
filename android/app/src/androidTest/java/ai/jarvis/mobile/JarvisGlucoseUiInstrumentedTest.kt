package ai.jarvis.mobile

import android.content.Context
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisGlucoseUiInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun glucoseDashboardNfcReportsAndEnglishLocaleAreAccessible() {
        val onboarding = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!onboarding.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
        composeRule.onNodeWithTag("jarvis-drawer-list").performScrollToIndex(
            JarvisToolCatalog.all.indexOfFirst { it.route == "Vércukor" })
        composeRule.onNodeWithText("Vércukor").performClick()
        composeRule.onNodeWithTag("jarvis-subpage-header").assert(hasText("Vércukor"))
        composeRule.onNodeWithText("NFC és LibreLink").performClick()
        composeRule.onNodeWithText("LibreLink megnyitása").assertExists()
        composeRule.onNodeWithText("Jelentések").performClick()
        composeRule.onNodeWithText("Nincs elegendő szenzoradat").assertExists()
        composeRule.onNodeWithTag("jarvis-language-switcher").performClick()
        composeRule.onNodeWithTag("jarvis-language-en-GB").performClick()
        composeRule.onNodeWithText("Reports").assertExists()
        composeRule.onNodeWithText("Not enough sensor data").assertExists()
        composeRule.runOnIdle {
            JarvisLanguageSettings.save(composeRule.activity, JarvisLanguage.HUNGARIAN)
        }
    }
}
