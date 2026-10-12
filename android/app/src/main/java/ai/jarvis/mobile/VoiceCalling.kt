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

    fun extractRequest(text: String): String? {
        val matched = Regex(
            "^(?:h[ií]vd\\s+fel|t[aá]rcs[aá]zd|telefon[aá]lj|call)\\s+(?:(?:a|az)\\s+)?(.+?)\\s*[.!?]?$",
            RegexOption.IGNORE_CASE
        ).find(text.trim()) ?: return null
        return matched.groupValues[1].trim().takeIf { it.isNotBlank() }
    }

    /**
     * Additional explicit natural-language call requests for AI proposals.
     * Return the target FROM THE USER, not a potentially hallucinated model target.
     */
    fun extractAiRequest(input: String): String? {
        val withoutWake = input.trim()
            .replace(Regex("""(?iu)^(?:(?:hey|hej|szia|ok)\s+)?jarvis\s*[,!:]?\s*"""), "")
            .replace(Regex("""(?iu)^k[eé]rlek\s+"""), "")
        val match = Regex(
            """(?iu)^(?:h[ií]vd\s+fel|t[aá]rcs[aá]zd|telefon[aá]lj|call|fel\s+tudn[aá]d\s+h[ií]vni|felh[ií]vn[aá]d|szeretn[eé]m\s+ha\s+felh[ií]vn[aá]d)\s+(?:(?:a|az)\s+)?(.+?)\s*[.!?]?$"""
        ).matchEntire(withoutWake) ?: return null
        val target = match.groupValues[1].trim()
        if (target.isBlank() || target.length > 70 ||
            Regex("""(?iu)\b(?:és|and|majd|aztán|then)\b""").containsMatchIn(target)) return null
        return target
    }

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
        val trusted = PeopleDirectory.find(context, spoken)
        if (trusted != null) return Lookup.Found(Target(trusted.nickname, trusted.phone))
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
                    val matches = matchesContactName(query, name)
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
