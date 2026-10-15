package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
        JarvisTool("Alkalmazások", "Alkalmazások", "▦"),
        JarvisTool("Előzmények", "Előzmények", "◷"),
        JarvisTool("Vércukor", "Vércukor", "♧"),
        JarvisTool("Smart Home", "Smart Home", "⌂"),
        JarvisTool("Számlázás", "Számlák", "▤"),
        JarvisTool("Email", "Email", "✉"),
        JarvisTool("Amazon", "Amazon", "▣"),
        JarvisTool("Üzenetek", "Üzenetek", "▢"),
        JarvisTool("Naptár", "Naptár", "▦"),
        JarvisTool("Jegyzetek", "Jegyzetek", "≡"),
        JarvisTool("Ébresztő", "Ébresztő", "◷"),
        JarvisTool("Beállítások", "Beállítások", "⚙")
    )
    val primary = all.filter { it.route in setOf("Alkalmazások", "Vércukor", "Számlák", "Email") }
    val more = all.filter { it.route !in primary.map { tool -> tool.route } &&
        it.route !in setOf("Főoldal", "Előzmények", "Beállítások") }
    // Actual destinations reachable from tool pages and Settings.
    val subpages = listOf(
        JarvisTool("Hangbeállítások", "Hangbeállítások", "≋"),
        JarvisTool("AI-agy", "AI-agy", "✧"),
        JarvisTool("Engedélyek", "Engedélyek", "✓"),
        JarvisTool("Továbbiak", "Továbbiak", "▣")
    )
}

@Composable
internal fun JarvisMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = JarvisTranslations.text("Menü megnyitása", LocalJarvisLanguage.current)
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, shadowElevation = 2.dp,
        modifier = modifier.size(48.dp).semantics { contentDescription = label }) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(23.dp)) {
                drawLine(Color(0xFF101820), Offset(1.dp.toPx(), size.height * .35f),
                    Offset(size.width - 1.dp.toPx(), size.height * .35f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Color(0xFF101820), Offset(1.dp.toPx(), size.height * .65f),
                    Offset(size.width * .65f, size.height * .65f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun DrawerTool(item: JarvisTool, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(item.name, fontSize = 15.sp, color = JarvisPalette.mainText) },
        icon = { Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            Text(item.glyph, fontSize = 21.sp, color = JarvisPalette.accent)
        } },
        selected = selected,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = if (LocalJarvisDark.current) Color(0xFF172F56) else Color(0xFFDDEAFF),
            unselectedContainerColor = Color.Transparent
        ),
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag("jarvis-tool-" + item.route)
    )
}

@Composable
internal fun JarvisDrawer(
    selected: String,
    conversations: List<JarvisChatSummary>,
    currentConversationId: String,
    onNewConversation: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    var moreExpanded by remember { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(.86f).widthIn(max = 360.dp),
        drawerContainerColor = JarvisPalette.background
    ) {
        Text("Jarvis", color = JarvisPalette.mainText, fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 14.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("jarvis-drawer-list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            items(JarvisToolCatalog.primary, key = { "tool-" + it.route }) { tool ->
                DrawerTool(tool, selected == tool.route) { onSelect(tool.route) }
            }
            item(key = "more") {
                NavigationDrawerItem(
                    label = { Text("Továbbiak", color = JarvisPalette.mainText) },
                    icon = { Text("▦", fontSize = 23.sp, color = JarvisPalette.accent) },
                    badge = { Text(if (moreExpanded) "−" else "+", color = JarvisPalette.subtleText) },
                    selected = false,
                    onClick = { moreExpanded = !moreExpanded },
                    modifier = Modifier.testTag("jarvis-drawer-more")
                )
            }
            if (moreExpanded) {
                items(JarvisToolCatalog.more + JarvisToolCatalog.subpages, key = { "extra-" + it.route }) { tool ->
                    // The existing settings subpage keeps its route, with a distinct visible label.
                    val entry = if (tool.route == "Továbbiak") tool.copy(name = "További beállítások") else tool
                    DrawerTool(entry, selected == tool.route) { onSelect(tool.route) }
                }
            }
            item(key = "recent-header") {
                Text("Legutóbbiak", color = JarvisPalette.subtleText, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp))
            }
            if (conversations.isEmpty()) item(key = "empty") {
                Text("Még nincs mentett beszélgetés", color = JarvisPalette.subtleText,
                    fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            }
            items(conversations, key = { "chat-" + it.id }) { chat ->
                NavigationDrawerItem(
                    label = {
                        // User-authored titles must never pass through the UI translator.
                        androidx.compose.material3.Text(
                            if (chat.id == "legacy") JarvisTranslations.text("Korábbi beszélgetések", LocalJarvisLanguage.current)
                            else chat.title,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = JarvisPalette.mainText, fontSize = 15.sp)
                    },
                    selected = selected == "Főoldal" && currentConversationId == chat.id,
                    onClick = { onOpenConversation(chat.id) },
                    modifier = Modifier.testTag("jarvis-conversation-" + chat.id)
                )
            }
            item(key = "history") {
                TextButton(onClick = { onSelect("Előzmények") }, modifier = Modifier.testTag("jarvis-all-history")) {
                    Text("Keresés az előzményekben")
                }
            }
        }
        HorizontalDivider(color = JarvisPalette.subtleText.copy(alpha = .16f))
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onNewConversation, modifier = Modifier.weight(1f).testTag("jarvis-new-chat"),
                shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075FF1))) {
                Text("Új beszélgetés", color = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            val settingsLabel = JarvisTranslations.text("Beállítások", LocalJarvisLanguage.current)
            IconButton(onClick = { onSelect("Beállítások") }, modifier = Modifier.testTag("jarvis-drawer-settings")
                .semantics { contentDescription = settingsLabel }) {
                Text("⚙", fontSize = 26.sp, color = JarvisPalette.mainText)
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

