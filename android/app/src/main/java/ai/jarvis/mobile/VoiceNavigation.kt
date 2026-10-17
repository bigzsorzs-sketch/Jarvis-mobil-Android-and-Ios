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
    internal enum class LaunchResult { GUIDANCE_REQUESTED, DIRECTIONS_OPENED, SAVED_NAME_SEARCH_OPENED, UNAVAILABLE }

    private val personDestination = Regex(
        """(?iu)^(?:nyisd\s+meg\s+a\s+térképet\s+és\s+állítsd\s+be\s+(?:a\s+|az\s+)?|navigálj\s+)(.+?)\s+(?:címnek|címére|címéhez)[.!?]?$"""
    )

    private val destinationCommands = listOf(
        // Hungarian: accept standard accents and common grave-accent keyboard typos, e.g. "navigàlj".
        // "Vigyél Harrogate-be", "Navigálj Leedsbe", "Indítsd el a navigációt Yorkba".
        // Also treat "Állítsd be a térképet Londonba" as destination navigation,
        // not a generic empty map open. Destination cities are NOT hard-coded.
        Regex("""(?iu)^(?:navig[aáà]lj(?:\s+el)?|vigy[eé]l(?:\s+el)?|vezess(?:\s+el)?|[uú]tvonal(?:at)?|ind[ií]tsd\s+el\s+a\s+navig[aá]ci[oó]t|[aáà]ll[iíì]tsd\s+be\s+(?:(?:a|az)\s+)?(?:(?:google\s+)?t[eéè]rk[eéè]pet|google\s*maps(?:-?et)?|maps(?:-?et)?)|[aá]ll[ií]tsd\s+be\s+az?\s+[uú]tvonalat|mutasd\s+meg(?:\s+az\s+[uú]tvonalat)?)\s+(.+)$"""),
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
    private val pleasePrefix = Regex(
        """(?iu)^(?:k[eé]rlek|l[eé]gy\s+sz[ií]ves|figyelj|please)[,!\s]+"""
    )
    // A compound instruction means one navigation request, not "open Maps" plus
    // another app action. The user does not need to tap Start in Jarvis.
    private val mapsPreamble = Regex(
        """(?iu)^nyisd\s+meg\s+(?:a\s+)?(?:(?:google\s+)?t[eé]rk[eé]pet|google\s*maps(?:-?et)?|maps(?:-?et)?)\s*(?:(?:[eé]s|majd)\s+)(.+)$"""
    )
    private val navigationPrefix = Regex(
        """(?iu)^(?:ind[ií]tsd\s+el\s+)?(?:az?\s+)?navig[aá]ci[oó]t\s+(?:ide|erre|ehhez)\s*[:,-]?\s*(.+)$"""
    )
    // Spoken and typed Hungarian may separate the directional suffix as "-be", " -be",
    // or " be". Only remove it AFTER recognising an explicit Hungarian command.
    private val separatedHungarianCase = Regex("""(?iu)^(.+?)(?:\s*-\s*|\s+)(?:hoz|hez|höz|ba|be|ra|re)\s*$""")
    private val joinedHungarianCase = Regex("""(?iu)^(.+?)(?:hoz|hez|höz|ba|be)$""")

    fun destinationFrom(command: String): String? {
        // Spoken politeness is not a destination. Strip at most two prefixes.
        // A negative request is still rejected by the action parser.
        val initial = JarvisCommandSafety.stripPrefixes(command)
        // "Open Maps AND navigate to York" is one existing, explicitly verified
        // navigation request, not two independent device side effects.
        // Preserve it while continuing to block further chained actions.
        val mapsNavigation = mapsPreamble.matchEntire(initial)?.groupValues?.get(1)
        if (!JarvisCommandSafety.isSafeDeviceRequest(command) &&
            (mapsNavigation == null ||
                !JarvisCommandSafety.isSafeDeviceRequest(mapsNavigation) ||
                JarvisCommandSafety.hasNegation(command) || '?' in command)) return null
        // Keep legacy named-contact address navigation before removing the
        // "open Maps and" prefix used by direct city navigation.
        personDestination.matchEntire(initial)?.let { return "PERSON:" + it.groupValues[1].trim() }
        val clean = (mapsPreamble.matchEntire(initial)?.groupValues?.get(1) ?: initial).trim()
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
        // The suffix belongs to the saved-place reference ("helyhez"), not to the place name.
        if (JarvisSavedPlaces.importedName(raw) != null) return raw
        // A destination addressed as a person (Eszterhez) must resolve to
        // a saved person address. It must not turn into an unrelated Maps search
        // for "Eszter" if the person has no saved address.
        val personSuffix = Regex("""(?iu)(?:hoz|hez|höz)$""")
        val explicit = separatedHungarianCase.matchEntire(raw)
        if (explicit != null) {
            val target = explicit.groupValues[1].trim()
            return if (personSuffix.containsMatchIn(raw)) "PERSON:$target" else target
        }
        val joined = joinedHungarianCase.matchEntire(raw)
        val target = (joined?.groupValues?.get(1) ?: raw).trim().takeIf { it.isNotBlank() }
        return if (target != null && personSuffix.containsMatchIn(raw)) "PERSON:$target" else target
    }

    /** Maps can search an explicit name; this never verifies a private saved list. */
    fun maySearchNamedPlace(destination: String): Boolean =
        destination.startsWith("PERSON:") &&
        destination.removePrefix("PERSON:").trim().let {
            it.length in 2..80 && !it.contains('\n') && !it.contains('\r')
        }

    fun homeAddress(context: Context): String =
        SecurePrefs(context).read("home_destination", "")

    fun saveHome(context: Context, destination: String) {
        require(destination.length <= 180)
        SecurePrefs(context).write("home_destination", destination.trim())
    }

    /** Consistent Hungarian/English contact destination lookup. The person's
     * name must NEVER go to Google Maps as if it were a routable address.
     */
    private fun knownPersonAddress(context: Context, spoken: String): String? {
        // User-saved addresses take precedence over a nickname/phone entry,
        // which may exist without any postal address at all.
        val places = JarvisSavedPlaces.list(context).filter {
            PeopleDirectory.matchingName(spoken, it.name)
        }
        if (places.size == 1) return places.single().destination
        if (places.size > 1) return null
        PeopleDirectory.find(context, spoken)?.address?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        return JarvisContactAddresses.find(context, spoken)
    }

    fun resolve(context: Context, destination: String): String? {
        if (destination == "HOME") return homeAddress(context).takeIf { it.isNotBlank() }
        if (destination.startsWith("PERSON:")) {
            return knownPersonAddress(context, destination.removePrefix("PERSON:"))
        }
        JarvisSavedPlaces.importedName(destination)?.let { savedName ->
            return JarvisSavedPlaces.resolveImported(context, savedName)
        }
        knownPersonAddress(context, destination)?.let { return it }
        // If the phrase names a known Android contact but that contact has no
        // unique routable address, do not send their name to Maps as a fake place.
        if (VoiceCalling.candidates(context, destination).isNotEmpty()) return null
        return destination
    }

    /** Google Maps private saved-place lists are NOT readable through Android
     * intents. If a Hungarian person name has no known local address, hand the
     * *name* to Maps search for user verification, never auto-start navigation
     * to an unrelated search result.
     */
    fun openDetailed(context: Context, destination: String): LaunchResult {
        val resolved = resolve(context, destination)?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= 180 && !it.contains('\n') && !it.contains('\r') }
        if (resolved == null) {
            if (!destination.startsWith("PERSON:")) return LaunchResult.UNAVAILABLE
            val name = destination.removePrefix("PERSON:").trim()
            if (name.length !in 2..80 || name.any { it == '\n' || it == '\r' })
                return LaunchResult.UNAVAILABLE
            val encodedName = Uri.encode(name)
            val mapsSearch = Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=$encodedName"))
                .setPackage("com.google.android.apps.maps")
            if (openSystemIntent(context, mapsSearch))
                return LaunchResult.SAVED_NAME_SEARCH_OPENED
            val browserSearch = Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=$encodedName"
            ))
            return if (openSystemIntent(context, browserSearch))
                LaunchResult.SAVED_NAME_SEARCH_OPENED else LaunchResult.UNAVAILABLE
        }
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
