package ai.jarvis.mobile

import android.content.Context
import androidx.compose.ui.test.assertIsNotEnabled
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
class JarvisBackupUiInstrumentedTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun backupAndLogoutRequireExplicitUserConfirmation() {
        val prefs = ui.activity.getSharedPreferences("jarvis_onboarding_v1", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            ui.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        try {
            ui.onNodeWithTag("jarvis-language-switcher").performClick()
            ui.onNodeWithTag("jarvis-language-en-GB").performClick()
            ui.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
            ui.onNodeWithTag("jarvis-drawer-settings").performClick()
            ui.onNodeWithText("Secure backup").performScrollTo().performClick()
            ui.onNodeWithText("Create encrypted backup").assertExists()
            ui.onNodeWithText("Google Drive (recommended)").assertExists()
            ui.onNodeWithText("Microsoft OneDrive").assertExists()
            ui.onNodeWithText("Continue").assertIsNotEnabled()
            ui.onNodeWithText("Cancel").performClick()
            ui.onNodeWithText("Log out").performScrollTo().performClick()
            ui.onNodeWithText("Log out of Jarvis AI?").assertExists()
            ui.onNodeWithText("Cancel").performClick()
        } finally {
            ui.runOnIdle { JarvisLanguageSettings.save(ui.activity, JarvisLanguage.HUNGARIAN) }
        }
    }
}
