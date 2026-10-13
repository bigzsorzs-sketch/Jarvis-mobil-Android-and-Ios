package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

/**
 * Amazon search and product-page handoff from the user's original spoken or
 * typed command. This deliberately does NOT scrape Amazon pages, invent
 * catalogue details, use hidden cookies or send purchases.
 */
internal enum class AmazonMarketplace(val host: String, val title: String) {
    UK("www.amazon.co.uk", "Amazon UK"),
    DE("www.amazon.de", "Amazon Deutschland"),
    US("www.amazon.com", "Amazon US"),
    FR("www.amazon.fr", "Amazon France"),
    ES("www.amazon.es", "Amazon España"),
    IT("www.amazon.it", "Amazon Italia"),
    PL("www.amazon.pl", "Amazon Polska");

    companion object {
        fun requested(raw: String): AmazonMarketplace {
            val n = raw.lowercase(Locale.ROOT)
            return when {
                "amazon.de" in n || "german amazon" in n || "nemet amazon" in n -> DE
                "amazon.com" in n || "american amazon" in n || "usa amazon" in n -> US
                "amazon.fr" in n || "french amazon" in n -> FR
                "amazon.es" in n || "spanish amazon" in n -> ES
                "amazon.it" in n || "italian amazon" in n -> IT
                "amazon.pl" in n || "polish amazon" in n -> PL
                else -> UK
            }
        }
    }
}

internal sealed interface AmazonAction {
    data class Search(
        val query: String,
        val business: Boolean,
        val marketplace: AmazonMarketplace
    ) : AmazonAction
    data class ViewProduct(
        val asin: String,
        val marketplace: AmazonMarketplace
    ) : AmazonAction
    data class OpenStore(
        val business: Boolean,
        val marketplace: AmazonMarketplace
    ) : AmazonAction
    data class ChangeToBusiness(val business: Boolean) : AmazonAction
    data object MissingQuery : AmazonAction
}

internal object JarvisAmazon {
    private fun normalized(raw: String) =
        Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""\s+"""), " ").trim()

    private val asin = Regex("""[A-Z0-9]{10}""")
    private val urlPattern = Regex("""https://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val domainPattern = Regex("""amazon(?:\.co\.uk|\.com|\.de|\.fr|\.es|\.it|\.pl)?""")
    private val productPath = Regex("""^/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})(?:/|$)""", RegexOption.IGNORE_CASE)

