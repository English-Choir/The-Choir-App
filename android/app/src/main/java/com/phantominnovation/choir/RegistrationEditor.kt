package com.phantominnovation.choir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val InputSaver = listSaver<RegistrationInput, String>(
    save = { listOf(it.name, it.sectionId, it.style, it.tempo, it.transpose) + it.links.flatMap { link -> listOf(link.title, link.url) } },
    restore = { RegistrationInput(it[0], it[1], it[2], it[3], it[4], it.drop(5).chunked(2).map { pair -> DriveLink(pair[0], pair[1]) }) },
)

@Composable
fun RegistrationEditor(
    catalog: Catalog,
    existing: Registration?,
    initialSectionId: String,
    onDismiss: () -> Unit,
    onSave: suspend (RegistrationInput) -> WriteResult,
    onDelete: () -> Unit,
) {
    val initial = remember(existing?.id) { existing?.let(RegistrationInput::from) ?: RegistrationInput(sectionId = initialSectionId) }
    var draft by rememberSaveable(existing?.id, stateSaver = InputSaver) { mutableStateOf(initial) }
    var expanded by remember { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun dismiss() { if (!saving) { if (draft != initial) discard = true else onDismiss() } }
    fun change(next: RegistrationInput) { draft = next; error = null }
    fun linkAt(index: Int, link: DriveLink) { change(draft.copy(links = draft.links.mapIndexed { i, old -> if (i == index) link else old })) }

    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Panel, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Border),
            modifier = Modifier.padding(12.dp).widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(0.95f).imePadding().testTag("registration_editor")) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                Text(if (existing == null) "HOST · ADD" else "HOST · EDIT", style = MaterialTheme.typography.labelSmall, color = Blue)
                        Text(if (existing == null) "Add registration" else "Edit registration", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                    }
                    IconButton(onClick = ::dismiss, enabled = !saving, modifier = Modifier.semantics { contentDescription = "Close editor" }) { Symbol(Mark.CLOSE, Muted) }
                }
                HorizontalDivider(color = Border)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp).testTag("editor_fields"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(draft.name, { change(draft.copy(name = it)) }, label = { Text("Registration name") }, singleLine = true,
                        enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("registration_name"))
                    Column {
                        Text("Section", style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
                        Box {
                            OutlinedButton(onClick = { expanded = true }, enabled = !saving, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Muted), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("registration_section")) {
                                Text(catalog.sections.firstOrNull { it.id == draft.sectionId }?.title ?: "Choose a section", color = Ink, modifier = Modifier.weight(1f))
                                Symbol(Mark.CHEVRON, Muted, Modifier.size(17.dp))
                            }
                            DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                                catalog.sections.forEach { section ->
                                    DropdownMenuItem(text = { Text(section.title) }, onClick = { change(draft.copy(sectionId = section.id)); expanded = false }, modifier = Modifier.testTag("choose_${section.id}"))
                                }
                            }
                        }
                    }
                    OutlinedTextField(draft.style, { change(draft.copy(style = it)) }, label = { Text("Style") }, singleLine = true,
                        enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("registration_style"))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(draft.tempo, { change(draft.copy(tempo = it)) }, label = { Text("Tempo (BPM)") }, supportingText = { Text("5–500") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !saving, modifier = Modifier.weight(1f).testTag("registration_tempo"))
                        OutlinedTextField(draft.transpose, { change(draft.copy(transpose = it)) }, label = { Text("Transpose") }, supportingText = { Text("−12 to +12") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), enabled = !saving, modifier = Modifier.weight(1f).testTag("registration_transpose"))
                    }
                    HorizontalDivider(color = Border)
                    Text("Drive files", style = MaterialTheme.typography.titleMedium)
                    if (draft.links.isEmpty()) Text("Optional. You can add a file now or later.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    draft.links.forEachIndexed { index, link ->
                        Surface(color = BlueSurface, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("File ${index + 1}", color = Blue, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { change(draft.copy(links = draft.links.filterIndexed { i, _ -> i != index })) }, enabled = !saving,
                                        modifier = Modifier.semantics { contentDescription = "Remove file ${index + 1} from this registration" }) { Symbol(Mark.CLOSE, Muted, Modifier.size(18.dp)) }
                                }
                                OutlinedTextField(link.title, { linkAt(index, link.copy(title = it)) }, label = { Text("File name (optional)") }, singleLine = true,
                                    enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("registration_link_title_$index"))
                                OutlinedTextField(link.url, { linkAt(index, link.copy(url = it)) }, label = { Text("Google Drive or Docs link") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), maxLines = 4,
                                    enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("registration_link_url_$index"))
                            }
                        }
                    }
                    OutlinedButton(onClick = { change(draft.copy(links = draft.links + DriveLink("", ""))) }, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("editor_add_link")) {
                        Symbol(Mark.PLUS, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add Drive file")
                    }
                    if (existing != null) {
                        HorizontalDivider(color = Border)
                        TextButton(onClick = onDelete, enabled = !saving, modifier = Modifier.fillMaxWidth().testTag("delete_registration")) {
                            Symbol(Mark.DELETE, MaterialTheme.colorScheme.error, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text("Delete registration", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider(color = Border)
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp).testTag("editor_error")) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = ::dismiss, enabled = !saving, modifier = Modifier.weight(1f).testTag("cancel_registration")) { Text("Cancel") }
                        Button(onClick = {
                            val validation = runCatching { draft.toRegistration(existing?.id ?: "new", catalog) }
                            if (validation.isFailure) error = validation.exceptionOrNull()?.message else {
                                saving = true
                                scope.launch {
                                    val result = onSave(draft)
                                    if (!result.saved) { error = result.error; saving = false }
                                }
                            }
                        }, enabled = !saving, modifier = Modifier.weight(1f).testTag("save_registration")) { Text(if (saving) "Saving…" else "Save") }
                    }
                }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard changes?") },
        text = { Text("Your changes to this registration haven't been saved.") }, containerColor = Panel,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
}

@Composable
fun DeleteRegistrationDialog(registration: Registration, onDismiss: () -> Unit, onDelete: suspend () -> WriteResult) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, containerColor = Panel,
        icon = { Symbol(Mark.DELETE, MaterialTheme.colorScheme.error) }, title = { Text("Delete registration?") },
        text = { Column {
            Text("Remove “${registration.name}” from the app? This doesn't delete any Drive files or change your keyboard backup.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        } },
        confirmButton = { TextButton(onClick = { saving = true; scope.launch { val result = onDelete(); if (!result.saved) { error = result.error; saving = false } } }, enabled = !saving,
            modifier = Modifier.testTag("confirm_delete")) { Text(if (saving) "Deleting…" else "Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag("cancel_delete")) { Text("Cancel") } })
}

