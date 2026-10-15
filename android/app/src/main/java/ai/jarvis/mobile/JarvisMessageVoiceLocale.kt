package ai.jarvis.mobile

import java.util.Locale

/**
 * Notification replies are created by the Android system speech recognizer.
 * Use Jarvis' *selected* UI language, not the phone's default, when dictating
 * and playing a notification preview. Never change microphone permissions.
 */
internal object JarvisMessageVoiceLocale {
    fun speechTag(language: JarvisLanguage): String = language.tag

    fun ttsLocale(language: JarvisLanguage): Locale =
        Locale.forLanguageTag(language.tag)

    /** Keep the source message private: the prompt is static and contains no text. */
    fun dictationPrompt(language: JarvisLanguage): String = when (language) {
        JarvisLanguage.HUNGARIAN -> "Diktáld a választ – küldés előtt ellenőrizheted"
        JarvisLanguage.ENGLISH -> "Dictate your reply – review it before sending"
        JarvisLanguage.GERMAN -> "Antwort diktieren – vor dem Senden prüfen"
        JarvisLanguage.FRENCH -> "Dictez votre réponse – vérifiez avant l'envoi"
        JarvisLanguage.SPANISH -> "Dicta tu respuesta – revísala antes de enviar"
        JarvisLanguage.ITALIAN -> "Detta la risposta – controllala prima di inviare"
        JarvisLanguage.POLISH -> "Podyktuj odpowiedź – sprawdź przed wysłaniem"
    }
}
