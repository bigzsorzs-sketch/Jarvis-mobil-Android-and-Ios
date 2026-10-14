package ai.jarvis.mobile

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JarvisConversationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun ready() {
        val prefs = rule.activity.getSharedPreferences("jarvis_onboarding_v1", 0)
        if (!prefs.getBoolean("permissions_reviewed", false))
            rule.onNodeWithText("Folytatás").performScrollTo().performClick()
    }
    private fun send(text: String) {
        rule.onNodeWithTag("jarvis-home-input").performTextInput(text)
        rule.onNodeWithTag("jarvis-home-input").performImeAction()
    }
    private fun session() = ViewModelProvider(rule.activity)[JarvisChatSession::class.java]

    @Test fun newChatKeepsOldConversationAndReopensItWithoutMixingMessages() {
        ready()
        send("szia")
        var originalId = ""
        rule.runOnIdle { originalId = session().current.id }
        rule.waitUntil(5_000) { session().summaries.any { it.id == originalId } }
        rule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
        rule.onNodeWithTag("jarvis-new-chat").performClick()
        rule.runOnIdle { assertTrue(session().current.turns.isEmpty()) }
        send("köszönöm")
        rule.onNodeWithText("Szia! Miben segíthetek?").assertDoesNotExist()
        rule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
        rule.onNodeWithTag("jarvis-drawer-list").performScrollToNode(hasTestTag("jarvis-conversation-$originalId"))
        rule.onNodeWithTag("jarvis-conversation-$originalId").performClick()
        rule.onNodeWithText("Szia! Miben segíthetek?").assertExists()
        rule.onNodeWithText("Szívesen!").assertDoesNotExist()
        send("jó estét")
        rule.runOnIdle {
            assertEquals(originalId, session().current.id)
            assertEquals(listOf("szia", "jó estét"), session().current.turns.map { it.first })
        }
    }

    @Test fun recreationKeepsCurrentChatButFreshLauncherIntentStartsBlank() {
        ready()
        send("szia")
        var originalId = ""
        rule.runOnIdle { originalId = session().current.id }
        rule.activityRule.scenario.recreate()
        rule.runOnIdle { assertEquals(originalId, session().current.id) }
        rule.onNodeWithText("Szia! Miben segíthetek?").assertExists()
        rule.runOnIdle {
            rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        rule.waitUntil(5_000) { session().current.id != originalId }
        rule.runOnIdle { assertTrue(session().current.turns.isEmpty()) }
        rule.onNodeWithTag("jarvis-home-input").assertExists()
    }

    @Test fun moreExpandsRemainingToolsAndMenuStaysAvailableWhileTyping() {
        ready()
        rule.onNodeWithTag("jarvis-home-input").performTextInput("draft")
        rule.onNodeWithTag("jarvis-home-drawer-trigger").assertIsDisplayed().performClick()
        rule.onNodeWithTag("jarvis-tool-Munka").assertDoesNotExist()
        rule.onNodeWithTag("jarvis-drawer-more").performClick()
        rule.onNodeWithTag("jarvis-drawer-list").performScrollToNode(hasTestTag("jarvis-tool-Munka"))
        rule.onNodeWithTag("jarvis-tool-Munka").performClick()
        rule.onNodeWithTag("jarvis-subpage-header").assertExists()
    }
}
