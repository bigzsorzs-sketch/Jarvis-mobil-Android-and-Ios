package ai.jarvis.mobile

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Two AI connection modes:
 * - DIRECT_TEST: personal OpenRouter key in Android Keystore-backed encrypted prefs.
 *   No shared key is embedded in APK/repository; use limited, revocable TEST keys.
 * - SERVER: separate tester token; provider key stays on the private backend.
 *
 * Sends only submitted question text. Model-proposed page openings are\n * validated locally, then handed off to the existing Android UI.
 */
internal object JarvisAiGateway {
    private const val KEY_MODE = "ai_connection_mode"
    private const val KEY_DIRECT = "ai_direct_openrouter_key"
    private const val KEY_URL = "ai_gateway_base_url"
    private const val KEY_TOKEN = "ai_gateway_access_token"
    private const val KEY_CONSENT = "ai_cloud_text_consent_v1"
    private const val MAX_MESSAGE = 8000
    private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    internal const val TEST_MODEL = "openai/gpt-4o-mini"

    const val DIRECT_TEST = "direct"
    const val SERVER = "server"

    fun normalizedBaseUrl(input: String): String? = try {
        val uri = URI(input.trim().trimEnd('/'))
        if (uri.scheme != "https" || uri.host.isNullOrBlank() ||
            !uri.host.matches(Regex("[A-Za-z0-9.-]+")) ||
            uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
            (uri.rawPath != "" && uri.rawPath != "/") ||
            uri.port > 65535 || uri.port == 0) null
        else "https://" + uri.host.lowercase() +
            (if (uri.port > 0 && uri.port != 443) ":" + uri.port else "")
    } catch (_: Exception) { null }

    /** A 404 is a routing/model issue, not proof that an access key is invalid.
     * Never echo credential, full server URL or an untrusted upstream error body.
     */
    fun http404Message(direct: Boolean): String =
        if (direct) {
            "OpenRouter HTTP 404: a kiválasztott modellhez nem talált használható " +
                "végpontot. A kulcs ettől még lehet érvényes. Ellenőrizd az OpenRouter " +
                "modellhozzáférést és az adatvédelmi/szolgáltatói szűrőket. " +
                "A Jarvis tesztmodellje: " + TEST_MODEL + "."
        } else {
            "Jarvis AI-szerver HTTP 404: a beállított HTTPS-kiszolgáló nem találja " +
                "a /v1/chat útvonalat. Az OpenRouter API-címe NEM saját Jarvis-szerver. " +
                "Ha közvetlen OpenRouter-kulcsod van, a Beállítások → AI-agy alatt " +
                "válaszd a Közvetlen OpenRouter tesztmódot."
        }

    fun isValidTesterToken(input: String): Boolean =
        input.matches(Regex("[a-fA-F0-9]{64}"))

    /** Structural check only; only OpenRouter can verify if a key is active. */
    fun isValidOpenRouterKey(input: String): Boolean =
        input.matches(Regex("sk-or-[A-Za-z0-9_-]{20,200}"))

    fun cloudConsent(context: Context): Boolean =
        SecurePrefs(context).read(KEY_CONSENT, "false") == "true"

    fun setCloudConsent(context: Context, approved: Boolean) {
        SecurePrefs(context).write(KEY_CONSENT, approved.toString())
    }

    fun mode(context: Context): String {
        // End-users never receive a direct provider-key workflow in release builds.
        if (!BuildConfig.DEBUG) return SERVER
        val prefs = SecurePrefs(context)
        val stored = prefs.read(KEY_MODE, "")
        if (stored == DIRECT_TEST || stored == SERVER) return stored
        // Existing 0.2.0 installations used backend-only mode.
        return if (normalizedBaseUrl(prefs.read(KEY_URL, "")) != null &&
            isValidTesterToken(prefs.read(KEY_TOKEN, ""))) SERVER else DIRECT_TEST
    }

    fun configured(context: Context): Boolean {
        return cloudConsent(context) && hasSavedConnection(context)
    }

    /** A saved key and cloud permission are independent, including after updates. */
    fun hasSavedConnection(context: Context): Boolean {
        val prefs = SecurePrefs(context)
        return if (mode(context) == DIRECT_TEST) {
            isValidOpenRouterKey(prefs.read(KEY_DIRECT, ""))
        } else {
            normalizedBaseUrl(prefs.read(KEY_URL, "")) != null &&
                isValidTesterToken(prefs.read(KEY_TOKEN, ""))
        }
    }

    fun baseUrl(context: Context): String = SecurePrefs(context).read(KEY_URL, "")

    fun saveDirect(context: Context, key: String): String? {
        if (!BuildConfig.DEBUG) return "Közvetlen OpenRouter-kulcs csak belső tesztalkalmazásban használható."
        val prefs = SecurePrefs(context)
        val clean = key.trim().ifEmpty {
            if (mode(context) == DIRECT_TEST) prefs.read(KEY_DIRECT, "") else ""
        }
        if (!isValidOpenRouterKey(clean)) {
            return "Érvénytelen OpenRouter-kulcs formátum. Ellenőrizd, hogy a kulcs teljes-e."
        }
        prefs.write(KEY_DIRECT, clean)
        prefs.write(KEY_MODE, DIRECT_TEST)
        // Switching off backend also removes stored family/tester credentials.
        prefs.write(KEY_TOKEN, "")
        return null
    }

