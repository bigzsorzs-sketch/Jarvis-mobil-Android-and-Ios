package ai.jarvis.mobile

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Very small set of user-directed, non-destructive Android app handoffs.
 * No SEND, CALL, account changes, file access or arbitrary intent extras.
 */
internal object JarvisPhoneActions {
    val supported = listOf("calendar", "alarms", "email", "maps", "dialer")

    private val aliases = mapOf(
        "calendar" to listOf("naptar", "calendar"),
        "alarms" to listOf("ebreszto", "ebresztok", "alarm", "alarms", "ora alkalmazas"),
        "email" to listOf("email", "e mail", "gmail", "levelezo", "mail"),
        "maps" to listOf("terkep", "maps", "navigacio", "map"),
        "dialer" to listOf("tarcsazo", "dialer", "telefon alkalmazas", "phone app")
    )

    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim().replace(Regex("\\s+"), " ")

    /** Every executed app must be both explicitly requested and named by user.
     * AI cannot substitute a different target, even if it chooses a valid enum.
     */
    fun authorized(action: String, original: String): Boolean {
        if (action !in supported || !JarvisAiTools.userAskedToOpen(original)) return false
        val words = normalize(original)
        if (Regex("\\b(ne|nem|not|don t|don't|soha|never)\\b").containsMatchIn(words)) return false
        if (Regex("\\b(torol|torold|delete|remove|erase|send|kuldd|hivas inditasa|fizess)\\b")
                .containsMatchIn(words)) return false
        val matches = aliases.filterValues { variants ->
            variants.any { alias ->
                val pattern = if (alias.contains(" ")) {
                    "(^|\\s)" + Regex.escape(alias) + "(\\s|$)"
                } else {
                    // Hungarian nouns are inflected: naptárat, térképet, ébresztőket.
                    "(^|\\s)" + Regex.escape(alias) + "[a-z]{0,7}(\\s|$)"
                }
                Regex(pattern).containsMatchIn(words)
            }
        }.keys
        return matches.size == 1 && action in matches
    }

    /** App launch/handoff is not proof that any user action was completed. */
    fun requestedAction(original: String): String? =
        supported.singleOrNull { authorized(it, original) }

    fun execute(context: Context, action: String): String = when (action) {
        "calendar" -> if (openCalendar(context)) "Átadtam a naptár megnyitását az Androidnak."
            else "Nem sikerült elindítani a naptárat."
        "alarms" -> if (openAlarms(context)) "Átadtam az ébresztőalkalmazás megnyitását az Androidnak."
            else "Nem sikerült megnyitni az ébresztőalkalmazást."
        "email" -> if (openEmail(context)) "Megnyitottam az emailírási felületet. Üzenetet nem küldtem."
            else "Nem találok kompatibilis levelezőalkalmazást."
        "maps" -> if (openMap(context)) "Átadtam a térkép megnyitását az Androidnak."
            else "Nem találok kompatibilis térképalkalmazást."
        "dialer" -> if (openDialer(context)) "Megnyitottam a tárcsázót. Telefonhívás nem indult."
            else "Nem találok kompatibilis tárcsázót."
        else -> "Ismeretlen telefonos művelet. Nem történt végrehajtás."
    }
}

