package com.phantominnovation.choir

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        setContent { ChoirTheme { ChoirApp() } }
    }
}

@Composable
fun ChoirApp(model: ChoirViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val hostSession by model.hostSession.collectAsStateWithLifecycle()
    val rawChords by model.chordState.collectAsStateWithLifecycle()
    val updateState by model.updateState.collectAsStateWithLifecycle()
    val automaticUpdate by model.automaticUpdatePrompt.collectAsStateWithLifecycle()
    val hymnPlans by model.hymnPlanState.collectAsStateWithLifecycle()
    val canEdit = hostSession.canEdit
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf("sections") }
    var sectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LinkFilter.ALL) }
    var chordFilter by rememberSaveable { mutableStateOf(ChordFilter.ALL) }
    var addingSection by rememberSaveable { mutableStateOf(false) }
    var managingSection by remember { mutableStateOf<ChoirSection?>(null) }
    var focusSearch by rememberSaveable { mutableStateOf(false) }
    var manageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var editIndex by rememberSaveable { mutableIntStateOf(-1) }
    var hostInfo by rememberSaveable { mutableStateOf(false) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var chordControl by remember { mutableStateOf<Pair<Registration, ChordOverride>?>(null) }
    val catalog = state.catalog
    val chords = remember(rawChords, catalog?.sectionSettings) { rawChords.forCatalog(catalog) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { model.checkForUpdate(automatic = true) }
    LaunchedEffect(canEdit) {
        if (!canEdit) { editorId = null; editId = null; deleteId = null; chordControl = null; addingSection = false; managingSection = null }
    }
    fun goBack() {
        if (catalog?.sections?.any { it.id == sectionId && it.other } == true) sectionId = "other"
        else if (sectionId != null) sectionId = null else tab = "sections"
        query = ""; filter = LinkFilter.ALL; chordFilter = ChordFilter.ALL
    }
    BackHandler(sectionId != null || tab != "sections") { goBack() }
    fun open(link: DriveLink) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(requireNotNull(validDriveUrl(link.url)))))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Install a browser or Google Drive to open this file.") }
        } catch (_: IllegalArgumentException) {
            scope.launch { snackbar.showSnackbar("This link is invalid. Edit it and try again.") }
        }
    }
    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sectionId != null) {
                    IconButton(onClick = { goBack() }, modifier = Modifier.testTag("back").semantics { contentDescription = "Back to sections" }) {
                        Symbol(Mark.BACK, Ink)
                    }
                } else {
                    Box(Modifier.size(42.dp).background(BlueSurface, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Symbol(Mark.WAVE) }
                    Spacer(Modifier.width(12.dp))
                }
                Text("THE CHOIR", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.dismissUpdatePrompt(); showUpdates = true; model.checkForUpdate() }, modifier = Modifier.testTag("app_update"),
                    colors = ButtonDefaults.textButtonColors(contentColor = Blue, containerColor = BlueSurface),
                    shape = RoundedCornerShape(8.dp)) { Text("Update") }
                IconButton(onClick = { hostInfo = true }, modifier = Modifier.testTag("host_access").semantics { contentDescription = if (canEdit) "Host controls" else "Host access locked" }) {
                    Symbol(Mark.LOCK, if (canEdit) Blue else Muted, Modifier.size(19.dp))
                }
            }
        },
        floatingActionButton = {
            if (canEdit && catalog != null && tab != "hymns") ExtendedFloatingActionButton(
                onClick = { editorId = "new" }, icon = { Symbol(Mark.PLUS, Color.Black) }, text = { Text("Add registration") },
                containerColor = Blue, contentColor = Color.Black, modifier = Modifier.testTag("add_registration"),
            )
        },
        bottomBar = {
            Column {
            state.syncMessage?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).testTag("sync_status")) }
            NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
                NavigationBarItem(
                    selected = tab == "sections", onClick = { tab = "sections"; sectionId = null; query = ""; filter = LinkFilter.ALL; chordFilter = ChordFilter.ALL; focusSearch = false },
                    icon = { Symbol(Mark.SECTIONS, if (tab == "sections") Blue else Muted) }, label = { Text("Sections") },
                    colors = navigationColors(), modifier = Modifier.testTag("nav_sections"),
                )
                NavigationBarItem(
                    selected = tab == "library", onClick = { tab = "library"; sectionId = null; query = ""; filter = LinkFilter.ALL; chordFilter = ChordFilter.ALL; focusSearch = false },
                    icon = { Symbol(Mark.LIBRARY, if (tab == "library") Blue else Muted) }, label = { Text("All registrations") },
                    colors = navigationColors(), modifier = Modifier.testTag("nav_library"),
                )
                NavigationBarItem(
                    selected = tab == "hymns", onClick = { tab = "hymns"; sectionId = null; focusSearch = false },
                    icon = { Symbol(Mark.HYMNS, if (tab == "hymns") Blue else Muted) }, label = { Text("Hymn lists") },
                    colors = navigationColors(), modifier = Modifier.testTag("nav_hymns"),
                )
            }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.error) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The library couldn't be loaded.", style = MaterialTheme.typography.titleMedium)
                    Text("Your saved data is still on this device.", color = Muted, modifier = Modifier.padding(vertical = 12.dp))
                    Button(onClick = model::reload) { Text("Try again") }
                }
            } else if (catalog == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Blue)
            } else if (tab == "hymns") {
                HymnPlansScreen(catalog, chords, hymnPlans, canEdit, model::saveHymnPlan, ::open,
                    onNotice = { scope.launch { snackbar.showSnackbar(it) } },
                    onEditRegistration = { if (canEdit) { model.beginEdit(it); editorId = it.id } })
            } else if (tab == "sections" && sectionId == null) {
                SectionHome(catalog, chords, onSearch = { tab = "library"; focusSearch = true }, onSection = { sectionId = it; filter = LinkFilter.ALL; chordFilter = ChordFilter.ALL; focusSearch = false })
            } else if (sectionId == "other") {
                OtherSections(catalog, canEdit, onAddSection = { addingSection = true }, onSection = { sectionId = it },
                    onManage = { managingSection = it })
            } else {
                val section = catalog.sections.find { it.id == sectionId }
                val source = if (section == null) catalog.registrations else catalog.registrations.filter { it.sectionId == section.id }
                RegistrationList(
                    source = source, title = section?.title ?: "All registrations", sectionTitles = catalog.sections.associate { it.id to it.title },
                    showSection = section == null, query = query, onQuery = { query = it }, filter = filter, onFilter = { filter = it }, autoFocus = focusSearch,
                    chordFilter = chordFilter, onChordFilter = { chordFilter = it },
                    canEdit = canEdit, onEditRegistration = { if (canEdit) { model.beginEdit(it); editorId = it.id } },
                    chords = chords, onChordControl = { if (canEdit) chordControl = it to chords.setting(it) },
                    onOpen = { if (it.links.size == 1) open(it.links.first()) else manageId = it.id },
                    onManage = { manageId = it.id },
                    onAdd = { if (canEdit) { model.beginEdit(it); editId = it.id; editIndex = -1 } else hostInfo = true },
                )
            }
        }
    }
    if (addingSection && canEdit && catalog != null) AddSectionDialog(catalog,
        onDismiss = { addingSection = false }, onSave = { title ->
            val created = newOtherSection(title, catalog.sections)
            val result = model.addSection(title)
            if (result.saved) {
                addingSection = false; sectionId = created.id; query = ""; filter = LinkFilter.ALL; chordFilter = ChordFilter.ALL
                scope.launch { snackbar.showSnackbar("Section added for everyone") }
            }
            result
        })
    managingSection?.takeIf { canEdit && catalog != null }?.let { section ->
        ManageSectionDialog(section, requireNotNull(catalog), onDismiss = { managingSection = null }, onSave = { title, destination ->
            val result = model.changeSection(section, title, destination)
            if (result.saved) {
                managingSection = null
                if (destination != null && sectionId == section.id) sectionId = destination
                scope.launch { snackbar.showSnackbar(if (destination == null) "Section renamed for everyone" else "Section removed · registrations kept in the chosen section") }
            }
            result
        })
    }
    if (showUpdates || automaticUpdate != null) AppUpdateDialog(
        if (showUpdates) updateState else AppUpdateState(checked = true, release = automaticUpdate),
        onDismiss = { showUpdates = false; model.dismissUpdatePrompt() },
        onRetry = { model.checkForUpdate() }, onDownload = { open(DriveLink("The Choir update", it)) })
    catalog?.registrations?.find { it.id == manageId }?.let { registration ->
        ManageLinksDialog(registration, canEdit = canEdit, onDismiss = { manageId = null }, onOpen = ::open,
            onEdit = { index -> if (canEdit) { model.beginEdit(registration); manageId = null; editId = registration.id; editIndex = index } })
    }
    catalog?.registrations?.find { canEdit && it.id == editId }?.let { registration ->
        EditLinkDialog(registration, editIndex, onDismiss = { editId = null }, onSave = { links ->
            val success = model.saveLinks(registration.id, links)
            if (success) {
                editId = null
                scope.launch { snackbar.showSnackbar("Drive links saved") }
            }
            success
        })
    }
    if (hostInfo) HostAccessDialog(hostSession, onDismiss = { hostInfo = false }, onSignOut = model::signOut,
        onSignIn = model::signIn, onResetPassword = model::resetPassword, onRefresh = model::refreshHost)
    chordControl?.takeIf { canEdit }?.let { (row, setting) ->
        OfflineChordsDialog(row, setting, chords.matches(row), chords.message,
            onDismiss = { chordControl = null }, onSave = { mode ->
                val result = model.saveChordMode(row, mode, setting.revision)
                if (result.saved) {
                    chordControl = null
                    scope.launch { snackbar.showSnackbar(if (mode == ChordMode.ENABLED)
                        "Offline Chords enabled · song queued for the sheet" else "Offline Chords set to ${mode.label.lowercase()}") }
                }
                result
            })
    }
    if (canEdit && catalog != null && editorId != null) {
        val existing = catalog.registrations.find { it.id == editorId }
        if (editorId == "new" || existing != null) RegistrationEditor(
            catalog = catalog, existing = existing,
            initialSectionId = catalog.sections.find { it.id == sectionId }?.id ?: catalog.sections.first().id,
            onDismiss = { editorId = null },
            onSave = { input ->
                val result = model.saveRegistration(existing?.id, input)
                if (result.saved) {
                    editorId = null
                    if (tab != "hymns") {
                        tab = "sections"; sectionId = input.sectionId
                        query = input.name.trim(); filter = LinkFilter.ALL; focusSearch = false
                    }
                    scope.launch { snackbar.showSnackbar(if (existing == null) "Registration added" else "Registration saved") }
                }
                result
            },
            onDelete = { deleteId = existing?.id },
        )
    }
    catalog?.registrations?.find { canEdit && it.id == deleteId }?.let { registration ->
        DeleteRegistrationDialog(registration, onDismiss = { deleteId = null }, onDelete = {
            val result = model.deleteRegistration(registration.id)
            if (result.saved) {
                deleteId = null; editorId = null
                scope.launch { snackbar.showSnackbar("Registration deleted") }
            }
            result
        })
    }
}

