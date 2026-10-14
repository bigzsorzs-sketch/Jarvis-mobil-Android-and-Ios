package ai.jarvis.mobile

import android.content.Context

/** User feedback in all seven selected Jarvis UI/voice languages. */
internal object JarvisAlarmText {
    private fun <T> choose(language: JarvisLanguage, hu: T, en: T, de: T, fr: T,
        es: T, it: T, pl: T): T = when (language) {
        JarvisLanguage.HUNGARIAN -> hu
        JarvisLanguage.ENGLISH -> en
        JarvisLanguage.GERMAN -> de
        JarvisLanguage.FRENCH -> fr
        JarvisLanguage.SPANISH -> es
        JarvisLanguage.ITALIAN -> it
        JarvisLanguage.POLISH -> pl
    }

    fun invalid(context: Context): String = choose(JarvisLanguageSettings.load(context),
        "Nem tudtam pontosan értelmezni az egyszeri ébresztés idejét vagy ismétlését. Nem állítottam be ébresztőt.",
        "I couldn't safely interpret the one-time alarm time or repetition. No alarm was created.",
        "Ich konnte Uhrzeit oder Wiederholung nicht sicher erkennen. Kein Wecker wurde gestellt.",
        "Je n'ai pas pu interpréter l'heure ou la répétition. Aucune alarme n'a été créée.",
        "No pude interpretar con seguridad la hora o la repetición. No se creó ninguna alarma.",
        "Non sono riuscito a capire l'ora o la ripetizione. Nessuna sveglia è stata impostata.",
        "Nie rozpoznałem bezpiecznie godziny lub powtarzania. Nie ustawiono budzika.")

    fun result(context: Context, result: JarvisAlarms.Result): String {
        val language = JarvisLanguageSettings.load(context)
        return when (result) {
            is JarvisAlarms.Result.Created -> {
                val t = result.time
                choose(language,
                    "A Jarvis egyszeri ébresztőjét $t időpontra beállítottam.",
                    "I set a one-time Jarvis alarm for $t.",
                    "Ich habe einen einmaligen Jarvis-Wecker für $t gestellt.",
                    "J'ai programmé une alarme Jarvis unique pour $t.",
                    "He programado una alarma Jarvis única para las $t.",
                    "Ho impostato una sveglia Jarvis singola per le $t.",
                    "Ustawiłem jednorazowy alarm Jarvis na $t.")
            }
            JarvisAlarms.Result.NoExactAlarmPermission -> choose(language,
                "A pontos ébresztéshez külön Android-engedély szükséges. Nem állítottam be ébresztőt.",
                "Exact alarm permission is required in Android. No alarm was created.",
                "Die Android-Berechtigung für genaue Alarme fehlt. Kein Wecker wurde gestellt.",
                "L'autorisation Android pour les alarmes exactes est requise. Aucune alarme créée.",
                "Se requiere permiso de Android para alarmas exactas. No se creó ninguna.",
                "Serve il permesso Android per le sveglie esatte. Nessuna sveglia impostata.",
                "Brakuje uprawnienia Androida do dokładnych alarmów. Nie ustawiono budzika.")
            JarvisAlarms.Result.NoNotificationPermission -> choose(language,
                "Engedélyezd az Android-értesítéseket, hogy az ébresztő jelezzen. Nem állítottam be ébresztőt.",
                "Allow Android notifications so the alarm can ring. No alarm was created.",
                "Bitte Android-Benachrichtigungen erlauben. Kein Wecker wurde gestellt.",
                "Autorisez les notifications Android. Aucune alarme n'a été créée.",
                "Permite las notificaciones de Android. No se creó ninguna alarma.",
                "Consenti le notifiche Android. Nessuna sveglia è stata impostata.",
                "Zezwól na powiadomienia Androida. Nie ustawiono budzika.")
            JarvisAlarms.Result.Failed -> choose(language,
                "Nem sikerült beállítani az ébresztőt. Nem állítom, hogy létrejött.",
                "The alarm could not be created. I won't claim it was set.",
                "Der Wecker konnte nicht gestellt werden.",
                "Impossible de créer l'alarme.",
                "No se pudo crear la alarma.",
                "Non è stato possibile impostare la sveglia.",
                "Nie udało się ustawić budzika.")
        }
    }
}
