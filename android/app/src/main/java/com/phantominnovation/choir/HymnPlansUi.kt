package com.phantominnovation.choir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun HymnPlansScreen(catalog: Catalog, chords: ChordState, state: HymnPlansState, canEdit: Boolean,
    onSave: suspend (HymnPlan, Long) -> WriteResult, onOpen: (DriveLink) -> Unit, onNotice: (String) -> Unit,
    onEditRegistration: (Registration) -> Unit = {}) {
    var slot by rememberSaveable { mutableStateOf("1") }
    var editing by remember { mutableStateOf<HymnPlan?>(null) }
    var choosing by remember { mutableStateOf<Pair<HymnPlan, HymnMatch>?>(null) }
    var clearing by remember { mutableStateOf<HymnPlan?>(null) }
    var collecting by remember { mutableStateOf<Pair<String, List<PdfSource>>?>(null) }
    val plan = state.plans.first { it.slot == slot }
    val titles = remember(catalog.sections) { catalog.sections.associate { it.id to it.title } }
    val matches = remember(plan, catalog, chords.songs) {
        matchHymns(parseHymnList(plan.text, catalog.sections), catalog, chords, plan.choices)
    }
    val resources = remember(matches, chords) { hymnResources(matches, chords) }
    val retrieval = resources.printed
    val context = LocalContext.current
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText(plan.title, text))
        onNotice("Copied to clipboard")
    }
    LaunchedEffect(canEdit) { if (!canEdit) { editing = null; clearing = null } }
    LazyColumn(Modifier.fillMaxSize().testTag("hymn_list_content"),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Hymn lists", color = Ink, style = MaterialTheme.typography.headlineLarge)
            Text("Three shared lists, in your order of service.", color = Muted, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.plans.forEach { item ->
                    FilterChip(selected = slot == item.slot, onClick = { slot = item.slot },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueSurface, selectedLabelColor = Blue, labelColor = Muted),
                        label = { Text("List ${item.slot}") }, modifier = Modifier.weight(1f).testTag("hymn_slot_${item.slot}"))
                }
            }
            state.message?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.loaded) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plan.title, color = Ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("hymn_plan_title"))
                    Text("${matches.count { it.selected != null }} of ${matches.size} registrations selected", color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { editing = plan }, modifier = Modifier.testTag("edit_hymn_list")) {
                            Text(if (!canEdit || plan.text.isBlank()) "Add hymns" else "Edit list")
                        }
                        if (canEdit && plan.text.isNotBlank()) OutlinedButton(onClick = { clearing = plan }, modifier = Modifier.testTag("clear_hymn_list")) { Text("Clear list") }
                    }
                    if (!canEdit) Text("Everyone can add hymns and choose registrations. Only hosts can remove hymns or clear lists.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (matches.isNotEmpty()) TextButton(onClick = { copy(hymnPlanText(matches, retrieval, titles, resources)) }) { Text("Copy full list") }
                    if (plan.text.isBlank()) Text("No hymns in this list yet.", color = Muted, modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            items(matches, key = { "hymn-${slot}-${it.request.position}" }) { item ->
                Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border),
                    modifier = Modifier.fillMaxWidth().testTag("hymn_result_${item.request.position}")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${item.request.position}. ${item.request.name}", style = MaterialTheme.typography.titleMedium)
                        item.request.sectionId?.let { Text(titles[it].orEmpty(), color = Muted, style = MaterialTheme.typography.labelMedium) }
                        val row = item.selected
                        if (row != null) {
                            Text("${titles[row.sectionId]} / ${row.name}", color = Blue, fontWeight = FontWeight.SemiBold)
                            Text("${row.style}\n${row.tempo} BPM · Transpose ${row.transposeLabel}", color = Ink)
                            if (canEdit) TextButton(onClick = { onEditRegistration(row) },
                                modifier = Modifier.testTag("edit_hymn_registration_${item.request.position}")) {
                                Text("Edit registration")
                            }
                            OfflineChordsIndicator(chords.available(row), "plan_${slot}_${item.request.position}")
                            row.links.forEach { link ->
                                TextButton(onClick = { onOpen(link) }, contentPadding = PaddingValues(0.dp)) { Text("Open ${link.title}") }
                            }
                        } else {
                            Text(item.status, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hymn_status_${item.request.position}"))
                            if (item.candidates.isNotEmpty()) Text(
                                if (item.exactMatches) "${item.candidates.size} registrations match. Choose the version."
                                else "Similar names were found. Choose a registration below.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { choosing = plan to item }, modifier = Modifier.testTag("choose_hymn_${item.request.position}")) {
                            Text(if (row == null) "Find registration" else "Change registration")
                        }
                    }
                }
            }
            if (matches.isNotEmpty()) {
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Border)
                    Text("Resources for this hymn list", color = Ink, style = MaterialTheme.typography.titleLarge)
                    Text("1 · With offline chords", color = Blue, modifier = Modifier.padding(top = 12.dp).testTag("resources_offline"))
                    Text("Hard-copy chords", color = Ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("chord_retrieval_heading"))
                    Text("Use these exact sheet names to find the printed chords. Repeated entries appear once.",
                        color = Muted, modifier = Modifier.padding(top = 8.dp))
                    chords.message?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                    if (retrieval.songs.isNotEmpty()) TextButton(onClick = {
                        copy(retrieval.songs.joinToString("\n") { "${titles[it.song.sectionId]} / ${it.song.name} (hymns ${it.positions.joinToString()})" })
                    }) { Text("Copy chord names") }
                    else Text("No available chord-list matches for the selected registrations.", color = Muted, modifier = Modifier.padding(top = 12.dp))
                }
                items(retrieval.songs) { item ->
                    Column(Modifier.testTag("retrieval_${item.positions.first()}_${chordKey(item.song.name)}")) {
                        Text(item.song.name, color = Blue, style = MaterialTheme.typography.titleMedium)
                        Text("${titles[item.song.sectionId]} · Hymns ${item.positions.joinToString()}", color = Muted)
                    }
                }
                items(retrieval.unavailable) { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Border)
                    Text("2 · Without offline chords or Drive links", color = Ink, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("resources_missing"))
                    Text("${resources.missing.size} hymns need resources or a registration choice.", color = Muted)
                    if (resources.missing.isEmpty()) Text("None in this list.", color = Muted)
                }
                items(resources.missing, key = { "missing-${slot}-${it.request.position}" }) { item ->
                    Column {
                        Text("${item.request.position}. ${item.selected?.name ?: item.request.name}", color = Ink)
                        Text(item.selected?.let { titles[it.sectionId].orEmpty() } ?: "${item.status} · no registration selected", color = Muted)
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Border)
                    Text("3 · Without offline chords, with Drive links", color = Ink, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("resources_drive"))
                    Text("Collect these attached files into one PDF in hymn order.", color = Muted)
                    if (resources.drive.isEmpty()) Text("None in this list.", color = Muted)
                    else Button(onClick = { collecting = plan.title to pdfSources(resources.drive) },
                        modifier = Modifier.testTag("collect_pdfs")) { Text("Create combined PDF") }
                }
                items(resources.drive, key = { "drive-${slot}-${it.request.position}" }) { item ->
                    Column {
                        Text("${item.request.position}. ${item.selected!!.name}", color = Blue, style = MaterialTheme.typography.titleMedium)
                        Text(titles[item.selected.sectionId].orEmpty(), color = Muted)
                        item.selected.links.forEach { link ->
                            TextButton(onClick = { onOpen(link) }) { Text(link.title) }
                        }
                    }
                }
            }
        }
    }
    collecting?.let { (title, sources) ->
        PdfCollectionDialog(title, sources, onDismiss = { collecting = null }, onOpen = onOpen)
    }
    editing?.let { original ->
        HymnListEditor(original, catalog, chords, appendOnly = !canEdit, onDismiss = { editing = null }, onSave = { updated ->
            val result = onSave(updated, original.revision)
            if (result.saved) { editing = null; onNotice("Hymn list saved for everyone") }
            result
        })
    }
    choosing?.let { (original, match) ->
        HymnRegistrationPicker(match, catalog, onDismiss = { choosing = null }, onSelect = { id ->
            val result = onSave(original.copy(choices = original.choices + (match.request.position to id)), original.revision)
            if (result.saved) choosing = null
            result
        })
    }
    clearing?.takeIf { canEdit }?.let { original ->
        HymnClearDialog(original, onDismiss = { clearing = null }, onClear = {
            val result = onSave(HymnPlan(original.slot), original.revision)
            if (result.saved) { clearing = null; onNotice("List cleared on all devices") }
            result
        })
    }
}

