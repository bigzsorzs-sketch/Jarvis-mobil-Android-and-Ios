package ai.jarvis.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.Normalizer
import java.util.Locale

/** Home Assistant adapter for Jarvis Smart Home. No vendor cloud credentials or
 * personal Home Assistant data are ever passed to the reasoning AI.
 */
internal data class JarvisHomeEntity(
    val id: String, val name: String, val state: String, val domain: String,
    val canToggle: Boolean
)
internal data class JarvisHomeConfig(val origin: String, val token: String)

internal object JarvisHomePolicy {
    private val idPattern = Regex("""^[a-z_]+\.[a-z0-9_]+$""")
    private val visibleDomains = setOf("light", "switch", "media_player", "fan",
        "sensor", "binary_sensor", "climate", "vacuum", "cover", "lock",
        "washing_machine", "microwave_oven")
    // Microwave, washer, heater, lock, and power plug operations are read-only.
    private val toggleDomains = setOf("light", "media_player", "fan")
    fun origin(raw: String): String? = try {
        val uri = URI(raw.trim().trimEnd('/'))
        val hostname = uri.host.orEmpty()
        if (uri.scheme != "https" || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ||
            !uri.rawPath.isNullOrEmpty() || hostname.isBlank() ||
            uri.port == 0 || uri.port > 65535 || raw.length > 250 ||
            hostname.any { it.isWhitespace() }) null
        else uri.toASCIIString().trimEnd('/')
    } catch (_: Exception) { null }

    fun validId(id: String): Boolean = idPattern.matches(id) && id.length <= 140
    fun visible(id: String): Boolean =
        validId(id) && id.substringBefore('.') in visibleDomains
    fun toggleAllowed(id: String): Boolean =
        validId(id) && id.substringBefore('.') in toggleDomains
    fun asEntity(item: JSONObject): JarvisHomeEntity? {
        val id = item.optString("entity_id")
        if (!visible(id)) return null
        val domain = id.substringBefore('.')
        val name = item.optJSONObject("attributes")?.optString("friendly_name").orEmpty()
            .ifBlank { id }.trim().take(120)
        return JarvisHomeEntity(id, name, item.optString("state", "unknown").take(60),
            domain, toggleAllowed(id))
    }
}

internal object JarvisHomeConnect {
    private const val ORIGIN = "smarthome_ha_origin"
    private const val TOKEN = "smarthome_ha_secret"
    private const val MAX_RESPONSE = 2_000_000
    fun load(context: Context): JarvisHomeConfig? {
        val prefs = SecurePrefs(context)
        val origin = JarvisHomePolicy.origin(prefs.read(ORIGIN, "")) ?: return null
        val token = prefs.read(TOKEN, "").trim().takeIf { it.length in 12..4096 } ?: return null
        return JarvisHomeConfig(origin, token)
    }
    fun save(context: Context, rawOrigin: String, rawToken: String) {
        val origin = JarvisHomePolicy.origin(rawOrigin)
            ?: error("Érvényes HTTPS Home Assistant szervercím szükséges.")
        val token = rawToken.trim()
        require(token.length in 12..4096 && '\n' !in token && '\r' !in token) {
            "Érvényes Home Assistant hozzáférési token szükséges."
        }
        SecurePrefs(context).write(ORIGIN, origin)
        SecurePrefs(context).write(TOKEN, token)
    }
    fun disconnect(context: Context) {
        SecurePrefs(context).write(TOKEN, "")
        SecurePrefs(context).write(ORIGIN, "")
    }

