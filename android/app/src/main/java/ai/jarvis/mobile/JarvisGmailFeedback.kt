package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/** Important Gmail faults must remain visible; successful background actions
 * and account changes stay on the Email screen instead of chat history. */
internal object JarvisGmailFeedback {
    fun needsAttention(status: String): Boolean {
        val n = Normalizer.normalize(status.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
        return listOf("nem ", "nincs ", "hiany", "sikertelen", "bizonytalan",
            "nem tortent", "ellenorizd", "meg", "nem kaptam", "varj", "nem tud").any {
            it in n
        } && !n.startsWith("megnyitottam") && !n.startsWith("megkaptam")
    }
}
