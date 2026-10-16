package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Supabase Auth client for Jarvis. Only the PUBLISHABLE (not secret/service-role)
 * key may be included in an APK. Access and refresh tokens are encrypted with
 * Android Keystore through SecurePrefs. No OAuth client secret lives on-device.
 *
 * The OAuth callback uses PKCE. A verified HTTPS Android App Link is preferred
 * before public launch; this private-beta custom scheme is not an App Link.
 */
internal object JarvisSupabaseAuth {
    const val REDIRECT_URI = "ai.jarvis.mobile://auth/callback"
    private const val SESSION = "supabase_auth_session"
    private const val PKCE_VERIFIER = "supabase_oauth_pkce_verifier"
    private const val PKCE_STARTED = "supabase_oauth_pkce_started"
    private const val ACCOUNT_ID = "supabase_account_id"
    private val mutex = Mutex()
    var revision by mutableIntStateOf(0)
        private set

    private val origin get() = BuildConfig.JARVIS_SUPABASE_URL.trim().trimEnd('/')
    private val apiKey get() = BuildConfig.JARVIS_SUPABASE_PUBLISHABLE_KEY.trim()

    fun enabled(): Boolean = origin.matches(Regex("https://[A-Za-z0-9.-]+")) &&
        apiKey.startsWith("sb_publishable_")

    fun hasSession(context: Context): Boolean = runCatching {
        JSONObject(SecurePrefs(context).read(SESSION, "{}"))
            .optString("refresh_token").isNotBlank()
    }.getOrDefault(false)

