package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.util.Locale

/**
 * Explicit deterministic local capabilities, NOT a cloud AI response.
 * Sensitive actions open the appropriate system UI for user confirmation.
 */
internal enum class BasicAction {
    OPEN_GLUCOSE, OPEN_INVOICES, OPEN_HOME, OPEN_NOTES,
    OPEN_EMAIL, OPEN_MAP, OPEN_CALENDAR, OPEN_ALARMS, OPEN_DIALER, OPEN_MESSAGES, OPEN_PERMISSIONS, UNKNOWN
}

internal object BasicCommandRouter {
    fun classify(raw: String): BasicAction {
        val input = raw.trim().lowercase(Locale.ROOT)
        if (input.isBlank()) return BasicAction.UNKNOWN
        // A topic question is NOT an instruction to open an app or trigger a tool.
        // Example: "Mit tudsz a vércukornaplóról?" must reach conversational AI.
        val questionText = input.removePrefix("hey jarvis,").removePrefix("hey jarvis ")
            .removePrefix("jarvis,").removePrefix("jarvis ").trimStart()
        if (listOf("mit ", "milyen ", "hogyan ", "miért ", "miert ",
                "mikor ", "mennyi ", "mi ", "what ", "why ", "how ",
                "when ", "which ", "tudsz ", "tudnál ", "tudnal ",
                "képes ", "kepes ", "lehet ", "can you ", "could you ",
                "meg tudod ", "van-e ", "van e ", "ne ", "nem ",
                "don't ", "do not ", "never ").any { questionText.startsWith(it) }) {
            return BasicAction.UNKNOWN
        }
        val shortcut = questionText.removePrefix("kérlek ").trim().trimEnd('.', '!')
        val bareShortcuts = setOf("vércukor", "vércukornapló", "számlázás", "számlák",
            "e-mail küldés", "whatsapp üzeneteim", "messenger", "engedélyek", "beállítások",
            "telefon jegyzetei", "térkép", "naptár", "ébresztő", "telefon", "főoldal",
            "glucose", "billing", "invoices", "email", "maps", "calendar", "alarms",
            "notes", "permissions", "settings", "home")
        // Mentioning a feature in a conversation is not a request to open it.
        val actionRequest = raw.trim().replace(Regex(
            "^(?:jó reggelt|jo reggelt|szia|helló|hello|good morning|good evening|hi)[,!:;\\s]+",
            RegexOption.IGNORE_CASE), "")
        if (!JarvisAiTools.userAskedToOpen(actionRequest) && shortcut !in bareShortcuts)
            return BasicAction.UNKNOWN
        return when {
            listOf("vércukor", "vercukor", "cukornapl", "glucose", "blood sugar").any { it in input } -> BasicAction.OPEN_GLUCOSE
            listOf("száml", "szaml", "invoice", "billing").any { it in input } -> BasicAction.OPEN_INVOICES
            listOf("whatsapp", "messenger", "üzenet", "uzenet", "sms", "telegram", "signal").any { it in input } -> BasicAction.OPEN_MESSAGES
            listOf("engedély", "engedely", "permissions", "beállítás", "beallitas", "settings")
                .any { it in input } -> BasicAction.OPEN_PERMISSIONS
            listOf("email", "e-mail", "gmail", "levelez", "levél", "level", "mail").any { it in input } -> BasicAction.OPEN_EMAIL
            listOf("térkép", "terkep", "navig", "maps", "route").any { it in input } -> BasicAction.OPEN_MAP
            listOf("naptár", "naptar", "calendar").any { it in input } -> BasicAction.OPEN_CALENDAR
            listOf("ébreszt", "ebreszt", "alarm", "riasztó").any { it in input } -> BasicAction.OPEN_ALARMS
            listOf("jegyzet", "notes", "note").any { it in input } -> BasicAction.OPEN_NOTES
            listOf("telefon", "tárcsáz", "tarcsaz", "dial", "hívás", "hivas").any { it in input } -> BasicAction.OPEN_DIALER
            listOf("főoldal", "fooldal", "home", "kezdőlap", "kezdolap").any { it in input } -> BasicAction.OPEN_HOME
            else -> BasicAction.UNKNOWN
        }
    }
}

/** Returns false if there is no compatible system handler. Never runs unapproved background actions. */
internal fun openSystemIntent(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}

internal fun openEmail(context: Context): Boolean =
    openSystemIntent(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))

internal fun openMap(context: Context): Boolean =
    openSystemIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))

internal fun openCalendar(context: Context): Boolean =
    openSystemIntent(context, Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI))

internal fun addCalendarEvent(context: Context, title: String): Boolean =
    openSystemIntent(
        context,
        Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
    )

internal fun openAlarms(context: Context): Boolean =
    openSystemIntent(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

internal fun openDialer(context: Context): Boolean =
    openSystemIntent(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))

internal fun performBasicAction(context: Context, action: BasicAction, onNavigate: (String) -> Unit): String {
    return when (action) {
        BasicAction.OPEN_MESSAGES -> { onNavigate("Üzenetek"); "Megnyitottam az üzeneteket. Értesítési hozzáférés szükséges." }
        BasicAction.OPEN_PERMISSIONS -> { onNavigate("Beállítások"); "Megnyitottam a beállításokat." }
        BasicAction.OPEN_GLUCOSE -> { onNavigate("Vércukor"); "Megnyitottam a vércukornaplót." }
        BasicAction.OPEN_INVOICES -> { onNavigate("Számlák"); "Megnyitottam a számlázást." }
        BasicAction.OPEN_HOME -> { onNavigate("Főoldal"); "Megnyitottam a főoldalt." }
        BasicAction.OPEN_NOTES -> when (JarvisExternalApps.openNotes(context)) {
            AppHandoffResult.OPENED -> "Megnyitottam a telefon jegyzetalkalmazását."
            AppHandoffResult.SEARCH_OPENED -> "Jegyzetalkalmazást keresek a Play Áruházban."
            AppHandoffResult.UNAVAILABLE -> "Nem sikerült megnyitni a telefon jegyzetalkalmazását."
        }
        BasicAction.OPEN_EMAIL -> if (openEmail(context)) "Megnyitottam az emailírást." else "Nem található emailalkalmazás."
        BasicAction.OPEN_MAP -> if (openMap(context)) "Megnyitottam a térképet." else "Nem található térképalkalmazás."
        BasicAction.OPEN_CALENDAR -> if (openCalendar(context)) "Megnyitottam a naptárat." else "Nem található naptáralkalmazás."
        BasicAction.OPEN_ALARMS -> if (openAlarms(context)) "Megnyitottam az ébresztőket." else "Nem található óraalkalmazás."
        BasicAction.OPEN_DIALER -> if (openDialer(context)) "Megnyitottam a tárcsázót. A hívás indításához erősítsd meg." else "Nem található tárcsázó."
        BasicAction.UNKNOWN -> "Ezt a parancsot még nem tudom helyben végrehajtani. Próbáld: nyisd meg a vércukrot, számlázást, emailt, térképet, naptárat vagy ébresztőt. Az AI még nincs csatlakoztatva."
    }
}
