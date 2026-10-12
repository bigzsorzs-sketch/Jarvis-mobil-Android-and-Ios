package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/** Small, explicit offline replies. Commands and arbitrary questions are never fabricated. */
internal object LocalConversation {
    fun reply(raw: String): String? {
        val normalized = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{Nd}\\s]"), " ")
            .trim().replace(Regex("\\s+"), " ")
            .removePrefix("hey jarvis ").removePrefix("jarvis ")
        return when (normalized) {
            "jo reggelt", "good morning" -> "Jó reggelt! Miben segíthetek?"
            "jo napot", "jo napot kivanok" -> "Jó napot! Miben segíthetek?"
            "jo estet", "good evening" -> "Jó estét! Miben segíthetek?"
            "jo ejszakat", "good night" -> "Jó éjszakát!"
            "szia", "sziasztok", "hello", "hi", "szevasz" -> "Szia! Miben segíthetek?"
            "koszonom", "koszi", "thanks", "thank you" -> "Szívesen!"
            "viszlat", "viszontlatasra", "goodbye" -> "Viszlát!"
            else -> null
        }
    }
}
