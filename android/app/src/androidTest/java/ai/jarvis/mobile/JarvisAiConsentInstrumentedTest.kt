package ai.jarvis.mobile

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Regression for an existing valid key blocked by the new 0.2.8 consent flag. */
@RunWith(AndroidJUnit4::class)
class JarvisAiConsentInstrumentedTest {
    private lateinit var context: Context

    @Before fun prepare() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                app.getSharedPreferences("consent_regression_" + name, mode)
        }
        context.getSharedPreferences("jarvis_basic_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun clean() {
        context.getSharedPreferences("jarvis_basic_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun existingKeyCanBeReusedAfterConsentIsAddedOrRevoked() = runBlocking {
        val prefs = SecurePrefs(context)
        val testKey = "sk-or-" + "x".repeat(40)
        // Simulate the pre-0.2.8 installation: saved key, no consent record.
        prefs.write("ai_direct_openrouter_key", testKey)
        prefs.write("ai_connection_mode", JarvisAiGateway.DIRECT_TEST)
        assertTrue(JarvisAiGateway.hasSavedConnection(context))
        assertFalse(JarvisAiGateway.configured(context))
        val refused = JarvisAiGateway.askStructured(context, "Magyarázd el a napfogyatkozást")
        assertTrue(refused.message.contains("engedélyezd"))
        assertNull(refused.deviceAction)

        // Saving an empty replacement field preserves the original key.
        assertNull(JarvisAiGateway.saveDirect(context, ""))
        assertEquals(testKey, prefs.read("ai_direct_openrouter_key", ""))
        assertFalse(JarvisAiGateway.cloudConsent(context))
        JarvisAiGateway.setCloudConsent(context, true)
        assertTrue(JarvisAiGateway.configured(context))
        JarvisAiGateway.setCloudConsent(context, false)
        assertFalse(JarvisAiGateway.configured(context))
        assertTrue(JarvisAiGateway.hasSavedConnection(context))
        assertEquals(testKey, prefs.read("ai_direct_openrouter_key", ""))
    }

    @Test fun savedServerTokenCannotBeReusedForAnotherServer() {
        assertNull(JarvisAiGateway.save(context, "https://example.com", "a".repeat(64)))
        assertNull(JarvisAiGateway.save(context, "https://example.com/", ""))
        assertNotNull(JarvisAiGateway.save(context, "https://other.example.com", ""))
        assertEquals("https://example.com", JarvisAiGateway.baseUrl(context))
        assertNotNull(JarvisAiGateway.saveDirect(context, ""))
    }
}
