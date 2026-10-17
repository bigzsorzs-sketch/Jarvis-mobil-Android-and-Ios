package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/**
 * Only the ORIGINAL user utterance authorizes an Android side effect.
 * Model output, contact names, history and retrieved documents are data,
 * never commands. Keep negated and ambiguous commands fail-closed.
 */
internal object JarvisCommandSafety {
    private val prefixes = Regex(
        """(?iu)^(?:szia|sziasztok|jó\s+reggelt|jó\s+napot|jó\s+estét|hello|hi|hey|hej|he|ok|okay|good\s+morning|good\s+afternoon|good\s+evening|bonjour|hola|ciao|hallo|cześć|jarvis|k[eé]rlek\s+sz[eé]pen|k[eé]rlek|l[eé]gy\s+sz[ií]ves|please|figyelj|bitte|s['’]il\s+vous\s+pla[iî]t|por\s+favor|per\s+favore|prosz[eę])(?:[\s,:!]+|$)"""
    )
    private val negations = Regex(
        """\b(?:ne|nem|soha|dont|don\s+t|do\s+not|not|never|without|nicht|kein|keine|pas|jamais|no|nunca|non|senza|nie|bez)\b"""
    )
    private val secondAction = Regex(
        """\b(?:majd|aztan|then|and\s+then|es|and|und|ensuite|puis|luego|potem)\s+(?:(?:a|az|the|please|kerlek)\s+){0,2}(?:nyisd|nyit|mutasd|hivd|torold|allitsd|navigalj|inditsd|kuldd|irj|open|launch|call|delete|set|send|navigate|show)\b"""
    )
    private val explicit = Regex(
        """^(?:nyisd|nyit|nyitnad|megnyit|megnyitnad|mutasd|mutatnad|lepj|menj|inditsd|szeretnem\s+megnyitni|szeretnem\s+latni|szeretnek\s+belepni|open|show|launch|go\s+to|take\s+me\s+to|offne|zeige|ouvre|affiche|abre|muestra|apri|mostra|otworz|pokaz|hivd|tarcsazd|call|phone|fel\s+tudnad\s+hivni|navigalj|vigyel|vezess|navigate|set|allitsd|be\s+tudnad\s+allitani|ebressz|kelts|wake|torold|cancel|irj|kuldd|send|write|keress|keresd|search|look\s+up)\b"""
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace("ł", "l").replace("ß", "ss")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .trim().replace(Regex("""\s+"""), " ")

    /** Strip a bounded number of LEADING politeness forms, not interior words. */
    fun stripPrefixes(original: String): String {
        var value = original.trim()
        repeat(8) {
            val match = prefixes.find(value) ?: return value
            value = value.substring(match.range.last + 1).trim()
        }
        return value
    }

    fun hasNegation(original: String): Boolean = negations.containsMatchIn(normalize(original))
    fun hasSecondAction(original: String): Boolean =
        secondAction.containsMatchIn(normalize(original))
    fun explicitCommand(original: String): Boolean =
        explicit.containsMatchIn(normalize(stripPrefixes(original)))

    /** A polite question can execute only when its actual verb is imperative. */
    fun isSafeDeviceRequest(original: String): Boolean =
        original.length in 1..8000 &&
            !hasNegation(original) && !hasSecondAction(original) &&
            ('?' !in original || explicitCommand(original))
}
