package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One menu for all tools; no duplicate map, attachment, chat or system-status pages. */
internal data class JarvisTool(val name: String, val route: String, val glyph: String)
internal object JarvisToolCatalog {
    val all = listOf(
        JarvisTool("Főoldal", "Főoldal", "⌂"),
        JarvisTool("Munka", "Munka", "▣"),
        JarvisTool("Navigáció", "Navigáció", "➤"),
        JarvisTool("Alkalmazások", "Alkalmazások", "▦"),
        JarvisTool("Előzmények", "Előzmények", "◷"),
        JarvisTool("Vércukor", "Vércukor", "♧"),
        JarvisTool("Számlázás", "Számlák", "▤"),
        JarvisTool("Email", "Email", "✉"),
        JarvisTool("Üzenetek", "Üzenetek", "▢"),
        JarvisTool("Hívás", "Névjegyek", "☎"),
        JarvisTool("Naptár", "Naptár", "▦"),
        JarvisTool("Jegyzetek", "Jegyzetek", "≡"),
        JarvisTool("Ébresztő", "Ébresztő", "◷"),
        JarvisTool("Ismerősök", "Ismerősök", "♡"),
        JarvisTool("Beállítások", "Beállítások", "⚙")
    )
    // Actual destinations reachable from tool pages and Settings.
    val subpages = listOf(
        JarvisTool("Hangbeállítások", "Hangbeállítások", "≋"),
        JarvisTool("AI-agy", "AI-agy", "✧"),
        JarvisTool("Engedélyek", "Engedélyek", "✓"),
        JarvisTool("Továbbiak", "Továbbiak", "▣")
    )
}

@Composable
internal fun JarvisDrawer(selected: String, onSelect: (String) -> Unit) {
    val dark = LocalJarvisDark.current
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 320.dp),
        drawerContainerColor = JarvisPalette.background
    ) {
        Text("Jarvis", color = JarvisPalette.mainText, fontSize = 31.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 22.dp, top = 24.dp, bottom = 24.dp))
        HorizontalDivider(color = if (dark) Color(0xFF25344E) else Color(0xFFE7ECF5))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(JarvisToolCatalog.all, key = { it.route }) { item ->
                NavigationDrawerItem(
                    label = { Text(item.name, fontSize = 15.sp,
                        color = JarvisPalette.mainText) },
                    icon = {
                        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                            Text(item.glyph, fontSize = 23.sp, color = JarvisPalette.accent)
                        }
                    },
                    selected = selected == item.route,
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = if (dark) Color(0xFF172F56) else Color(0xFFDDEAFF),
                        unselectedContainerColor = Color.Transparent
                    ),
                    onClick = { onSelect(item.route) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }
}

@Composable
internal fun JarvisToolLanding(context: Context, page: String, onOpen: (String) -> Unit) {
    var message by remember(page) { mutableStateOf("") }
    Text(page, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = JarvisPalette.mainText)
    Spacer(Modifier.height(12.dp))
    when (page) {
        "Munka" -> {
            Text("Eszközök", color = JarvisPalette.subtleText)
            JarvisToolCatalog.all.filter {
                it.route in setOf("Számlák", "Naptár", "Jegyzetek")
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
    }
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(message, color = JarvisPalette.accent)
    }
}

