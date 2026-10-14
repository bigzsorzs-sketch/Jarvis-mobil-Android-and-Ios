package ai.jarvis.mobile

import java.util.Locale

/**
 * Use GPT-5.2 reasoning only where it helps. Bigger budgets make requests
 * slower and more expensive; tool dispatch is kept separate and deterministic.
 */
internal object JarvisReasoningPolicy {
    internal data class Budget(val effort: String, val maxTokens: Int)

    fun forQuestion(question: String, hasTools: Boolean): Budget {
        if (hasTools) return Budget("none", 800)
        val q = question.lowercase(Locale.ROOT)
        val advanced = listOf(
            "kotlin", "swift", "programoz", "kódot", "kód", "code",
            "hibakeres", "debug", "stacktrace", "exception",
            "gradle", "android", "ios", "architect", "architekt",
            "elemezd", "elemezz", "analyze", "diagnos",
            "részletesen", "hasonlíts", "tervezz", "komplex"
        ).any(q::contains)
        if (advanced) return Budget("medium", 3200)
        val moderate = q.length > 280 || listOf(
            "miért", "hogyan", "why", "how", "összehason", "magyarázd"
        ).any(q::contains)
        return if (moderate) Budget("low", 1500) else Budget("none", 900)
    }
}
