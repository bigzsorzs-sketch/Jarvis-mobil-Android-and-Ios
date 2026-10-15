package ai.jarvis.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/** Temporary developer-test access only. Samsung's personal tokens expire in 24h.
 * Commercial Jarvis releases require SmartThings OAuth2 service integration.
 */
internal data class JarvisSmartThingsAccess(val token: String)
internal data class JarvisSmartThingsDevice(
    val id: String,
    val name: String,
    val category: String,
    val canSwitchLight: Boolean,
    val state: String = "unknown"
)

internal object JarvisSmartThingsPolicy {
    private val idPattern = Regex("^[a-fA-F0-9-]{36}$")
    const val MAX_DEVICES = 100
    const val MAX_TOKEN_AGE = 24L * 60 * 60 * 1000L

    fun validId(raw: String) = raw.length == 36 && idPattern.matches(raw) &&
        runCatching { UUID.fromString(raw) }.isSuccess
    fun classify(item: JSONObject): JarvisSmartThingsDevice? {
        val id = item.optString("deviceId")
        if (!validId(id)) return null
        val name = item.optString("label").ifBlank {
            item.optString("name").ifBlank { "SmartThings device" }
        }.take(90)
        val main = item.optJSONArray("components")
            ?.let { components ->
                (0 until components.length()).mapNotNull {
                    components.optJSONObject(it)
                }.firstOrNull { it.optString("id") == "main" }
            }
        val categories = main?.optJSONArray("categories") ?: JSONArray()
        val isLight = (0 until categories.length()).any {
            categories.optJSONObject(it)?.optString("name")
                ?.lowercase(Locale.ROOT) == "light"
        }
        val capabilities = main?.optJSONArray("capabilities") ?: JSONArray()
        val hasSwitch = (0 until capabilities.length()).any {
            capabilities.optJSONObject(it)?.optString("id") == "switch"
        }
        val category = if (isLight) "light" else
            categories.optJSONObject(0)?.optString("name")?.take(40).orEmpty()
                .ifBlank { "other" }
        return JarvisSmartThingsDevice(id, name, category, isLight && hasSwitch)
    }
    fun switchValue(json: JSONObject): String? =
        json.optJSONObject("components")?.optJSONObject("main")
            ?.optJSONObject("switch")?.optJSONObject("switch")
            ?.optString("value")?.takeIf { it == "on" || it == "off" }
    /** A restricted set of read-only public capability values, never control fields.
     * Some Samsung appliance models expose other vendor-specific status keys:
     * if unsupported, show unknown rather than inferring a running cycle.
     */
    fun statusSummary(json: JSONObject): String {
        val main = json.optJSONObject("components")?.optJSONObject("main") ?: return "unknown"
        val permitted = listOf(
            "washerOperatingState" to "machineState",
            "dishwasherOperatingState" to "machineState",
            "dryerOperatingState" to "machineState",
            "mediaPlayback" to "playbackStatus",
            "tvChannel" to "tvChannel",
            "temperatureMeasurement" to "temperature",
            "contactSensor" to "contact",
            "battery" to "battery",
            "switch" to "switch"
        )
        for ((capability, attribute) in permitted) {
            val value = main.optJSONObject(capability)?.optJSONObject(attribute)
                ?.opt("value") ?: continue
            if (value is String || value is Number || value is Boolean) {
                val trimmed = value.toString().trim().take(60)
                if (trimmed.isNotBlank() && trimmed != "null")
                    return capability + ": " + trimmed
            }
        }
        return "unknown"
    }
    fun command(device: JarvisSmartThingsDevice, on: Boolean): JSONObject {
        require(device.canSwitchLight && device.category == "light" &&
            validId(device.id)) { "Only a verified Light category may be switched." }
        return JSONObject().put("commands", JSONArray().put(
            JSONObject().put("component", "main").put("capability", "switch")
                .put("command", if (on) "on" else "off")
                .put("arguments", JSONArray())
        ))
    }
}