@Composable
private fun navigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Blue, selectedTextColor = Blue, indicatorColor = BlueSurface,
    unselectedIconColor = Muted, unselectedTextColor = Muted,
)

@Composable
private fun SearchBox(value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, autoFocus: Boolean = false) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) requester.requestFocus() }
    OutlinedTextField(
        value, onValue, modifier.fillMaxWidth().focusRequester(requester).testTag("search"),
        placeholder = { Text("Search registrations or styles", fontSize = 14.sp) },
        leadingIcon = { Symbol(Mark.SEARCH, Muted) },
        trailingIcon = if (value.isNotEmpty()) ({
            IconButton(onClick = { onValue("") }, modifier = Modifier.semantics { contentDescription = "Clear search" }) { Symbol(Mark.CLOSE, Muted) }
        }) else null,
        singleLine = true, shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Border, focusedBorderColor = Blue,
            unfocusedContainerColor = Panel, focusedContainerColor = Panel),
    )
}

@Composable
private fun SectionHome(catalog: Catalog, chords: ChordState, onSearch: () -> Unit, onSection: (String) -> Unit) {
    val main = catalog.sections.filterNot { it.other }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().testTag("sections_grid"),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Your library", style = MaterialTheme.typography.headlineLarge)
                Text("PSR-SX900  ·  Ready for every service", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
                Surface(onClick = onSearch, color = Panel, border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("home_search")) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Symbol(Mark.SEARCH, Muted); Spacer(Modifier.width(13.dp)); Text("Search registrations or styles", color = Muted, fontSize = 14.sp)
                    }
                }
                Text("${catalog.registrations.count { chords.available(it) }} registrations with Offline Chords", color = Blue,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp).testTag("home_offline_count"))
                Row(Modifier.fillMaxWidth().padding(vertical = 23.dp), verticalAlignment = Alignment.CenterVertically) {
                    SummaryNumber(catalog.registrations.size.toString(), "registrations", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(35.dp).background(Border))
                    SummaryNumber(catalog.registrations.count { it.links.isNotEmpty() }.toString(), "with Drive links", Modifier.weight(1f).padding(start = 24.dp))
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Order of service", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("13 SECTIONS", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        items(main, key = { it.id }) { section ->
            val rows = catalog.registrations.filter { it.sectionId == section.id }
            SectionTile(section, main.indexOf(section) + 1, rows.size, onClick = { onSection(section.id) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            val otherCount = catalog.registrations.count { row -> catalog.sections.any { it.id == row.sectionId && it.other } }
            Surface(onClick = { onSection("other") }, shape = RoundedCornerShape(18.dp), color = Panel, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("section_other")) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Symbol(Mark.FOLDER)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Other registrations", style = MaterialTheme.typography.titleMedium)
                        Text("$otherCount registrations · ${catalog.sections.count { it.other }} folders", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Symbol(Mark.CHEVRON, Muted, Modifier.size(16.dp))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Style, tempo & transpose, always at hand.", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SummaryNumber(value: String, caption: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(caption, color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SectionTile(section: ChoirSection, order: Int, count: Int, onClick: () -> Unit) {
    Surface(onClick, shape = RoundedCornerShape(18.dp), color = Panel, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth().testTag("section_${section.id}")) {
        Column(Modifier.padding(17.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(order.toString().padStart(2, '0'), color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(BlueSurface, RoundedCornerShape(7.dp)).padding(horizontal = 8.dp, vertical = 5.dp))
                Spacer(Modifier.weight(1f))
                Symbol(Mark.CHEVRON, Muted, Modifier.size(14.dp))
            }
            Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
            Text("$count registrations", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun OtherSections(catalog: Catalog, canEdit: Boolean, onAddSection: () -> Unit, onSection: (String) -> Unit,
    onManage: (ChoirSection) -> Unit) {
    val otherIds = catalog.sections.filter { it.other }.map { it.id }.toSet()
    val count = catalog.registrations.count { it.sectionId in otherIds }
    LazyVerticalGrid(GridCells.Adaptive(156.dp), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = if (canEdit) 110.dp else 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("other_grid")) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 12.dp)) {
                Text("Other registrations", style = MaterialTheme.typography.headlineMedium)
                Text("$count registrations · ${otherIds.size} folders", color = Muted, modifier = Modifier.padding(top = 7.dp))
                if (canEdit) Button(onClick = onAddSection, modifier = Modifier.padding(top = 14.dp).testTag("add_section")) {
                    Symbol(Mark.PLUS, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add section")
                }
            }
        }
        items(catalog.sections.filter { it.other }, key = { it.id }) { section ->
            Column {
                SectionTile(section, catalog.sections.filter { it.other }.indexOf(section) + 1,
                    catalog.registrations.count { it.sectionId == section.id }, onClick = { onSection(section.id) })
                if (canEdit) TextButton(onClick = { onManage(section) }, modifier = Modifier.testTag("manage_section_${section.id}")) {
                    Text("Rename / remove")
                }
            }
        }
    }
}

@Composable
private fun RegistrationList(
    source: List<Registration>, title: String, sectionTitles: Map<String, String>, showSection: Boolean,
    query: String, onQuery: (String) -> Unit, filter: LinkFilter, onFilter: (LinkFilter) -> Unit, autoFocus: Boolean,
    chordFilter: ChordFilter, onChordFilter: (ChordFilter) -> Unit,
    canEdit: Boolean, onEditRegistration: (Registration) -> Unit,
    chords: ChordState, onChordControl: (Registration) -> Unit,
    onOpen: (Registration) -> Unit, onManage: (Registration) -> Unit, onAdd: (Registration) -> Unit,
) {
    val filtered = remember(source, query, filter, sectionTitles, chords, chordFilter) {
        filterRegistrations(source, query, filter, sectionTitles, chords, chordFilter)
    }
    var chordMenu by remember { mutableStateOf(false) }
    val header: @Composable () -> Unit = {
        Column(Modifier.padding(top = 14.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text("${source.size} registrations · ${source.count { it.links.isNotEmpty() }} with Drive links", color = Muted,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
            chords.message?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 10.dp).testTag("chord_sync_status")) }
            SearchBox(query, onQuery, autoFocus = autoFocus)
            Text("${source.count { chords.available(it) }} registrations with Offline Chords", color = Blue,
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp).testTag("section_offline_count"))
            if (showSection) Box(Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = { chordMenu = true }, modifier = Modifier.fillMaxWidth().testTag("chord_filter_selector")) {
                    Text(chordFilter.title, modifier = Modifier.weight(1f)); Symbol(Mark.CHEVRON, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(chordMenu, onDismissRequest = { chordMenu = false }) {
                    ChordFilter.entries.forEach { item ->
                        DropdownMenuItem(text = { Text(item.title) }, onClick = { onChordFilter(item); chordMenu = false },
                            modifier = Modifier.testTag("chord_filter_${item.name}"))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 2.dp)) {
                LinkFilter.entries.forEach { item ->
                    FilterChip(selected = filter == item, onClick = { onFilter(item) }, label = { Text(item.title) },
                        modifier = Modifier.testTag("filter_${item.name}"),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueSurface, selectedLabelColor = Blue))
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        // On short windows, let the header scroll so controls remain reachable in landscape.
        // Use window configuration, not IME-reduced height, to keep search focus stable.
        val compact = LocalConfiguration.current.screenHeightDp < 500
        Column(Modifier.fillMaxSize()) {
            if (!compact) Box(Modifier.padding(horizontal = 20.dp)) { header() }
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = if (canEdit) 100.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f).testTag("registration_list")) {
                if (compact) item(key = "header") { header() }
                if (filtered.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Symbol(Mark.SEARCH, Muted, Modifier.size(36.dp))
                        Text("No registrations found", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
                        Text("Try a different name, style or filter.", color = Muted, modifier = Modifier.padding(top = 8.dp))
                        TextButton(onClick = { onQuery(""); onFilter(LinkFilter.ALL); onChordFilter(ChordFilter.ALL) }) { Text("Clear filters") }
                    }
                }
                else {
                item { Text("${filtered.size} REGISTRATIONS", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 2.dp)) }
                items(filtered, key = { it.id }) { registration ->
                    RegistrationCard(registration, sectionTitles[registration.sectionId].takeIf { showSection }, canEdit = canEdit,
                        chords = chords, onChordControl = { onChordControl(registration) },
                        onEdit = { onEditRegistration(registration) },
                        onOpen = { onOpen(registration) }, onManage = { onManage(registration) }, onAdd = { onAdd(registration) })
                }
                }
            }
        }
    }
}

@Composable
private fun RegistrationCard(registration: Registration, section: String?, canEdit: Boolean, chords: ChordState, onChordControl: () -> Unit, onEdit: () -> Unit, onOpen: () -> Unit, onManage: () -> Unit, onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Panel, border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth().testTag("registration_${registration.id}")) {
        Column(Modifier.padding(18.dp)) {
            section?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = Blue, modifier = Modifier.padding(bottom = 7.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(registration.name, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.weight(1f))
                if (canEdit) IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_registration_${registration.id}").semantics { contentDescription = "Edit registration ${registration.name}" }) { Symbol(Mark.EDIT, Blue, Modifier.size(19.dp)) }
            }
            Text("STYLE", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 17.dp, bottom = 4.dp))
            Text(registration.style, color = Blue, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().padding(top = 17.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TEMPO", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                        Text(registration.tempo.toString(), fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        Text("BPM", color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp, bottom = 3.dp))
                    }
                }
                Box(Modifier.width(1.dp).height(40.dp).background(Border))
                Column(Modifier.weight(1f).padding(start = 24.dp)) {
                    Text("TRANSPOSE", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(registration.transposeLabel, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
            HorizontalDivider(color = Border)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OfflineChordsIndicator(chords.available(registration), registration.id)
                Spacer(Modifier.weight(1f))
                if (canEdit) TextButton(onClick = onChordControl, modifier = Modifier.testTag("chord_control_${registration.id}")) {
                    Text("Change", color = Blue)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (registration.links.isEmpty()) {
                    OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("add_${registration.id}"), border = BorderStroke(1.dp, Color(0xFF345377)), shape = RoundedCornerShape(12.dp)) {
                        Symbol(if (canEdit) Mark.PLUS else Mark.LOCK, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(9.dp)); Text(if (canEdit) "Add Drive link" else "Host adds Drive link")
                    }
                } else {
                    Button(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueSurface, contentColor = Blue)) {
                        Symbol(Mark.EXTERNAL, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(9.dp))
                        Text(if (registration.links.size > 1) "View ${registration.links.size} Drive files" else "Open Drive file")
                    }
                    if (canEdit) IconButton(onClick = onManage, modifier = Modifier.testTag("manage_${registration.id}").semantics { contentDescription = "Manage Drive links for ${registration.name}" }) {
                        Symbol(Mark.LINK, Muted, Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}
