package ai.jarvis.mobile

import ai.jarvis.mobile.LocalizedText as Text

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Voice configuration and diagnostics only; recording is controlled on Home. */
@Composable
internal fun HandsFreePanel(context: Context) {
    val status = HandsFreeStatus.status
    Card(colors = CardDefaults.cardColors(containerColor = JarvisPalette.panel)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Hangbeállítások", color = JarvisPalette.mainText,
                fontWeight = FontWeight.Bold)
            Text("A mikrofon és a folyamatos beszélgetés kizárólag a Jarvis főoldalán indítható és állítható le. A kék mikrofon elindítja, a piros leállítja.",
                color = JarvisPalette.subtleText)
            Text("A hangfigyelés a telefon Android beszédfelismerőjét használja; egyes készülékeken a háttérben megszakadhat. A mikrofon működését az Android értesítése is jelzi.",
                color = JarvisPalette.subtleText,
                style = MaterialTheme.typography.bodySmall)
            Text("Állapot: $status", color = JarvisPalette.accent)
            Text("Biztonság: hangazonosítás még nincs beépítve. " +
                "Lezárt telefonon a hangutasítások nem indítanak hívást, nem olvasnak " +
                "személyes adatot és nem vezérelnek Smart Home eszközt. " +
                "Oldd fel a telefont a hangvezérléshez.",
                color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
            if (HandsFreeStatus.audioError.isNotBlank()) {
                Text("Hanghiba: " + HandsFreeStatus.audioError, color = Color(0xFFFFAAAA))
            }
            Text("A hangos választ a főoldalon tesztelheted. A hangbeállítások itt nem indítanak újabb beszédfelismerőt.",
                color = JarvisPalette.subtleText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