    private fun randomVerifier(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    internal fun challenge(verifier: String): String =
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    /** Open the system browser, never a WebView. OAuth state is protected by PKCE. */
    fun startGoogleSignIn(context: Context) {
        check(enabled()) { "A Supabase bejelentkezés még nincs konfigurálva." }
        val verifier = randomVerifier()
        SecurePrefs(context).apply {
            write(PKCE_VERIFIER, verifier)
            write(PKCE_STARTED, System.currentTimeMillis().toString())
        }
        val uri = Uri.parse("$origin/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge(verifier))
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        context.startActivity(intent)
    }

    private fun errorMessage(code: Int): String = when (code) {
        400, 401 -> "Hibás bejelentkezési adatok, lejárt kód vagy érvénytelen munkamenet."
        422 -> "Ellenőrizd az e-mail-címet és a jelszót."
        429 -> "Túl sok próbálkozás. Próbáld újra később."
        else -> "A bejelentkezési szolgáltatás most nem érhető el (HTTP $code)."
    }

    private suspend fun request(path: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        require(enabled()) { "A Supabase még nincs beállítva." }
        var connection: HttpURLConnection? = null
        try {
            connection = URL("$origin/auth/v1/$path").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("apikey", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200 && connection.responseCode != 201)
                throw IllegalStateException(errorMessage(connection.responseCode))
            val data = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(data.size() + n <= 65_536) { "Túl nagy hitelesítési válasz." }
                    data.write(buffer, 0, n)
                }
            }
            JSONObject(data.toString(Charsets.UTF_8.name()))
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {
            throw IllegalStateException("Nem sikerült kapcsolódni a bejelentkezési szolgáltatáshoz.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun persist(context: Context, response: JSONObject) {
        val access = response.optString("access_token")
        val refresh = response.optString("refresh_token")
        val uid = response.optJSONObject("user")?.optString("id").orEmpty()
        require(access.isNotBlank() && refresh.isNotBlank() && uid.matches(
            Regex("[a-fA-F0-9-]{36}"))) { "Nem érkezett érvényes bejelentkezési munkamenet." }

        val prefs = SecurePrefs(context)
        val previous = prefs.read(ACCOUNT_ID, "")
        // The existing chat/notes database is DEVICE-LOCAL and not user-partitioned.
        // Do not expose prior local history to a different newly signed-in account.
        if (previous.isNotBlank() && previous != uid)
            throw IllegalStateException(
                "Másik Jarvis-fiók helyi adatai vannak ezen a készüléken. " +
                    "Fiókváltás előtt mentsd, majd töröld az alkalmazás helyi adatait.")

        val expiresIn = response.optLong("expires_in", 3600L).coerceIn(60L, 86_400L)
        val record = JSONObject()
            .put("access_token", access).put("refresh_token", refresh)
            .put("expires_at", System.currentTimeMillis() / 1000L + expiresIn)
        prefs.write(SESSION, record.toString())
        prefs.write(ACCOUNT_ID, uid)
        revision++
    }

    suspend fun signIn(context: Context, email: String, password: String) {
        require(email.contains("@") && password.isNotEmpty()) { "Add meg az e-mail-címet és a jelszót." }
        mutex.withLock {
            persist(context, request("token?grant_type=password", JSONObject()
                .put("email", email.trim()).put("password", password)))
        }
    }

    /** Usually no session until email confirmation has been completed. */
    suspend fun signUp(context: Context, email: String, password: String): String {
        require(email.contains("@") && password.length >= 8) {
            "Adj meg érvényes e-mail-címet és legalább 8 karakteres jelszót."
        }
        return mutex.withLock {
            val response = request("signup", JSONObject()
                .put("email", email.trim()).put("password", password))
            if (response.optString("access_token").isNotBlank()) {
                persist(context, response)
                "A regisztráció sikeres."
            } else "Nézd meg a megerősítő e-mailt, majd jelentkezz be."
        }
    }

    /** Handle ONLY the exact app redirect, consume verifier once, never log codes. */
    suspend fun completeGoogleSignIn(context: Context, redirect: Uri) {
        require(redirect.scheme == "ai.jarvis.mobile" && redirect.host == "auth" &&
            redirect.path == "/callback") { "Ismeretlen visszatérési cím." }
        val prefs = SecurePrefs(context)
        val verifier = prefs.read(PKCE_VERIFIER, "")
        val started = prefs.read(PKCE_STARTED, "0").toLongOrNull() ?: 0L
        prefs.write(PKCE_VERIFIER, "")
        prefs.write(PKCE_STARTED, "0")
        require(verifier.isNotBlank() &&
            System.currentTimeMillis() - started in 0..600_000L) {
            "A Google-belépés lejárt. Próbáld újra."
        }
        val error = redirect.getQueryParameter("error")
        require(error.isNullOrBlank()) { "A Google-belépés megszakadt vagy elutasításra került." }
        val code = redirect.getQueryParameter("code")
        require(!code.isNullOrBlank() && code.length < 4096) {
            "A Google nem küldött érvényes belépési kódot."
        }
        mutex.withLock {
            persist(context, request("token?grant_type=pkce",
                JSONObject().put("auth_code", code).put("code_verifier", verifier)))
        }
    }

    /** Refresh tokens rotate; serialize every refresh to prevent reuse/races. */
    suspend fun accessToken(context: Context): String? = mutex.withLock {
        val prefs = SecurePrefs(context)
        val session = runCatching { JSONObject(prefs.read(SESSION, "{}")) }.getOrNull()
            ?: return@withLock null
        val token = session.optString("access_token")
        val expiry = session.optLong("expires_at")
        if (token.isNotBlank() && expiry > System.currentTimeMillis() / 1000L + 60L)
            return@withLock token
        val refresh = session.optString("refresh_token")
        if (refresh.isBlank()) return@withLock null
        try {
            val response = request("token?grant_type=refresh_token", JSONObject()
                .put("refresh_token", refresh))
            persist(context, response)
            response.optString("access_token").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun signOut(context: Context) {
        SecurePrefs(context).apply {
            write(SESSION, "")
            write(PKCE_VERIFIER, "")
            write(PKCE_STARTED, "0")
            // Keep ACCOUNT_ID to block cross-account access to existing local chats.
        }
        revision++
    }
}
