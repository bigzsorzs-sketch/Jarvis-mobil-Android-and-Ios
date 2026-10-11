package ai.jarvis.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Explicit user-created alias and contact details; never guessed from a name. */
internal data class KnownPerson(val nickname: String, val phone: String, val address: String)

internal object PeopleDirectory {
    private const val STORE = "trusted_people_v1"

    fun normalized(raw: String): String = Normalizer.normalize(
        raw.lowercase(Locale.forLanguageTag("hu-HU")), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
     .replace(Regex("[^a-z0-9 ]"), " ")
     .trim().replace(Regex("\\s+"), " ")

    fun matchingName(spoken: String, stored: String): Boolean {
        val a = normalized(spoken)
        val b = normalized(stored)
        if (a == b) return true
        // Hungarian accusative, e.g. "a drágámat" -> "a drágám";
        // "Ferenc Várnait" -> "Ferenc Várnai". Exact user aliases only.
        return listOf("at", "et", "ot", "t").any { a.endsWith(it) && a.dropLast(it.length) == b }
    }

    fun validPhone(phone: String): Boolean =
        phone.matches(Regex("\\+?[0-9]{8,15}"))

    fun load(context: Context): List<KnownPerson> {
        val source = SecurePrefs(context).read(STORE, "[]")
        return try {
            val arr = JSONArray(source)
            (0 until arr.length()).mapNotNull {
                val value = arr.optJSONObject(it) ?: return@mapNotNull null
                val name = value.optString("nickname").trim()
                val phone = value.optString("phone").trim()
                val address = value.optString("address").trim()
                if (name.isBlank() || !validPhone(phone)) null
                else KnownPerson(name, phone, address)
            }.take(100)
        } catch (_: Exception) { emptyList() }
    }

    fun find(context: Context, spoken: String): KnownPerson? =
        load(context).singleOrNull { matchingName(spoken, it.nickname) }

    fun save(context: Context, person: KnownPerson): Boolean {
        val nick = person.nickname.trim()
        val phone = person.phone.replace(Regex("[\\s()\\-]"), "")
        val address = person.address.trim()
        if (nick.isBlank() || nick.length > 60 || !validPhone(phone) || address.length > 180) return false
        val existing = load(context).filterNot { normalized(it.nickname) == normalized(nick) }
        if (existing.size >= 100) return false
        val all = existing + KnownPerson(nick, phone, address)
        val arr = JSONArray()
        all.forEach { arr.put(JSONObject().put("nickname", it.nickname).put("phone", it.phone).put("address", it.address)) }
        SecurePrefs(context).write(STORE, arr.toString())
        return true
    }

    fun delete(context: Context, nickname: String) {
        val arr = JSONArray()
        load(context).filterNot { normalized(it.nickname) == normalized(nickname) }
            .forEach { arr.put(JSONObject().put("nickname", it.nickname)
                .put("phone", it.phone).put("address", it.address)) }
        SecurePrefs(context).write(STORE, arr.toString())
    }
}
