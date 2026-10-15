package ai.jarvis.mobile

/** Clean display markup from speech only; chat keeps the original rich text. */
internal object JarvisSpokenText {
    fun clean(source: String): String {
        if (source.isBlank()) return ""
        return source
            // Keep meaningful link captions without reading URLs, brackets or markup.
            .replace(Regex("""!?\[([^\]\n]+)]\(https?://[^\s)]+\)"""), "$1")
            .replace(Regex("""https?://\S+"""), "")
            .replace(Regex("""(?m)^\s{0,3}#{1,6}\s+"""), "")
            .replace(Regex("""(?m)^\s*(?:[-*+]\s+|\d+[.)]\s+)"""), "")
            .replace(Regex("""(?m)^\s*```[^\n]*$"""), "")
            .replace(Regex("""[\\`*_~#|]+"""), "")
            .replace(Regex("""(?iu)\bpl\.\s*"""), "például ")
            .replace(Regex("""(?iu)\be\.g\.\s*"""), "for example ")
            // Punctuation used for formatting is not meant to be read verbatim.
            .replace(Regex("""[()\[\]{}]"""), " ")
            .replace(Regex("""(?<!\S)/+(?!\S)"""), " ")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex(""" +([,.!?;:])"""), "$1")
            .replace(Regex(""" *\n *"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }
}
