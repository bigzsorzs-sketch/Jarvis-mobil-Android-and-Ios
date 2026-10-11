package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Personal aliases are saved locally through Android Keystore encryption.
 * No automatic contact scraping or guessing; user selects each mapping.
 */
@Composable
internal fun PeopleDirectoryScreen(context: Context, onPermissions: () -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var contactSearch by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(PeopleDirectory.load(context)) }

    Text("Ismerősök és becenevek", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text("Például: Drágám → Eszter telefonszáma, címe. " +
        "Ezt te állítod be; Jarvis nem találja ki a személyazonosságot.",
        color = Color(0xFFB5C3D8))
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        nickname, onValueChange = { nickname = it.take(60) },
        label = { Text("Becenév vagy név (pl. Drágám)") }, modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        contactSearch, onValueChange = { contactSearch = it },
        label = { Text("Telefonos névjegy pontos neve (opcionális)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(onClick = {
        when (val match = VoiceCalling.lookup(context, contactSearch)) {
            is VoiceCalling.Lookup.Found -> {
                phone = match.target.number
                message = "Megtaláltam: " + match.target.label + ". Ellenőrizd a számot mentés előtt."
            }
            VoiceCalling.Lookup.NoContactsAccess -> message = "Engedélyezd a Névjegyek hozzáférést."
            VoiceCalling.Lookup.Ambiguous -> message = "Több névjegy van ezzel a névvel. Adj meg pontosabb nevet."
            VoiceCalling.Lookup.NotFound -> message = "Nem találtam a névjegyet."
        }
    }, enabled = contactSearch.isNotBlank()) {
        Text("Névjegy számának keresése")
    }
    OutlinedButton(onClick = onPermissions) { Text("Névjegyengedélyek") }
    OutlinedTextField(
        phone, onValueChange = { phone = it.take(30) },
        label = { Text("Telefonszám (+44… nemzetközi formátum)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        address, onValueChange = { address = it.take(180) },
        label = { Text("Navigációs cím (opcionális)") },
        modifier = Modifier.fillMaxWidth()
    )
    Button(onClick = {
        try {
            if (PeopleDirectory.save(context, KnownPerson(nickname, phone, address))) {
                people = PeopleDirectory.load(context)
                message = "A becenevet, számot és címet a telefon titkosított tárhelyére mentettem."
                nickname = ""
                phone = ""
                contactSearch = ""
                address = ""
            } else {
                message = "Adj meg egy becenevet és országkóddal kezdődő számot (pl. +447…). A 0-val kezdődő helyi szám nem elég."
            }
        } catch (_: Exception) { message = "A titkosított mentés nem sikerült." }
    }) { Text("Személy mentése") }
    if (message.isNotBlank()) Text(message, color = Color(0xFF60D5E5))
    Spacer(Modifier.height(18.dp))
    people.forEach { person ->
        key(person.nickname) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF182334))) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(person.nickname, color = Color.White)
                    Text(person.phone, color = Color(0xFFB5C3D8))
                    if (person.address.isNotBlank()) Text(person.address, color = Color(0xFFB5C3D8))
                    OutlinedButton(onClick = {
                        nickname = person.nickname
                        phone = person.phone
                        address = person.address
                        message = "Szerkesztés után mentsd újra."
                    }) { Text("Szerkesztés") }
                    TextButton(onClick = {
                        try {
                            PeopleDirectory.delete(context, person.nickname)
                            people = PeopleDirectory.load(context)
                            message = "Becenév törölve."
                        } catch (_: Exception) { message = "Nem sikerült a törlés." }
                    }) { Text("Törlés") }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
    Text("WhatsApphoz nemzetközi telefonszám kell. Messenger-névjegy nem azonos a telefonos " +
        "névjeggyel; ott a címzett kiválasztását a Messenger végzi.",
        color = Color(0xFFB5C3D8), style = MaterialTheme.typography.bodySmall)
}
