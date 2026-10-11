package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/** Google Maps app when available, otherwise a device-supported maps/browser Intent. */
internal object VoiceNavigation {
    fun destinationFrom(command: String): String? {
        val named = Regex(
            "(?iu)^(?:nyisd\s+meg\s+a\s+térképet\s+és\s+állítsd\s+be\s+(?:a\s+|az\s+)?|navigálj\s+)(.+?)\s+(?:címnek|címére|címéhez)[.!?]?$"
        ).find(command.trim())
        if (named != null) return "PERSON:" + named.groupValues[1].trim()
        val match = Regex(
            "^(?:navig[aá]lj|navig[aá]ci[oó]\\s+(?:ide|oda|c[ií]mre)?|[uú]tvonal\\s+(?:ide|oda)?|vigy[eé]l\\s+(?:el\\s+)?(?:ide|oda)?|mutasd\\s+meg)\\s+(.+?)[.!?]?$",
            RegexOption.IGNORE_CASE
        ).find(command.trim()) ?: return null
        val text = match.groupValues[1].trim()
        if (text.isBlank()) return null
        if (text.lowercase(Locale.ROOT) in listOf("haza", "otthonra", "otthon")) return "HOME"
        // Common Hungarian place-name direction suffixes.
        val suffix = listOf("hoz", "hez", "höz", "ból", "ből", "ban", "ben", "ba", "be", "ra", "re")
            .firstOrNull { text.length > it.length + 2 && text.lowercase(Locale.ROOT).endsWith(it) }
        return (if (suffix == null) text else text.dropLast(suffix.length)).trim()
    }

    fun homeAddress(context: Context): String =
        SecurePrefs(context).read("home_destination", "")

    fun saveHome(context: Context, destination: String) {
        require(destination.length <= 180)
        SecurePrefs(context).write("home_destination", destination.trim())
    }

    fun resolve(context: Context, destination: String): String? {
        if (destination == "HOME") return homeAddress(context).takeIf { it.isNotBlank() }
        if (destination.startsWith("PERSON:")) {
            return PeopleDirectory.find(context, destination.removePrefix("PERSON:"))
                ?.address?.takeIf { it.isNotBlank() }
        }
        val person = PeopleDirectory.find(context, destination)
        return if (person != null) person.address.takeIf { it.isNotBlank() } else destination
    }

    fun open(context: Context, destination: String): Boolean {
        val resolved = resolve(context, destination) ?: return false
        val google = Intent(Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=" + Uri.encode(resolved)))
            .setPackage("com.google.android.apps.maps")
        if (openSystemIntent(context, google)) return true
        val maps = Intent(Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=" + Uri.encode(resolved)))
        return openSystemIntent(context, maps)
    }
}
