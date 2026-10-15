package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/** GPT-5.2 stays selected for every chat. Depth follows the task, not a fixed short-answer cap. */
internal object JarvisReasoningPolicy {
    const val MAX_CONVERSATION_TOKENS = 128_000
    internal data class Budget(val effort: String, val maxTokens: Int)

    fun forQuestion(question: String, hasTools: Boolean): Budget {
        if (hasTools) return Budget("none", 800)
        val q = Normalizer.normalize(question.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('ł', 'l')
        val deep = listOf("gondold at alaposan", "elemezd alaposan", "think deeply",
            "analyze thoroughly", "in depth", "denke grundlich", "analyse approfondie",
            "analiza a fondo", "analizza a fondo", "przeanalizuj dokladnie").any(q::contains)
        if (deep) return Budget("high", MAX_CONVERSATION_TOKENS)
        val advanced = q.length > 600 || listOf(
            "kotlin", "swift", "programoz", "kod", "code", "hibakeres", "debug",
            "stacktrace", "exception", "gradle", "android", "ios", "architect", "architekt",
            "elemez", "analy", "diagnos", "reszletes", "hasonlits", "tervezz", "komplex",
            "compare", "step by step", "detailed", "plan ", "vergleiche", "ausfuhrlich",
            "erklar", "comparez", "detail", "explique", "compara", "explica", "confronta",
            "spiega", "porownaj", "wyjasnij", "szczegol"
        ).any(q::contains)
        if (advanced) return Budget("medium", MAX_CONVERSATION_TOKENS)
        val greeting = q.trim().trimEnd('!', '?', '.') in setOf(
            "szia", "hello", "hi", "koszi", "koszonom", "thanks", "thank you", "hallo",
            "danke", "bonjour", "merci", "hola", "gracias", "ciao", "grazie", "czesc", "dziekuje")
        return if (greeting) Budget("none", MAX_CONVERSATION_TOKENS) else Budget("low", MAX_CONVERSATION_TOKENS)
    }
}
