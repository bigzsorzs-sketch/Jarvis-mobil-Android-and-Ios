package ai.jarvis.mobile

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale

/**
 * Offline, deliberately explicit voice intent. Avoids interpreting ordinary
 * questions about diabetes, treatment or reports as "what is my glucose now".
 */
internal object JarvisGlucoseVoice {
    private val patterns = listOf(
        Regex("""\b(nez(d)? meg|mennyi|mutasd|ellenorizd|mi)[a-z\s]{0,35}\b(cukrom|vercukrom|vercukorszintem|glukozom)\b"""),
        Regex("""\b(cukrom|vercukrom|vercukorszintem|glukozom)[a-z\s]{0,35}\b(mennyi|most|nezd|mutasd|erteke)\b"""),
        Regex("""\b(check|show|tell me|what is|what's)[a-z\s]{0,35}\b(my glucose|my blood sugar|my sugar level)\b"""),
        Regex("""\b(wie hoch|pruf|zeige)[a-z\s]{0,35}\b(mein blutzucker|meinen blutzucker|meine glukose)\b"""),
        Regex("""\b(verifie|montre|combien)[a-z\s]{0,35}\b(ma glycemie|mon taux de sucre)\b"""),
        Regex("""\b(comprueba|muestra|cuanto)[a-z\s]{0,35}\b(mi glucosa|mi azucar)\b"""),
        Regex("""\b(controlla|mostra|quanto)[a-z\s]{0,35}\b(la mia glicemia|mio zucchero)\b"""),
        Regex("""\b(sprawdz|pokaz|ile)[a-z\s]{0,35}\b(moj cukier|moja glukoza)\b""")
    )
    private fun normalize(input: String) =
        Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    fun isQuery(raw: String): Boolean {
        val q = normalize(raw)
        return q.length in 5..150 && patterns.any { it.containsMatchIn(q) }
    }

    private fun local(context: Context): JarvisGlucoseData.Point? = runCatching {
        val j = JSONArray(SecurePrefs(context).read("readings", "[]"))
        val readings = (0 until j.length()).map { i ->
            j.getJSONObject(i).let {
                Reading(it.optString("v"), it.optString("t"), it.optString("src", "manual"))
            }
        }
        JarvisGlucoseData.points(readings).lastOrNull()
    }.getOrNull()

    /** Always refresh the authorized source; never report stale data as current. */
    suspend fun reply(context: Context, language: JarvisLanguage = JarvisLanguageSettings.load(context)): String {
        val synced = JarvisHealthGlucose.latest(context)
        val cached = local(context)
        val latest = listOfNotNull(synced, cached).maxByOrNull { it.timestamp }
        val template = when (language) {
            JarvisLanguage.HUNGARIAN -> listOf(
                "Nem érhető el vércukorérték a csatlakoztatott forrásokból vagy a Jarvis naplójából.",
                "A legutóbbi mentett mérés nem friss: ",
                "A legfrissebb elérhető vércukorérték: ",
                ". Mérve: ",
                ". Forrás: ",
                "Health Connect", "LibreView import", "Jarvis kézi napló")
            JarvisLanguage.ENGLISH -> listOf(
                "No glucose reading is available from connected sources or Jarvis history.",
                "The last saved reading is not current: ",
                "The most recent available glucose reading is ",
                ". Recorded: ", ". Source: ",
                "Health Connect", "LibreView import", "Jarvis manual log")
            JarvisLanguage.GERMAN -> listOf(
                "Kein Glukosewert aus verbundenen Quellen oder Jarvis verfügbar.",
                "Der letzte gespeicherte Wert ist nicht aktuell: ",
                "Der neueste verfügbare Glukosewert ist ",
                ". Gemessen: ", ". Quelle: ",
                "Health Connect", "LibreView-Import", "Jarvis manuelles Tagebuch")
            JarvisLanguage.FRENCH -> listOf(
                "Aucune mesure de glycémie disponible dans les sources connectées ou Jarvis.",
                "La dernière mesure n'est pas actuelle : ",
                "La mesure de glycémie la plus récente est ",
                ". Enregistrée : ", ". Source : ",
                "Health Connect", "Import LibreView", "Journal manuel Jarvis")
            JarvisLanguage.SPANISH -> listOf(
                "No hay datos de glucosa de fuentes conectadas ni de Jarvis.",
                "La última medición no es reciente: ",
                "La última glucosa disponible es ",
                ". Medida: ", ". Origen: ",
                "Health Connect", "Importación LibreView", "Registro manual Jarvis")
            JarvisLanguage.ITALIAN -> listOf(
                "Nessuna lettura glicemica disponibile nelle fonti collegate o in Jarvis.",
                "L'ultima misura salvata non è recente: ",
                "L'ultima misura glicemica disponibile è ",
                ". Registrata: ", ". Origine: ",
                "Health Connect", "Importazione LibreView", "Registro manuale Jarvis")
            JarvisLanguage.POLISH -> listOf(
                "Brak dostępnego pomiaru glukozy w połączonych źródłach lub Jarvis.",
                "Ostatni zapisany wynik nie jest aktualny: ",
                "Najnowsza dostępna glukoza to ",
                ". Zmierzono: ", ". Źródło: ",
                "Health Connect", "Import LibreView", "Ręczny dziennik Jarvis")
        }
        if (latest == null) return template[0]
        val age = System.currentTimeMillis() - latest.timestamp
        val source = when (latest.source) {
            "health-connect" -> template[5]
            "libreview-historic", "libreview-scan" -> template[6]
            else -> template[7]
        }
        val time = JarvisGlucoseData.normalizeStamp(latest.timestamp)
        val value = JarvisGlucoseData.shownValue(latest.mmol, false) + " mmol/L"
        val prefix = if (age in 0..600_000L) template[2] else template[1]
        return prefix + value + template[3] + time + template[4] + source + "."
    }
}
