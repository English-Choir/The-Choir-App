package com.phantominnovation.choir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AddSectionDialog(catalog: Catalog, onDismiss: () -> Unit, onSave: suspend (String) -> WriteResult) {
    var title by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, containerColor = Panel,
        modifier = Modifier.testTag("section_editor"), title = { Text("Add section") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create a section under Other registrations. It will be available on every device.", color = Muted)
            OutlinedTextField(title, { title = it; error = null }, enabled = !saving, singleLine = true,
                label = { Text("Section name") }, modifier = Modifier.fillMaxWidth().testTag("section_name"))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("section_error")) }
        } },
        confirmButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("save_section"), onClick = {
            val checked = runCatching { newOtherSection(title, catalog.sections) }
            if (checked.isFailure) error = checked.exceptionOrNull()?.message else {
                saving = true; scope.launch { val result = onSave(title); saving = false; error = result.error }
            }
        }) { Text(if (saving) "Saving…" else "Add section") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun ManageSectionDialog(section: ChoirSection, catalog: Catalog, onDismiss: () -> Unit,
    onSave: suspend (String, String?) -> WriteResult) {
    var title by rememberSaveable(section.id) { mutableStateOf(section.title) }
    var removing by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, containerColor = Panel,
        modifier = Modifier.testTag("manage_section_dialog"),
        title = { Text(if (removing) "Remove ${section.title}?" else "Manage section") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!removing) {
                OutlinedTextField(title, { title = it; error = null }, enabled = !saving,
                    label = { Text("Section name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rename_section_name"))
                Text("Changes appear on every device. Existing chord-sheet tabs are preserved.", color = Muted)
                TextButton(enabled = !saving, onClick = { removing = true; error = null }, modifier = Modifier.testTag("remove_section")) {
                    Text("Remove section", color = MaterialTheme.colorScheme.error)
                }
            } else {
                val count = catalog.registrations.count { it.sectionId == section.id }
                Text("Choose where to keep this section's $count registrations. Their settings, links and chord availability will be preserved. This removes the section for everyone.", color = Muted)
                Box {
                    OutlinedButton(enabled = !saving, onClick = { menu = true }, modifier = Modifier.fillMaxWidth().testTag("section_destination")) {
                        Text(catalog.sections.find { it.id == destination }?.title ?: "Choose destination")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        catalog.sections.filter { it.id != section.id }.forEach { target ->
                            DropdownMenuItem(text = { Text(target.title) }, onClick = { destination = target.id; menu = false },
                                modifier = Modifier.testTag("section_destination_${target.id}"))
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("confirm_section_change"), onClick = {
            val validation = runCatching {
                if (removing) require(destination.isNotEmpty()) { "Choose a destination section." }
                else renamedSection(section, title, catalog.sections)
            }
            if (validation.isFailure) error = validation.exceptionOrNull()?.message else {
                saving = true
                scope.launch {
                    val result = onSave(if (removing) section.title else title, if (removing) destination else null)
                    saving = false; error = result.error
                }
            }
        }) { Text(if (saving) "Saving…" else if (removing) "Remove section" else "Save name") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
