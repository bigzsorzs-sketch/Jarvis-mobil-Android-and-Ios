package ai.jarvis.mobile

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Check the request from the device screenshot with actual Compose UI. */
@RunWith(AndroidJUnit4::class)
class JarvisHomeChatUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun repliesAreVisibleWithoutInlineReadAloudButton() {
        val prefs = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("jarvis-home-input").performTextInput("szia")
        composeRule.onNodeWithTag("jarvis-home-send").performClick()
        composeRule.onNodeWithText("Szia! Miben segíthetek?").assertExists()
        composeRule.onNodeWithText("Felolvasás").assertDoesNotExist()
    }
}
