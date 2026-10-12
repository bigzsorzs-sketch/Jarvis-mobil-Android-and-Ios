package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** User-controlled app picker, without global package-management permissions. */
@Composable
internal fun JarvisInstalledAppsScreen(context: Context) {
    var apps by remember { mutableStateOf(JarvisInstalledApps.list(context)) }
    var filter by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf("") }
    Text("Telepített alkalmazások", style = MaterialTheme.typography.headlineSmall)
    Text("Jarvis csak megnyitja a kiválasztott alkalmazást. Nem olvas bele, " +
        "nem nyom meg gombokat és nem küld üzeneteket helyetted. " +
        "A telepített alkalmazások listája csak a telefonon marad; " +
        "nem küldöm sem a Jarvis szerverre, sem az AI-szolgáltatónak.",
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = filter, onValueChange = { filter = it.take(100) },
        label = { Text("Keresés az alkalmazások között") },
        singleLine = true, modifier = Modifier.fillMaxWidth()
    )
    TextButton(onClick = { apps = JarvisInstalledApps.list(context); feedback = "" }) {
        Text("Lista frissítése")
    }
    if (feedback.isNotBlank()) Text(feedback, color = MaterialTheme.colorScheme.primary)
    val visible = apps.filter { it.label.contains(filter, ignoreCase = true) }
    Text(visible.size.toString() + " alkalmazás", style = MaterialTheme.typography.labelMedium)
    visible.take(40).forEach { app ->
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                feedback = if (JarvisInstalledApps.open(context, app))
                    "Az Androidnak átadtam: " + app.label
                else "Nem sikerült megnyitni: " + app.label
            }
        ) { Text(app.label, modifier = Modifier.weight(1f)); Text("Megnyitás") }
    }
    if (visible.size > 40) Text("További találatokhoz pontosítsd a keresést.")
}