@Composable
private fun HymnListEditor(original: HymnPlan, catalog: Catalog, chords: ChordState, appendOnly: Boolean,
    onDismiss: () -> Unit, onSave: suspend (HymnPlan) -> WriteResult) {
    var title by rememberSaveable { mutableStateOf(original.title) }
    var text by rememberSaveable { mutableStateOf(if (appendOnly) "" else original.text) }
    val combined = if (appendOnly) listOf(original.text, text.trim()).filter { it.isNotEmpty() }.joinToString("\n") else text
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("hymn_editor"), color = Panel) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (appendOnly) "Add hymns to ${original.title}" else "Edit hymn list ${original.slot}", style = MaterialTheme.typography.titleLarge)
                if (!appendOnly) OutlinedTextField(title, { if (it.length <= 80) title = it }, enabled = !saving, singleLine = true,
                    label = { Text("List title") }, modifier = Modifier.fillMaxWidth().testTag("hymn_title_input"))
                Text("One hymn per line. Optional: Entrance: Beautiful Sunday. Section headings and numbered lists also work.", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (appendOnly) Text("New hymns will be added at the end. Existing hymns and choices are kept.", color = Muted, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { if (it.length <= 20_000) text = it }, enabled = !saving,
                    label = { Text("Hymns in service order") }, modifier = Modifier.fillMaxWidth().weight(1f).testTag("hymn_text_input"))
                Text("${parseHymnList(combined, catalog.sections).size} / 200 hymns · saves to all devices", color = Muted, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    Button(enabled = !saving, modifier = Modifier.testTag("save_hymn_list"), onClick = {
                        val choices = if (combined == original.text) original.choices else
                            matchHymns(parseHymnList(combined, catalog.sections), catalog, chords,
                                if (appendOnly) original.choices else emptyMap())
                                .mapNotNull { item -> item.selected?.let { item.request.position to it.id } }.toMap()
                        val updated = original.copy(title = title, text = combined, choices = if (appendOnly) choices + original.choices else choices)
                        val validation = runCatching {
                            if (appendOnly) require(parseHymnList(text, catalog.sections).isNotEmpty()) { "Enter at least one hymn to add." }
                            validateHymnPlan(updated, catalog.sections)
                        }.exceptionOrNull()
                        if (validation != null) error = validation.message else {
                            saving = true; error = null
                            scope.launch { val result = onSave(updated); error = result.error; saving = false }
                        }
                    }) { Text(if (saving) "Saving…" else "Find & save") }
                }
            }
        }
    }
}

