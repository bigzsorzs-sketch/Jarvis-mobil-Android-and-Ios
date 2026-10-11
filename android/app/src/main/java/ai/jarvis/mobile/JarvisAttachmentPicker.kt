package ai.jarvis.mobile

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Android-owned Camera, Photo Picker, and scoped document picker. No broad storage permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JarvisAttachmentPicker(
    context: Context, dark: Boolean, onStatus: (String) -> Unit
) {
    val items = remember { mutableStateListOf<JarvisAttachment>() }
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var inspectedVideo by remember { mutableStateOf<JarvisVideoInspection?>(null) }
    var previewTitle by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun stage(uris: List<Uri>, image: Boolean, sourceFile: File? = null) {
        if (busy || uris.isEmpty()) return
        busy = true
        scope.launch {
            var accepted = 0
            var errors = ""
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) {
                    JarvisAttachments.importSelected(context, uri, items.toList(), image)
                }
                result.onSuccess { items.add(it); accepted++ }
                    .onFailure { errors = it.message ?: "Nem sikerült beolvasni a csatolmányt." }
            }
            sourceFile?.delete()
            busy = false
            onStatus(
                (if (accepted > 0) "Helyben előkészítve: " + accepted + " csatolmány. " +
                    "AI-feltöltés még nincs bekötve." else "") +
                    (if (errors.isNotBlank()) " " + errors else "")
            )
        }
    }

    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(JarvisAttachments.MAX_IMAGES)
    ) { selected -> stage(selected, true) }

    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { selected -> stage(selected, false) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val target = pendingCameraFile
        pendingCameraFile = null
        if (saved && target != null) {
            val uri = FileProvider.getUriForFile(context,
                context.packageName + ".files", target)
            stage(listOf(uri), true, target)
        } else target?.delete()
    }

    IconButton(onClick = { open = true }) {
        Text("+", fontSize = 29.sp,
            color = JarvisPalette.mainText)
    }
    if (inspectedVideo != null) {
        val detail = inspectedVideo!!
        AlertDialog(
            onDismissRequest = { inspectedVideo = null },
            title = { Text("Videó áttekintése: " + previewTitle) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 540.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Hossz: " + JarvisVideoSampling.formatDuration(detail.durationMs) +
                            " · Felbontás: " + detail.width + "×" + detail.height
                    )
                    Text(
                        if (detail.hasAudioTrack) "Hangsáv van a videóban."
                        else "Nem található hangsáv."
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Helyben kinyert képkockák: " + detail.frames.size + "/" +
                            JarvisVideoSampling.MAX_FRAMES
                    )
                    Text(
                        "Ez még nem AI-alapú tartalomelemzés. Beszédátirat és " +
                            "a videó értelmezése az AI-agy bekötése után lesz elérhető.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    detail.frames.forEach { frame ->
                        val bitmap = remember(frame.thumbnail.absolutePath) {
                            BitmapFactory.decodeFile(frame.thumbnail.absolutePath)
                                ?.asImageBitmap()
                        }
                        Text(JarvisVideoSampling.formatDuration(frame.timestampMs),
                            style = MaterialTheme.typography.labelMedium)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = "Videóképkocka " +
                                    JarvisVideoSampling.formatDuration(frame.timestampMs),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedVideo = null }) {
                    Text("Bezárás")
                }
            }
        )
    }
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { if (!busy) open = false },
            containerColor = JarvisPalette.panel
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text("Csatolás Jarvishoz", style = MaterialTheme.typography.titleLarge,
                    color = JarvisPalette.mainText)
                Spacer(Modifier.height(12.dp))
                Text("PDF, Word, Excel, PowerPoint, TXT, CSV, ZIP, RAR, 7Z, " +
                    "képek, hangok, videók és más fájlok. " +
                    "Legfeljebb 20 kép és összesen 1 GB; egyelőre helyi előkészítés.",
                    color = JarvisPalette.subtleText)
                Spacer(Modifier.height(14.dp))
                TextButton(onClick = {
                    if (busy) return@TextButton
                    try {
                        val folder = File(context.cacheDir, "jarvis_attachments").apply { mkdirs() }
                        val temp = File.createTempFile("camera_", ".jpg", folder)
                        pendingCameraFile = temp
                        val uri = FileProvider.getUriForFile(context,
                            context.packageName + ".files", temp)
                        open = false
                        camera.launch(uri)
                    } catch (_: Exception) { onStatus("Nem sikerült megnyitni a kamerát.") }
                }, enabled = !busy) {
                    Text("📷  Kamera", color = JarvisPalette.mainText)
                }
                TextButton(onClick = {
                    open = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, enabled = !busy && JarvisAttachments.imageCount(items) < JarvisAttachments.MAX_IMAGES) {
                    Text("▧  Fényképek", color = JarvisPalette.mainText)
                }
                TextButton(onClick = {
                    open = false
                    // Unrestricted scoped system picker: common and uncommon
                    // formats remain selectable; no unrestricted storage access.
                    files.launch(arrayOf("*/*"))
                }, enabled = !busy) {
                    Text("▤  Fájlok", color = JarvisPalette.mainText)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                val mb = JarvisAttachments.total(items).toDouble() / 1_048_576.0
                Text(
                    JarvisAttachments.imageCount(items).toString() + "/20 kép · " +
                        String.format(Locale.UK, "%.1f", mb) + " / 1024 MB",
                    color = JarvisPalette.accent
                )
                items.forEach { attachment ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(attachment.displayName.take(36),
                                color = JarvisPalette.mainText)
                            Text(
                                JarvisAttachments.categoryFor(attachment.mimeType, attachment.isImage) +
                                    " · " + String.format(Locale.UK, "%.1f MB",
                                        attachment.sizeBytes / 1_048_576.0),
                                color = JarvisPalette.subtleText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (attachment.mimeType.startsWith("video/")) {
                            TextButton(onClick = {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            JarvisVideoInspector.inspect(context, attachment)
                                        }
                                        busy = false
                                        result.onSuccess {
                                            previewTitle = attachment.displayName
                                            inspectedVideo = it
                                        }.onFailure {
                                            onStatus("A videó nem dolgozható fel ezen a készüléken: " +
                                                (it.message ?: "ismeretlen hiba"))
                                        }
                                    }
                                }
                            }, enabled = !busy) {
                                Text("Képkockák")
                            }
                        }
                        TextButton(onClick = {
                            JarvisVideoInspector.removeCache(context, attachment.id)
                            JarvisAttachments.delete(attachment)
                            items.remove(attachment)
                        }) { Text("Eltávolítás") }
                    }
                }
                Text("A csatolmányok helyben maradnak; a felhőalapú AI-feldolgozás " +
                    "még nincs összekapcsolva.",
                    color = JarvisPalette.subtleText,
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(26.dp))
            }
        }
    }
}
