package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * Completed local device actions should not spam the chat timeline. Always
 * retain questions, confirmation prompts, errors and uncertain outcomes.
 * The user can still see the result in the destination app or tool screen.
 */
internal object JarvisQuietActions {
    private fun n(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "").trim()

    fun hideSuccessfulTurn(command: String, response: String): Boolean {
        val q = n(command)
        val r = n(response)
        if ("amazon" in q && r.startsWith("megnyitottam") &&
            (r.contains("amazon") || r.contains("talalatait"))) return true
        if (q.isBlank() || r.isBlank() || "?" in response ||
            listOf("nem sikerult", "nem talal", "nem tortent", "nincs",
                "hiba", "sikertelen", "bizonytalan", "nem indult", "nem tudom",
                "lejar", "engedely", "megerosit", "jo vahagy",
                "varom", "mondd", "melyik", "mi legyen", "add meg").any { it in r })
            return false

        return when {
            ("amazon" in q) &&
                (r.startsWith("megnyitottam az amazon") ||
                 r.startsWith("megnyitottam az ") && "talalatait" in r ||
                 r.startsWith("megnyitottam az amazon termekoldalat")) -> true
            r.startsWith("a gmail elfogadta az emailt") ||
                r.startsWith("a gmail elfogadta a valaszt") -> true
            r.startsWith("a hivaskerest atadtam az androidnak") -> true
            // A successful map handoff was already done in Android. Do not
            // modify the route/destination or misreport a launch as navigation.
            (q.contains("navigalj") || q.contains("vigyel") || q.contains("navigacio")) &&
                (r.contains("navigacio elindult") || r.contains("google maps elindult")) -> true
            else -> false
        }
    }
}
