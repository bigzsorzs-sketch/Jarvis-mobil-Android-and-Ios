package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** One-time tester pairing. The provider API key never reaches this form. */
@Composable
internal fun JarvisAiSettings(context: Context) {
    var endpoint by remember { mutableStateOf(JarvisAiGateway.baseUrl(context)) }
    var accessCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(JarvisAiGateway.configured(context)) }
    val scope = rememberCoroutineScope()

    Text("AI-agy – tesztkapcsolat", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Text("A saját AI-szervered HTTPS-címét és a személyes tesztelői kódodat kell egyszer megadni. A kód titkosítva marad a telefonon; az OpenRouter API-kulcs nem kerül a telefonra.")
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = endpoint, onValueChange = { endpoint = it },
        singleLine = true, label = { Text("AI-szerver HTTPS-címe") },
        placeholder = { Text("https://ai.pelda.hu") },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = accessCode, onValueChange = { accessCode = it },
        singleLine = true, label = { Text("Személyes tesztelői kód") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text(if (paired) "A hozzáférés mentve ezen a telefonon." else "Nincs beállított AI-hozzáférés.")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val error = JarvisAiGateway.save(context, endpoint, accessCode)
            status = error ?: "Beállítás mentve. A próbakérdéssel ellenőrizheted."
            paired = JarvisAiGateway.configured(context)
            if (error == null) accessCode = ""
        }) { Text("Mentés") }
        OutlinedButton(onClick = {
            JarvisAiGateway.clear(context)
            accessCode = ""
            endpoint = ""
            paired = false
            status = "A hozzáférést töröltem erről a készülékről."
        }) { Text("Leválasztás") }
    }
    Spacer(Modifier.height(10.dp))
    Button(onClick = {
        scope.launch {
            busy = true
            try { status = JarvisAiGateway.ask(context, "Válaszolj röviden magyarul: működik a Jarvis AI-kapcsolat?") }
            finally { busy = false }
        }
    }, enabled = paired && !busy) { Text(if (busy) "Kapcsolódás..." else "AI-próbakérdés") }
    if (status.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(status)
    }
    Spacer(Modifier.height(10.dp))
    Text("Minden tesztelő saját kódot kap. A szokásos telefonparancsok helyben futnak; az általános kérdéseket a Jarvis AI-szervere válaszolja meg.")
}
