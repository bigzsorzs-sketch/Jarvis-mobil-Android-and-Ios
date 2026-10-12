package ai.jarvis.mobile

import android.content.Context

/** Two explicitly requested steps, with no model-supplied payloads. */
internal data class JarvisSequencePlan(
    val original: String,
    val first: String,
    val second: String,
    val app: String,
    val alarm: AlarmTime
)

internal object JarvisSequence {
    private val separator = Regex("""(?iu)\s*,?\s+(?:majd|aztán|and\s+then)\s+""")

    fun plan(original: String): JarvisSequencePlan? {
        if (original.length > 500) return null
        val clean = JarvisNativeActions.stripped(original)
        val fragments = separator.split(clean)
        if (fragments.size != 2) return null
        val first = fragments[0].trim()
        val second = fragments[1].trim()
        if (JarvisNativeActions.identify(first) != JarvisNativeActions.SET_ALARM)
            return null
        val time = AlarmCommands.parseAlarmTime(first) ?: return null
        val matches = JarvisPhoneActions.supported.filter {
            JarvisPhoneActions.authorized(it, second)
        }
        if (matches.size != 1) return null
        return JarvisSequencePlan(original, first, second, matches.single(), time)
    }

    /** Revalidate on confirmation and stop on any failure of the first step. */
    fun execute(context: Context, requested: JarvisSequencePlan): String {
        val verified = plan(requested.original)
            ?: return "A műveletsor nem érvényes. Semmit nem hajtottam végre."
        if (verified != requested) {
            return "A műveletsor megváltozott. Semmit nem hajtottam végre."
        }
        return when (val saved = JarvisAlarms.schedule(context, verified.alarm)) {
            is JarvisAlarms.Result.Created -> {
                val next = JarvisPhoneActions.execute(context, verified.app)
                "1/2: Jarvis-ébresztő " + saved.time + " időpontra beállítva. 2/2: " + next
            }
            JarvisAlarms.Result.NoExactAlarmPermission ->
                "Az első lépéshez hiányzik a pontos ébresztési engedély; a második nem indult."
            JarvisAlarms.Result.NoNotificationPermission ->
                "Az első lépéshez hiányzik az értesítési engedély; a második nem indult."
            JarvisAlarms.Result.Failed ->
                "Az ébresztő nem jött létre; a második lépés nem indult."
        }
    }
}
