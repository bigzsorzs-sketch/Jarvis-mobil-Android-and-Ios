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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisBillingUiInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun billingTabsAndAllSevenLanguagesAreLive() {
        val onboarding = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!onboarding.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
        val currentLocale = JarvisLanguageSettings.load(composeRule.activity)
        composeRule.onNodeWithText(JarvisTranslations.text("Számlázás", currentLocale)).performClick()
        composeRule.onNodeWithTag("billing-heading").assertExists()
        for (language in JarvisLanguage.entries) {
            composeRule.onNodeWithTag("jarvis-language-switcher").performClick()
            composeRule.onNodeWithTag("jarvis-language-" + language.tag).performClick()
            composeRule.onNodeWithTag("billing-heading")
                .assert(hasText(BillingStrings.get(language, "invoices")))
            composeRule.onNodeWithTag("billing-tab-clients").performScrollTo().performClick()
            composeRule.onNodeWithText(BillingStrings.get(language, "newClient")).assertExists()
            composeRule.onNodeWithTag("billing-tab-settings").performScrollTo().performClick()
            composeRule.onNodeWithText(BillingStrings.get(language, "smtp")).assertExists()
            composeRule.onNodeWithTag("billing-tab-invoices").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("jarvis-language-switcher").performClick()
        composeRule.onNodeWithTag("jarvis-language-hu-HU").performClick()
    }
}
