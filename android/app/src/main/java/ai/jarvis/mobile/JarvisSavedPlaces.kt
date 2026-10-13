package ai.jarvis.mobile

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.text.Normalizer
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Imported copy of the user's Google Takeout places, not live Google account access. */
internal data class JarvisSavedPlace(val name: String, val destination: String)

internal object JarvisSavedPlaces {
    private const val FIELD = "saved_places_import_v1"
    private const val MAX_FILE_BYTES = 2_000_000
    private const val MAX_PLACES = 1000

    private fun normalized(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .trim().replace(Regex("""\s+"""), " ")

    /** A local import is required; app permissions and a Maps API key cannot read private Maps lists. */
    fun list(context: Context): List<JarvisSavedPlace> = runCatching {
        val items = JSONArray(SecurePrefs(context).read(FIELD, "[]"))
        (0 until minOf(items.length(), MAX_PLACES)).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            val target = item.optString("destination").trim()
            if (name.isEmpty() || name.length > 120 || target.isEmpty() || target.length > 180)
                null else JarvisSavedPlace(name, target)
        }
    }.getOrDefault(emptyList())

    fun isListRequest(command: String): Boolean {
        val s = normalized(command)
        if (s.isEmpty() || listOf("ne ", "nem ", "dont ", "do not ").any { s.startsWith(it) })
            return false
        val subject = listOf("csillagozott", "mentett hely", "mentett lista", "kedvenc hely",
            "saved place", "starred place", "saved list").any { it in s }
        val verb = listOf("mutasd", "listazd", "nezd", "nyisd", "keresd", "melyik",
            "milyen", "hol", "latom", "meg tudod nezni", "le tudod kerdezni",
            "show", "list", "find", "open", "what are", "can you show").any { it in s }
        return subject && verb
    }

    /** Recognises references to the imported list, not arbitrary person or city names. */
    fun importedName(destination: String): String? {
        val match = Regex(
            """(?iu)^(?:a\s+|az\s+)?(?:mentett|csillagozott|kedvenc)\s+(?:hely\s+)?(.+?)(?:\s+(?:helyre|helyhez|címére|cimere))?$"""
        ).matchEntire(destination.trim()) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }

    fun resolveImported(context: Context, query: String): String? {
        val search = normalized(query)
        if (search.isBlank()) return null
        val entries = list(context)
        // Never silently choose one of several similarly named saved places.
        val exact = entries.filter { normalized(it.name) == search }
        if (exact.size == 1) return exact.single().destination
        if (exact.isNotEmpty()) return null
        return entries.singleOrNull { normalized(it.name).contains(search) }?.destination
    }

    private fun csvRows(input: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var cells = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        fun cell() { cells.add(current.toString()); current.setLength(0) }
        fun row() { cell(); rows.add(cells); cells = ArrayList() }
        while (index < input.length && rows.size <= MAX_PLACES + 2) {
            val c = input[index]
            when {
                c == '"' && quoted && index + 1 < input.length && input[index + 1] == '"' -> {
                    current.append('"'); index++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> cell()
                (c == '\n' || c == '\r') && !quoted -> {
                    row()
                    if (c == '\r' && index + 1 < input.length && input[index + 1] == '\n') index++
                }
                else -> current.append(c)
            }
            index++
        }
        require(!quoted) { "Hibás CSV idézőjel." }
        if (current.isNotEmpty() || cells.isNotEmpty()) row()
        return rows
    }

    fun parseCsv(input: String): List<JarvisSavedPlace> {
        val rows = csvRows(input.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { normalized(it) }
        val title = header.indexOfFirst { it in setOf("title", "name", "nev", "cim") }
        val address = header.indexOfFirst { it in setOf("address", "location address", "cimzett") }
        if (title < 0) return emptyList()
        return rows.drop(1).take(MAX_PLACES).mapNotNull { row ->
            val name = row.getOrNull(title)?.trim()?.take(120).orEmpty()
            val where = row.getOrNull(address)?.trim()?.take(180)
                .orEmpty().ifBlank { name }
            if (name.isBlank() || where.isBlank()) null else JarvisSavedPlace(name, where)
        }.distinctBy { normalized(it.name) + "|" + normalized(it.destination) }
    }

    fun parseGeoJson(input: String): List<JarvisSavedPlace> {
        val root = JSONObject(input)
        val features = root.optJSONArray("features") ?: return emptyList()
        val places = ArrayList<JarvisSavedPlace>()
        for (i in 0 until minOf(features.length(), MAX_PLACES)) {
            val feature = features.optJSONObject(i) ?: continue
            if (feature.optString("type") != "Feature") continue
            val props = feature.optJSONObject("properties") ?: JSONObject()
            val location = props.optJSONObject("location") ?: feature.optJSONObject("location")
            val name = (location?.optString("name").orEmpty().ifBlank {
                props.optString("name").ifBlank { props.optString("title") }
            }).trim().take(120)
            val address = location?.optString("address").orEmpty()
                .ifBlank { props.optString("address") }.trim().take(180)
            val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates")
            val lat = coords?.optDouble(1, Double.NaN) ?: Double.NaN
            val lon = coords?.optDouble(0, Double.NaN) ?: Double.NaN
            val destination = if (lat.isFinite() && lon.isFinite() &&
                lat in -90.0..90.0 && lon in -180.0..180.0 &&
                (lat != 0.0 || lon != 0.0)) "$lat,$lon"
            else address.ifBlank { name }
            if (name.isNotBlank() && destination.isNotBlank()) {
                places.add(JarvisSavedPlace(name, destination))
            }
        }
        return places.distinctBy { normalized(it.name) + "|" + it.destination }
    }

    /** Android SAF URI, at most 2 MB. No Google credentials or import contents leave the phone. */
    fun importFile(context: Context, uri: Uri): Int {
        val resolver = context.contentResolver
        val fileName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            .orEmpty().lowercase(Locale.ROOT)
        require(fileName.endsWith(".csv") || fileName.endsWith(".geojson") ||
            fileName.endsWith(".json")) { "Csak Google Takeout CSV vagy GeoJSON fájl importálható." }
        val bytes = resolver.openInputStream(uri)?.use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                require(total <= MAX_FILE_BYTES) { "Túl nagy fájl (maximum 2 MB)." }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("A fájl nem olvasható.")
        val content = bytes.toString(Charsets.UTF_8)
        val found = if (fileName.endsWith(".csv")) parseCsv(content) else parseGeoJson(content)
        require(found.isNotEmpty()) { "Nem találtam támogatott mentett helyeket a fájlban." }
        val combined = (list(context) + found).distinctBy {
            normalized(it.name) + "|" + it.destination
        }.take(MAX_PLACES)
        val json = JSONArray()
        combined.forEach { json.put(JSONObject().put("name", it.name).put("destination", it.destination)) }
        SecurePrefs(context).write(FIELD, json.toString())
        return found.size
    }
}
