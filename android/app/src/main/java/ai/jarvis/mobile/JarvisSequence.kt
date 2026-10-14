package ai.jarvis.mobile

import android.content.Context
import java.util.Calendar

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

    /** Only the local alarm state can be verified. An external Android app
     * handoff is never proof that a third-party task was actually completed.
     */
    fun alarmMatches(savedAtMs: Long, time: AlarmTime, nowMs: Long): Boolean {
        if (savedAtMs <= nowMs || savedAtMs > nowMs + 25L * 60 * 60 * 1000) return false
        val calendar = Calendar.getInstance().apply { timeInMillis = savedAtMs }
        return calendar.get(Calendar.HOUR_OF_DAY) == time.hour &&
            calendar.get(Calendar.MINUTE) == time.minute
    }

    /** Revalidate on confirmation, verify persistence, stop on failure. */
    fun execute(context: Context, requested: JarvisSequencePlan): String {
        val verified = plan(requested.original)
            ?: return "A műveletsor nem érvényes. Semmit nem hajtottam végre."
        if (verified != requested) {
            return "A műveletsor megváltozott. Semmit nem hajtottam végre."
        }
        return when (val saved = JarvisAlarms.schedule(context, verified.alarm)) {
            is JarvisAlarms.Result.Created -> {
                if (!alarmMatches(JarvisAlarms.current(context), verified.alarm,
                        System.currentTimeMillis())) {
                    return "Az ébresztő mentését nem sikerült visszaellenőrizni; " +
                        "a következő lépést nem hajtottam végre."
                }
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
