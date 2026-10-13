package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.util.Locale

/** Calls always require a targeted number and explicit confirmation. */
internal object VoiceCalling {
    data class Target(val label: String, val number: String)
    sealed class Lookup {
        data class Found(val target: Target) : Lookup()
        data object NoContactsAccess : Lookup()
        data object NotFound : Lookup()
        data object Ambiguous : Lookup()
    }

    private fun normalize(text: String): String = Normalizer.normalize(
        text.lowercase(Locale.ROOT), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9+ ]"), "")
        .trim().replace(Regex("\\s+"), " ")

    /** Full-name equality with only common Hungarian address/dative suffixes.
     * This is never a partial/fuzzy identity match: ambiguous contacts stay blocked.
     */
    internal fun matchesContactName(spoken: String, contactName: String): Boolean {
        val query = normalize(spoken)
        val name = normalize(contactName)
        return name.length >= 2 && query in setOf(name, name + "t", name + "nak", name + "nek")
    }

    /** A unique first-name match is permitted, but duplicates must never auto-dial. */
    internal fun matchesFirstName(spoken: String, contactName: String): Boolean {
        val query = normalize(spoken)
        val first = normalize(contactName).substringBefore(' ')
        return first.length >= 2 && (query == first ||
            query == first + "t" || query == first + "nak" || query == first + "nek")
    }

    // Some spontaneous Hungarian call requests put the target before the verb,
    // e.g. "Esztert hívd fel". Empty/placeholder targets MUST NOT be dialed.
    private val callPrefix = Regex(
        """(?iu)^(?:h[ií]vd(?:\s+fel)?|h[ií]vj(?:\s+fel)?|t[aá]rcs[aá]zd|telefon[aá]lj|fel\s+tudn[aá]d\s+h[ií]vni|felh[ií]vn[aá]d|szeretn[eé]m\s+ha\s+felh[ií]vn[aá]d|call|phone|ruf\s+an|rufe|anrufen|appelle|appeler|llama\s+a|llamar|chiama|chiamare|zadzwo[nń]\s+do|zadzwo[nń])(?:\s+(?:(?:a|az|the|le|la|el|il|den|die|do)\s+)?(.+?))?\s*[.!?]?$"""
    )
    private val callSuffix = Regex(
        """(?iu)^(?:(?:a|az)\s+)?(.+?)\s+(?:h[ií]vd\s+fel|t[aá]rcs[aá]zd)\s*[.!?]?$"""
    )
    private val forbiddenParts = Regex("""(?iu)\b(?:és|and|majd|aztán|then|und|et|y|e|i)\b""")
    private val placeholder = Regex(
        """(?iu)^(?:(?:a|az)\s+)?(?:adott|megadott|eml[ií]tett|el[oő]z[oő])?\s*(?:telefon)?sz[aá]m(?:ot|ot\s+most|ot\s+l[eé]gyszi)?$|^(?:a|az)\s+(?:telefonsz[aá]mot|sz[aá]mot|n[eé]vjegyet|kontaktot)$"""
    )

    private fun cleanCommand(text: String): String =
        text.trim()
            .replace(Regex("""(?iu)^(?:(?:hey|hej|szia|ok|okay)\s+)?jarvis\s*[,!:]?\s*"""), "")
            .replace(Regex("""(?iu)^(?:k[eé]rlek|please|bitte|s'il vous plaît|por favor|per favore|proszę)\s+"""), "")
            .trim()

    internal fun isUnspecifiedTarget(raw: String): Boolean {
        val v = raw.trim().trimEnd('.', '!', '?').trim()
        return v.isEmpty() || placeholder.matches(v)
    }

    fun extractRequest(text: String): String? {
        val clean = cleanCommand(text)
        val match = callPrefix.matchEntire(clean) ?: callSuffix.matchEntire(clean) ?: return null
        val rawTarget = match.groupValues[1].trim()
        // Check vague placeholders BEFORE stripping a trailing "számot":
        // "az adott telefonszámot hívd fel" is missing a real target.
        if (isUnspecifiedTarget(rawTarget)) return null
        val target = rawTarget.replace(
            Regex("""(?iu)\s+(?:telefonsz[aá]m(?:[aá]t|ot)|sz[aá]m(?:[aá]t|ot))$"""), ""
        ).trim()
        if (isUnspecifiedTarget(target) || target.length > 70 ||
            forbiddenParts.containsMatchIn(target)) return null
        return target
    }

    /** Spoken bare call intents or placeholder targets request a follow-up,
     * never open an empty dialer or silently dial an old/cached number.
     */
    fun needsTarget(text: String): Boolean {
        val clean = cleanCommand(text)
        val match = callPrefix.matchEntire(clean) ?: callSuffix.matchEntire(clean) ?: return false
        return isUnspecifiedTarget(match.groupValues[1])
    }

