package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every displayed drawer entry has a real destination; no decorative dead buttons. */
internal data class JarvisTool(val name: String, val route: String, val glyph: String)
internal object JarvisToolCatalog {
    val all = listOf(
        JarvisTool("Főoldal", "Főoldal", "⌂"),
        JarvisTool("Chat", "Chat", "◉"),
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
internal fun JarvisDrawer(selected: String, onSelect: (String) -> Unit) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 320.dp),
        drawerContainerColor = Color(0xFFF7FAFF)
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp, start = 22.dp, bottom = 20.dp)) {
            Text("Jarvis", color = Color(0xFF06112C), fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Text("Egy agy. Mindenhez.", color = Color(0xFF74849C), fontSize = 14.sp)
        }
        HorizontalDivider(color = Color(0xFFE7ECF5))
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(JarvisToolCatalog.all, key = { it.name }) { item ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            item.name, fontSize = 15.sp,
                            color = if (selected == item.route) Color(0xFF0752D7) else Color(0xFF24334F),
                            fontWeight = if (selected == item.route) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            Text(item.glyph, fontSize = 23.sp,
                                color = if (selected == item.route) Color(0xFF0752D7) else Color(0xFF263954))
                        }
                    },
                    selected = selected == item.route,
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = Color(0xFFDDEAFF),
                        unselectedContainerColor = Color.Transparent
                    ),
                    onClick = { onSelect(item.route) },
                    modifier = Modifier.height(47.dp)
                )
            }
        }
    }
}

@Composable
internal fun JarvisToolLanding(context: Context, page: String, onOpen: (String) -> Unit) {
    var message by remember(page) { mutableStateOf("") }
    Text(page, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF1F5FA))
    Spacer(Modifier.height(12.dp))
    when (page) {
        "Munka" -> {
            Text("Munkához tartozó eszközök. Az AI-alapú feladatautomatizálás még nincs bekötve.",
                color = Color(0xFFB5C3D8))
            Button(onClick = { onOpen("Számlák") }) { Text("Számlázás") }
            OutlinedButton(onClick = { onOpen("Naptár") }) { Text("Naptár") }
            OutlinedButton(onClick = { onOpen("Jegyzetek") }) { Text("Jegyzetek") }
        }
        "Navigáció" -> {
            Text("Hangos útvonaltervezés: a mentett címek alapján a telepített térképalkalmazás indul.",
                color = Color(0xFFB5C3D8))
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
                color = Color(0xFFB5C3D8))
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
            Text("A telefon saját választójában engedélyezhetsz konkrét képeket és dokumentumokat.",
                color = Color(0xFFB5C3D8))
            Button(onClick = { onOpen("Engedélyek") }) { Text("Fotók és dokumentumok megnyitása") }
        }
        "Jegyzetek" -> {
            Text("A helyi, titkosított jegyzeteket az Eszközök oldalon szerkesztheted.",
                color = Color(0xFFB5C3D8))
            Button(onClick = { onOpen("Továbbiak") }) { Text("Jegyzetek megnyitása") }
        }
        "Ébresztő" -> {
            val next = JarvisAlarms.current(context)
            Text(if (next > 0) "Aktív saját Jarvis-ébresztő." else "Nincs aktív saját Jarvis-ébresztő.",
                color = Color(0xFFB5C3D8))
            Button(onClick = {
                message = if (openAlarms(context)) "Megnyitottam az Óra alkalmazást."
                else "Nem találok Óra alkalmazást."
            }) { Text("Telefon ébresztői") }
            OutlinedButton(onClick = { onOpen("Továbbiak") }) { Text("Jarvis-ébresztő kezelése") }
        }
        "Rendszerállapot" -> {
            Text("Jarvis Mobile – belső Android prototípus. Az AI-agy még nincs élesen bekötve.",
                color = Color(0xFFB5C3D8))
            Text("Hey Jarvis: " + if (HandsFreeStatus.running) "Aktív" else "Kikapcsolva",
                color = Color(0xFF60D5E5))
            Text("A zöld GitHub-build nem helyettesíti a telefonos tesztet.",
                color = Color(0xFFB5C3D8))
            Button(onClick = { onOpen("Engedélyek") }) { Text("Engedélyek") }
        }
    }
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(message, color = Color(0xFF60D5E5))
    }
}
