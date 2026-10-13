package ai.jarvis.mobile

import android.content.Context
import androidx.compose.runtime.*

/** User-directed, allowlisted local settings changes shared by voice and typed chat. */
internal object JarvisLocalPreferences {
    sealed class Command {
        data class Theme(val dark: Boolean) : Command()
        data class Language(val selected: JarvisLanguage) : Command()
    }

    var requestedDark by mutableStateOf<Boolean?>(null)

    private fun clean(input: String): String =
        java.text.Normalizer.normalize(input.lowercase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9 ]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim()
            .removePrefix("jarvis ").removePrefix("kerlek ")

    fun parse(input: String): Command? {
        val text = clean(input)
        if (text.isBlank() || text.length > 130 ||
            listOf("ne ", "nem ", "dont ", "milyen ", "hogyan ", "how ",
                "miert ", "why ", "mit ").any { text.startsWith(it) }) return null
        if (text in setOf("kapcsold be a sotet modot", "allitsd sotet modra",
            "valts sotet modra", "sotet mod be", "enable dark mode",
            "turn on dark mode", "dark mode on")) return Command.Theme(true)
        if (text in setOf("kapcsold ki a sotet modot", "allitsd vilagos modra",
            "valts vilagos modra", "vilagos mod be", "disable dark mode",
            "turn off dark mode", "light mode on")) return Command.Theme(false)

        val prefix = listOf("valts ", "allitsd at a nyelvet ", "allitsd a nyelvet ",
            "nyelvvaltas ", "switch language to ", "change language to ",
            "set language to ").firstOrNull { text.startsWith(it) } ?: return null
        val target = text.removePrefix(prefix).trim()
        val selected = when (target) {
            "magyar", "magyarra", "hungarian" -> JarvisLanguage.HUNGARIAN
            "angol", "angolra", "english" -> JarvisLanguage.ENGLISH
            "nemet", "nemetre", "german", "deutsch" -> JarvisLanguage.GERMAN
            "francia", "franciara", "french", "francais" -> JarvisLanguage.FRENCH
            "spanyol", "spanyolra", "spanish", "espanol" -> JarvisLanguage.SPANISH
            "olasz", "olaszra", "italian", "italiano" -> JarvisLanguage.ITALIAN
            "lengyel", "lengyelre", "polish", "polski" -> JarvisLanguage.POLISH
            else -> null
        }
        return selected?.let { Command.Language(it) }
    }

    fun apply(context: Context, command: Command): String = when (command) {
        is Command.Theme -> {
            SecurePrefs(context).write("ui_dark_mode", command.dark.toString())
            requestedDark = command.dark
            if (command.dark) "A Jarvis megjelenését sötét módra állítottam."
            else "A Jarvis megjelenését világos módra állítottam."
        }
        is Command.Language -> {
            JarvisLanguageSettings.save(context, command.selected)
            JarvisSpeechLanguageEvents.detected = command.selected
            "A Jarvis nyelvét " + command.selected.label + " nyelvre állítottam."
        }
    }
}
