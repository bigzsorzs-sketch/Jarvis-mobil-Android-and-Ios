package ai.jarvis.mobile

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.text.SimpleDateFormat
import kotlin.math.roundToInt

private val glucoseBlue = Color(0xFF2789EB)
private val glucoseMuted = Color(0xFF94A3B8)
private val glucosePanel = Color(0xFF192A44)

@Composable
private fun GText(id: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    weight: FontWeight? = null, size: Int = 14) {
    Text(JarvisGlucoseStrings.text(id, LocalJarvisLanguage.current),
        modifier = modifier, color = color, fontWeight = weight, fontSize = size.sp)
}

@Composable
private fun GlucoseCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = JarvisPalette.panel) {
        Column(Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

@Composable
internal fun JarvisGlucoseDashboard(
    context: Context,
    readings: MutableList<Reading>,
    onPersist: () -> Unit
) {
    val prefs = remember { context.getSharedPreferences("jarvis_glucose_ui_v1", Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf("overview") }
    var report by remember { mutableStateOf("daily") }
    var days by remember { mutableIntStateOf(7) }
    var mgDl by remember { mutableStateOf(prefs.getBoolean("mgdl", false)) }
    var targetLow by remember { mutableStateOf(prefs.getString("target_low", "3.9") ?: "3.9") }
    var targetHigh by remember { mutableStateOf(prefs.getString("target_high", "10.0") ?: "10.0") }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var importStatus by remember { mutableStateOf("") }
    var healthReading by remember { mutableStateOf<JarvisGlucoseData.Point?>(null) }
    var loadingHealth by remember { mutableStateOf(false) }
    var checkedHealth by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lang = LocalJarvisLanguage.current

    fun checkHealth() {
        scope.launch {
            loadingHealth = true
            healthReading = withContext(Dispatchers.IO) { JarvisHealthGlucose.latest(context) }
            checkedHealth = true
            loadingHealth = false
        }
    }
    LaunchedEffect(Unit) { checkHealth() }

    val healthPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed -> if (allowed) checkHealth() }
    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            // Capped input; malformed or enormous exports cannot fill memory.
                            // Compatible with Android 8+, capped at 5 MB.
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                require(output.size() + n <= 5_000_000)
                                output.write(buffer, 0, n)
                            }
                            val csv = output.toByteArray().toString(Charsets.UTF_8)
                            JarvisGlucoseData.importLibreView(csv, readings.toList())
                        } ?: throw IllegalArgumentException("Cannot read file")
                    }
                }
                data.onSuccess { result ->
                    if (result.accepted > 0) {
                        readings.addAll(0, result.readings)
                        onPersist()
                        importStatus = JarvisGlucoseStrings.text("imported", lang) + result.accepted
                    } else importStatus = JarvisGlucoseStrings.text("importNone", lang)
                }.onFailure {
                    importStatus = JarvisGlucoseStrings.text("importError", lang)
                }
            }
        }
    }

    val now = System.currentTimeMillis()
    val points = remember(readings.size, readings.firstOrNull(), readings.lastOrNull()) {
        JarvisGlucoseData.points(readings.toList())
    }
    val w = remember(readings.size, days, now / 60_000L) {
        JarvisGlucoseData.window(readings.toList(), days, now)
    }
    val low = targetLow.replace(',', '.').toDoubleOrNull()?.coerceIn(2.0, 12.0) ?: 3.9
    val high = targetHigh.replace(',', '.').toDoubleOrNull()?.coerceIn(low + 0.1, 20.0) ?: 10.0
    val mostRecent = (points.lastOrNull()?.let { p -> p } to healthReading).let { (saved, health) ->
        listOfNotNull(saved, health).maxByOrNull { it.timestamp }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GText("title", color = JarvisPalette.mainText, weight = FontWeight.Bold, size = 24)
        GText("offline", color = glucoseMuted, size = 12)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("overview", "log", "reports", "settings", "nfc").forEach { t ->
                ControlledFilterChip(selected = tab == t, onClick = { tab = t },
                    modifier = Modifier.testTag("jarvis-glucose-tab-$t"),
                    label = { GText(t, color = JarvisPalette.mainText) }, commandLabel = JarvisGlucoseStrings.text(t, LocalJarvisLanguage.current))
            }
        }
        when (tab) {
            "overview" -> {
                GlucoseCard {
                    GText("latest", color = JarvisPalette.mainText, weight = FontWeight.Bold, size = 17)
                    if (mostRecent == null) GText("nodata", color = glucoseMuted)
                    else {
                        Text(JarvisGlucoseData.shownValue(mostRecent.mmol, mgDl) +
                            if (mgDl) " mg/dL" else " mmol/L",
                            fontSize = 34.sp, fontWeight = FontWeight.Bold, color = glucoseBlue)
                        Text(JarvisGlucoseData.normalizeStamp(mostRecent.timestamp),
                            color = glucoseMuted, fontSize = 13.sp)
                        val sourceKey = when (mostRecent.source) {
                            "health-connect" -> "health"
                            "libreview-historic" -> "historic"
                            "libreview-scan" -> "scan"
                            else -> "manual"
                        }
                        GText(sourceKey, color = glucoseMuted)
                        if (now - mostRecent.timestamp > 10 * 60_000L) GText("stale", color = Color(0xFFFFBE7B))
                    }
                    GText("local", color = glucoseMuted, size = 12)
                    if (JarvisHealthGlucose.supported() && JarvisHealthGlucose.isAuthorized(context)) {
                        ControlledOutlinedButton(onClick = { checkHealth() }, enabled = !loadingHealth, commandLabel = JarvisGlucoseStrings.text("refresh", LocalJarvisLanguage.current)) {
                            GText("refresh", color = JarvisPalette.mainText)
                        }
                    }
                }
                ReadingEntry(entered, { entered = it }, mgDl, error, { error = it }) {
                    val mmol = JarvisGlucoseData.mmolInput(entered, mgDl)
                    if (mmol == null) error = JarvisGlucoseStrings.text("error", lang)
                    else {
                        readings.add(0, Reading(String.format(Locale.UK, "%.3f", mmol),
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date()), "manual"))
                        onPersist()
                        entered = ""; error = ""
                    }
                }
                GlucoseCard {
                    GText("daily", color = JarvisPalette.mainText, weight = FontWeight.Bold)
                    GlucoseLineGraph(points.takeLast(50), low, high)
                }
                ControlledOutlinedButton(onClick = { csvPicker.launch(arrayOf("text/*", "text/csv", "application/octet-stream")) }, commandLabel = JarvisGlucoseStrings.text("import", LocalJarvisLanguage.current)) {
                    GText("import", color = JarvisPalette.mainText)
                }
                if (importStatus.isNotBlank()) Text(importStatus, color = JarvisPalette.mainText)
                GText("noAlerts", color = glucoseMuted, size = 12)
            }
            "log" -> {
                ReadingEntry(entered, { entered = it }, mgDl, error, { error = it }) {
                    val mmol = JarvisGlucoseData.mmolInput(entered, mgDl)
                    if (mmol == null) error = JarvisGlucoseStrings.text("error", lang)
                    else {
                        readings.add(0, Reading(String.format(Locale.UK, "%.3f", mmol),
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date()), "manual"))
                        onPersist(); entered = ""; error = ""
                    }
                }
                if (readings.isEmpty()) GText("nodata", color = glucoseMuted)
                readings.take(100).forEach { r ->
                    GlucoseCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(JarvisGlucoseData.shownValue(
                                    r.value.toDoubleOrNull() ?: 0.0, mgDl) +
                                    if (mgDl) " mg/dL" else " mmol/L",
                                    fontWeight = FontWeight.Bold, color = JarvisPalette.mainText)
                                Text(r.stamp, color = glucoseMuted, fontSize = 12.sp)
                                GText(when (r.source) {
                                    "libreview-historic" -> "historic"
                                    "libreview-scan" -> "scan"
                                    else -> "manual"
                                }, color = glucoseMuted, size = 12)
                            }
                            ControlledTextButton(onClick = { readings.remove(r); onPersist() }, commandLabel = JarvisGlucoseStrings.text("delete", LocalJarvisLanguage.current)) {
                                GText("delete", color = JarvisPalette.accent)
                            }
                        }
                    }
                }
            }
            "reports" -> {
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 14, 30, 90).forEach { n ->
                        ControlledFilterChip(selected = days == n, onClick = { days = n },
                            label = { GText("days$n", color = JarvisPalette.mainText) }, commandLabel = JarvisGlucoseStrings.text("days$n", LocalJarvisLanguage.current))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("patterns", "tir", "low", "average", "daily", "gmi", "usage").forEach { key ->
                        ControlledFilterChip(selected = report == key, onClick = { report = key },
                            label = { GText(key, color = JarvisPalette.mainText) }, commandLabel = JarvisGlucoseStrings.text(key, LocalJarvisLanguage.current))
                    }
                }
                GlucoseCard {
                    GText(report, color = JarvisPalette.mainText, size = 18, weight = FontWeight.Bold)
                    when (report) {
                        "average" -> {
                            if (w.samples.isEmpty()) GText("nodata", color = glucoseMuted)
                            else {
                                Text(JarvisGlucoseData.shownValue(w.average ?: 0.0, mgDl) +
                                    if (mgDl) " mg/dL" else " mmol/L",
                                    color = glucoseBlue, fontSize = 30.sp)
                                GText("avgManual", color = glucoseMuted)
                            }
                        }
                        "daily", "patterns" -> {
                            if (w.samples.size < 2) GText("noSensor", color = glucoseMuted)
                            else GlucoseLineGraph(w.samples.takeLast(150), low, high)
                            if (!w.sufficient) GText("noSensorDetail", color = glucoseMuted, size = 12)
                        }
                        "tir" -> {
                            val percent = w.sensorPercent(low, high)
                            if (percent == null) {
                                GText("noSensor", color = glucoseMuted)
                                GText("noSensorDetail", color = glucoseMuted, size = 12)
                            } else {
                                Text("$percent%", fontSize = 34.sp, color = glucoseBlue)
                                GText("samplePercent", color = glucoseMuted, size = 12)
                            }
                        }
                        "low" -> if (!w.sufficient) {
                            GText("noSensor", color = glucoseMuted)
                        } else {
                            Text(JarvisGlucoseData.lowRuns(w.sensor, low).toString(),
                                fontSize = 32.sp, color = glucoseBlue)
                            GText("low", color = glucoseMuted)
                        }
                        "gmi" -> {
                            val estimate = JarvisGlucoseData.estimatedGmi(readings.toList())
                            if (estimate == null) GText("noSensor", color = glucoseMuted)
                            else Text(String.format(Locale.UK, "%.1f%%", estimate),
                                fontSize = 35.sp, color = glucoseBlue)
                            GText("gmiNote", color = glucoseMuted, size = 12)
                        }
                        "usage" -> {
                            Text("${(w.coverage * 100).roundToInt()}%",
                                fontSize = 32.sp, color = glucoseBlue)
                            Text("${w.sensor.size} / ${w.expected}",
                                color = glucoseMuted)
                            GText("usageNote", color = glucoseMuted, size = 12)
                        }
                    }
                }
            }
            "settings" -> {
                GlucoseCard {
                    GText("unit", color = JarvisPalette.mainText, weight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false, true).forEach { choice ->
                            ControlledFilterChip(selected = mgDl == choice, onClick = {
                                mgDl = choice
                                prefs.edit().putBoolean("mgdl", choice).apply()
                            }, label = { Text(if (choice) "mg/dL" else "mmol/L") }, commandLabel = if (choice) "mg/dL" else "mmol/L")
                        }
                    }
                    GText("target", color = JarvisPalette.mainText, weight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ControlledOutlinedTextField(value = targetLow, onValueChange = {
                            targetLow = it.take(6)
                            prefs.edit().putString("target_low", targetLow).apply()
                        }, modifier = Modifier.weight(1f), label = { Text("Min mmol/L") },
                            singleLine = true, commandLabel = "Min mmol/L")
                        ControlledOutlinedTextField(value = targetHigh, onValueChange = {
                            targetHigh = it.take(6)
                            prefs.edit().putString("target_high", targetHigh).apply()
                        }, modifier = Modifier.weight(1f), label = { Text("Max mmol/L") },
                            singleLine = true, commandLabel = "Max mmol/L")
                    }
                    GText("carbs", color = glucoseMuted)
                    GText("scanSounds", color = glucoseMuted)
                    GText("tts", color = glucoseMuted)
                }
                GlucoseCard {
                    GText("import", color = JarvisPalette.mainText, weight = FontWeight.Bold)
                    GText("importNote", color = glucoseMuted, size = 12)
                    ControlledButton(onClick = { csvPicker.launch(arrayOf("text/*", "text/csv", "application/octet-stream")) }, commandLabel = JarvisGlucoseStrings.text("import", LocalJarvisLanguage.current)) {
                        GText("import", color = Color.White)
                    }
                    if (importStatus.isNotEmpty()) Text(importStatus, color = JarvisPalette.mainText)
                }
                GlucoseCard {
                    GText("health", color = JarvisPalette.mainText, weight = FontWeight.Bold)
                    GText("healthHint", color = glucoseMuted, size = 12)
                    GText("linkupBridge", color = glucoseMuted, size = 12)
                    if (JarvisHealthGlucose.supported() && !JarvisHealthGlucose.isAuthorized(context)) {
                        ControlledOutlinedButton(onClick = {
                            healthPermission.launch(JarvisHealthGlucose.READ_PERMISSION)
                        }, commandLabel = JarvisGlucoseStrings.text("healthPermission", LocalJarvisLanguage.current)) { GText("healthPermission", color = JarvisPalette.mainText) }
                    } else if (JarvisHealthGlucose.supported()) {
                        ControlledOutlinedButton(onClick = { checkHealth() }, enabled = !loadingHealth,
                            commandLabel = JarvisGlucoseStrings.text("refresh", LocalJarvisLanguage.current)) {
                            GText("refresh", color = JarvisPalette.mainText)
                        }
                        if (checkedHealth && !loadingHealth) {
                            val synced = healthReading
                            if (synced == null) {
                                GText("healthNoRecords", color = glucoseMuted, size = 12)
                            } else {
                                Text(JarvisGlucoseData.shownValue(synced.mmol, mgDl) +
                                    (if (mgDl) " mg/dL" else " mmol/L") + " · " +
                                    JarvisGlucoseData.normalizeStamp(synced.timestamp),
                                    color = JarvisPalette.mainText, fontSize = 14.sp)
                                GText("healthReadingSource", color = glucoseMuted, size = 12)
                                if (System.currentTimeMillis() - synced.timestamp > 600_000L ||
                                    synced.timestamp > System.currentTimeMillis() + 60_000L) {
                                    GText("stale", color = glucoseMuted, size = 12)
                                }
                                GText("healthNotRealtime", color = glucoseMuted, size = 12)
                            }
                        }
                    }
                }
                GText("noAlerts", color = glucoseMuted, size = 12)
            }
            "nfc" -> {
                GlucoseCard {
                    GText("nfc", color = JarvisPalette.mainText, size = 20, weight = FontWeight.Bold)
                    val statusKey = if (!JarvisLibreNfc.hasNfc(context)) "nfcAbsent"
                        else if (JarvisLibreNfc.isEnabled(context)) "nfcReady" else "nfcOff"
                    GText(statusKey, color = JarvisPalette.mainText)
                    if (JarvisLibreNfc.hasNfc(context)) {
                        ControlledOutlinedButton(onClick = { JarvisLibreNfc.openNfcSettings(context) }, commandLabel = JarvisGlucoseStrings.text("nfcSettings", LocalJarvisLanguage.current)) {
                            GText("nfcSettings", color = JarvisPalette.mainText)
                        }
                    }
                    ControlledButton(
                        onClick = { JarvisLibreNfc.openOfficialApp(context) },
                        modifier = Modifier.testTag("jarvis-glucose-open-librelink")
                    , commandLabel = JarvisGlucoseStrings.text("libreLink", LocalJarvisLanguage.current)) {
                        GText("libreLink", color = Color.White)
                    }
                    GText("nfcGuide", color = glucoseMuted, size = 13)
                    GText("noAlerts", color = glucoseMuted, size = 12)
                }
            }
        }
    }
}

