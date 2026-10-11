package ai.jarvis.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
internal fun HandsFreePanel(context: Context) {
    val running = HandsFreeStatus.running
    val status = HandsFreeStatus.status
    val heard = HandsFreeStatus.heard
    val answer = HandsFreeStatus.answer
    var issue by remember { mutableStateOf("") }
    var allowWhileLocked by remember(context) {
        mutableStateOf(SecurePrefs(context).read("voice_private_on_lock", "false") == "true")
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            issue = "A mikrofonengedély hiányzik."
            return
        }
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HandsFreeService::class.java).setAction(HandsFreeService.START)
            )
            issue = ""
        } catch (_: Exception) {
            issue = "Az Android nem engedte elindítani a hangfigyelést."
        }
    }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
        else issue = "Hangfigyeléshez mikrofonengedély szükséges."
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF182334))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎙️ Hey Jarvis – kéz nélküli mód (BÉTA)",
                color = Color.White, fontWeight = FontWeight.Bold)
            Text("Indítsd el, majd mondd: Hey Jarvis. Az üzeneteket felolvasom, " +
                "a válaszokat csak az igen/nem megerősítésed után adom át a gyorsválasznak.",
                color = Color(0xFFB5C3D8))
            Text("Időkorlát: 15 perc. A telefon beszédfelismerőjét használja, " +
                "amely nem egész napos ébresztőszó-motor; egyes készülékeken " +
                "háttérben leállhat, és a beszédfelismerő szolgáltatója felhőt is használhat.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB5C3D8))
            Text("Állapot: $status", color = Color(0xFF60D5E5))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Privát üzenetek felolvasása lezárt képernyőn",
                        color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text("Alapértelmezés: kikapcsolva. Mások is hallhatják a felolvasást.",
                        color = Color(0xFFB5C3D8), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = allowWhileLocked, onCheckedChange = { enabled ->
                    SecurePrefs(context).write("voice_private_on_lock", enabled.toString())
                    allowWhileLocked = enabled
                })
            }
            if (!running) {
                Button(onClick = {
                    val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
                    val missing = wanted.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) request.launch(missing.toTypedArray()) else start()
                }) { Text("Hangfigyelés indítása") }
            } else {
                Button(onClick = {
                    context.startService(
                        Intent(context, HandsFreeService::class.java).setAction(HandsFreeService.STOP)
                    )
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB84444))) {
                    Text("Hangfigyelés leállítása")
                }
            }
            if (issue.isNotBlank()) Text(issue, color = Color(0xFFFFAAAA))
            if (heard.isNotBlank()) Text("Hallottam: $heard", color = Color.White)
            if (answer.isNotBlank()) Text("Jarvis: $answer", color = Color(0xFFB5C3D8))
            Text("A mikrofon aktív állapotát az Android értesítés mutatja. " +
                "Ott is leállíthatod a figyelést. Az üzenetekhez külön értesítési hozzáférés kell.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB5C3D8))
        }
    }
}
