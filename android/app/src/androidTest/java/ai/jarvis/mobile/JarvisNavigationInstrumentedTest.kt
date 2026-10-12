package ai.jarvis.mobile

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Compose navigation smoke test, not proof of physical phone parity. */
@RunWith(AndroidJUnit4::class)
class JarvisNavigationInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun homeMenusAndTwoNewPagesCanBeOpened() {
        // First-run permissions are optional: never grant access in a UI test.
        val prefs = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performClick()
        }
        composeRule.onNodeWithText("Jarvis").assertExists().performClick()
        composeRule.onNodeWithText("Alkalmazások").assertExists().performClick()
        composeRule.onNodeWithText("Telepített alkalmazások").assertExists()
        // Header opens the same drawer without a duplicate floating hamburger.
        composeRule.onNodeWithText("Alkalmazások").performClick()
        composeRule.onNodeWithText("Előzmények").assertExists().performClick()
        composeRule.onNodeWithText("Helyi beszélgetések").assertExists()
        composeRule.onNodeWithText("Keresés az előzményekben").assertExists()
    }
}
