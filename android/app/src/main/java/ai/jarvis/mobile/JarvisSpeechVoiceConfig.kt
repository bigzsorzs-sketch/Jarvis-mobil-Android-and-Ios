package ai.jarvis.mobile

/**
 * Live OpenRouter model with explicit per-locale Azure voices.
 * A voice for the wrong locale can silently speak the wrong language.
 * Keep the model suffix in every voice id in sync with the model.
 */
internal object JarvisSpeechVoiceConfig {
    const val MODEL = "microsoft/mai-voice-2.1-flash"
    // Same trusted voice provider, alternate more resilient model in case
    // the flash model becomes unavailable or temporarily fails upstream.
    const val FALLBACK_MODEL = "microsoft/mai-voice-2.1"
    fun voice(language: JarvisLanguage): String {
        val localeVoice = when (language) {
            JarvisLanguage.HUNGARIAN -> "hu-HU-Bence"
            JarvisLanguage.ENGLISH -> "en-GB-Emily"
            JarvisLanguage.GERMAN -> "de-DE-Harper"
            JarvisLanguage.FRENCH -> "fr-FR-Harper"
            JarvisLanguage.SPANISH -> "es-ES-Marta"
            JarvisLanguage.ITALIAN -> "it-IT-Harper"
            JarvisLanguage.POLISH -> "pl-PL-Harper"
        }
        return "$localeVoice:MAI-Voice-2.1-Flash"
    }
    fun fallbackVoice(language: JarvisLanguage): String =
        voice(language).replace(":MAI-Voice-2.1-Flash", ":MAI-Voice-2.1")
}
