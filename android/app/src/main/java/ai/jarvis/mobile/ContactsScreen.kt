package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

internal data class PhoneContact(val name: String, val number: String)

private fun readPhoneContacts(context: Context): List<PhoneContact> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED) return emptyList()
    val result = ArrayList<PhoneContact>()
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val columns = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    try {
        context.contentResolver.query(
            uri, columns, null, null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(columns[0])
            val numberIndex = cursor.getColumnIndexOrThrow(columns[1])
            while (cursor.moveToNext() && result.size < 80) {
                val number = cursor.getString(numberIndex).orEmpty()
                if (number.isNotBlank()) {
                    result.add(PhoneContact(cursor.getString(nameIndex).orEmpty(), number))
                }
            }
        }
    } catch (_: SecurityException) {
        return emptyList()
    } catch (_: Exception) {
        return emptyList()
    }
    return result
}

@Composable
internal fun ContactsScreen(context: Context, onPermissions: () -> Unit) {
    val permissionRefresh = PermissionRefresh.serial
    val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    val contacts = remember(permissionRefresh, allowed) {
        if (allowed) readPhoneContacts(context) else emptyList()
    }
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Text("Névjegyek", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Spacer(Modifier.height(10.dp))
    androidx.compose.material3.Text(
        listOf(
            "A névjegykeresés a telefonon történik. Ha engedélyezed az AI-hangot, a hangosan kimondott nevek és számok a hangszolgáltatóhoz kerülhetnek.",
            "Contact lookup stays on your phone. If cloud AI voice is enabled, spoken names and numbers may be sent to the voice provider.",
            "Die Kontaktsuche bleibt auf dem Telefon. Mit KI-Stimme können vorgelesene Namen und Nummern an den Anbieter gesendet werden.",
            "La recherche de contacts reste sur le téléphone. Avec la voix IA, noms et numéros prononcés peuvent être transmis au fournisseur.",
            "La búsqueda de contactos es local. Con voz IA, nombres y números pronunciados pueden enviarse al proveedor.",
            "La ricerca contatti è locale. Con voce IA, nomi e numeri pronunciati possono essere inviati al fornitore.",
            "Wyszukiwanie kontaktów jest lokalne. Głos AI może przesyłać wypowiadane imiona i numery do dostawcy."
        )[LocalJarvisLanguage.current.ordinal],
        color = Color(0xFFB5C3D8)
    )
    OutlinedButton(onClick = onPermissions) {
        Text(if (allowed) "Engedélyek kezelése" else "Névjegy-hozzáférés engedélyezése")
    }
    if (!allowed) {
        Text("A névjegyek megtekintéséhez előbb engedélyt kell adnod.", color = Color(0xFFB5C3D8))
        return
    }
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        label = { Text("Névjegy keresése") }, modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    contacts.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        .take(30).forEach { contact ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF182334))) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(contact.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(contact.number, color = Color(0xFFB5C3D8))
                    OutlinedButton(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(contact.number)))
                        result = if (openSystemIntent(context, intent)) "Tárcsázó megnyitva." else
                            "A tárcsázót nem sikerült megnyitni."
                    }) { Text("Tárcsázás előkészítése") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    if (result.isNotBlank()) Text(result, color = Color(0xFF60D5E5))
    Text("Legfeljebb 80 névjegyet jelenítünk meg a prototípusban.",
        color = Color(0xFFB5C3D8))
}
