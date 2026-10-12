package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Foreground, single-request Android speech transcription for the SMALL mic.
 * The recognized text is submitted to the exact same Home command path as typing.
 * The large mic deliberately uses the separate speaking HandsFreeService.
 */
internal class JarvisDictation(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun stop() {
        val active = recognizer
        recognizer = null
        active?.setRecognitionListener(null)
        active?.cancel()
        active?.destroy()
    }

    fun start(
        partial: (String) -> Unit,
        complete: (String) -> Unit,
        status: (String) -> Unit
    ) {
        stop()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            status("A diktáláshoz engedélyezd a mikrofont.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status("A telefonon nincs működő Android beszédfelismerő. " +
                "Ellenőrizd a Google hangfelismerés beállításait.")
            return
        }
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status("Beszélj most…") }
                override fun onBeginningOfSpeech() { status("Hallgatlak…") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status("Felismerés folyamatban…") }
                override fun onError(error: Int) {
                    status(when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "Nem hallottam érthető szöveget. Nyomd meg újra a mikrofont."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "A mikrofonengedély hiányzik vagy visszavonták."
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                            "A magyar beszédfelismerés nem érhető el. Állítsd be az Androidon."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "A telefon beszédfelismerője hálózati hibát jelzett."
                        else -> "A beszédfelismerés hibát jelzett ($error). Próbáld újra."
                    })
                    stop()
                }
                override fun onResults(results: Bundle?) {
                    val heard = results?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    stop()
                    if (heard.isNullOrBlank()) status("Nem kaptam felismert szöveget.")
                    else complete(heard)
                }
                override fun onPartialResults(partials: Bundle?) {
                    val heard = partials?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!heard.isNullOrBlank()) partial(heard)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            status("Mikrofon elindítva…")
        } catch (_: Exception) {
            stop()
            status("Nem sikerült elindítani a beszédfelismerést ezen a telefonon.")
        }
    }
}
