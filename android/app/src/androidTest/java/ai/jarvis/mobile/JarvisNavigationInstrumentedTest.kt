package ai.jarvis.mobile

import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android Compose navigation smoke test, not proof of physical phone parity. */
@RunWith(AndroidJUnit4::class)
class JarvisNavigationInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private fun displayedRoute(route: String): String =
        JarvisTranslations.text(route, JarvisLanguageSettings.load(composeRule.activity))

    private fun finishOnboarding() {
        val prefs = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
    }

    @Test fun subpagesHaveNoDuplicatedJarvisComposerAndRemainReachableFromHome() {
        finishOnboarding()
        for (tool in (JarvisToolCatalog.all + JarvisToolCatalog.subpages)
            .filter { it.route != "Főoldal" }) {
            composeRule.onNodeWithTag("jarvis-home-input").performTextClearance()
            composeRule.onNodeWithTag("jarvis-home-input")
                .performTextInput("Nyisd meg a " + tool.name + " oldalt")
            composeRule.onNodeWithTag("jarvis-home-input").performImeAction()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("jarvis-subpage-header"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("jarvis-subpage-header").assert(hasText(displayedRoute(tool.route)))
            composeRule.onNodeWithTag("jarvis-subpage-input").assertDoesNotExist()
            composeRule.onNodeWithTag("jarvis-subpage-send").assertDoesNotExist()
            composeRule.onNodeWithText(displayedRoute("Kérdezd meg Jarvist")).assertDoesNotExist()
            composeRule.onNodeWithTag("jarvis-subpage-home-button").performClick()
        }
        composeRule.onNodeWithTag("jarvis-home-input").assertExists()
        composeRule.onNodeWithTag("jarvis-home-chat-timeline").assertExists()
    }

    @Test fun everyToolAndSubpageAcceptsTheSameForegroundVoiceDestination() {
        finishOnboarding()
        for (tool in JarvisToolCatalog.all + JarvisToolCatalog.subpages) {
            val command = "Jarvis, nyisd meg a " + tool.name + " oldalt"
            val destination = JarvisCapabilities.localPageToOpen(command)!!
            composeRule.runOnIdle { HandsFreeNavigation.destination = destination }
            if (destination == "Főoldal")
                composeRule.onNodeWithTag("jarvis-home-input").assertExists()
            else composeRule.onNodeWithTag("jarvis-subpage-header").assert(hasText(displayedRoute(destination)))
        }
    }

    @Test fun graveAccentNavigationOpensMapsWithDestinationNotEmptyMap() {
        // Capture the actual Activity intent without launching an external app.
        val context = object : ContextWrapper(composeRule.activity) {
            var launched: Intent? = null
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val destination = VoiceNavigation.destinationFrom("navigàlj Harrogate be")
        assertEquals("Harrogate", destination)
        assertEquals(VoiceNavigation.LaunchResult.GUIDANCE_REQUESTED,
            VoiceNavigation.openDetailed(context, destination!!))
        val intent = context.launched
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("com.google.android.apps.maps", intent?.`package`)
        assertEquals("google.navigation:q=Harrogate&mode=d", intent?.dataString)
    }

    @Test fun typedCommandExecutorPassesCitiesToAndroidNavigationInsteadOfEmptyMap() {
        val context = object : ContextWrapper(composeRule.activity) {
            var launched: Intent? = null
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val commands = listOf(
            "navigàlj Harrogate be" to "Harrogate",
            "Navigálj Leeds -be" to "Leeds",
            "Állítsd be a térképet Londonba" to "London",
            "Állítsd be a térképet Liverpoolba" to "Liverpool",
            "Állítsd be a térképet Manchester -be" to "Manchester",
            "Nyisd meg a térképet és navigálj Yorkba" to "York"
        )
        for ((command, city) in commands) {
            context.launched = null
            var openedPage: String? = null
            val answer = JarvisCommandExecutor.run(
                context, command,
                { openedPage = it },
                { _ -> throw AssertionError("Unexpected call request") }
            )
            assertEquals(command, null, openedPage)
            assertEquals(command, "com.google.android.apps.maps", context.launched?.`package`)
            assertEquals(command, "google.navigation:q=$city&mode=d", context.launched?.dataString)
            org.junit.Assert.assertTrue(command, answer.contains("navigációindítási"))
        }
    }

    @Test fun removedFriendsAliasPageIsAbsentFromDrawer() {
        finishOnboarding()
        composeRule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
        composeRule.onNodeWithText("Ismerősök").assertDoesNotExist()
        composeRule.onNodeWithText("Hívás").assertDoesNotExist()
        composeRule.onNodeWithText("Navigáció").assertDoesNotExist()
        composeRule.onNodeWithText("Névjegyek").assertDoesNotExist()
        composeRule.onNodeWithText(displayedRoute("Főoldal")).assertExists()
    }

    @Test fun savedPlacesImportRemainsAccessibleFromSettingsWithoutNavigationMenu() {
        finishOnboarding()
        composeRule.onNodeWithTag("jarvis-home-drawer-trigger").performClick()
        // The drawer contains a lazy list: Settings is below the viewport on
        // phones and is not composed until the list scrolls to that item.
        composeRule.onNodeWithTag("jarvis-drawer-list")
            .performScrollToNode(hasText(displayedRoute("Beállítások")))
        composeRule.onNodeWithText(displayedRoute("Beállítások")).performClick()
        composeRule.onNodeWithText("Mentett helyek és otthoni cím")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Otthoni cím mentése").assertExists()
        composeRule.onNodeWithText("Navigálás haza").assertExists()
        composeRule.onNodeWithText("Mentett helyek importálása").assertExists()
    }

    @Test fun homeMenusAndTwoNewPagesCanBeOpened() {
        // First-run permissions are optional: never grant access in a UI test.
        val prefs = composeRule.activity.getSharedPreferences(
            "jarvis_onboarding_v1", android.content.Context.MODE_PRIVATE)
        if (!prefs.getBoolean("permissions_reviewed", false)) {
            composeRule.onNodeWithText("Folytatás").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("jarvis-home-drawer-trigger").assertExists().performClick()
        composeRule.onNodeWithText(displayedRoute("Alkalmazások")).assertExists().performClick()
        composeRule.onNodeWithText(displayedRoute("Telepített alkalmazások")).assertExists()
        // Header opens the same drawer without a duplicate floating hamburger.
        composeRule.onNodeWithTag("jarvis-subpage-header").performClick()
        composeRule.onNodeWithText(displayedRoute("Előzmények")).assertExists().performClick()
        composeRule.onNodeWithText(displayedRoute("Helyi beszélgetések")).assertExists()
        composeRule.onNodeWithText(displayedRoute("Keresés az előzményekben")).assertExists()
    }
}

