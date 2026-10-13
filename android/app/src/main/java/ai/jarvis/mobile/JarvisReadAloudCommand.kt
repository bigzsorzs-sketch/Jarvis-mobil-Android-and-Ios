package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * Read-aloud requests use consent-gated AI cloud MP3 speech; Android owns
 * playback and stop. Requests without supplied text ask the AI for the content.
 */
internal sealed class ReadAloudRequest {
    data object LastAnswer : ReadAloudRequest()
    data class Literal(val text: String) : ReadAloudRequest()
    data class Generate(val request: String) : ReadAloudRequest()
}

internal object JarvisReadAloudCommand {
    private fun normalized(s: String): String = Normalizer.normalize(
        s.lowercase(Locale.ROOT), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").trim()

    private val commands = listOf(
        "olvasd fel", "olvass fel", "felolvasnad", "felolvasnal",
        "read aloud", "read out", "read me", "read this", "read the",
        "lies mir", "lies das", "lies vor", "vorlesen", "lies bitte",
        "lis moi", "lis ce", "lis cette", "lisez", "lecture a voix haute",
        "lee en voz alta", "lee esto", "leeme", "leeme esto",
        "leggi ad alta voce", "leggimi", "leggi questo", "leggi questa",
        "przeczytaj", "czytaj na glos", "odczytaj"
    )
    private val lastAnswer = listOf(
        "elozo valasz", "utolso valasz", "legutobbi valasz", "amit irtal",
        "az elozo uzenet", "last answer", "previous answer", "last reply",
        "previous reply", "your answer", "letzte antwort", "vorherige antwort",
        "derniere reponse", "reponse precedente", "ultima respuesta",
        "respuesta anterior", "ultima risposta", "risposta precedente",
        "ostatnia odpowiedz", "poprzednia odpowiedz"
    )

    fun parse(raw: String): ReadAloudRequest? {
        val clean = raw.trim().replace(Regex("(?iu)^(?:hey\\s+)?jarvis[,!\\s]*"), "").trim()
        val n = normalized(clean)
        val prefix = commands.firstOrNull {
            n == it || n.startsWith(it + " ") || n.startsWith(it + ":") ||
                n.startsWith(it + ",")
        } ?: return null
        val nTail = n.drop(prefix.length).trim()
        if (nTail.isEmpty() || lastAnswer.any { it in nTail })
            return ReadAloudRequest.LastAnswer

        // Explicit text after a colon is never sent to a cloud model.
        // The prefix before ':' may be 'the following text', in any of 7 languages.
        val colon = clean.indexOf(':')
        if (colon >= 0 && colon < 130) {
            val text = clean.substring(colon + 1).trim()
            if (text.isNotEmpty()) return ReadAloudRequest.Literal(text)
        }
        return ReadAloudRequest.Generate(clean)
    }

    /** Content-creation prompts never ask the model to perform device audio. */
    fun generationPrompt(request: String, language: JarvisLanguage): String {
        val instructions = arrayOf(
            "Írd meg a kért felolvasandó szöveget magyarul, természetes, összefüggő formában. Csak a tartalmat add meg; ne magyarázd, hogy képes vagy-e hangosan felolvasni. Kérés: ",
            "Write the requested content in natural English, ready to be read aloud. Give only the content; do not discuss whether you can speak. Request: ",
            "Schreibe den gewünschten Inhalt auf Deutsch zum Vorlesen. Nur den Inhalt, keine Aussagen zur Sprachfunktion. Anfrage: ",
            "Rédige le contenu demandé en français, prêt à être lu à voix haute. Donne uniquement le contenu. Demande : ",
            "Escribe el contenido solicitado en español, listo para leer en voz alta. Da solo el contenido. Solicitud: ",
            "Scrivi il contenuto richiesto in italiano, pronto per la lettura ad alta voce. Fornisci solo il contenuto. Richiesta: ",
            "Napisz żądaną treść po polsku, gotową do odczytania na głos. Podaj tylko treść. Prośba: "
        )
        return instructions[language.ordinal] + request
    }

    fun response(language: JarvisLanguage, key: String): String {
        val values = when (key) {
            "started" -> arrayOf("Felolvasás elindítva.", "Reading aloud.", "Vorlesen gestartet.", "Lecture lancée.", "Lectura iniciada.", "Lettura avviata.", "Odczyt rozpoczęty.")
            "noAnswer" -> arrayOf("Még nincs korábbi válasz, amit felolvashatnék.", "There is no previous reply to read.", "Keine vorherige Antwort zum Vorlesen.", "Aucune réponse précédente à lire.", "No hay respuesta anterior para leer.", "Nessuna risposta precedente da leggere.", "Brak poprzedniej odpowiedzi do odczytania.")
            "missingTts" -> arrayOf("AI-hang nem érhető el. Engedélyezd külön a Beállítások → AI-agy menüben, és ellenőrizd az internetkapcsolatot.", "AI voice unavailable. Enable cloud voice under Settings → AI and check your internet.", "KI-Stimme nicht verfügbar. Unter Einstellungen → KI aktivieren und Internet prüfen.", "Voix IA indisponible. Activez-la dans Paramètres → IA et vérifiez Internet.", "Voz IA no disponible. Actívala en Ajustes → IA y comprueba Internet.", "Voce IA non disponibile. Attivala in Impostazioni → IA e verifica Internet.", "Głos AI niedostępny. Włącz w Ustawienia → AI i sprawdź internet.")
            "stopped" -> arrayOf("Felolvasás leállítva.", "Reading stopped.", "Vorlesen angehalten.", "Lecture arrêtée.", "Lectura detenida.", "Lettura interrotta.", "Odczyt zatrzymany.")
            "stop" -> arrayOf("Leállítás", "Stop", "Stopp", "Arrêter", "Detener", "Ferma", "Zatrzymaj")
            "read" -> arrayOf("Felolvasás", "Read aloud", "Vorlesen", "Lire à voix haute", "Leer en voz alta", "Leggi ad alta voce", "Czytaj na głos")
            "finished" -> arrayOf("Felolvasás kész.", "Reading finished.", "Vorlesen beendet.", "Lecture terminée.", "Lectura terminada.", "Lettura terminata.", "Odczyt zakończony.")
            "failed" -> arrayOf("Az AI-hang generálása vagy lejátszása sikertelen. Ellenőrizd az OpenRouter-kapcsolatot és a keretet.", "AI voice generation or playback failed. Check your OpenRouter connection and balance.", "KI-Sprachausgabe fehlgeschlagen. OpenRouter-Verbindung und Guthaben prüfen.", "La voix IA a échoué. Vérifiez la connexion OpenRouter et le crédit.", "Error de voz IA. Comprueba la conexión a OpenRouter y el saldo.", "Errore voce IA. Controlla la connessione OpenRouter e il credito.", "Błąd głosu AI. Sprawdź połączenie OpenRouter i saldo.")
            else -> error("Unknown TTS label: " + key)
        }
        return values[language.ordinal]
    }
}

/**
 * Splits at whitespace when available, without silently dropping the tail.
 * Keep chunks reasonably small for cloud speech API processing.
 */
internal object JarvisSpeechChunks {
    fun split(text: String, maxChars: Int = 2600): List<String> {
        require(maxChars >= 16)
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            // Keep every original character, including whitespace at chunk edges.
            // Dropping whitespace can merge words when reading a long document.
            var end = (start + maxChars).coerceAtMost(text.length)
            if (end < text.length) {
                val boundary = (end - 1 downTo (start + maxChars / 2))
                    .firstOrNull { text[it].isWhitespace() }
                if (boundary != null) end = boundary + 1
                else if (end > start && text[end - 1].isHighSurrogate() &&
                    text[end].isLowSurrogate()) end--
            }
            if (end <= start) end = (start + maxChars).coerceAtMost(text.length)
            result.add(text.substring(start, end))
            start = end
        }
        return result
    }
}