    /** Never follow a redirect with bearer credentials. Use real HTTPS TLS checks. */
    private fun request(config: JarvisHomeConfig, method: String, path: String,
                        body: JSONObject? = null): String {
        require(config.origin == JarvisHomePolicy.origin(config.origin))
        require(path.startsWith("/api/"))
        val connection = URL(config.origin + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7500
            connection.readTimeout = 7500
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer " + config.token)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException(when (status) {
                401, 403 -> "A Home Assistant megtagadta a hozzáférést."
                in 300..399 -> "Átirányítás elutasítva; ellenőrizd a HTTPS címet."
                else -> "Home Assistant API-hiba: " + status
            })
            return connection.inputStream.use { stream ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= MAX_RESPONSE) { "Túl nagy Home Assistant válasz." }
                    output.write(buffer, 0, n)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }

    fun discover(config: JarvisHomeConfig): List<JarvisHomeEntity> {
        val items = JSONArray(request(config, "GET", "/api/states"))
        val found = ArrayList<JarvisHomeEntity>()
        for (i in 0 until items.length()) {
            JarvisHomePolicy.asEntity(items.optJSONObject(i) ?: continue)?.let {
                if (found.size < 300) found.add(it)
            }
        }
        return found
    }
    fun state(config: JarvisHomeConfig, id: String): JarvisHomeEntity? {
        require(JarvisHomePolicy.visible(id))
        return JarvisHomePolicy.asEntity(JSONObject(request(config, "GET", "/api/states/" + id)))
            ?.takeIf { it.id == id }
    }
    /** Never mark a POST as a confirmed physical action without updated HA state. */
    fun toggle(config: JarvisHomeConfig, id: String, on: Boolean): String {
        if (!JarvisHomePolicy.toggleAllowed(id))
            return "Az eszköz csak állapotlekérdezésre használható."
        val before = state(config, id) ?: return "Az eszköz nem található."
        if (before.state in setOf("unavailable", "unknown"))
            return "Az eszköz nem elérhető a Home Assistant szerint."
        val domain = id.substringBefore('.')
        request(config, "POST", "/api/services/" + domain +
            (if (on) "/turn_on" else "/turn_off"), JSONObject().put("entity_id", id))
        repeat(3) { attempt ->
            if (attempt > 0) Thread.sleep(550L)
            val actual = state(config, id)?.state
            val verified = if (on && domain == "media_player")
                actual in setOf("on", "playing", "paused", "idle", "standby")
            else actual == (if (on) "on" else "off")
            if (verified) return "Home Assistant visszaigazolta: " +
                before.name + " – " + (if (on) "be" else "ki") + "."
        }
        return "A kérést átadtam, de a tényleges új állapot nem igazolt."
    }
}

internal object JarvisHomeCommands {
    data class Toggle(val target: String, val on: Boolean)
    fun normalize(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT),
        Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""[^\p{L}0-9 ]+"""), " ").trim()
        .replace(Regex("""\s+"""), " ")

    fun parse(raw: String): Toggle? {
        var input = normalize(raw).removePrefix("jarvis ").trim()
        if (input.startsWith("kerlek ")) input = input.removePrefix("kerlek ")
        val prefixes = listOf(
            "kapcsold be " to true, "kapcsold ki " to false,
            "turn on " to true, "turn off " to false,
            "switch on " to true, "switch off " to false,
            "schalte ein " to true, "schalte aus " to false,
            "allume " to true, "eteins " to false,
            "enciende " to true, "apaga " to false,
            "accendi " to true, "spegni " to false,
            "wlacz " to true, "wylacz " to false
        )
        val prefix = prefixes.firstOrNull { input.startsWith(it.first) } ?: return null
        val target = input.removePrefix(prefix.first)
            .replace(Regex("""^(?:a|az|the|le|la|el|il|lo|die|das)\s+"""), "")
            .trim()
        if (target.length !in 2..90 ||
            listOf("mikrofon", "telefon", "bluetooth", "wifi", "kepernyo",
                "hangero", "minden ", "osszes ", "all ", "everything",
                "every device", "whole house").any { target.startsWith(it) }) return null
        return Toggle(target, prefix.second)
    }
    /** Read-only status queries also work for washers and microwaves. */
    fun statusTarget(raw: String): String? {
        val input = normalize(raw).removePrefix("jarvis ").trim()
        val starts = listOf("mi a ", "hogy all a ", "hogy all az ",
            "milyen allapotban van a ", "milyen allapotban van az ",
            "what is the status of ", "show status of ", "status of ",
            "status von ", "etat de ", "estado de ", "stato di ", "stan ")
        val start = starts.firstOrNull { input.startsWith(it) } ?: return null
        val target = input.removePrefix(start).replace(
            Regex("""(?: allapota| statusza| status| allapot)$"""), "").trim()
        return target.takeIf { it.length in 2..90 }
    }
    fun match(action: Toggle, devices: List<JarvisHomeEntity>): JarvisHomeEntity? {
        val matched = devices.filter {
            val label = normalize(it.name)
            val idLabel = normalize(it.id.substringAfter('.').replace('_', ' '))
            action.target == label || action.target == idLabel ||
                action.target.endsWith("t") && action.target.dropLast(1) == label
        }
        return matched.singleOrNull()
    }
}
