package ai.jarvis.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * Local, deterministic navigation: a user-spoken destination is never invented by AI.
 * Google Maps' google.navigation: URI requests immediate turn-by-turn guidance.
 * A Maps URL fallback may show directions, but cannot promise guidance has started.
 */
internal object VoiceNavigation {
    internal enum class LaunchResult { GUIDANCE_REQUESTED, DIRECTIONS_OPENED, UNAVAILABLE }

    private val personDestination = Regex(
        """(?iu)^(?:nyisd\s+meg\s+a\s+térképet\s+és\s+állítsd\s+be\s+(?:a\s+|az\s+)?|navigálj\s+)(.+?)\s+(?:címnek|címére|címéhez)[.!?]?$"""
    )

    private val destinationCommands = listOf(
        // Hungarian: "Vigyél Harrogate-be", "Navigálj Leedsbe", "Indítsd el a navigációt Yorkba".
        Regex("""(?iu)^(?:navig[aá]lj(?:\s+el)?|vigy[eé]l(?:\s+el)?|vezess(?:\s+el)?|[uú]tvonal(?:at)?|ind[ií]tsd\s+el\s+a\s+navig[aá]ci[oó]t|[aá]ll[ií]tsd\s+be\s+az?\s+[uú]tvonalat|mutasd\s+meg\s+az\s+[uú]tvonalat)\s+(.+)$"""),
        // English.
        Regex("""(?iu)^(?:navigate|drive|go|take\s+me|guide\s+me|route|start\s+navigation|show\s+(?:me\s+)?directions|get\s+directions)\s+to\s+(.+)$"""),
        // German.
        Regex("""(?iu)^(?:navigiere|fahr(?:e)?(?:\s+mich)?|bring\s+mich|route)\s+(?:nach|zu|zum|zur)\s+(.+)$"""),
        // French.
        Regex("""(?iu)^(?:navigue\s+vers|emm[eè]ne[- ]moi\s+[aà]|conduis[- ]moi\s+[aà]|itin[eé]raire\s+vers)\s+(.+)$"""),
        // Spanish.
        Regex("""(?iu)^(?:navega\s+(?:a|hacia)|ll[eé]vame\s+a|dir[ií]geme\s+a|ruta\s+a)\s+(.+)$"""),
        // Italian.
        Regex("""(?iu)^(?:naviga\s+(?:verso|a)|portami\s+a|guidami\s+a|indicazioni\s+per)\s+(.+)$"""),
        // Polish.
        Regex("""(?iu)^(?:nawiguj\s+do|zawie[zź]\s+mnie\s+do|prowad[zź]\s+(?:mnie\s+)?do|trasa\s+do)\s+(.+)$""")
    )

    private val spokenPrefix = Regex("""(?iu)^(?:(?:hey|hej|szia|ok|okay)\s+)?jarvis\s*[,!:]?\s*""")
    private val pleasePrefix = Regex("""(?iu)^k[eé]rlek[,\s]+""")
    private val separatedHungarianCase = Regex("""(?iu)^(.+?)\s*-\s*(?:hoz|hez|höz|ba|be|ra|re)\s*$""")
    private val joinedHungarianCase = Regex("""(?iu)^(.+?)(?:hoz|hez|höz|ba|be|ra|re)$""")

    fun destinationFrom(command: String): String? {
        val clean = command.trim().replace(spokenPrefix, "").replace(pleasePrefix, "").trim()
        if (clean.isBlank() || clean.length > 240 || clean.any { it == '\n' || it == '\r' }) return null
        personDestination.matchEntire(clean)?.let { return "PERSON:" + it.groupValues[1].trim() }
        val index = destinationCommands.indexOfFirst { it.matches(clean) }
        if (index < 0) return null
        val raw = destinationCommands[index].matchEntire(clean)!!.groupValues[1]
            .trim().replace(Regex("""[.!?]+$"""), "").trim()
        if (raw.isBlank() || raw.length > 180) return null
        val lower = raw.lowercase(Locale.ROOT)
        if (lower in setOf("haza", "otthon", "otthonra", "home", "hause",
                "maison", "casa", "domu", "dom")) return "HOME"
        // Only strip Hungarian case endings from Hungarian commands. Other
        // languages and full addresses must be passed unmodified to Maps.
        if (index != 0) return raw
        val explicit = separatedHungarianCase.matchEntire(raw)
        if (explicit != null) return explicit.groupValues[1].trim()
        val joined = joinedHungarianCase.matchEntire(raw)
        return (joined?.groupValues?.get(1) ?: raw).trim().takeIf { it.isNotBlank() }
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

    fun openDetailed(context: Context, destination: String): LaunchResult {
        val resolved = resolve(context, destination)?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 180 && !it.contains('\n') && !it.contains('\r') }
            ?: return LaunchResult.UNAVAILABLE
        val encoded = Uri.encode(resolved)
        val mapsNavigation = Intent(Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=$encoded&mode=d"))
            .setPackage("com.google.android.apps.maps")
        if (openSystemIntent(context, mapsNavigation)) return LaunchResult.GUIDANCE_REQUESTED

        // Universal URL fallback requests directions; a browser or other maps app
        // may still require a tap on Start. Do not report this as active guidance.
        val directions = Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=$encoded&travelmode=driving&dir_action=navigate"
        ))
        return if (openSystemIntent(context, directions)) LaunchResult.DIRECTIONS_OPENED
        else LaunchResult.UNAVAILABLE
    }

    fun open(context: Context, destination: String): Boolean =
        openDetailed(context, destination) != LaunchResult.UNAVAILABLE
}