internal object JarvisSmartThings {
    private const val TOKEN_FIELD = "smartthings_test_pat_2026"
    private const val EXPIRY_FIELD = "smartthings_test_pat_expires"
    private const val BASE = "https://api.smartthings.com/v1"
    private const val MAX_REPLY = 750_000

    fun saveTestToken(context: Context, provided: String) {
        val token = provided.trim()
        require(token.length in 20..4096 && !token.any { it == '\n' || it == '\r' }) {
            "Invalid temporary SmartThings access token."
        }
        SecurePrefs(context).write(TOKEN_FIELD, token)
        SecurePrefs(context).write(EXPIRY_FIELD,
            (System.currentTimeMillis() + JarvisSmartThingsPolicy.MAX_TOKEN_AGE).toString())
    }
    fun revoke(context: Context) {
        SecurePrefs(context).write(TOKEN_FIELD, "")
        SecurePrefs(context).write(EXPIRY_FIELD, "")
    }
    fun loadTestToken(context: Context): JarvisSmartThingsAccess? = runCatching {
        val prefs = SecurePrefs(context)
        val expiry = prefs.read(EXPIRY_FIELD, "0").toLongOrNull() ?: 0L
        if (expiry <= System.currentTimeMillis()) return null
        val token = prefs.read(TOKEN_FIELD, "")
        token.takeIf { it.length in 20..4096 }?.let { JarvisSmartThingsAccess(it) }
    }.getOrNull()

    private fun api(access: JarvisSmartThingsAccess, path: String,
                    body: JSONObject? = null): JSONObject {
        require(path.matches(Regex("""^/(?:devices)(?:/[a-fA-F0-9-]{36})?(?:/(?:status|commands))?$"""))) {
            "Unsupported SmartThings endpoint."
        }
        require(access.token.length in 20..4096 && access.token.none { it == '\n' || it == '\r' })
        val connection = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer " + access.token)
            connection.setRequestProperty("Accept", "application/json")
            connection.requestMethod = if (body == null) "GET" else "POST"
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException(when (code) {
                401, 403 -> "SmartThings rejected or expired your token. Reconnect."
                429 -> "SmartThings request limit reached. Try later."
                in 300..399 -> "SmartThings redirect rejected for token safety."
                else -> "SmartThings API error " + code
            })
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(output.size() + n <= MAX_REPLY) { "SmartThings response too large." }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            return if (bytes.isEmpty()) JSONObject()
            else JSONObject(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    fun list(access: JarvisSmartThingsAccess): List<JarvisSmartThingsDevice> {
        val response = api(access, "/devices")
        val items = response.optJSONArray("items") ?: return emptyList()
        return (0 until minOf(items.length(), JarvisSmartThingsPolicy.MAX_DEVICES))
            .mapNotNull { JarvisSmartThingsPolicy.classify(items.optJSONObject(it) ?: return@mapNotNull null) }
    }
    fun state(access: JarvisSmartThingsAccess, device: JarvisSmartThingsDevice): String {
        require(JarvisSmartThingsPolicy.validId(device.id))
        val response = api(access, "/devices/" + device.id + "/status")
        return JarvisSmartThingsPolicy.switchValue(response) ?: "unknown"
    }
    fun readStatus(access: JarvisSmartThingsAccess, device: JarvisSmartThingsDevice): String {
        require(JarvisSmartThingsPolicy.validId(device.id))
        return JarvisSmartThingsPolicy.statusSummary(
            api(access, "/devices/" + device.id + "/status"))
    }

    /** POST 200 means accepted, NOT executed; follow with actual status. */
    fun switchLight(access: JarvisSmartThingsAccess, device: JarvisSmartThingsDevice,
                    on: Boolean): String {
        val payload = JarvisSmartThingsPolicy.command(device, on)
        val before = state(access, device)
        if (before == "unknown") return "The light state is not verifiable."
        api(access, "/devices/" + device.id + "/commands", payload)
        repeat(3) { index ->
            if (index > 0) Thread.sleep(600)
            if (state(access, device) == if (on) "on" else "off")
                return device.name + ": light state confirmed by SmartThings."
        }
        return "Command sent but SmartThings has not confirmed the actual light state."
    }
}
