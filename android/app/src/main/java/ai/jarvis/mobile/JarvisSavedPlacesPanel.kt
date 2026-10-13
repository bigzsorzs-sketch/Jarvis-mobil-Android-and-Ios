package ai.jarvis.mobile

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The user selects Google Takeout exports explicitly; no hidden Google account scraping. */
@Composable
internal fun JarvisSavedPlacesPanel(context: Context) {
    val language = LocalJarvisLanguage.current
    val places = remember(context) {
        mutableStateListOf<JarvisSavedPlace>().apply { addAll(JarvisSavedPlaces.list(context)) }
    }
    var status by remember { mutableStateOf("") }
    val coroutine = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) coroutine.launch {
            status = message(language, "Importálás…", "Importing…", "Importiere…",
                "Importation…", "Importando…", "Importazione…", "Importowanie…")
            val result = withContext(Dispatchers.IO) {
                runCatching { JarvisSavedPlaces.importFile(context, uri) }
            }
            result.fold(
                onSuccess = { count ->
                    places.clear()
                    places.addAll(JarvisSavedPlaces.list(context))
                    status = message(language,
                        "$count hely feldolgozva. Az adatok csak ezen a telefonon vannak.",
                        "$count places processed. Data stays on this phone.",
                        "$count Orte verarbeitet. Daten bleiben auf diesem Gerät.",
                        "$count lieux traités. Les données restent sur cet appareil.",
                        "$count lugares procesados. Los datos quedan en el teléfono.",
                        "$count luoghi elaborati. Dati solo su questo telefono.",
                        "Przetworzono $count miejsc. Dane pozostają w telefonie.")
                },
                onFailure = {
                    status = message(language, "Importálási hiba: ",
                        "Import failed: ", "Import fehlgeschlagen: ", "Erreur : ",
                        "Error de importación: ", "Importazione fallita: ",
                        "Błąd importu: ") + (it.message ?: "")
                }
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(message(language,
        "Google Maps mentett helyek (helyi másolat)",
        "Google Maps saved places (local copy)",
        "Gespeicherte Google-Maps-Orte (lokale Kopie)",
        "Lieux Google Maps enregistrés (copie locale)",
        "Lugares guardados de Google Maps (copia local)",
        "Luoghi salvati di Google Maps (copia locale)",
        "Zapisane miejsca Google Maps (kopia lokalna)"))
    Text(message(language,
        "A Google-fiókhoz nincs élő hozzáférés. A Google Takeout »Mentett« CSV-listáját vagy a »Maps (your places)« GeoJSON-fájlt importálhatod.",
        "No live Google account access. Import a Google Takeout Saved CSV list or Maps (your places) GeoJSON.",
        "Kein Live-Zugriff auf Google. Importiere die CSV-Liste „Saved“ oder „Maps (your places)“ GeoJSON aus Google Takeout.",
        "Pas d'accès direct au compte Google. Importe un CSV « Saved » ou un GeoJSON Google Takeout.",
        "Sin acceso directo a Google. Importa el CSV de « Saved » o GeoJSON de Google Takeout.",
        "Nessun accesso diretto a Google. Importa CSV « Saved » o GeoJSON da Google Takeout.",
        "Brak bezpośredniego dostępu do Google. Zaimportuj CSV « Saved » lub GeoJSON z Google Takeout."))
    Button(
        onClick = { picker.launch(arrayOf("text/*", "application/json", "application/geo+json", "application/octet-stream")) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(message(language, "Mentett helyek importálása", "Import saved places",
            "Gespeicherte Orte importieren", "Importer des lieux enregistrés",
            "Importar lugares guardados", "Importa luoghi salvati",
            "Importuj zapisane miejsca"))
    }
    if (status.isNotBlank()) Text(status)
    Text(message(language, "Importált helyek: ", "Imported places: ",
        "Importierte Orte: ", "Lieux importés : ", "Lugares importados: ",
        "Luoghi importati: ", "Zaimportowane miejsca: ") + places.size)
    if (places.isNotEmpty()) {
        // Keep screen scroll ownership in JarvisToolLanding; avoid nested unbounded lazy scrolling.
        places.take(60).forEach { place ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(place.name, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    status = JarvisNavigationText.result(
                        language, VoiceNavigation.openDetailed(context, place.destination))
                }) {
                    Text(message(language, "Navigálj", "Navigate", "Navigieren",
                        "Naviguer", "Navegar", "Naviga", "Nawiguj"))
                }
            }
        }
        if (places.size > 60) Text(message(language,
            "Az első 60 hely látható.", "Showing the first 60 places.",
            "Die ersten 60 Orte werden angezeigt.", "Affichage des 60 premiers lieux.",
            "Se muestran los primeros 60 lugares.", "Mostrati i primi 60 luoghi.",
            "Wyświetlono pierwsze 60 miejsc."))
    }
}

private fun message(language: JarvisLanguage, hu: String, en: String,
    de: String, fr: String, es: String, it: String, pl: String): String =
    when (language) {
        JarvisLanguage.HUNGARIAN -> hu
        JarvisLanguage.ENGLISH -> en
        JarvisLanguage.GERMAN -> de
        JarvisLanguage.FRENCH -> fr
        JarvisLanguage.SPANISH -> es
        JarvisLanguage.ITALIAN -> it
        JarvisLanguage.POLISH -> pl
    }
