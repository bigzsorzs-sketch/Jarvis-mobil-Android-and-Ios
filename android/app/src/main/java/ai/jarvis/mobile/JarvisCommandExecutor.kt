package ai.jarvis.mobile

import android.content.Context

/** Foreground executor shared by text and one-shot voice on Home.
 * Only Android actions or explicit handoffs; no false AI or delivery claims. */
internal object JarvisCommandExecutor {
    fun run(
        context: Context,
        command: String,
        onNavigate: (String) -> Unit,
        onConfirmCall: (VoiceCalling.Target) -> Unit
    ): String {
        val text = command.trim()
        if (text.isEmpty()) return "Nem kaptam parancsot."
        LocalConversation.reply(text)?.let { return it }
        return when {
            ExternalTasks.parseMessage(text) != null ->
                ExternalTasks.dispatch(context, HandoffTask.Message(ExternalTasks.parseMessage(text)!!))
            ExternalTasks.parseUcJournal(text) ->
                ExternalTasks.dispatch(context, HandoffTask.UcJournal)
            ExternalTasks.parseGmailSearch(text) != null ->
                ExternalTasks.dispatch(context, HandoffTask.GmailSearch(ExternalTasks.parseGmailSearch(text)!!))
            ExternalTasks.resemblesGmailSearch(text) ->
                "Add meg az email feladóját vagy tárgyát. Gmail OAuth nélkül nem olvashatok vagy küldhetek választ."
            VoiceEmail.parse(text) != null ->
                if (VoiceEmail.openComposer(context, VoiceEmail.parse(text)!!))
                    "Megnyitottam a levelet. A levelező alkalmazásban ellenőrizd és küldd el."
                else "Nincs kompatibilis levelezőalkalmazás."
            VoiceNavigation.destinationFrom(text) != null -> {
                val destination = VoiceNavigation.destinationFrom(text)!!
                when {
                    VoiceNavigation.resolve(context, destination) == null ->
                        "Ehhez a személyhez nem mentettél el címet."
                    VoiceNavigation.open(context, destination) ->
                        "Az útvonalat átadtam a térképalkalmazásnak."
                    else -> "Nem sikerült elindítani a térképalkalmazást."
                }
            }
            VoiceCalling.extractRequest(text) != null -> {
                when (val match = VoiceCalling.lookup(context, VoiceCalling.extractRequest(text)!!)) {
                    is VoiceCalling.Lookup.Found -> {
                        onConfirmCall(match.target)
                        "A hívást még nem indítottam el. Erősítsd meg a címzettet."
                    }
                    VoiceCalling.Lookup.NoContactsAccess -> "A névjegyek megtekintéséhez külön engedély szükséges."
                    VoiceCalling.Lookup.NotFound -> "Nem találtam a megadott számot vagy névjegyet."
                    VoiceCalling.Lookup.Ambiguous -> "Több lehetséges címzettet találtam. Pontosítsd a nevet."
                }
            }
            AlarmCommands.hasUnsupportedDate(text) &&
                BasicCommandRouter.classify(text) == BasicAction.OPEN_ALARMS ->
                "Konkrét napra a saját ébresztő még nem állítható. Nem hoztam létre ébresztést."
            text.contains("töröld", ignoreCase = true) &&
                text.contains("ébreszt", ignoreCase = true) ->
                if (JarvisAlarms.cancel(context)) "Töröltem a Jarvis saját ébresztőjét."
                else "Nincs törölhető Jarvis-ébresztő."
            AlarmCommands.parseAlarmTime(text) != null -> {
                when (val outcome = JarvisAlarms.schedule(context, AlarmCommands.parseAlarmTime(text)!!)) {
                    is JarvisAlarms.Result.Created ->
                        "A Jarvis saját béta ébresztőjét " + outcome.time + " időpontra időzítettem. " +
                            "A jelzést a telefonon ellenőrizd."
                    JarvisAlarms.Result.NoExactAlarmPermission ->
                        "A pontos ébresztés külön Android-engedélyt igényel."
                    JarvisAlarms.Result.NoNotificationPermission ->
                        "Engedélyezd az értesítéseket az ébresztő hangjához."
                    JarvisAlarms.Result.Failed ->
                        "Az ébresztő nem jött létre."
                }
            }
            else -> performBasicAction(context, BasicCommandRouter.classify(text), onNavigate)
        }
    }
}
