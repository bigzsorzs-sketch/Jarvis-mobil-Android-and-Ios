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
            .replace(Regex("""(?iu)^(?:figyelj[,!]?\s+)?(?:k[eé]rlek|l[eé]gy\s+sz[ií]ves|please|bitte|s'il vous plaît|por favor|per favore|proszę)[,!\s]+"""), "")
            .replace(Regex("""(?iu)^figyelj[,!]?\s+"""), "")
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
                "igen telefonalj", "igen inditsd a hivast", "hivd", "yes call",
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

    /** Compare UK local mobile / 0-prefixed and +44 numbers as the same number.
     * Never collapse different numbers for the same display name.
     */
    internal fun canonicalPhone(raw: String): String {
        val compact = raw.filter { it.isDigit() || it == '+' }
        return when {
            compact.startsWith("0044") -> "+44" + compact.drop(4)
            compact.startsWith("0") && compact.length in 10..11 ->
                "+44" + compact.drop(1)
            else -> compact
        }
    }

    internal fun matchingPhonebookRows(spoken: String, rows: List<Target>): List<Target> {
        val unique = rows.filter { it.label.isNotBlank() && it.number.isNotBlank() }
            .distinctBy { normalize(it.label) + "|" + canonicalPhone(it.number) }
        val exact = unique.filter { matchesContactName(spoken, it.label) }
        return exact.ifEmpty { unique.filter { matchesFirstName(spoken, it.label) } }
    }

    internal fun selectPhonebookMatch(spoken: String, rows: List<Target>): Lookup {
        val choices = matchingPhonebookRows(spoken, rows)
        return when (choices.size) {
            0 -> Lookup.NotFound
            1 -> Lookup.Found(choices.single())
            else -> Lookup.Ambiguous
        }
    }

    /** System Contacts is the sole name source, no historic call log scan
     * or personal cloud upload. Caller must obtain Android READ_CONTACTS.
     */
    fun candidates(context: Context, spoken: String): List<Target> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return emptyList()
        val rows = mutableListOf<Target>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameAt = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberAt = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameAt).orEmpty()
                    val number = cursor.getString(numberAt).orEmpty()
                    if (number.isNotBlank() &&
                        (matchesContactName(spoken, name) || matchesFirstName(spoken, name)))
                        rows.add(Target(name, number))
                }
            }
        } catch (_: SecurityException) { return emptyList() }
          catch (_: Exception) { return emptyList() }
        return matchingPhonebookRows(spoken, rows)
    }

    fun lookup(context: Context, spoken: String): Lookup {
        val query = spoken.trim()
        val cleaned = query.replace(Regex("[\\s()\\-]"), "")
        if (cleaned.matches(Regex("\\+?[0-9]{5,16}"))) {
            return Lookup.Found(Target(query, cleaned))
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return Lookup.NoContactsAccess
        return selectPhonebookMatch(query, candidates(context, query))
    }

    /** A call selection like "első", "második" is valid ONLY after the
     * application has explicitly displayed numbered matches.
     */
    fun selectedChoice(input: String, choices: List<Target>): Target? {
        val n = normalize(input.trim()).trimEnd('.', '!', '?')
        val idx = when (n) {
            "1", "1 es", "elso", "az elso", "elsot", "az elsot", "one", "first" -> 0
            "2", "2 es", "masodik", "a masodik", "masodikat", "a masodikat", "two", "second" -> 1
            "3", "3 as", "harmadik", "a harmadik", "harmadikat", "a harmadikat", "three", "third" -> 2
            else -> return null
        }
        return choices.getOrNull(idx)
    }

    /** Other commands must interrupt a stale call flow, never become
     * phantom contacts or receive "call timed out" as their reply.
     */
    fun isOtherCommand(text: String): Boolean =
        JarvisInstalledApps.requestedLabel(text) != null ||
        VoiceNavigation.destinationFrom(text) != null ||
        JarvisPhoneActions.requestedAction(text) != null ||
        JarvisUpdateVoice.isRequest(text) ||
        JarvisMessageIntent.parse(text) != null

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
