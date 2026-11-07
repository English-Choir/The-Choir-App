package com.phantominnovation.choir

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

data class HymnSection(val id: String, val title: String, val hymns: List<String>)

object HymnCatalogLoader {
    fun load(context: Context): List<HymnSection> {
        return runCatching {
            val text = context.assets.open("hymn_catalog.json").bufferedReader().use { it.readText() }
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val hymnsArr = obj.getJSONArray("hymns")
                val hymns = (0 until hymnsArr.length()).map { hymnsArr.getString(it) }
                HymnSection(obj.getString("id"), obj.getString("title"), hymns)
            }
        }.getOrDefault(emptyList())
    }
}

@Composable
fun AllHymnsScreen(
    selectedSectionId: String?,
    onSelectSection: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sections = remember { HymnCatalogLoader.load(context) }
    val activeSection = remember(selectedSectionId, sections) {
        sections.find { it.id == selectedSectionId }
    }

    if (activeSection != null) {
        SectionHymnsView(
            section = activeSection,
            onBack = { onSelectSection(null) },
            modifier = modifier
        )
    } else {
        MainHymnsOverview(
            sections = sections,
            onSelectSection = onSelectSection,
            modifier = modifier
        )
    }
}

@Composable
private fun MainHymnsOverview(
    sections: List<HymnSection>,
    onSelectSection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val totalHymns = remember(sections) { sections.sumOf { it.hymns.size } }
    val trimmedQuery = query.trim()

    val searchResults = remember(trimmedQuery, sections) {
        if (trimmedQuery.isEmpty()) emptyList()
        else {
            sections.flatMap { sec ->
                sec.hymns.filter { it.contains(trimmedQuery, ignoreCase = true) }
                    .map { hymn -> sec to hymn }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Persistent header and Search TextField - NEVER unmounted while typing
        Column(modifier = Modifier.padding(top = 14.dp)) {
            Text("All Hymns", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Browse hymns by Mass section",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hymn_search_input"),
                placeholder = { Text("Search across all hymns", fontSize = 14.sp) },
                leadingIcon = { Symbol(Mark.SEARCH, Muted) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = "" }) { Symbol(Mark.CLOSE, Muted) }
                }) else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Border,
                    focusedBorderColor = Blue,
                    unfocusedContainerColor = Panel,
                    focusedContainerColor = Panel
                )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (trimmedQuery.isNotEmpty()) {
                    Text(
                        "${searchResults.size} MATCHING HYMNS",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    Text(
                        "Sections",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "13 SECTIONS · $totalHymns HYMNS",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Content Area below search box
        Box(modifier = Modifier.weight(1f)) {
            if (trimmedQuery.isNotEmpty()) {
                if (searchResults.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            "No hymns matching \"$trimmedQuery\"",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("all_hymns_search_list"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(searchResults, key = { index, (sec, hymn) -> "${sec.id}-$index-$hymn" }) { _, (sec, hymn) ->
                            Surface(
                                onClick = { onSelectSection(sec.id) },
                                shape = RoundedCornerShape(12.dp),
                                color = Panel,
                                border = BorderStroke(1.dp, Border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = hymn,
                                            color = Ink,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = sec.title,
                                            color = Blue,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                    Symbol(Mark.CHEVRON, Muted, Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(156.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("all_hymns_grid")
                ) {
                    itemsIndexed(sections, key = { _, s -> s.id }) { index, sec ->
                        Surface(
                            onClick = { onSelectSection(sec.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = Panel,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("hymn_section_${sec.id}")
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = BlueSurface,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "%02d".format(index + 1),
                                            color = Blue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Symbol(Mark.CHEVRON, Muted, Modifier.size(14.dp))
                                }
                                Column {
                                    Text(
                                        text = sec.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${sec.hymns.size} hymns",
                                        color = Blue,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHymnsView(
    section: HymnSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmedQuery = query.trim()

    val filteredHymns = remember(trimmedQuery, section) {
        if (trimmedQuery.isEmpty()) section.hymns
        else section.hymns.filter { it.contains(trimmedQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Persistent section header and Search Box
        Column(modifier = Modifier.padding(top = 10.dp)) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Symbol(Mark.BACK, Blue, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("All Hymns", color = Blue, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "ORDER OF SERVICE HYMNS",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                section.title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "${section.hymns.size} hymns in this section",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hymn_search"),
                placeholder = { Text("Search hymns in ${section.title}", fontSize = 14.sp) },
                leadingIcon = { Symbol(Mark.SEARCH, Muted) },
                trailingIcon = if (query.isNotEmpty()) ({
                    IconButton(onClick = { query = "" }) { Symbol(Mark.CLOSE, Muted) }
                }) else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Border,
                    focusedBorderColor = Blue,
                    unfocusedContainerColor = Panel,
                    focusedContainerColor = Panel
                )
            )
            Spacer(Modifier.height(12.dp))
        }

        // List area
        Box(modifier = Modifier.weight(1f)) {
            if (filteredHymns.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "No hymns found",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("all_hymns_section_list"),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredHymns, key = { index, hymn -> "$index-$hymn" }) { index, hymn ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Panel,
                            border = BorderStroke(1.dp, Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "%02d".format(index + 1),
                                    color = Blue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.width(32.dp)
                                )
                                Text(
                                    text = hymn,
                                    color = Ink,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