@Composable
private fun ReadingEntry(input: String, onInput: (String) -> Unit, mgDl: Boolean,
    error: String, onError: (String) -> Unit, onSave: () -> Unit) {
    GlucoseCard {
        GText("new", color = JarvisPalette.mainText, weight = FontWeight.Bold)
        ControlledOutlinedTextField(input, onValueChange = { onInput(it.take(12)) },
            label = { GText("value") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = { Text(if (mgDl) "mg/dL" else "mmol/L", fontSize = 12.sp) },
            singleLine = true, commandLabel = JarvisGlucoseStrings.text("value", LocalJarvisLanguage.current))
        ControlledButton(onClick = onSave, commandLabel = JarvisGlucoseStrings.text("save", LocalJarvisLanguage.current)) { GText("save", color = Color.White) }
        if (error.isNotEmpty()) Text(error, color = Color(0xFFFFB8B8))
    }
}

@Composable
private fun GlucoseLineGraph(
    samples: List<JarvisGlucoseData.Point>,
    targetLow: Double,
    targetHigh: Double
) {
    if (samples.size < 2) {
        GText("noSensor", color = glucoseMuted)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val ordered = samples.sortedBy { it.timestamp }
        val first = ordered.first().timestamp.toDouble()
        val rangeTime = (ordered.last().timestamp - ordered.first().timestamp).coerceAtLeast(1L)
        val min = minOf(targetLow - 1.0, ordered.minOf { it.mmol } - 0.3)
        val max = maxOf(targetHigh + 1.0, ordered.maxOf { it.mmol } + 0.3)
        fun coordinate(item: JarvisGlucoseData.Point): Offset = Offset(
            (size.width * ((item.timestamp - first) / rangeTime)).toFloat(),
            (size.height * (1.0 - ((item.mmol - min) / (max - min)))).toFloat()
        )
        val points = ordered.map(::coordinate)
        for (i in 0 until points.lastIndex) {
            // Never bridge missing historic sensor coverage with a deceptive line.
            if (ordered[i + 1].timestamp - ordered[i].timestamp <= 30 * 60_000L ||
                !ordered[i].historic) drawLine(glucoseBlue, points[i], points[i + 1],
                    strokeWidth = 2.dp.toPx())
        }
        points.forEach { drawCircle(glucoseBlue, 2.dp.toPx(), it) }
    }
}