    fun save(context: Context, url: String, token: String): String? {
        val base = normalizedBaseUrl(url)
            ?: return "Adj meg egy HTTPS-címet, például https://ai.sajatdomain.hu"
        val prefs = SecurePrefs(context)
        val clean = token.trim().ifEmpty {
            if (mode(context) == SERVER && base == normalizedBaseUrl(prefs.read(KEY_URL, "")))
                prefs.read(KEY_TOKEN, "") else ""
        }
        if (!isValidTesterToken(clean)) {
            return "Érvénytelen tesztelői kód (64 hexadecimális karakter)."
        }
        prefs.write(KEY_URL, base)
        prefs.write(KEY_TOKEN, clean)
        prefs.write(KEY_MODE, SERVER)
        prefs.write(KEY_DIRECT, "")
        return null
    }

    fun clear(context: Context) {
        val prefs = SecurePrefs(context)
        prefs.write(KEY_DIRECT, "")
        prefs.write(KEY_TOKEN, "")
        prefs.write(KEY_URL, "")
        prefs.write(KEY_MODE, DIRECT_TEST)
        prefs.write(KEY_CONSENT, "false")
    }

    suspend fun ask(context: Context, message: String): String =
        askStructured(context, message).message

    /** Only an on-device allowlisted dispatcher can execute the proposed route. */
    suspend fun askStructured(context: Context, message: String): JarvisAiOutcome =
        withContext(Dispatchers.IO) {
        val question = message.trim()
        if (question.isEmpty() || question.length > MAX_MESSAGE) {
            return@withContext JarvisAiOutcome("A kérdés üres vagy túl hosszú.")
        }
        if (!cloudConsent(context.applicationContext)) {
            return@withContext JarvisAiOutcome(
                "Az AI-kérdés elküldéséhez előbb engedélyezd a felhőalapú szövegfeldolgozást a Beállítások → AI-agy oldalon.")
        }
        val prefs = SecurePrefs(context.applicationContext)
        val direct = mode(context) == DIRECT_TEST
        val credential = if (direct) prefs.read(KEY_DIRECT, "") else prefs.read(KEY_TOKEN, "")
        val base = if (direct) null else normalizedBaseUrl(prefs.read(KEY_URL, ""))
        if ((direct && !isValidOpenRouterKey(credential)) ||
            (!direct && (base == null || !isValidTesterToken(credential)))) {
            return@withContext JarvisAiOutcome(if (direct) {
                "Az AI-agy nincs beállítva. Beállítások → AI-agy alatt add meg a saját OpenRouter-kulcsodat."
            } else {
                "A szerverkapcsolat nincs beállítva. Add meg a HTTPS-címet és a tesztelői kódot."
            })
        }

        var connection: HttpURLConnection? = null
        try {
            val endpoint = if (direct) OPENROUTER_ENDPOINT else "$base/v1/chat"
            connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $credential")

            // Never force function calling for ordinary chat. Some OpenRouter
            // providers support this model for text but return 404 with tools.
            val payload = if (direct) JarvisAiToolPolicy.providerPayload(question)
                else JSONObject().put("message", question)

            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            when (val status = connection.responseCode) {
                200 -> {
                    val response = try {
                        connection.inputStream.use { JarvisNetworkSafety.readUtf8Limited(it) }
                    } catch (_: IllegalArgumentException) {
                        return@withContext JarvisAiOutcome(
                            "Az AI válasza túl nagy volt; semmilyen AI-eszközkérést nem hajtottam végre.")
                    }
                    val body = JSONObject(response)
                    if (direct) JarvisAiTools.fromProvider(body, question)
                    else JarvisAiTools.fromServer(body, question)
                }
                401 -> JarvisAiOutcome(if (direct) "Az OpenRouter-kulcs érvénytelen vagy visszavonták."
                    else "A tesztelői kód érvénytelen vagy visszavonták.")
                402 -> JarvisAiOutcome("Az OpenRouter-egyenleg vagy a kulcshoz tartozó keret nem elegendő.")
                403 -> JarvisAiOutcome("Az AI-hozzáférés nincs engedélyezve.")
                404 -> JarvisAiOutcome(
                    if (direct && JarvisAiToolPolicy.selectedTools(question).length() > 0)
                        "Az OpenRouter nem talált eszközhívásra alkalmas modellt ehhez a kéréshez. " +
                            "Az Android-műveletet nem hajtottam végre. A támogatott helyi parancsok " +
                            "AI-kapcsolat nélkül is használhatók."
                    else http404Message(direct))
                429 -> JarvisAiOutcome("Elérted az ideiglenes használati limitet. Próbáld később.")
                301, 302, 303, 307, 308 -> JarvisAiOutcome(
                    "A szerver átirányított; biztonsági okból megszakítottam.")
                else -> JarvisAiOutcome("Az AI-szolgáltató hibát jelzett (HTTP $status).")
            }
        } catch (_: Exception) {
            JarvisAiOutcome("Nem sikerült kapcsolódni az AI-hoz. Ellenőrizd az internetet és a kulcsot.")
        } finally {
            connection?.disconnect()
        }
    }
}

