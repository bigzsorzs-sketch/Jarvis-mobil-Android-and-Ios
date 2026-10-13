package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
        JarvisTool("Amazon", "Amazon", "▣"),
        JarvisTool("Üzenetek", "Üzenetek", "▢"),
        JarvisTool("Naptár", "Naptár", "▦"),
        JarvisTool("Jegyzetek", "Jegyzetek", "≡"),
        JarvisTool("Ébresztő", "Ébresztő", "◷"),
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
            modifier = Modifier.weight(1f).testTag("jarvis-drawer-list"),
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
                it.route in setOf("Számlák", "Naptár", "Jegyzetek", "Amazon")
            }.forEach { item ->
                ControlledOutlinedButton(
                    onClick = { onOpen(item.route) },
                    modifier = Modifier.fillMaxWidth()
                , commandLabel = item.name) {
                    Text(item.glyph, fontSize = 21.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(item.name, modifier = Modifier.weight(1f))
                }
            }
        }
        "Navigáció" -> {
            Text("Hangos útvonaltervezés: a mentett címek alapján a telepített térképalkalmazás indul.",
                color = JarvisPalette.subtleText)
            ControlledButton(onClick = {
                message = if (VoiceNavigation.resolve(context, "HOME") == null)
                    "Előbb mentsd el az otthoni címet az Eszközök menüben."
                else JarvisNavigationText.result(
                    JarvisLanguageSettings.load(context), VoiceNavigation.openDetailed(context, "HOME"))
            }, commandLabel = "Navigálás haza") { Text("Navigálás haza") }
            ControlledOutlinedButton(onClick = { onOpen("Továbbiak") }, commandLabel = "Otthoni cím beállítása") { Text("Otthoni cím beállítása") }
            JarvisSavedPlacesPanel(context)
        }
        "Email" -> {
            Text("Emailvázlatot készíthetsz. Gmail-fiók olvasása és keresése külön OAuth-kapcsolatot igényel.",
                color = JarvisPalette.subtleText)
            ControlledButton(onClick = { message = if (openEmail(context)) "Megnyitottam az emailírást."
                else "Nincs kompatibilis levelezőalkalmazás." }, commandLabel = "Email írása") { Text("Email írása") }
        }
        "Naptár" -> {
            ControlledButton(onClick = { message = if (openCalendar(context)) "Naptár megnyitva."
                else "Nem találok naptáralkalmazást." }, commandLabel = "Naptár megnyitása") { Text("Naptár megnyitása") }
            ControlledOutlinedButton(onClick = { onOpen("Továbbiak") }, commandLabel = "Esemény hozzáadása") { Text("Esemény hozzáadása") }
        }
        "Ébresztő" -> {
            val next = JarvisAlarms.current(context)
            Text(if (next > 0) "Aktív saját Jarvis-ébresztő." else "Nincs aktív saját Jarvis-ébresztő.",
                color = JarvisPalette.subtleText)
            ControlledButton(onClick = {
                message = if (openAlarms(context)) "Megnyitottam az Óra alkalmazást."
                else "Nem találok Óra alkalmazást."
            }, commandLabel = "Telefon ébresztői") { Text("Telefon ébresztői") }
            ControlledOutlinedButton(onClick = { onOpen("Továbbiak") }, commandLabel = "Jarvis-ébresztő kezelése") { Text("Jarvis-ébresztő kezelése") }
        }
    }
    if (message.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text(message, color = JarvisPalette.accent)
    }
}

