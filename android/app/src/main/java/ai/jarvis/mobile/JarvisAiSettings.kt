package ai.jarvis.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Temporary device-local OpenRouter testing without a hosted Jarvis server. */
@Composable
internal fun JarvisAiSettings(context: Context) {
    var selectedMode by remember { mutableStateOf(JarvisAiGateway.mode(context)) }
    var savedMode by remember { mutableStateOf(JarvisAiGateway.mode(context)) }
    var endpoint by remember { mutableStateOf(JarvisAiGateway.baseUrl(context)) }
    var credential by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(JarvisAiGateway.configured(context)) }
    var cloudConsent by remember { mutableStateOf(JarvisAiGateway.cloudConsent(context)) }
    val scope = rememberCoroutineScope()

    Text(if (BuildConfig.DEBUG) "AI-agy – belső teszt" else "AI-agy – biztonságos szerverkapcsolat",
        style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (BuildConfig.DEBUG) FilterChip(
            selected = selectedMode == JarvisAiGateway.DIRECT_TEST,
            onClick = {
                selectedMode = JarvisAiGateway.DIRECT_TEST
                credential = ""
                status = ""
            },
            label = { Text("Közvetlen OpenRouter") }
        )
        FilterChip(
            selected = selectedMode == JarvisAiGateway.SERVER,
            onClick = {
                selectedMode = JarvisAiGateway.SERVER
                credential = ""
                status = ""
            },
            label = { Text("Saját szerver") }
        )
    }
    Spacer(Modifier.height(8.dp))

    if (selectedMode == JarvisAiGateway.DIRECT_TEST && BuildConfig.DEBUG) {
        Text("Tesztmód: nincs szükség szervercímre. Az OpenRouter-kulcsodat egyszer kell megadni, a telefon titkosított tárhelyére mentjük.")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = credential,
            onValueChange = { credential = it },
            singleLine = true,
            label = { Text("OpenRouter API-kulcs") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "A kulcs nem kerül az APK-ba vagy a GitHubra. Teszteléshez korlátozott keretű, visszavonható kulcsot használj. " +
                "Mindkét telefonra külön meg kell adni a saját kulcsot.",
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Text("Szerveres mód: HTTPS-cím és saját tesztelői kód. Az OpenRouter-kulcs a szerveren marad.")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = endpoint, onValueChange = { endpoint = it },
            singleLine = true, label = { Text("AI-szerver HTTPS-címe") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = credential, onValueChange = { credential = it },
            singleLine = true, label = { Text("Személyes tesztelői kód") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(12.dp))
    Text(
        "Az AI-nak küldött kérdésed szövege a kiválasztott külső AI-szolgáltatóhoz " +
            "vagy a privát Jarvis szerveren keresztül a modellszolgáltatóhoz kerül. " +
            "Ne írj be jelszót, API-kulcsot, orvosi adatot vagy más személyek bizalmas adatait. " +
            "A helyi névjegyek, üzenetértesítések, fájlok és vércukornapló nem részei " +
            "automatikusan az AI-kérésnek.",
        style = MaterialTheme.typography.bodySmall
    )
    CheckboxRowCloudConsent(cloudConsent) { approved ->
        cloudConsent = approved
        if (!approved) {
            // Revocation takes effect immediately, even if the user does not
            // press Save. Any new cloud AI request is refused by the gateway.
            JarvisAiGateway.setCloudConsent(context, false)
            paired = false
            status = "Az AI-kérdések továbbításához adott hozzájárulást visszavontam."
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        if (paired && selectedMode == savedMode) "AI-hozzáférés mentve ezen a telefonon."
        else "Ehhez a módhoz még nincs beállított AI-hozzáférés."
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            if (!cloudConsent) {
                status = "Az AI-szöveg továbbításához külön, önkéntes hozzájárulás szükséges."
            } else {
                val error = if (selectedMode == JarvisAiGateway.DIRECT_TEST && BuildConfig.DEBUG)
                    JarvisAiGateway.saveDirect(context, credential)
                else JarvisAiGateway.save(context, endpoint, credential)
                if (error == null) {
                    JarvisAiGateway.setCloudConsent(context, true)
                    credential = ""
                    savedMode = selectedMode
                    paired = JarvisAiGateway.configured(context)
                }
                status = error ?: "Hozzáférés és hozzájárulás mentve. Teszteld az AI-kapcsolatot!"
            }
        }) { Text("Mentés") }
        OutlinedButton(onClick = {
            JarvisAiGateway.clear(context)
            credential = ""
            endpoint = ""
            selectedMode = JarvisAiGateway.mode(context)
            savedMode = JarvisAiGateway.mode(context)
            cloudConsent = false
            paired = false
            status = "A helyben tárolt hozzáférést töröltem."
        }) { Text("Leválasztás") }
    }

    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            scope.launch {
                busy = true
                try {
                    status = JarvisAiGateway.ask(
                        context,
                        "Válaszolj röviden magyarul: működik a Jarvis AI-kapcsolat?"
                    )
                } finally { busy = false }
            }
        },
        enabled = paired && selectedMode == savedMode && !busy
    ) { Text(if (busy) "Kapcsolódás..." else "AI-próbakérdés") }

    if (status.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(status)
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "A szokásos telefonparancsok helyben futnak. Az általános kérdéseket a kiválasztott AI-kapcsolat válaszolja meg.",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun CheckboxRowCloudConsent(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text("Hozzájárulok ahhoz, hogy a saját AI-kérdéseim szövegét " +
            "a kiválasztott külső AI-szolgáltató feldolgozza.",
            style = MaterialTheme.typography.bodySmall)
    }
}
