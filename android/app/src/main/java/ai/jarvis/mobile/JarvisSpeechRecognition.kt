package ai.jarvis.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Android 14+ language switching is best-effort. The platform recognizer can
 * ignore switching extras or lack downloaded speech models. On older devices,
 * recognition remains locked to the language selected with the flag.
 */
internal object JarvisSpeechRecognition {
    fun configure(intent: Intent, fallback: JarvisLanguage, autoSwitch: Boolean = true) {
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, fallback.tag)
        if (autoSwitch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val supported = ArrayList(JarvisLanguage.entries.map { it.tag })
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, supported)
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, supported)
        }
    }

    fun acceptedLanguage(
        tag: String?,
        confidence: Int,
        switched: Int,
        fallback: JarvisLanguage
    ): JarvisLanguage? {
        val detected = JarvisLanguage.fromSpeechTag(tag) ?: return null
        if (confidence < SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT) return null
        if (detected == fallback) return detected
        // Detection without a successful engine switch does not mean the transcript
        // was actually decoded in that language. Avoid falsely relabeling it.
        return detected.takeIf { switched == SpeechRecognizer.LANGUAGE_SWITCH_RESULT_SUCCEEDED }
    }

    fun fromPlatformResult(bundle: Bundle, fallback: JarvisLanguage): JarvisLanguage? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return acceptedLanguage(
            bundle.getString(SpeechRecognizer.DETECTED_LANGUAGE),
            bundle.getInt(SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL,
                SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN),
            bundle.getInt(SpeechRecognizer.LANGUAGE_SWITCH_RESULT,
                SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED),
            fallback
        )
    }
}

/** Keeps the UI flag in sync when supported speech recognition switches language. */
internal object JarvisSpeechLanguageEvents {
    var detected by mutableStateOf<JarvisLanguage?>(null)
}
