package ai.jarvis.mobile

import java.nio.charset.Charset
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

/**
 * Extract and compare Gmail From/To header identities on-device only.
 * Never guess an address from an AI model or take the first partial match.
 */
internal object GmailMailboxHeader {
    private val namedAddress = Regex(
        """(?i)(?:"?([^<>,"]+?)"?\s*)?<([A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,})>"""
    )
    private val directAddress = Regex(
        """(?i)\b[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}\b"""
    )
    private val encodedWord = Regex("""=\?([^?]+)\?([bBqQ])\?([^?]+)\?=""")

    fun plain(input: String): String = Normalizer.normalize(input.lowercase(Locale.ROOT),
        Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim().replace(Regex("""\s+"""), " ")

    private fun mimeDecoded(raw: String): String = encodedWord.replace(raw) { match ->
        runCatching {
            val charset = Charset.forName(match.groupValues[1])
            val bytes = if (match.groupValues[2].equals("B", true)) {
                Base64.getDecoder().decode(match.groupValues[3])
            } else {
                val text = match.groupValues[3].replace('_', ' ')
                val out = java.io.ByteArrayOutputStream()
                var offset = 0
                while (offset < text.length) {
                    if (text[offset] == '=' && offset + 2 < text.length) {
                        val hex = text.substring(offset + 1, offset + 3).toIntOrNull(16)
                        if (hex != null) {
                            out.write(hex)
                            offset += 3
                            continue
                        }
                    }
                    out.write(text[offset].code)
                    offset++
                }
                out.toByteArray()
            }
            String(bytes, charset)
        }.getOrDefault(match.value)
    }

    /** Return only addresses explicitly associated with the requested display name
     * (or a matching complete mailbox local-part when the header lacks a name).
     */
    fun matchAddress(requestedName: String, header: String): Set<String> {
        val name = plain(requestedName)
        if (name.length < 2) return emptySet()
        val matched = linkedSetOf<String>()
        val decoded = mimeDecoded(header).replace(Regex("""[\r\n\p{Cntrl}]"""), " ")
        for (item in namedAddress.findAll(decoded)) {
            val address = item.groupValues[2]
            val displayName = plain(item.groupValues[1].trim().trim('"'))
            val localPart = plain(address.substringBefore('@'))
            // Some mailers show Hungarian family/given names in reverse order.
            // Require the same complete tokens rather than a fuzzy substring.
            val reversedNamesMatch = name.contains(' ') &&
                displayName.split(' ').sorted() == name.split(' ').sorted()
            val fullMatch = displayName == name || reversedNamesMatch ||
                (displayName.isBlank() && localPart == name)
            // A one-word request can match a complete token only, but multiple
            // matching addresses remain ambiguous and MUST NOT be auto-selected.
            val oneWordMatch = !name.contains(' ') &&
                displayName.split(' ').contains(name) && displayName.isNotBlank()
            if (fullMatch || oneWordMatch) matched.add(address)
        }
        // Bare email addresses have no display name; exact local-part match only.
        for (item in directAddress.findAll(decoded)) {
            val address = item.value
            if (plain(address.substringBefore('@')) == name) matched.add(address)
        }
        return matched
    }
}
