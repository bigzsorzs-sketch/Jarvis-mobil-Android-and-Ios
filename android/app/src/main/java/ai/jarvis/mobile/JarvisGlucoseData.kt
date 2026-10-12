package ai.jarvis.mobile

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Glucose records are stored in encrypted SecurePrefs by MainActivity.
 * Legacy records without source remain manual, never silently reclassified as CGM.
 * Import is offline, with no Abbott password, BLE pairing or cloud impersonation.
 */
internal object JarvisGlucoseData {
    private const val DAY_MS = 86_400_000L
    private val formats = listOf(
        "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss",
        "dd-MM-yyyy HH:mm", "dd-MM-yyyy HH:mm:ss",
        "d/M/yyyy HH:mm", "dd/MM/yyyy HH:mm:ss",
        "d/M/yy HH:mm", "dd/MM/yy HH:mm",
        "M/d/yyyy h:mm a", "M/d/yy h:mm a", "M/d/yyyy h:mm:ss a",
        "dd-MM-yyyy h:mm a", "d/M/yyyy h:mm a",
        "yyyy/MM/dd HH:mm"
    ).map { DateTimeFormatter.ofPattern(it, Locale.UK) }
    private val stored = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.UK)

    data class Point(val timestamp: Long, val mmol: Double, val source: String) {
        val historic: Boolean get() = source == "libreview-historic"
    }
    data class ImportResult(val readings: List<Reading>, val accepted: Int, val skipped: Int)
    data class Window(val samples: List<Point>, val sensor: List<Point>,
        val expected: Int, val coverage: Double, val sufficient: Boolean) {
        val average: Double? get() = samples.map { it.mmol }.takeIf { it.isNotEmpty() }?.average()
        val sensorAverage: Double? get() = sensor.map { it.mmol }.takeIf { it.isNotEmpty() }?.average()
        fun sensorPercent(min: Double, max: Double): Int? =
            if (!sufficient) null else (sensor.count { it.mmol >= min && it.mmol <= max } *
                100.0 / sensor.size).roundToInt()
    }

    fun parseDate(text: String): Long? {
        val str = text.trim().removePrefix("\uFEFF")
        for (fmt in formats) {
            val time = runCatching { LocalDateTime.parse(str, fmt) }.getOrNull()
            if (time != null) return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return null
    }
    fun normalizeStamp(timestamp: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(stored)

    fun mmolInput(raw: String, mgDl: Boolean): Double? {
        val v = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val mmol = if (mgDl) v / 18.0182 else v
        return mmol.takeIf { it.isFinite() && it >= 1.0 && it <= 35.0 }
    }
    fun shownValue(mmol: Double, mgDl: Boolean): String =
        if (mgDl) (mmol * 18.0182).roundToInt().toString()
        else String.format(Locale.UK, "%.1f", mmol)

    fun points(readings: List<Reading>): List<Point> =
        readings.mapNotNull { r ->
            val value = r.value.replace(',', '.').toDoubleOrNull()
            val timestamp = parseDate(r.stamp)
            if (value == null || timestamp == null || !value.isFinite() ||
                value !in 1.0..35.0) null
            else Point(timestamp, value, r.source)
        }.sortedBy { it.timestamp }

    fun window(readings: List<Reading>, days: Int, now: Long = System.currentTimeMillis()): Window {
        val cutoff = now - days * DAY_MS
        val selected = points(readings).filter { it.timestamp in cutoff..now }
        // Every 15 minutes is the standard LibreView historic sample interval.
        // Never calculate continuous metrics from manual or scan-only observations.
        val sensor = selected.filter { it.historic }
            .distinctBy { it.timestamp }
        val expected = days * 24 * 4
        val coverage = (sensor.size.toDouble() / expected).coerceIn(0.0, 1.0)
        val maxGap = sensor.zipWithNext().maxOfOrNull { (a, b) -> b.timestamp - a.timestamp } ?: Long.MAX_VALUE
        return Window(selected, sensor, expected, coverage,
            days >= 1 && coverage >= 0.70 && maxGap <= 6 * 60 * 60 * 1000L)
    }

    /** GMI is NOT a laboratory HbA1c and is never available from manual readings. */
    fun estimatedGmi(readings: List<Reading>, now: Long = System.currentTimeMillis()): Double? {
        val last14 = window(readings, 14, now)
        if (!last14.sufficient) return null
        val meanMgDl = (last14.sensorAverage ?: return null) * 18.0182
        return 3.31 + (0.02392 * meanMgDl)
    }

    /** Historic-sample low runs are illustrative, NOT a medical alarm. */
    fun lowRuns(sensor: List<Point>, low: Double): Int {
        var events = 0
        var lastLowTime: Long? = null
        for (point in sensor.sortedBy { it.timestamp }) {
            if (point.mmol < low) {
                if (lastLowTime == null || point.timestamp - lastLowTime > 30 * 60_000L)
                    events++
                lastLowTime = point.timestamp
            } else lastLowTime = null
        }
        return events
    }

    /** RFC-4180 compatible quoted cells, including escaped double quotes. */
    internal fun csvCells(line: String): List<String> {
        val out = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            when {
                line[i] == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"'); i++
                }
                line[i] == '"' -> quoted = !quoted
                line[i] == ',' && !quoted -> { out.add(cell.toString()); cell.clear() }
                else -> cell.append(line[i])
            }
            i++
        }
        out.add(cell.toString())
        return out
    }

    /**
     * Import only clearly labeled LibreView CSV columns. Do not infer values
     * from a random file or treat scan values as continuous historic CGM samples.
     * File processing stays on device; errors do not erase existing readings.
     */
    fun importLibreView(csv: String, existing: List<Reading>): ImportResult {
        require(csv.length <= 5_000_000) { "CSV file is too large (5 MB maximum)." }
        val lines = csv.lineSequence().take(40_050).toList()
        val headings = lines.take(40).mapIndexedNotNull { index, line ->
            val cells = csvCells(line.removePrefix("\uFEFF")).map { it.trim().lowercase(Locale.ROOT) }
            val timeIndex = cells.indexOfFirst { it == "device timestamp" || it == "timestamp" }
            val historicIndex = cells.indexOfFirst {
                it.startsWith("historic glucose ") && (it.contains("mmol/l") || it.contains("mg/dl"))
            }
            val scanIndex = cells.indexOfFirst {
                it.startsWith("scan glucose ") && (it.contains("mmol/l") || it.contains("mg/dl"))
            }
            if (timeIndex < 0 || (historicIndex < 0 && scanIndex < 0)) null
            else listOf(index, timeIndex, historicIndex, scanIndex) to cells
        }
        val header = headings.firstOrNull()
            ?: throw IllegalArgumentException("Missing LibreView Device Timestamp and glucose columns.")
        val indices = header.first
        val timestampIndex = indices[1]; val historicIndex = indices[2]; val scanIndex = indices[3]
        val headingsList = header.second
        val known = existing.mapNotNull { reading ->
            parseDate(reading.stamp)?.let { stamp ->
                reading.value.toDoubleOrNull()?.let { value ->
                    "${stamp}|${(value * 100).roundToInt()}"
                }
            }
        }.toMutableSet()
        val added = ArrayList<Reading>()
        var skipped = 0
        for (line in lines.drop(indices[0] + 1)) {
            if (added.size >= 20_000) break
            if (line.isBlank()) continue
            val cells = csvCells(line)
            val stamp = cells.getOrNull(timestampIndex)?.let(::parseDate)
            if (stamp == null || stamp > System.currentTimeMillis() + DAY_MS) {
                skipped++; continue
            }
            val hist = cells.getOrNull(historicIndex)?.trim()
            val scan = cells.getOrNull(scanIndex)?.trim()
            val isHistoric = !hist.isNullOrBlank()
            val rawValue = if (isHistoric) hist else scan
            if (rawValue.isNullOrBlank()) { skipped++; continue }
            val unitHeader = headingsList[if (isHistoric) historicIndex else scanIndex]
            val mgDl = unitHeader.contains("mg/dl")
            val value = mmolInput(rawValue, mgDl)
            if (value == null) { skipped++; continue }
            val id = "${stamp}|${(value * 100).roundToInt()}"
            if (!known.add(id)) { skipped++; continue }
            added.add(Reading(
                String.format(Locale.UK, "%.3f", value),
                normalizeStamp(stamp),
                if (isHistoric) "libreview-historic" else "libreview-scan"
            ))
        }
        return ImportResult(added, added.size, skipped)
    }
}