    /** A second utterance is a phonebook name or a fully specified number.
     * Do not treat arbitrary Android commands or sentences as a contact.
     */
    fun followUpTarget(input: String): String? {
        val text = cleanCommand(input).trim().trimEnd('.', '!', '?').trim()
        if (text.isEmpty() || text.length > 70 || isUnspecifiedTarget(text) ||
            forbiddenParts.containsMatchIn(text)) return null
        val direct = extractRequest(text)
        if (direct != null) return direct
        if (needsTarget(text)) return null
        if (text.contains(Regex("""(?iu)\b(?:nyisd|keress|navig[aá]lj|t[oö]r[oö]ld|ind[ií]tsd|h[ií]vd|k[uü]ldj|[ií]rj)\b"""))) return null
        return text
    }

    /** User-supplied phone number for the CURRENT explicit call only.
     * Never write this value to Contacts or treat the model's text as a number.
     */
    fun statedNumber(text: String): Target? {
        val cleaned = cleanCommand(text).trim().trimEnd('.', '!', '?').trim()
        val pattern = Regex(
            """(?iu)^(?:(?:ez|itt)\s+)?(?:(?:a|az)\s+)?(.+?)\s+(?:telefon)?sz[aá]ma\s*[:,-]?\s*(\+?\d[\d\s().-]{3,24}\d)$"""
        )
        val named = pattern.matchEntire(cleaned)
        val numberSource = if (named != null) named.groupValues[2] else cleaned
        val number = numberSource.replace(Regex("""[\s().-]"""), "")
        if (!number.matches(Regex("""\+?[0-9]{5,16}"""))) return null
        val name = named?.groupValues?.get(1)?.trim().orEmpty()
        if (name.length > 70 || (name.isNotBlank() && forbiddenParts.containsMatchIn(name))) return null
        return Target(name.ifBlank { number }, number)
    }

    fun callConfirmationYes(text: String): Boolean {
        val normalized = normalize(cleanCommand(text))
        return VoiceIntentParser.isCallYes(text) ||
            normalized in setOf("igen hivd", "igen hivd fel", "igen hivhatod",
                "igen telefonalj", "igen inditsd a hivast", "yes call",
                "yes call now", "ok hivd fel", "rendben hivd fel")
    }

    /**
     * Additional explicit natural-language call requests for AI proposals.
     * Return the target FROM THE USER, not a potentially hallucinated model target.
     */
    fun extractAiRequest(input: String): String? = extractRequest(input)

    /** Model must not substitute a different contact or phone number. */
    fun matchesAiTarget(originalTarget: String?, suggested: String): Boolean {
        if (originalTarget == null || suggested.isBlank() || suggested.length > 70) return false
        val fromUser = normalize(originalTarget)
        val fromModel = normalize(suggested)
        if (fromModel.isBlank()) return false
        return fromUser == fromModel ||
            (fromUser.length > 4 && fromUser.endsWith("t") && fromUser.dropLast(1) == fromModel)
    }

    fun lookup(context: Context, spoken: String): Lookup {
        // Only system phonebook contacts are used for name resolution.
        val query = spoken.trim()
        val cleanedNumber = query.replace(Regex("[\\s()\\-]"), "")
        if (cleanedNumber.matches(Regex("\\+?[0-9]{5,16}"))) {
            return Lookup.Found(Target(query, cleanedNumber))
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return Lookup.NoContactsAccess
        val candidates = mutableListOf<Target>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ), null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameAt = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberAt = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && candidates.size <= 2) {
                    val name = cursor.getString(nameAt).orEmpty()
                    val phone = cursor.getString(numberAt).orEmpty()
                    val matches = matchesContactName(query, name) || matchesFirstName(query, name)
                    if (matches && phone.isNotBlank()) candidates.add(Target(name, phone))
                }
            }
        } catch (_: Exception) { return Lookup.NotFound }
        return when (candidates.size) {
            0 -> Lookup.NotFound
            1 -> Lookup.Found(candidates.single())
            else -> Lookup.Ambiguous
        }
    }

    fun canCall(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /** This actually initiates the outgoing call; invoke only after explicit yes. */
    @android.annotation.SuppressLint("MissingPermission")
    fun place(context: Context, target: Target): Boolean {
        // The caller must confirm explicitly; the permission is checked again
        // immediately before the Telecom call, including after any revocation.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val manager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            manager.placeCall(Uri.parse("tel:" + Uri.encode(target.number)), Bundle.EMPTY)
            true // Submitted to Android Telecom; call establishment is not verified.
        } catch (_: SecurityException) { false }
          catch (_: Exception) { false }
    }
}
