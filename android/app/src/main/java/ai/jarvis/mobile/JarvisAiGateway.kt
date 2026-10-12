package ai.jarvis.mobile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * HTTPS-only AI gateway. The provider's OPENROUTER_API_KEY lives on the backend,
 * never in this APK. Each tester stores an independent random access token
 * encrypted using Android Keystore through SecurePrefs.
 *
 * Only explicit messages go to the AI. No contacts, device data, files or
 * conversation history are uploaded by this client.
 */
internal object JarvisAiGateway {
    private const val KEY_URL = "ai_gateway_base_url"
    private const val KEY_TOKEN = "ai_gateway_access_token"
    private const val MAX_MESSAGE = 8000

    // Users may configure a trusted HTTPS deployment URL. A redirect is NEVER
    // followed so a bearer token cannot leak to a new host via Location.
    fun normalizedBaseUrl(input: String): String? {
        return try {
            val uri = URI(input.trim().trimEnd('/'))
            if (uri.scheme != "https" || uri.host.isNullOrBlank() ||
                !uri.host.matches(Regex("[A-Za-z0-9.-]+")) ||
                uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
                (uri.rawPath != "" && uri.rawPath != "/") ||
                uri.port > 65535 || uri.port == 0) null
            else "https://" + uri.host.lowercase() +
                (if (uri.port > 0 && uri.port != 443) ":" + uri.port else "")
        } catch (_: Exception) { null }
    }

    fun isValidTesterToken(input: String): Boolean =
        input.matches(Regex("[a-fA-F0-9]{64}"))

    fun configured(context: Context): Boolean {
        val prefs = SecurePrefs(context)
        return normalizedBaseUrl(prefs.read(KEY_URL, "")) != null &&
            isValidTesterToken(prefs.read(KEY_TOKEN, ""))
    }

    fun baseUrl(context: Context): String = SecurePrefs(context).read(KEY_URL, "")

    fun save(context: Context, url: String, token: String): String? {
        val base = normalizedBaseUrl(url)
            ?: return "Adj meg egy HTTPS-címet, például https://ai.sajatdomain.hu"
        if (!isValidTesterToken(token.trim())) {
            return "Érvénytelen tesztelői hozzáférési kód (64 hexadecimális karakter)."
        }
        val prefs = SecurePrefs(context)
        prefs.write(KEY_URL, base)
        prefs.write(KEY_TOKEN, token.trim())
        return null
    }

    fun clear(context: Context) {
        // Replace the encrypted token; it is never written to plain preferences.
        val prefs = SecurePrefs(context)
        prefs.write(KEY_TOKEN, "")
        prefs.write(KEY_URL, "")
    }

    suspend fun ask(context: Context, message: String): String = withContext(Dispatchers.IO) {
        val question = message.trim()
        if (question.isEmpty() || question.length > MAX_MESSAGE) {
            return@withContext "A kérdés üres vagy túl hosszú."
        }
        val prefs = SecurePrefs(context.applicationContext)
        val base = normalizedBaseUrl(prefs.read(KEY_URL, ""))
        val token = prefs.read(KEY_TOKEN, "")
        if (base == null || !isValidTesterToken(token)) {
            return@withContext "Az AI-agy nincs csatlakoztatva. A Beállítások → AI-agy alatt add meg a szerver HTTPS-címét és a saját tesztelői kódodat."
        }
        var connection: HttpURLConnection? = null
        try {
            connection = URL("$base/v1/chat").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val bytes = JSONObject().put("message", question).toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            when (connection.responseCode) {
                200 -> {
                    val answer = connection.inputStream.bufferedReader().use { it.readText() }
                    val reply = JSONObject(answer).optString("reply").trim()
                    if (reply.isNotEmpty()) reply else "Az AI-szerver üres választ küldött."
                }
                401 -> "A tesztelői hozzáférés érvénytelen vagy visszavonták."
                403 -> "Ehhez a felhasználóhoz nincs engedélyezve az AI-hozzáférés."
                429 -> "Elérted az ideiglenes AI-használati limitet. Próbáld meg később."
                301, 302, 303, 307, 308 -> "A szerver átirányítást kért, amit biztonsági okból elutasítottam."
                else -> "Az AI-szerver jelenleg nem elérhető (HTTP ${connection.responseCode})."
            }
        } catch (_: Exception) {
            "Nem sikerült kapcsolódni az AI-szerverhez. Ellenőrizd az internetet, a szervercímet és hogy a szerver fut-e."
        } finally {
            connection?.disconnect()
        }
    }
}
