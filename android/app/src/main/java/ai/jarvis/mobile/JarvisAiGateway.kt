package ai.jarvis.mobile

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.ByteArrayOutputStream

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
    private const val KEY_SPEECH_CONSENT = "ai_cloud_voice_consent_v1"
    private const val KEY_TRANSCRIPTION_CONSENT = "ai_cloud_audio_upload_consent_v1"
    internal const val TRANSCRIPTION_MODEL = "openai/gpt-transcribe"
    @Volatile var lastTranscriptionError: String = ""
        private set
    internal const val SPEECH_MODEL = JarvisSpeechVoiceConfig.MODEL
    // No credentials or provider body are ever written to this diagnostic.
    @Volatile internal var lastSpeechError: String = ""
        private set
    private const val MAX_MESSAGE = 8000
    private const val OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    internal const val TEST_MODEL = "openai/gpt-5.2"

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

    /** Separate permission: narration may contain private chat, contacts or health data. */
    fun voiceConsent(context: Context): Boolean =
        SecurePrefs(context).read(KEY_SPEECH_CONSENT, "false") == "true"

    fun setVoiceConsent(context: Context, approved: Boolean) {
        SecurePrefs(context).write(KEY_SPEECH_CONSENT, approved.toString())
    }

    /** Raw microphone audio leaves the phone ONLY after this explicit, separate opt-in. */
    fun transcriptionConsent(context: Context): Boolean =
        SecurePrefs(context).read(KEY_TRANSCRIPTION_CONSENT, "false") == "true"

    fun setTranscriptionConsent(context: Context, approved: Boolean) {
        SecurePrefs(context).write(KEY_TRANSCRIPTION_CONSENT, approved.toString())
    }

    fun canTranscribe(context: Context): Boolean =
        transcriptionConsent(context) && configured(context)

    fun setCloudConsent(context: Context, approved: Boolean) {
        SecurePrefs(context).write(KEY_CONSENT, approved.toString())
    }

    /**
     * A personal, user-entered OpenRouter connection works in both signed release
     * and debug builds. Never ship a provider credential in BuildConfig or APK.
     * Explicitly saved mode always wins; existing paired server users remain paired.
     */
    fun mode(context: Context): String {
        val prefs = SecurePrefs(context)
        val stored = prefs.read(KEY_MODE, "")
        val hasServer = (
            normalizedBaseUrl(prefs.read(KEY_URL, "")) != null &&
            isValidTesterToken(prefs.read(KEY_TOKEN, ""))
        ) || normalizedBaseUrl(BuildConfig.JARVIS_GATEWAY_ORIGIN) != null
        return chooseMode(stored, hasServer)
    }

    internal fun chooseMode(stored: String, hasServer: Boolean): String = when (stored) {
        DIRECT_TEST -> DIRECT_TEST
        SERVER -> SERVER
        else -> if (hasServer) SERVER else DIRECT_TEST
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

    /** The public HTTPS origin can be preconfigured at build time; no secret is embedded. */
    fun baseUrl(context: Context): String {
        val stored = SecurePrefs(context).read(KEY_URL, "")
        return stored.ifBlank { normalizedBaseUrl(BuildConfig.JARVIS_GATEWAY_ORIGIN).orEmpty() }
    }

    /** One-time tester invitation exchange. The provider key stays on the host;
     * only a revocable, device-local tester session is stored with SecurePrefs.
     */
    suspend fun activateInvitation(context: Context, server: String, invitation: String): String? =
        withContext(Dispatchers.IO) {
            val base = normalizedBaseUrl(server)
                ?: return@withContext "A Jarvis HTTPS-szerverének címe még nincs beállítva."
            val code = invitation.trim().lowercase()
            if (!code.matches(Regex("[0-9a-f]{48}"))) {
                return@withContext "Érvénytelen meghívó: 48 hexadecimális karakter szükséges."
            }
            var conn: HttpURLConnection? = null
            try {
                conn = URL("$base/v1/activate").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.outputStream.use { it.write(JSONObject().put("invitation", code)
                    .toString().toByteArray(Charsets.UTF_8)) }
                when (conn.responseCode) {
                    200 -> {
                        val answer = conn.inputStream.use {
                            JarvisNetworkSafety.readUtf8Limited(it)
                        }
                        val token = JSONObject(answer).optString("token")
                        if (!isValidTesterToken(token)) {
                            "A szerver érvénytelen hozzáférést adott. Nem mentettem."
                        } else save(context.applicationContext, base, token)
                    }
                    401 -> "A meghívó lejárt, hibás vagy már felhasználták. Kérj újat."
                    404 -> "A megadott szerveren a Jarvis privát tesztaktiválás még nem működik."
                    429 -> "Túl sok aktiválási kísérlet. Próbáld később."
                    else -> "Nem sikerült a tesztaktiválás (HTTP " + conn.responseCode + ")."
                }
            } catch (_: Exception) {
                "Nem sikerült csatlakozni a Jarvis privát szerveréhez."
            } finally {
                conn?.disconnect()
            }
        }

    fun saveDirect(context: Context, key: String): String? {
        // Manual key entry is also supported in release APKs. Keystore encryption
        // and explicit cloud consent remain mandatory; never bundle a shared key.
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
        prefs.write(KEY_SPEECH_CONSENT, "false")
        prefs.write(KEY_TRANSCRIPTION_CONSENT, "false")
    }

    /** OpenRouter's dedicated STT endpoint; recorded WAV is never stored or logged. */
    suspend fun transcribe(context: Context, wav: ByteArray, language: JarvisLanguage): String? =
        withContext(Dispatchers.IO) {
            lastTranscriptionError = ""
            if (!canTranscribe(context)) {
                lastTranscriptionError = "A hangfelismeréshez engedélyezd a felhős hangfeltöltést az AI-agy beállításainál."
                return@withContext null
            }
            if (wav.size !in 44..550_000 ||
                !wav.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII))) {
                lastTranscriptionError = "A mikrofon felvétele érvénytelen vagy túl hosszú."
                return@withContext null
            }
            val direct = mode(context) == DIRECT_TEST
            val credential = SecurePrefs(context).read(
                if (direct) KEY_DIRECT else KEY_TOKEN, "")
            val endpoint = if (direct)
                "https://openrouter.ai/api/v1/audio/transcriptions"
            else normalizedBaseUrl(baseUrl(context))?.plus("/v1/transcribe")
            if (endpoint == null || if (direct) !isValidOpenRouterKey(credential)
                else !isValidTesterToken(credential)) {
                lastTranscriptionError = "A beszédfelismerő kapcsolata nincs beállítva."
                return@withContext null
            }
            var conn: HttpURLConnection? = null
            try {
                if (!canTranscribe(context)) return@withContext null
                conn = URL(endpoint)
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 12_000
                conn.readTimeout = 58_000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $credential")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                val payload = JSONObject()
                    .put("model", TRANSCRIPTION_MODEL)
                    .put("input_audio", JSONObject()
                        .put("format", "wav")
                        .put("data", Base64.encodeToString(wav, Base64.NO_WRAP)))
                    .put("language", language.tag.substringBefore('-').lowercase(java.util.Locale.ROOT))
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode != 200) {
                    lastTranscriptionError = when (conn.responseCode) {
                        401 -> "A hangfelismerő hozzáférése érvénytelen."
                        402 -> "Az OpenRouter-egyenleg elfogyott."
                        404 -> "A beszédfelismerő modell/végpont nem elérhető."
                        429 -> "A hangfelismerés használati korlátot ért el."
                        else -> "A felhős beszédfelismerés HTTP " + conn.responseCode + " hibát adott."
                    }
                    return@withContext null
                }
                val text = conn.inputStream.use { JarvisNetworkSafety.readUtf8Limited(it) }
                JSONObject(text).optString("text").trim()
                    .takeIf { it.isNotBlank() && it.length <= 1500 }
                    ?: run {
                        lastTranscriptionError = "A felismerő üres vagy túl hosszú szöveget adott."
                        null
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                lastTranscriptionError = "A felhős beszédfelismerés nem érhető el. Ellenőrizd az internetet."
                null
            } finally {
                conn?.disconnect()
            }
        }

    /**
     * Generate real cloud audio, not Android TTS. Each byte goes directly from
     * OpenRouter (or the authenticated private Jarvis gateway) to a temporary
     * local MP3; never keep a voice recording or API key in the repository.
     */
    suspend fun speechBytes(context: Context, text: String, language: JarvisLanguage): ByteArray? =
        withContext(Dispatchers.IO) {
            lastSpeechError = ""
            if (!cloudConsent(context) || !voiceConsent(context) || !hasSavedConnection(context)) {
                lastSpeechError = "Engedélyezd az AI-szöveget és a külön AI-hang hozzájárulást a Beállításokban."
                return@withContext null
            }
            if (text.isBlank() || text.length > 1600) {
                lastSpeechError = "A felolvasandó szöveg üres vagy túl hosszú."
                return@withContext null
            }
            val prefs = SecurePrefs(context)
            val direct = mode(context) == DIRECT_TEST
            val token = prefs.read(if (direct) KEY_DIRECT else KEY_TOKEN, "")
            val endpoint = if (direct) "https://openrouter.ai/api/v1/audio/speech"
                else normalizedBaseUrl(baseUrl(context))?.plus("/v1/speech")
                    ?: return@withContext null
            val choices = if (direct) listOf(
                JarvisSpeechVoiceConfig.MODEL to JarvisSpeechVoiceConfig.voice(language),
                JarvisSpeechVoiceConfig.FALLBACK_MODEL to JarvisSpeechVoiceConfig.fallbackVoice(language)
            ) else listOf("" to "")
            // Fail over only between Azure voices that are available for the
            // selected locale. No token or spoken text is logged.
            for ((model, selectedVoice) in choices) {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL(endpoint).openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 10000
                    connection.readTimeout = 90000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "audio/mpeg")
                    val payload = if (direct) JSONObject()
                        .put("model", model).put("input", text)
                        .put("voice", selectedVoice).put("response_format", "mp3")
                    else JSONObject().put("text", text).put("language", language.tag)
                    connection.outputStream.use {
                        it.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }
                    val responseStatus = connection.responseCode
                    if (responseStatus != 200) {
                        lastSpeechError = when (responseStatus) {
                            400 -> "A hangmodell nem fogadta el a választott nyelvet vagy hangot (HTTP 400)."
                            401 -> "Az OpenRouter nem fogadta el a hanghoz az API-kulcsot (HTTP 401)."
                            402 -> "A hangkérés keret- vagy költési limitbe ütközött (HTTP 402)."
                            403 -> "A hangmodellhez nincs hozzáférés vagy szolgáltatói engedély (HTTP 403)."
                            404 -> "Az OpenRouter nem talált elérhető hangmodellt/végpontot (HTTP 404)."
                            429 -> "A hangszolgáltató sebességkorlátot jelzett (HTTP 429)."
                            else -> "A felhős hangszolgáltató hibát jelzett (HTTP $responseStatus)."
                        }
                        // Credentials/credit/consent failures cannot be fixed
                        // with a different model, do not send another request.
                        if (responseStatus in listOf(401, 402, 403)) return@withContext null
                        continue
                    }
                    if (!connection.contentType.orEmpty().lowercase().startsWith("audio/mpeg")) {
                        lastSpeechError = "A hangszolgáltató nem MP3-hangot küldött."
                        continue
                    }
                    val audio = ByteArrayOutputStream()
                    val limit = 3 * 1024 * 1024
                    var oversized = false
                    connection.inputStream.use { body ->
                        val buffer = ByteArray(8192)
                        var size = 0
                        while (true) {
                            val read = body.read(buffer)
                            if (read == -1) break
                            size += read
                            if (size > limit) {
                                oversized = true
                                break
                            }
                            audio.write(buffer, 0, read)
                        }
                    }
                    if (oversized) {
                        lastSpeechError = "A hangfájl túllépte a megengedett méretet."
                        continue
                    }
                    val bytes = audio.toByteArray()
                    if (bytes.size > 100) {
                        lastSpeechError = ""
                        return@withContext bytes
                    }
                    lastSpeechError = "A felhős hangszolgáltató túl rövid vagy üres hangfájlt küldött."
                } catch (_: Exception) {
                    lastSpeechError = "Hálózati hiba történt a felhős hang letöltésekor."
                } finally {
                    connection?.disconnect()
                }
            }
            null
        }

    /** Server and direct-provider modes must use the same selected response language.
     * Only one of the seven supported language tags is passed, never model-produced text.
     */
    internal fun serverChatPayload(question: String, selectedLanguage: String, developerNote: String? = null,
        history: List<Pair<String, String>> = emptyList()): JSONObject {
        val supported = setOf("hu-HU", "en-GB", "de-DE", "fr-FR", "es-ES", "it-IT", "pl-PL")
        val tag = selectedLanguage.takeIf { it in supported } ?: "hu-HU"
        val payload = JSONObject().put("message", question).put("language", tag)
        if (JarvisAiToolPolicy.selectedTools(question).length() == 0 && history.isNotEmpty())
            payload.put("history", JarvisConversationContext.messages(history))
        developerNote?.take(1800)?.takeIf { it.length >= 100 }?.let {
            payload.put("developerNote", it)
        }
        return payload
    }


    /**
     * Stream only ordinary text in direct OpenRouter mode. Commands and web
     * requests continue to require the fully validated non-streaming handler.
     */
    fun canStreamText(context: Context, question: String): Boolean =
        mode(context) == DIRECT_TEST &&
            question.isNotBlank() && question.length <= MAX_MESSAGE &&
            JarvisAiToolPolicy.selectedTools(question).length() == 0 &&
            !JarvisAiToolPolicy.needsLiveWeb(question) &&
            !JarvisGlucoseVoice.isQuery(question)

    /** The only output from streamed SSE is text; NEVER execute a streamed tool. */
    suspend fun askIncremental(
        context: Context,
        message: String,
        responseLanguageTag: String? = null,
        cancellation: JarvisStreamCancellation = JarvisStreamCancellation(),
        onDelta: (String) -> Unit,
        onPhrase: (String) -> Unit,
        history: List<Pair<String, String>> = emptyList()
    ): JarvisAiOutcome {
        if (!canStreamText(context, message) ||
            !cloudConsent(context) || !hasSavedConnection(context)) {
            return askStructured(context, message, responseLanguageTag, history = history)
        }
        return withContext(Dispatchers.IO) {
            val credential = SecurePrefs(context.applicationContext).read(KEY_DIRECT, "")
            if (!isValidOpenRouterKey(credential)) {
                return@withContext JarvisAiOutcome("Az OpenRouter-kulcs nincs beállítva.")
            }
            val languageTag = responseLanguageTag
                ?.let { JarvisLanguage.fromSpeechTag(it)?.tag }
                ?: JarvisLanguageSettings.load(context.applicationContext).tag
            val payload = JarvisAiToolPolicy.providerPayload(message.trim(), languageTag,
                JarvisBackgroundBrain.relevantNote(context.applicationContext, message), history)
            if (payload.has("tools")) {
                return@withContext askStructured(context, message, languageTag, history = history)
            }
            payload.put("stream", true)
            var connection: HttpURLConnection? = null
            val decoder = JarvisSseDecoder()
            val phrases = JarvisIncrementalPhrases()
            try {
                connection = URL(OPENROUTER_ENDPOINT).openConnection() as HttpURLConnection
                cancellation.bind(connection)
                if (cancellation.isCancelled()) {
                    throw kotlinx.coroutines.CancellationException("Voice turn cancelled")
                }
                connection.requestMethod = "POST"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10000
                connection.readTimeout = 90000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "text/event-stream")
                connection.setRequestProperty("Authorization", "Bearer $credential")
                connection.outputStream.use {
                    it.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                if (status != 200) {
                    return@withContext JarvisAiOutcome(when (status) {
                        401 -> "Az OpenRouter-kulcs érvénytelen vagy visszavonták."
                        402 -> "Az OpenRouter-egyenleg nem elegendő."
                        403 -> "Az AI-hozzáférés nincs engedélyezve."
                        404 -> http404Message(true)
                        429 -> "Az OpenRouter használati limitet jelzett."
                        301, 302, 303, 307, 308 ->
                            "Az AI-szerver átirányított; biztonsági okból megszakítottam."
                        else -> "Az OpenRouter hibát jelzett (HTTP $status)."
                    })
                }
                if (!connection.contentType.orEmpty().lowercase().contains("text/event-stream")) {
                    // Some providers ignore stream=true but return a valid,
                    // complete JSON chat result. Preserve working plain chat
                    // without retrying the request or authorizing new tools.
                    if (connection.contentType.orEmpty().lowercase().contains("json")) {
                        val fullBody = connection.inputStream.use {
                            JarvisNetworkSafety.readUtf8Limited(it)
                        }
                        val parsed = JarvisAiTools.fromProvider(JSONObject(fullBody), message)
                        return@withContext JarvisAiOutcome(parsed.message, conversationReply = parsed.conversationReply)
                    }
                    return@withContext JarvisAiOutcome(
                        "Az AI-szolgáltató nem küldött folyamatos szöveget. Próbáld újra.")
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (kotlin.coroutines.coroutineContext.isActive && !decoder.done) {
                        val line = reader.readLine() ?: break
                        val delta = decoder.accept(line) ?: continue
                        val ready = phrases.accept(delta)
                        withContext(Dispatchers.Main.immediate) {
                            onDelta(delta)
                            ready.forEach(onPhrase)
                        }
                    }
                }
                if (!decoder.done || decoder.failure != null) {
                    val reason = decoder.failure
                        ?: "A hálózati válasz idő előtt megszakadt."
                    return@withContext JarvisAiOutcome(
                        decoder.text.takeIf { it.isNotBlank() }?.plus(
                            "\n($reason)")
                            ?: reason)
                }
                val last = phrases.finish()
                if (last.isNotBlank()) withContext(Dispatchers.Main.immediate) {
                    onPhrase(last)
                }
                JarvisAiOutcome(decoder.text.ifBlank { "Az AI nem adott szöveges választ." },
                    conversationReply = decoder.text.isNotBlank())
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                JarvisAiOutcome(decoder.text.takeIf { it.isNotBlank() }?.plus(
                    "\n(Az AI-kapcsolat megszakadt.)")
                    ?: "Nem sikerült kapcsolódni az AI-hoz. Ellenőrizd az internetet és a kulcsot.")
            } finally {
                cancellation.release(connection)
                connection?.disconnect()
            }
        }
    }

    suspend fun ask(context: Context, message: String): String =
        askStructured(context, message).message

    /** Only an on-device allowlisted dispatcher can execute the proposed route. */
    suspend fun askStructured(
        context: Context,
        message: String,
        responseLanguageTag: String? = null,
        attachments: List<JarvisAttachment> = emptyList(),
        history: List<Pair<String, String>> = emptyList()
    ): JarvisAiOutcome =
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
        if (attachments.isNotEmpty() && !direct) {
            return@withContext JarvisAiOutcome(
                "Fájlok, képek és videó-képkockák AI-elemzése jelenleg közvetlen OpenRouter-kapcsolattal használható. Nem küldtem fájlt.")
        }
        val credential = if (direct) prefs.read(KEY_DIRECT, "") else prefs.read(KEY_TOKEN, "")
        val base = if (direct) null else normalizedBaseUrl(baseUrl(context))
        if ((direct && !isValidOpenRouterKey(credential)) ||
            (!direct && (base == null || !isValidTesterToken(credential)))) {
            return@withContext JarvisAiOutcome(if (direct) {
                "Az AI-agy nincs beállítva. Beállítások → AI-agy alatt add meg a saját OpenRouter-kulcsodat."
            } else {
                "A szerverkapcsolat nincs beállítva. Add meg a HTTPS-címet és a tesztelői kódot."
            })
        }

        var connection: HttpURLConnection? = null
        // Cancellation (new voice request / STOP / chat change) must close the
        // blocking HttpURLConnection promptly. A readTimeout alone only stops
        // an idle socket and otherwise retains a background IO thread.
        val cancelConnection = currentCoroutineContext()[Job]?.invokeOnCompletion {
            connection?.disconnect()
        }
        try {
            val endpoint = if (direct) OPENROUTER_ENDPOINT else "$base/v1/chat"
            connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = JarvisResponseDeadline.socketReadMs(question)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $credential")

            // Never force function calling for ordinary chat. Some OpenRouter
            // providers support this model for text but return 404 with tools.
            val payload = if (direct) JarvisAiToolPolicy.providerPayload(
                question, responseLanguageTag?.let { JarvisLanguage.fromSpeechTag(it)?.tag }
                    ?: JarvisLanguageSettings.load(context.applicationContext).tag,
                JarvisBackgroundBrain.relevantNote(context.applicationContext, question), history)
                else serverChatPayload(question,
                    responseLanguageTag?.let { JarvisLanguage.fromSpeechTag(it)?.tag }
                        ?: JarvisLanguageSettings.load(context.applicationContext).tag,
                    JarvisBackgroundBrain.relevantNote(context.applicationContext, question), history)
            if (attachments.isNotEmpty()) {
                val parts = try {
                    JarvisAiAttachments.buildParts(context.applicationContext, question, attachments)
                } catch (e: Exception) {
                    return@withContext JarvisAiOutcome(
                        e.message?.take(250) ?: "A csatolmányokat nem sikerült biztonságosan előkészíteni.")
                }
                payload.getJSONArray("messages").let { messages ->
                    messages.getJSONObject(messages.length() - 1).put("content", parts)
                }
                // Attachments are untrusted content, NEVER a device-control request.
                payload.remove("tools")
                payload.remove("tool_choice")
            }

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
                    when {
                        direct && JarvisAiToolPolicy.selectedTools(question).length() > 0 ->
                            "Az OpenRouter nem talált eszközhívásra alkalmas modellt ehhez a kéréshez. " +
                            "Az Android-műveletet nem hajtottam végre. A támogatott helyi parancsok " +
                            "AI-kapcsolat nélkül is használhatók."
                        direct && JarvisAiToolPolicy.needsLiveWeb(question) ->
                            "Az OpenRouter webes keresője most nem elérhető ehhez a modellhez (HTTP 404). " +
                            "Nem állítok ellenőrizetlen, aktuális eredményt. Próbáld újra később, " +
                            "vagy nézd meg az OpenRouter eszközhozzáférését."
                        else -> http404Message(direct)
                    })
                429 -> JarvisAiOutcome("Elérted az ideiglenes használati limitet. Próbáld később.")
                301, 302, 303, 307, 308 -> JarvisAiOutcome(
                    "A szerver átirányított; biztonsági okból megszakítottam.")
                else -> JarvisAiOutcome("Az AI-szolgáltató hibát jelzett (HTTP $status).")
            }
        } catch (e: CancellationException) {
            // A cancelled turn is not a provider failure and must never
            // surface as a new user-visible response or action proposal.
            throw e
        } catch (_: Exception) {
            // Disconnect from cancellation often manifests as IOException;
            // restore structured cancellation rather than a fake AI error.
            currentCoroutineContext().ensureActive()
            JarvisAiOutcome("Nem sikerült kapcsolódni az AI-hoz. Ellenőrizd az internetet és a kulcsot.")
        } finally {
            cancelConnection?.dispose()
            connection?.disconnect()
        }
    }
}

