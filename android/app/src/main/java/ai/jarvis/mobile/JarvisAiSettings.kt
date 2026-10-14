package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

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
    var invitation by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var paired by remember { mutableStateOf(JarvisAiGateway.hasSavedConnection(context)) }
    var cloudConsent by remember { mutableStateOf(JarvisAiGateway.cloudConsent(context)) }
    var voiceConsent by remember { mutableStateOf(JarvisAiGateway.voiceConsent(context)) }
    val selectedLanguage = LocalJarvisLanguage.current
    val scope = rememberCoroutineScope()
    var audioTestRunning by remember { mutableStateOf(false) }
    var audioTestStatus by remember { mutableStateOf("") }
    val voiceTester = remember(context) { JarvisCloudVoicePlayer(context) }
    DisposableEffect(voiceTester) {
        onDispose { voiceTester.release() }
    }

    Text("AI-agy",
        style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ControlledFilterChip(
            selected = selectedMode == JarvisAiGateway.DIRECT_TEST,
            onClick = {
                selectedMode = JarvisAiGateway.DIRECT_TEST
                credential = ""
                status = ""
            },
            label = { Text("Közvetlen OpenRouter") }
        , commandLabel = "Közvetlen OpenRouter")
        ControlledFilterChip(
            selected = selectedMode == JarvisAiGateway.SERVER,
            onClick = {
                selectedMode = JarvisAiGateway.SERVER
                credential = ""
                status = ""
            },
            label = { Text("Saját szerver") }
        , commandLabel = "Saját szerver")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Jelenleg mentett kapcsolat: " +
            if (savedMode == JarvisAiGateway.DIRECT_TEST)
                "Közvetlen OpenRouter (modell: " + JarvisAiGateway.TEST_MODEL + ")"
            else "Saját Jarvis AI-szerver (/v1/chat)",
        style = MaterialTheme.typography.bodySmall
    )
    if (selectedMode != savedMode) {
        Text("A módváltás csak Mentés után lép életbe.",
            style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(8.dp))

    if (selectedMode == JarvisAiGateway.DIRECT_TEST) {
        Text("Saját OpenRouter-kapcsolat: nincs szükség Jarvis-szervercímre. Az API-kulcsodat a telefon titkosított tárhelyére mentjük.")
        Spacer(Modifier.height(8.dp))
        ControlledOutlinedTextField(
            value = credential,
            onValueChange = { credential = it },
            singleLine = true,
            label = { Text(if (paired && selectedMode == savedMode)
                "Új OpenRouter-kulcs (csak cseréhez)" else "OpenRouter API-kulcs") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        , commandLabel = if (paired && selectedMode == savedMode)
                "Új OpenRouter-kulcs (csak cseréhez)" else "OpenRouter API-kulcs")
        Spacer(Modifier.height(8.dp))
        Text(
            "A kulcs nem kerül az APK-ba vagy a GitHubra. Teszteléshez korlátozott keretű, visszavonható kulcsot használj. " +
                "Mindkét telefonra külön meg kell adni a saját kulcsot.",
            style = MaterialTheme.typography.bodySmall
        )
        if (paired && selectedMode == savedMode) {
            Text("A korábban mentett kulcsot megtartjuk. A hozzájárulás mentéséhez nem kell újra beírnod.",
                style = MaterialTheme.typography.bodySmall)
        }
    } else {
        Text("Privát Jarvis AI-szerver: a tesztelőnek nem kell OpenRouter API-kulcs. " +
            "A szervert először üzembe kell helyezni; csak ezután működik a meghívós aktiválás.")
        Spacer(Modifier.height(8.dp))
        ControlledOutlinedTextField(
            value = endpoint, onValueChange = { endpoint = it },
            singleLine = true, label = { Text("AI-szerver HTTPS-címe") },
            modifier = Modifier.fillMaxWidth()
        , commandLabel = "AI-szerver HTTPS-címe")
        Spacer(Modifier.height(8.dp))
        if (!paired || selectedMode != savedMode) {
            ControlledOutlinedTextField(
                value = invitation, onValueChange = { invitation = it.take(48) },
                singleLine = true, label = { Text("Egyszer használatos Jarvis meghívó") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            , commandLabel = "Egyszer használatos Jarvis meghívó")
            Spacer(Modifier.height(8.dp))
            ControlledButton(enabled = !busy, onClick = {
                if (!cloudConsent) {
                    status = "A külső AI-feldolgozáshoz először jelöld be a hozzájárulást."
                } else {
                    scope.launch {
                        busy = true
                        try {
                            val error = JarvisAiGateway.activateInvitation(
                                context, endpoint, invitation)
                            if (error == null) {
                                JarvisAiGateway.setCloudConsent(context, true)
                                invitation = ""
                                credential = ""
                                savedMode = JarvisAiGateway.SERVER
                                selectedMode = savedMode
                                paired = JarvisAiGateway.hasSavedConnection(context)
                            }
                            status = error ?: "Jarvis AI sikeresen aktiválva ezen a telefonon."
                        } finally { busy = false }
                    }
                }
            }, commandLabel = "Jarvis aktiválása") { Text("Jarvis aktiválása") }
        } else {
            Text("Ezen a telefonon már aktív a privát Jarvis-hozzáférés. " +
                "A szerver a hozzáférést visszavonhatja.",
                style = MaterialTheme.typography.bodySmall)
        }
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
            status = "Az AI-kérdések továbbításához adott hozzájárulást visszavontam."
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        RegisterJarvisControl("AI-hang használata", click = {
            voiceConsent = !voiceConsent
            JarvisAiGateway.setVoiceConsent(context, voiceConsent)
        })
        Checkbox(checked = voiceConsent, onCheckedChange = { enabled ->
            voiceConsent = enabled
            JarvisAiGateway.setVoiceConsent(context, enabled)
        })
        androidx.compose.material3.Text(
            listOf(
                "AI-hang használata (felhő, külön engedély)",
                "Use AI voice (cloud, separate consent)",
                "KI-Stimme verwenden (Cloud, gesonderte Zustimmung)",
                "Utiliser la voix IA (cloud, accord séparé)",
                "Usar voz de IA (nube, permiso separado)",
                "Usa la voce IA (cloud, consenso separato)",
                "Używaj głosu AI (chmura, osobna zgoda)"
            )[selectedLanguage.ordinal],
            style = MaterialTheme.typography.bodyMedium
        )
    }
    androidx.compose.material3.Text(
        listOf(
            "A felolvasott szöveg (akár személyes üzenet, névjegy vagy egészségügyi adat) az AI-hang szolgáltatójához kerül. Nincs Android TTS-tartalék, internet nélkül a hang nem működik.",
            "Spoken text (possibly messages, contact details or health information) is sent to the AI voice provider. There is no Android TTS fallback; voice requires internet.",
            "Der gesprochene Text (auch private Nachrichten, Kontaktdaten oder Gesundheitsdaten) wird an den KI-Sprachanbieter gesendet. Ohne Internet keine Sprachausgabe.",
            "Le texte lu (messages privés, contacts ou données médicales compris) est transmis au fournisseur vocal IA. Sans Internet, pas de voix.",
            "El texto leído (incluso mensajes, contactos o datos de salud) se envía al proveedor de voz de IA. Sin internet no hay voz.",
            "Il testo letto (anche messaggi, contatti o dati sanitari) viene inviato al fornitore della voce IA. Senza internet niente voce.",
            "Odczytywany tekst (także wiadomości, kontakty lub dane zdrowotne) jest przesyłany dostawcy głosu AI. Bez internetu głos nie działa."
        )[selectedLanguage.ordinal],
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(12.dp))
    Text("Hangellenőrzés: " + (JarvisVoiceReadiness.reason(context)
        ?: "Az AI-hang beállításai rendben vannak."),
        style = MaterialTheme.typography.bodySmall)
    Text(JarvisVoiceReadiness.mediaVolumeMessage(context),
        style = MaterialTheme.typography.bodySmall)
    ControlledOutlinedButton(
        enabled = !audioTestRunning,
        onClick = {
            val unavailable = JarvisVoiceReadiness.reason(context)
            if (unavailable != null) {
                audioTestStatus = unavailable
            } else {
                audioTestRunning = true
                audioTestStatus = "Kapcsolódás a felhős AI-hanghoz…"
                val testPhrase = when (selectedLanguage) {
                    JarvisLanguage.HUNGARIAN -> "Jarvis hangteszt. Ha ezt hallod, az AI-hang és a lejátszás működik."
                    JarvisLanguage.ENGLISH -> "Jarvis voice test. If you hear this, AI speech playback is working."
                    JarvisLanguage.GERMAN -> "Jarvis Sprachtest. Wenn du mich hörst, funktioniert die Sprachausgabe."
                    JarvisLanguage.FRENCH -> "Test vocal Jarvis. Si vous entendez ceci, le son fonctionne."
                    JarvisLanguage.SPANISH -> "Prueba de voz Jarvis. Si me escuchas, la voz funciona."
                    JarvisLanguage.ITALIAN -> "Test voce Jarvis. Se mi senti, la voce funziona."
                    JarvisLanguage.POLISH -> "Test głosu Jarvis. Jeśli mnie słyszysz, dźwięk działa."
                }
                val started = voiceTester.speak(testPhrase, selectedLanguage,
                    onStart = {
                        audioTestStatus = "Az Android elindította a médiahang lejátszását. " +
                            JarvisVoiceReadiness.mediaVolumeMessage(context)
                    },
                    completed = { success ->
                        audioTestRunning = false
                        audioTestStatus = if (success)
                            "Az MP3 lejátszása technikailag befejeződött. Ha nem hallottad, ellenőrizd a médiahangot és a Bluetooth-kimenetet."
                        else voiceTester.lastFailure.ifBlank {
                            "Az AI-hang nem játszható le. Ellenőrizd a kapcsolatot és a telefon hangkimenetét."
                        }
                    })
                if (!started) {
                    audioTestRunning = false
                    audioTestStatus = voiceTester.lastFailure.ifBlank {
                        "AI-hang nem indult el; ellenőrizd a hozzájárulást és a beállításokat."
                    }
                }
            }
        },
        commandLabel = "AI-hang tesztelése"
    ) { Text(if (audioTestRunning) "AI-hang teszt folyamatban…" else "AI-hang tesztelése") }
    if (audioTestStatus.isNotBlank())
        Text(audioTestStatus, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
    Text(
        if (paired && selectedMode == savedMode) "AI-hozzáférés mentve ezen a telefonon."
        else "Ehhez a módhoz még nincs beállított AI-hozzáférés."
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ControlledButton(onClick = {
            if (!cloudConsent) {
                status = "Az AI-szöveg továbbításához külön, önkéntes hozzájárulás szükséges."
            } else {
                val error = if (selectedMode == JarvisAiGateway.DIRECT_TEST)
                    JarvisAiGateway.saveDirect(context, credential)
                else if (paired && selectedMode == savedMode &&
                    endpoint == JarvisAiGateway.baseUrl(context)) null
                else "Előbb aktiváld a Jarvist a meghívóval."
                if (error == null) {
                    JarvisAiGateway.setCloudConsent(context, true)
                    credential = ""
                    savedMode = selectedMode
                    paired = JarvisAiGateway.hasSavedConnection(context)
                }
                status = error ?: "Hozzáférés és hozzájárulás mentve. Teszteld az AI-kapcsolatot!"
            }
        }, commandLabel = "Mentés") { Text("Mentés") }
        ControlledOutlinedButton(onClick = {
            JarvisAiGateway.clear(context)
            credential = ""
            invitation = ""
            endpoint = JarvisAiGateway.baseUrl(context)
            selectedMode = JarvisAiGateway.mode(context)
            savedMode = JarvisAiGateway.mode(context)
            cloudConsent = false
            paired = false
            status = "A helyben tárolt hozzáférést töröltem."
        }, commandLabel = "Leválasztás") { Text("Leválasztás") }
    }

    Spacer(Modifier.height(10.dp))
    ControlledButton(
        onClick = {
            scope.launch {
                busy = true
                try {
                    status = JarvisAiGateway.ask(
                        context,
                        if (JarvisLanguageSettings.load(context) == JarvisLanguage.ENGLISH)
                            "In one brief sentence, confirm that the Jarvis AI connection works."
                        else "Válaszolj röviden magyarul: működik a Jarvis AI-kapcsolat?"
                    )
                } finally { busy = false }
            }
        },
        enabled = paired && selectedMode == savedMode &&
            JarvisAiGateway.cloudConsent(context) && !busy
    , commandLabel = if (busy) "Kapcsolódás..." else "AI-próbakérdés") { Text(if (busy) "Kapcsolódás..." else "AI-próbakérdés") }

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
        RegisterJarvisControl("AI-kérdések felhőbe küldése", click = { onChange(!checked) })
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text("Hozzájárulok ahhoz, hogy a saját AI-kérdéseim szövegét " +
            "a kiválasztott külső AI-szolgáltató feldolgozza.",
            style = MaterialTheme.typography.bodySmall)
    }
}