@Composable
private fun HymnRegistrationPicker(match: HymnMatch, catalog: Catalog, onDismiss: () -> Unit,
    onSelect: suspend (String) -> WriteResult) {
    var query by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val titles = catalog.sections.associate { it.id to it.title }
    val source = if (query.isBlank() && match.candidates.isNotEmpty()) match.candidates else
        filterRegistrations(catalog.registrations, query, LinkFilter.ALL, titles)
    fun select(id: String) { saving = true; error = null; scope.launch { val result = onSelect(id); saving = false; error = result.error } }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("hymn_picker"), color = Panel) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose registration", style = MaterialTheme.typography.titleLarge)
                Text("${match.request.position}. ${match.request.name}", color = Muted)
                OutlinedTextField(query, { query = it }, enabled = !saving, singleLine = true,
                    label = { Text("Search all registrations") }, modifier = Modifier.fillMaxWidth().testTag("hymn_picker_search"))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (source.isEmpty()) item { Text("No registrations found.", color = Muted) }
                    items(source, key = { it.id }) { row ->
                        OutlinedButton(onClick = { select(row.id) }, enabled = !saving,
                            modifier = Modifier.fillMaxWidth().testTag("pick_registration_${row.id}"), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(row.name, fontWeight = FontWeight.SemiBold)
                                Text("${titles[row.sectionId]} · ${row.style} · ${row.tempo} BPM · ${row.transposeLabel}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
                    TextButton(onClick = { select("") }, enabled = !saving) { Text("Mark unavailable") }
                }
            }
        }
    }
}

@Composable
private fun HymnClearDialog(plan: HymnPlan, onDismiss: () -> Unit, onClear: suspend () -> WriteResult) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, containerColor = Panel,
        title = { Text("Clear ${plan.title}?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("This clears the hymn list and its registration choices for everyone. Registrations and the chord sheet are kept.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !saving, modifier = Modifier.testTag("confirm_clear_hymn_list"), onClick = {
            saving = true; scope.launch { val result = onClear(); error = result.error; saving = false }
        }) { Text(if (saving) "Clearing…" else "Clear for everyone") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
