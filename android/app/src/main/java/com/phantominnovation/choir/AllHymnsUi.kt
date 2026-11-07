package com.phantominnovation.choir

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
    var query by rememberSaveable { mutableStateOf("") }

    val activeSection = remember(selectedSectionId, sections) {
        sections.find { it.id == selectedSectionId }
    }

    if (activeSection != null) {
        // Section Hymns View
        val filteredHymns = remember(query, activeSection) {
            if (query.isBlank()) activeSection.hymns
            else activeSection.hymns.filter { it.contains(query, ignoreCase = true) }
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .testTag("all_hymns_section_list"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .clickable { onSelectSection(null); query = "" }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Symbol(Mark.BACK, Blue, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("All Hymns", color = Blue, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Text(
                    "ORDER OF SERVICE HYMNS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    activeSection.title,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "${activeSection.hymns.size} hymns in this section",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hymn_search"),
                    placeholder = { Text("Search hymns in ${activeSection.title}", fontSize = 14.sp) },
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
                Spacer(Modifier.height(10.dp))
            }

            if (filteredHymns.isEmpty()) {
                item {
                    Text(
                        "No hymns found",
                        color = Muted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
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
    } else {
        // Sections Overview (or global search)
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            val searchResults = remember(trimmedQuery, sections) {
                sections.flatMap { sec ->
                    sec.hymns.filter { it.contains(trimmedQuery, ignoreCase = true) }
                        .map { hymn -> sec to hymn }
                }
            }

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .testTag("all_hymns_search_list"),
                contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "ORDER OF SERVICE HYMNS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "All Hymns",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 12.dp)
                            .testTag("hymn_search_global"),
                        placeholder = { Text("Search across all hymns", fontSize = 14.sp) },
                        leadingIcon = { Symbol(Mark.SEARCH, Muted) },
                        trailingIcon = { IconButton(onClick = { query = "" }) { Symbol(Mark.CLOSE, Muted) } },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Border,
                            focusedBorderColor = Blue,
                            unfocusedContainerColor = Panel,
                            focusedContainerColor = Panel
                        )
                    )
                    Text(
                        "${searchResults.size} matching hymns",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (searchResults.isEmpty()) {
                    item {
                        Text(
                            "No hymns matching '$trimmedQuery'",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    itemsIndexed(searchResults) { _, (sec, hymn) ->
                        Surface(
                            onClick = { onSelectSection(sec.id); query = "" },
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
            // Main 13 Headings Grid
            val totalHymns = remember(sections) { sections.sumOf { it.hymns.size } }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(156.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier
                    .fillMaxSize()
                    .testTag("all_hymns_grid")
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text("All Hymns", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Browse hymns by Mass section",
                            color = Muted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                        )
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("hymn_search_input"),
                            placeholder = { Text("Search across all hymns", fontSize = 14.sp) },
                            leadingIcon = { Symbol(Mark.SEARCH, Muted) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
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
                                .padding(top = 22.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(bottom = 4.dp)
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