    fun productFromUrl(input: String): AmazonAction.ViewProduct? {
        val uri = runCatching { URI(input.trim().trimEnd(')', '.', ',', ';')) }.getOrNull()
            ?: return null
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1) return null
        val marketplace = AmazonMarketplace.entries.firstOrNull {
            it.host == uri.host?.lowercase(Locale.ROOT) ||
                it.host.removePrefix("www.") == uri.host?.lowercase(Locale.ROOT)
        } ?: return null
        val code = productPath.find(uri.path.orEmpty())?.groupValues?.get(1)
            ?.uppercase(Locale.ROOT) ?: return null
        return AmazonAction.ViewProduct(code, marketplace)
    }

    fun searchUrl(search: AmazonAction.Search): String {
        require(search.query.length in 2..160)
        return "https://" + search.marketplace.host + "/s?k=" +
            URLEncoder.encode(search.query, "UTF-8")
    }

    fun productUrl(product: AmazonAction.ViewProduct): String {
        require(asin.matches(product.asin))
        return "https://" + product.marketplace.host + "/dp/" + product.asin
    }

    /** Unambiguous commands only; default marketplace is UK. */
    fun parse(raw: String): AmazonAction? {
        val original = (VoiceIntentParser.afterWakeWord(raw) ?: raw)
            .trim().replace(Regex("""(?iu)^(?:hey\s+|szia\s+)?jarvis[,:! ]*"""), "").trim()
        if (original.length > 1200 || original.isBlank()) return null
        val n = normalized(original)
        if (!domainPattern.containsMatchIn(n)) return null
        val business = Regex("""\b(?:business|uzleti|ceges)\b""").containsMatchIn(n)
        val market = AmazonMarketplace.requested(n)
        val url = urlPattern.find(original)?.value
        if (url != null) {
            return productFromUrl(url) ?: AmazonAction.MissingQuery
        }
        // "az Amazon Business alkalmazáson keresztül": operate on the last
        // local Amazon query only, not an AI-invented or ancient chat query.
        if (Regex("""^(?:az?\s+)?amazon\s+business(?:\s+alkalmazason\s+keresztul|\s+app(?:lication)?|\s+alkalmazas)?[.?!]?$""")
                .matches(n)) return AmazonAction.ChangeToBusiness(true)
        if (Regex("""^(?:nyisd\s+meg|mutasd|open|launch|show)\s+(?:(?:az?|the)\s+)?amazon(?:\s+business)?(?:\s+alkalmazas(?:t)?|\s+app|\s+oldal(?:t)?)?[.?!]?$""")
                .matches(n)) return AmazonAction.OpenStore(business, market)

        val code = Regex("""(?i)\b(?:ASIN|termekkod)\s*[:#]?\s*([A-Z0-9]{10})\b""")
            .find(n)?.groupValues?.get(1)?.uppercase(Locale.ROOT)
        if (code != null && asin.matches(code)) return AmazonAction.ViewProduct(code, market)

        // Supported HU/EN natural commands. Removing only a fixed prefix means
        // the query is ALWAYS derived from the user's exact instruction.
        val patterns = listOf(
            """^(?:(?:az?|a)\s+)?amazon(?:\\.co\\.uk|\\.com|\\.de|\\.fr|\\.es|\\.it|\\.pl|on|en|ban)?(?:\s+business)?(?:\s+alkalmazas(?:on|ban)?\s+keresztul)?\s+(?:keress|keresd\s+meg|talalj|search(?:\s+for)?|find)\s+""",
            """^(?:keress(?:\s+nekem)?|keresd\s+meg|talalj|search(?:\s+for)?|find)\s+(?:egy\s+)?(?:(?:az?|a|on|in|the)\s+)?amazon(?:\\.co\\.uk|\\.com|\\.de|\\.fr|\\.es|\\.it|\\.pl|on|en|ban)?(?:\s+business)?(?:\s+alkalmazas(?:ban|on)?)?\s+""",
            """^(?:amazon(?:\s+business)?)\s+(?:kereses|search)\s+""",
            """^(?:mutasd\s+(?:meg\s+)?|show\s+)(?:(?:az?|on)\s+)?amazon(?:on|en)?\s+""",
            """^(?:mennyi(?:be)?\s+kerul|milyen\s+aron\s+van|what\s+is\s+the\s+price\s+of)\s+"""
        )
        for ((i, pattern) in patterns.withIndex()) {
            val prefix = Regex(pattern).find(n) ?: continue
            if (prefix.range.first != 0) continue
            var query = original.substring(prefix.range.last + 1).trim()
            if (i == 4) query = query.replace(Regex("""(?iu)\s+(?:az?\s+)?amazon(?:\s+business)?(?:on|en)?[?.!]*$"""), "")
            query = query.replace(Regex("""(?iu)^(?:egy|a|an)\s+"""), "")
                .trim().trimEnd('?', '.', '!', ',', ' ')
            if (query.length !in 2..160) return AmazonAction.MissingQuery
            return AmazonAction.Search(query, business, market)
        }
        return if (Regex("""\b(keress|keres|search|find|ar|price|termek|product)\b""")
                .containsMatchIn(n)) AmazonAction.MissingQuery else null
    }

    fun open(context: Context, action: AmazonAction): String {
        if (action is AmazonAction.MissingQuery) return "Mit keressek az Amazonon? Add meg a terméket."
        if (action is AmazonAction.ChangeToBusiness) return "Még nincs korábbi Amazon-termékkeresés."
        val url = when (action) {
            is AmazonAction.Search -> searchUrl(action)
            is AmazonAction.ViewProduct -> productUrl(action)
            is AmazonAction.OpenStore -> "https://" + action.marketplace.host + "/"
            else -> return "Amazon művelet nem elérhető."
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            when (action) {
                is AmazonAction.Search -> "Megnyitottam az " + action.marketplace.title +
                    " találatait: " + action.query + ". " +
                    (if (action.business) "Az üzleti árakhoz jelentkezz be Amazon Business-fiókoddal. " else "") +
                    "A hiteles árakat, készletet és szállítást az Amazon mutatja; Jarvis még nem kapott ezekhez hivatalos termék-API hozzáférést."
                is AmazonAction.ViewProduct -> "Megnyitottam az Amazon termékoldalát (" +
                    action.asin + "). A tényleges árat az Amazon mutatja."
                is AmazonAction.OpenStore -> "Megnyitottam az Amazon oldalt. " +
                    (if (action.business) "Az üzleti ajánlatokhoz lépj be a Business-fiókodba." else "")
                else -> "Nem hajtottam végre Amazon-műveletet."
            }
        } catch (_: Exception) {
            "Nem sikerült megnyitni az Amazont. Nem állítom, hogy találtam terméket."
        }
    }
}
