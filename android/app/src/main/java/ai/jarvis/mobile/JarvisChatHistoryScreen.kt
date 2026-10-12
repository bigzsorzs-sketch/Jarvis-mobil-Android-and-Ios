package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Searches the encrypted database on the phone. No AI request or cloud upload. */
@Composable
internal fun JarvisChatHistoryScreen(context: Context) {
    val store = remember(context) { JarvisChatStore(context) }
    var term by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JarvisChatEntry>>(emptyList()) }
    var count by remember { mutableLongStateOf(0L) }
    var issue by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            busy = true
            try {
                val fresh = withContext(Dispatchers.IO) {
                    store.count() to store.search(term.take(120))
                }
                count = fresh.first
                results = fresh.second
                issue = ""
            } catch (_: Exception) {
                issue = "Nem sikerült feloldani az előzményeket. Nem töröltem adatot."
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Text("Helyi beszélgetések", style = MaterialTheme.typography.headlineSmall)
    Text("A Jarvis saját beszélgetéseit titkosítva ezen a telefonon tárolja. " +
        "A keresés offline történik. Más alkalmazások teljes chatjeit nem olvassa.",
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = term, onValueChange = { term = it.take(120) },
        singleLine = true, label = { Text("Keresés az előzményekben") },
        modifier = Modifier.fillMaxWidth())
    Button(onClick = { refresh() }, enabled = !busy) {
        Text(if (busy) "Keresés…" else "Helyi keresés")
    }
    Text(count.toString() + " mentett beszélgetési forduló; legfeljebb 60 találat látható.",
        style = MaterialTheme.typography.labelMedium)
    if (issue.isNotBlank()) Text(issue, color = MaterialTheme.colorScheme.error)
    results.forEach { item ->
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK)
                    .format(Date(item.createdAtMs)), style = MaterialTheme.typography.labelSmall)
                Text(item.request, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(item.answer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
