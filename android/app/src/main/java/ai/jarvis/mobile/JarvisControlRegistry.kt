package ai.jarvis.mobile

import java.text.Normalizer
import java.util.Locale

/** Only controls currently composed by Jarvis. No accessibility permission,
 * reflection, screen scraping, AI-generated callbacks or third-party content. */
internal class JarvisControlRegistry {
    data class Control(val label: String, val enabled: Boolean = true,
        val edit: ((String) -> Unit)? = null, val click: (() -> Unit)? = null)
    private val controls = linkedMapOf<Any, Control>()
    fun put(id: Any, control: Control) { controls[id] = control }
    fun remove(id: Any) { controls.remove(id) }
    fun clear() { controls.clear() }

    fun describe(language: JarvisLanguage = JarvisLanguage.HUNGARIAN): String = controls.values.filter { it.enabled && it.label.isNotBlank() }
        .groupBy { JarvisCommandTexts.text(language, if (it.edit != null) "fields" else "buttons") }
        .map { (kind, items) -> "$kind: " + items.map { it.label }.distinct().joinToString("; ") }
        .joinToString(". ").ifBlank { JarvisCommandTexts.text(language, "empty") }

    fun execute(request: JarvisControlCommand, language: JarvisLanguage = JarvisLanguage.HUNGARIAN): String {
        fun t(key: String, label: String = "") = JarvisCommandTexts.text(language, key, label)
        if (request is JarvisControlCommand.ListControls) return describe(language)
        val label = when (request) {
            is JarvisControlCommand.Click -> request.label
            is JarvisControlCommand.Fill -> request.label
            else -> return describe(language)
        }
        val field = request is JarvisControlCommand.Fill
        val matches = controls.values.filter {
            it.label.isNotBlank() && (if (field) it.edit != null else it.click != null) &&
                normalize(it.label) == normalize(label)
        }
        if (matches.isEmpty()) return t("missing", label)
        val index = when (request) {
            is JarvisControlCommand.Click -> request.ordinal
            is JarvisControlCommand.Fill -> request.ordinal
            else -> null
        }
        val target = if (index == null) matches.singleOrNull() else matches.getOrNull(index - 1)
        if (target == null) return t("duplicate", label)
        if (!target.enabled) return t("disabled", label)
        return try {
            when (request) {
                is JarvisControlCommand.Fill -> {
                    target.edit?.invoke(request.value)
                    t("filled", target.label)
                }
                is JarvisControlCommand.Click -> {
                    target.click?.invoke()
                    // The underlying UI owns validation, network results and OS handoffs.
                    t("clicked", target.label)
                }
                else -> describe(language)
            }
        } catch (_: Exception) { t("failed", label) }
    }

    companion object {
        fun normalize(input: String): String = Normalizer.normalize(input.lowercase(Locale.ROOT),
            Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), " ").trim()
    }
}

internal sealed class JarvisControlCommand {
    data object ListControls : JarvisControlCommand()
    data class Click(val label: String, val ordinal: Int? = null) : JarvisControlCommand()
    data class Fill(val label: String, val value: String, val ordinal: Int? = null) : JarvisControlCommand()

    companion object {
        private fun target(raw: String): Pair<String, Int?> {
            val text = raw.trim().trim('"', '“', '”', '\'')
            val first = text.substringBefore(' ')
            val ordinal = when (JarvisControlRegistry.normalize(first)) {
                "elso", "first", "1" -> 1
                "masodik", "second", "2" -> 2
                "harmadik", "third", "3" -> 3
                else -> null
            }
            return (if (ordinal == null) text else text.substringAfter(' ').trim()) to ordinal
        }
        fun parse(input: String): JarvisControlCommand? {
            val text = (VoiceIntentParser.afterWakeWord(input) ?: input).trim()
                .replace(Regex("^(?:kérlek|please)\\s+", RegexOption.IGNORE_CASE), "")
            val n = JarvisControlRegistry.normalize(text)
            if (n in setOf("sorold fel az oldal vezerloit", "mit tudok ezen az oldalon",
                    "list page controls", "show page controls", "zeige steuerelemente",
                    "liste les controles", "muestra controles", "mostra controlli", "pokaz kontrolki")) return ListControls
            // Preserve dictated values exactly, including accents, case and punctuation.
            val fill = Regex("""^(?:írd be|ird be|írd|ird|állítsd|allitsd|fill|set|type into|schreibe|remplis|rellena|scrivi|wpisz)\s+(?:(?:az|a|the)\s+)?(.+?)\s+(?:mezőbe|mezobe|mezőt|mezot|field|feld|champ|campo|pole)\s*(?:[:=]\s*)?([\s\S]+)$""", RegexOption.IGNORE_CASE).matchEntire(text)
            if (fill != null) {
                val (label, ordinal) = target(fill.groupValues[1])
                return Fill(label, fill.groupValues[2].trim(), ordinal)
            }
            val click = Regex("""^(?:nyomd meg|koppints|érintsd meg|erintsd meg|válaszd|valaszd|click|tap|press|select|klicke|clique|pulsa|premi|kliknij)\s+(?:(?:az|a|the)\s+)?(.+?)(?:\s+(?:gombot|gombra|fület|fulet|button|tab|taste|bouton|botón|pulsante|przycisk))?[.!]?$""", RegexOption.IGNORE_CASE).matchEntire(text) ?: return null
            val (label, ordinal) = target(click.groupValues[1])
            return label.takeIf { it.isNotBlank() }?.let { Click(it, ordinal) }
        }
    }
}

/** Registered only while the Activity is composed; voice shares its exact
 * executor, pending call state and completed result with typed commands. */
internal object JarvisForegroundCommands {
    var handler: ((String, (String) -> Unit) -> Unit)? = null
    var interruptSpeech: (() -> Unit)? = null
    fun submit(command: String, respond: (String) -> Unit): Boolean {
        val current = handler ?: return false
        if (!HandsFreeNavigation.appIsForeground) return false
        current(command, respond)
        return true
    }
}
