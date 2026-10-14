package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Local only: read the addresses a contact owner explicitly saved in Android
 * Contacts, never infer a destination from the person's name or send addresses
 * to AI. Ambiguous names/addresses are not automatically chosen.
 */
internal object JarvisContactAddresses {
    data class Entry(val contactId: Long, val displayName: String, val address: String)

    /** The same normalization as confirmed calling, but never initiates a call. */
    internal fun uniqueAddress(spoken: String, entries: List<Entry>): String? {
        val matching = entries.filter {
            it.address.isNotBlank() && VoiceCalling.matchesContactName(spoken, it.displayName)
        }.ifEmpty {
            entries.filter {
                it.address.isNotBlank() && VoiceCalling.matchesFirstName(spoken, it.displayName)
            }
        }
        // A person with two saved addresses is ambiguous, as are two people
        // sharing a first name. Do not silently pick an arbitrary row.
        val contacts = matching.groupBy { it.contactId }
        if (contacts.size != 1) return null
        val addresses = contacts.values.single().map { it.address.trim() }
            .filter { it.length in 5..180 && '\n' !in it && '\r' !in it }
            .distinct()
        return addresses.singleOrNull()
    }

    fun find(context: Context, spoken: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED) return null
        val entries = ArrayList<Entry>()
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID,
                    ContactsContract.CommonDataKinds.StructuredPostal.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS
                ),
                null, null, null
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID)
                val name = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.StructuredPostal.DISPLAY_NAME)
                val address = cursor.getColumnIndexOrThrow(
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                while (cursor.moveToNext() && entries.size < 1000) {
                    val label = cursor.getString(name).orEmpty()
                    if (VoiceCalling.matchesContactName(spoken, label) ||
                        VoiceCalling.matchesFirstName(spoken, label)) {
                        entries.add(Entry(cursor.getLong(id), label, cursor.getString(address).orEmpty()))
                    }
                }
            }
            uniqueAddress(spoken, entries)
        } catch (_: SecurityException) { null }
          catch (_: Exception) { null }
    }
}
