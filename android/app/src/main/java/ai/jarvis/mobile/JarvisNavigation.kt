package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every tool in the Munka page has a real destination. */
internal data class JarvisTool(val name: String, val route: String, val glyph: String)
internal object JarvisToolCatalog {
    val all = listOf(
        JarvisTool("Főoldal", "Főoldal", "⌂"),
        JarvisTool("Munka", "Munka", "▣"),
        JarvisTool("Navigáció", "Navigáció", "➤"),
        JarvisTool("Vércukor", "Vércukor", "♧"),
        JarvisTool("Számlázás", "Számlák", "▤"),
        JarvisTool("Email", "Email", "✉"),
        JarvisTool("Üzenetek", "Üzenetek", "▢"),
        JarvisTool("Hívás", "Névjegyek", "☎"),
        JarvisTool("Naptár", "Naptár", "▦"),
        JarvisTool("Térkép", "Térkép", "◎"),
        JarvisTool("Fájlok és képek", "Fájlok", "▱"),
        JarvisTool("Jegyzetek", "Jegyzetek", "≡"),
        JarvisTool("Ébresztő", "Ébresztő", "◷"),
        JarvisTool("Ismerősök", "Ismerősök", "♡"),
        JarvisTool("Beállítások", "Engedélyek", "⚙"),
        JarvisTool("Rendszerállapot", "Rendszerállapot", "◈")
    )
}

@Composable
internal fun JarvisToolLanding(context: Context, page: String, onOpen: (String) -> Unit) {
    var message by remember(page) { mutableStateOf("") }
    Text(page, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = JarvisPalette.mainText)
    Spacer(Modifier.height(12.dp))
    when (page) {
        "Munka" -> {
            Text("Eszközök", color = JarvisPalette.subtleText)
            JarvisToolCatalog.all.filterNot {
                it.route == "Főoldal" || it.route == "Munka"
            }.forEach { item ->
                OutlinedButton(
                    onClick = { onOpen(item.route) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(item.glyph, fontSize = 21.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(item.name, modifier = Modifier.weight(1f))
                }
            }
        }
        "Navigáció" -> {
            Text("Hangos útvonaltervezés: a mentett címek alapján a telepített térképalkalmazás indul.",
                color = JarvisPalette.subtleText)
            Button(onClick = {
                message = if (VoiceNavigation.resolve(context, "HOME") == null)
                    "Előbb mentsd el az otthoni címet az Eszközök menüben."
                else if (VoiceNavigation.open(context, "HOME")) "Átadtam az útvonalat a térképnek."
                else "Nem érhető el térképalkalmazás."
            }) { Text("Navigálás haza") }
            OutlinedButton(onClick = { onOpen("Ismerősök") }) { Text("Személyek és címek") }
            OutlinedButton(onClick = { onOpen("Továbbiak") }) { Text("Otthoni cím beállítása") }
        }
        "Email" -> {
            Text("Emailvázlatot készíthetsz. Gmail-fiók olvasása és keresése külön OAuth-kapcsolatot igényel.",
                color = JarvisPalette.subtleText)
            Button(onClick = { message = if (openEmail(context)) "Megnyitottam az emailírást."
                else "Nincs kompatibilis levelezőalkalmazás." }) { Text("Email írása") }
        }
        "Naptár" -> {
            Button(onClick = { message = if (openCalendar(context)) "Naptár megnyitva."
                else "Nem találok naptáralkalmazást." }) { Text("Naptár megnyitása") }
            OutlinedButton(onClick = { onOpen("Továbbiak") }) { Text("Esemény hozzáadása") }
        }
        "Térkép" -> {
            Button(onClick = { message = if (openMap(context)) "Térkép megnyitva."
                else "Nem találok térképalkalmazást." }) { Text("Térkép megnyitása") }
            OutlinedButton(onClick = { onOpen("Navigáció") }) { Text("Navigációs beállítások") }
        }
        "Fájlok" -> {
            Text("Kamera, legfeljebb 20 kép és 1 GB összméret a főoldalon lévő + gombbal.",
                color = JarvisPalette.subtleText)
            Button(onClick = { onOpen("Főoldal") }) { Text("Vissza a + csatoláshoz") }
        }
        "Jegyzetek" -> {
            Text("A helyi, titkosított jegyzeteket az Eszközök oldalon szerkesztheted.",
                color = JarvisPalette.subtleText)
            Button(onClick = { onOpen("Továbbiak") }) { Text("Jegyzetek megnyitása") }
        }
        "Ébresztő" -> {
            val next = JarvisAlarms.current(context)
            Text(if (next > 0) "Aktív saját Jarvis-ébresztő." else "Nincs aktív saját Jarvis-ébresztő.",
                color = JarvisPalette.subtleText)
            Button(onClick = {
                message = if (openAlarms(context)) "Megnyitottam az Óra alkalmazást."
                else "Nem találok Óra alkalmazást."
            }) { Text("Telefon ébresztői") }
            OutlinedButton(onClick = { onOpen("Továbbiak") }) { Text("Jarvis-ébresztő kezelése") }
        }
        "Rendszerállapot" -> {
            Text("Jarvis Mobile – belső Android prototípus. Az AI-agy még nincs élesen bekötve.",
                color = JarvisPalette.subtleText)
            Text("Hey Jarvis: " + if (HandsFreeStatus.running) "Aktív" else "Kikapcsolva",
                color = JarvisPalette.accent)
            Text("A zöld GitHub-build nem helyettesíti a telefonos tesztet.",
                color = JarvisPalette.subtleText)
            Button(onClick = { onOpen("Engedélyek") }) { Text("Engedélyek") }
        }
    }
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(message, color = JarvisPalette.accent)
    }
}
