package com.phantominnovation.choir

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun PdfCollectionDialog(title: String, sources: List<PdfSource>, onDismiss: () -> Unit, onOpen: (DriveLink) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var running by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(PdfProgress(0, sources.size, "Ready to download")) }
    var result by remember { mutableStateOf<PdfCollectionResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun close() { job?.cancel(); onDismiss() }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Panel, shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 680.dp).testTag("pdf_collection_dialog")) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Collect Drive chords", style = MaterialTheme.typography.titleLarge)
                Text(title, color = Blue)
                Text("${sources.size} distinct files, in hymn order. Repeated links appear once. Google Docs become PDFs; merging happens on this device.", color = Muted)
                Text("Up to 60 files, 20 MB per file, 50 MB total and 300 pages. Keep this window open while preparing.", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (sources.size > PdfCollection.MAX_FILES) Text("This collection has too many files. Use a smaller hymn list.", color = MaterialTheme.colorScheme.error)
                if (running) {
                    LinearProgressIndicator(progress = { progress.done.toFloat() / maxOf(1, progress.total) }, modifier = Modifier.fillMaxWidth())
                    Text("${progress.done} of ${progress.total} files · ${progress.title}", modifier = Modifier.testTag("pdf_progress"))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                result?.let { completed ->
                    Text("${completed.included} of ${sources.size} files included · ${completed.pages} pages${if (completed.failures.isNotEmpty()) " · INCOMPLETE" else ""}",
                        color = if (completed.failures.isNotEmpty()) MaterialTheme.colorScheme.error else Blue, modifier = Modifier.testTag("pdf_result"))
                    completed.failures.forEach { failure ->
                        Text("${failure.source.link.title} (hymns ${failure.source.positions.joinToString()})", style = MaterialTheme.typography.titleSmall)
                        Text(failure.message, color = Muted)
                        TextButton(onClick = { onOpen(failure.source.link) }) { Text("Open original Drive file") }
                    }
                    if (completed.file == null) Text("No PDF was created. Check the files above and try again.", color = Muted)
                    completed.file?.let { file ->
                        Button(onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.pdfs", file, pdfFilename(title))
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    clipData = ClipData.newRawUri("Choir chord PDF", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share chord PDF"))
                            } catch (e: Exception) { error = "Could not open sharing. Try another PDF app." }
                        }, modifier = Modifier.testTag("share_pdf")) {
                            Text(if (completed.failures.isEmpty()) "Share combined PDF" else "Share partial PDF")
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { close() }) { Text(if (running) "Cancel" else "Close") }
                    Button(enabled = !running && sources.isNotEmpty() && sources.size <= PdfCollection.MAX_FILES,
                        onClick = {
                            running = true; result = null; error = null
                            job = scope.launch {
                                try { result = PdfCollection.create(context, sources, title, { progress = it }) }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message ?: "The PDF could not be created. Try fewer files." }
                                finally { running = false }
                            }
                        }, modifier = Modifier.testTag("start_pdf")) { Text(if (result == null) "Create PDF" else "Create again") }
                }
            }
        }
    }
}
