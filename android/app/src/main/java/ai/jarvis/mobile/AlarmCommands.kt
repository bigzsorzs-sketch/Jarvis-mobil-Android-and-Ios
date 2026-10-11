package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import java.text.Normalizer
import java.util.Locale

internal data class AlarmTime(val hour: Int, val minute: Int)

internal object AlarmCommands {
    private fun normal(text: String) = Normalizer.normalize(
        text.lowercase(Locale.forLanguageTag("hu-HU")), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")

    /** Android's generic clock intent cannot schedule a specific calendar date. */
    fun parseAlarmTime(command: String): AlarmTime? {
        val text = normal(command)
        if (listOf("torold", "torles", "kapcsold ki", "kikapcsol", "nyisd meg", "mutasd").any { it in text }) return null
        if (hasUnsupportedDate(command)) return null
        if (!listOf("ebreszt", "ebressz", "kelts", "alarm", "set alarm").any { it in text }) return null
        val clock = Regex("(?<!\\d)(\\d{1,2})\\s*[:.]\\s*(\\d{1,2})(?!\\d)").find(text)
        val spelled = Regex("(?<!\\d)(\\d{1,2})\\s*ora(?:ra|kor|t)?(?:\\s*(\\d{1,2})\\s*perc(?:re|kor|et)?)?").find(text)
        val half = Regex("\\bfel\\s+(\\d{1,2})\\b").find(text)
        var hour: Int
        var minute: Int
        when {
            clock != null -> {
                hour = clock.groupValues[1].toInt()
                minute = clock.groupValues[2].toInt()
            }
            spelled != null -> {
                hour = spelled.groupValues[1].toInt()
                minute = spelled.groupValues.getOrElse(2) { "" }.toIntOrNull() ?: 0
            }
            half != null -> {
                hour = half.groupValues[1].toInt() - 1
                minute = 30
            }
            else -> return null
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        if ("este" in text || "delutan" in text) {
            if (hour in 1..11) hour += 12
        }
        if ("reggel" in text || "hajnal" in text) {
            if (hour == 12) hour = 0
        }
        return AlarmTime(hour, minute)
    }

    fun hasUnsupportedDate(text: String): Boolean =
        listOf("holnap", "holnaputan", "jovo heten").any { it in normal(text) }

    /**
     * Android Clock app handles the intent. A successful startActivity does not
     * prove the clock app actually created an alarm.
     */
    fun requestClockAlarm(context: Context, time: AlarmTime): Boolean =
        openSystemIntent(
            context,
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Jarvis")
        )

    fun userFacingResult(context: Context, command: String): String? {
        val time = parseAlarmTime(command) ?: return null
        val clock = String.format(Locale.UK, "%02d:%02d", time.hour, time.minute)
        return if (requestClockAlarm(context, time)) {
            "Átadtam a " + clock + " ébresztés beállítását az Óra alkalmazásnak. " +
                "A tényleges létrejöttét az órában ellenőrizd."
        } else {
            "A telefon nem fogadta el az ébresztés beállítását. Ellenőrizd az Óra alkalmazást."
        }
    }
}
