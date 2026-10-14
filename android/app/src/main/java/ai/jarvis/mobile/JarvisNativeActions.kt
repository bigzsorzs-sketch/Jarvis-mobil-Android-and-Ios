package ai.jarvis.mobile

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** One allowlisted Android action registry shared by text, voice and model tools.
 * Device arguments come from the original user request, never the model.
 */
internal object JarvisNativeActions {
    const val NAVIGATE = "navigate"
    const val SET_ALARM = "set_alarm"
    const val CANCEL_ALARM = "cancel_alarm"
    const val COMPOSE_EMAIL = "compose_email"
    const val GMAIL_SEARCH = "gmail_search"
    const val UC_PORTAL = "uc_portal"

    val supported = listOf(NAVIGATE, SET_ALARM, CANCEL_ALARM,
        COMPOSE_EMAIL, GMAIL_SEARCH, UC_PORTAL)

    private fun normalized(raw: String): String =
        Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim().replace(Regex("\\s+"), " ")

    fun stripped(raw: String): String = raw.trim()
        .replace(Regex("""(?iu)^(?:(?:hey|hej|szia|ok|okay)\s+)?jarvis\s*[,!:]?\s*"""), "")
        .replace(Regex("""(?iu)^k[eé]rlek\s+"""), "")
        .trim()

    /** Strict original-intent gate. Do not silently pick one side effect from a batch. */
    fun identify(original: String): String? {
        val command = stripped(original)
        val n = normalized(command)
        if (command.isEmpty() || command.length > 8000 ||
            listOf("ne ", "nem ", "dont ", "don't ", "never ", "can ", "tudsz ",
                "hogyan ", "mit ", "miert ").any { n.startsWith(it) }) return null
        if (Regex("""(?iu)\s+(?:és|and|then|majd)\s+(?:nyisd|hívd|hivd|küldj|írj|irj|töröld|torold|állítsd|allitsd|navigálj|navigalj)\b""")
                .containsMatchIn(command)) return null

        if (Regex("""^(?:t[oö]r[oö]ld|kapcsold\s+ki)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(command) &&
            ("ebreszt" in n || "alarm" in n)) return CANCEL_ALARM

        if (Regex("""^(?:[aá]ll[ií]tsd\s+be|[eé]bressz|[eé]breszt[oő]t\s+[aá]ll[ií]ts|set\s+alarm|kelts)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(command) &&
            AlarmCommands.parseAlarmTime(command) != null) return SET_ALARM

        if (VoiceNavigation.destinationFrom(command) != null) return NAVIGATE

        if (Regex("""^(?:[ií]rj|k[uü]ldj|k[eé]sz[ií]ts)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(command) &&
            VoiceEmail.parse(command) != null) return COMPOSE_EMAIL

        if (ExternalTasks.parseGmailSearch(command) != null) return GMAIL_SEARCH

        if (Regex("""^(?:nyisd|n[eé]zd|keresd|open)\b""",
                RegexOption.IGNORE_CASE).containsMatchIn(command) &&
            ExternalTasks.parseUcJournal(command)) return UC_PORTAL

        return null
    }

    fun validate(kind: String, original: String): Boolean =
        kind in supported && identify(original) == kind

    /** Handoff success is not proof of completion in the target application. */
    fun execute(context: Context, kind: String, original: String): String {
        if (!validate(kind, original)) {
            return "A művelet nem egyezik az eredeti parancsoddal. Nem történt végrehajtás."
        }
        val command = stripped(original)
        return when (kind) {
            NAVIGATE -> {
                val target = VoiceNavigation.destinationFrom(command)
                    ?: return "Nem azonosítható a navigáció célpontja."
                if (VoiceNavigation.resolve(context, target) == null &&
                    !VoiceNavigation.maySearchNamedPlace(target)) {
                    "Ehhez a célponthoz nincs mentett cím."
                } else when (VoiceNavigation.openDetailed(context, target)) {
                    VoiceNavigation.LaunchResult.SAVED_NAME_SEARCH_OPENED ->
                        "Google Maps névkeresés megnyitva. Válaszd ki a mentett helyet; navigáció nem indult."
                    VoiceNavigation.LaunchResult.GUIDANCE_REQUESTED ->
                        "A navigációindítási kérést átadtam a Google Mapsnek; az indulás nem igazolt."
                    VoiceNavigation.LaunchResult.DIRECTIONS_OPENED ->
                        "Az útvonalnézet megnyílt; a navigáció indítása még szükséges lehet."
                    VoiceNavigation.LaunchResult.UNAVAILABLE ->
                        "Nem sikerült megnyitni a térképalkalmazást."
                }
            }
            SET_ALARM -> when (val result = JarvisAlarms.schedule(context,
                AlarmCommands.parseAlarmTime(command)
                    ?: return "Érvénytelen ébresztési idő.")) {
                is JarvisAlarms.Result.Created ->
                    "A Jarvis-ébresztőt " + result.time + " időpontra időzítettem. Ellenőrizd a telefonon."
                JarvisAlarms.Result.NoExactAlarmPermission ->
                    "A pontos ébresztéshez Android-engedély szükséges."
                JarvisAlarms.Result.NoNotificationPermission ->
                    "Az ébresztő értesítéseihez engedély szükséges."
                JarvisAlarms.Result.Failed -> "Nem sikerült létrehozni az ébresztőt."
            }
            CANCEL_ALARM -> if (JarvisAlarms.cancel(context))
                "A Jarvis saját ébresztőjét töröltem."
                else "Nem találtam törölhető Jarvis-ébresztőt."
            COMPOSE_EMAIL -> {
                val email = VoiceEmail.parse(command)
                    ?: return "Nem érthető az emailkérés."
                if (VoiceEmail.openComposer(context, email))
                    "Megnyitottam az email-piszkozatot. Küldés nem történt."
                else "Nem található kompatibilis levelezőalkalmazás."
            }
            GMAIL_SEARCH -> ExternalTasks.dispatch(context,
                HandoffTask.GmailSearch(ExternalTasks.parseGmailSearch(command)
                    ?: return "Nem azonosítható a Gmail-keresés."))
            UC_PORTAL -> ExternalTasks.dispatch(context, HandoffTask.UcJournal)
            else -> "Nem támogatott művelet."
        }
    }
}
