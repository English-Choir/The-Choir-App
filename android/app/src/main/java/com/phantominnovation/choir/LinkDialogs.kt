package com.phantominnovation.choir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ManageLinksDialog(registration: Registration, canEdit: Boolean, onDismiss: () -> Unit, onOpen: (DriveLink) -> Unit, onEdit: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        icon = { Symbol(Mark.LINK) }, title = { Text("Drive files") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(registration.name, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(bottom = 12.dp))
                registration.links.forEachIndexed { index, link ->
                    HorizontalDivider(color = Border)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onOpen(link) }, modifier = Modifier.weight(1f)) {
                            Text(link.title, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Symbol(Mark.EXTERNAL, modifier = Modifier.size(16.dp))
                        }
                        if (canEdit) IconButton(onClick = { onEdit(index) }, modifier = Modifier.semantics { contentDescription = "Edit ${link.title}" }) { Symbol(Mark.EDIT, Muted, Modifier.size(18.dp)) }
                    }
                }
                if (canEdit) TextButton(onClick = { onEdit(-1) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Symbol(Mark.PLUS, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add another link")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun EditLinkDialog(registration: Registration, index: Int, onDismiss: () -> Unit, onSave: suspend (List<DriveLink>) -> Boolean) {
    val existing = registration.links.getOrNull(index)
    var title by rememberSaveable(registration.id, index) { mutableStateOf(existing?.title ?: registration.name) }
    var url by rememberSaveable(registration.id, index) { mutableStateOf(existing?.url ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save(links: List<DriveLink>) {
        saving = true
        scope.launch {
            if (!onSave(links)) { error = "Couldn't save the link. Please try again."; saving = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, containerColor = Panel,
        icon = { Symbol(if (existing == null) Mark.PLUS else Mark.EDIT) },
        title = { Text(if (existing == null) "Add Drive link" else "Edit Drive link") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(registration.name, color = Ink, style = MaterialTheme.typography.titleMedium)
                Text("Save a Google Drive or Google Docs link for this registration.", modifier = Modifier.padding(top = 8.dp, bottom = 16.dp), color = Muted)
                OutlinedTextField(title, { title = it; error = null }, label = { Text("File name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("link_title"), enabled = !saving)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(url, { url = it; error = null }, label = { Text("Drive link") }, placeholder = { Text("https://drive.google.com/…") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), minLines = 2, maxLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("link_url"), enabled = !saving, isError = error != null)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Text("Changes are shared in the app. Your original files stay in Drive.", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                if (existing != null) {
                    TextButton(onClick = { save(registration.links.filterIndexed { i, _ -> i != index }) }, enabled = !saving, modifier = Modifier.padding(top = 8.dp)) { Text("Remove this link", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val valid = validDriveUrl(url)
                when {
                    title.isBlank() -> error = "Enter a file name."
                    valid == null -> error = "Use an https://drive.google.com or https://docs.google.com link."
                    registration.links.withIndex().any { (i, link) -> i != index && link.url == valid } -> error = "This link is already attached."
                    else -> {
                        val next = registration.links.toMutableList()
                        val newLink = DriveLink(title.trim(), valid)
                        if (index in next.indices) next[index] = newLink else next.add(newLink)
                        save(next)
                    }
                }
            }, enabled = !saving, modifier = Modifier.testTag("save_link")) { Text(if (saving) "Saving…" else "Save link") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
