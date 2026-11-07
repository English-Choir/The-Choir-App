package com.phantominnovation.choir

data class HymnRequest(val position: Int, val name: String, val sectionId: String? = null)
data class HymnMatch(val request: HymnRequest, val selected: Registration?,
    val candidates: List<Registration>, val exactMatches: Boolean, val chosenId: String? = null) {
    val status: String get() = when {
        selected != null -> "Available"
        chosenId != null -> "Not available"
        exactMatches && candidates.size > 1 -> "Choose a registration"
        else -> "Not available"
    }
}

/** Numbered/bulleted lines and optional section headings preserve the user's song order. */
fun parseHymnList(text: String, sections: List<ChoirSection>): List<HymnRequest> {
    val sectionNames = buildMap {
        sections.forEach { put(chordKey(it.title), it.id); put(chordKey(it.id), it.id) }
        put("lordhavemercy", "lhm"); put("lambofgod", "log"); put("thanksgiving", "thanks-giving")
    }
    var currentSection: String? = null
    return buildList {
        text.lineSequence().forEach { raw ->
            val line = raw.trim().replace(Regex("^(?:\\d+[.)]\\s*|[-*•]\\s+|#+\\s*)"), "").trim()
            if (line.isEmpty()) return@forEach
            val heading = sectionNames[chordKey(line.trimEnd(':'))]
            if (heading != null) { currentSection = heading; return@forEach }
            val parts = line.split(Regex("\\s*[:–—]\\s*|\\s+-\\s+"), limit = 2)
            val inlineSection = if (parts.size == 2) sectionNames[chordKey(parts[0])] else null
            val title = if (inlineSection != null) parts[1].trim() else line
            if (title.isNotEmpty()) add(HymnRequest(size + 1, title, inlineSection ?: currentSection))
        }
    }
}

fun matchHymns(requests: List<HymnRequest>, catalog: Catalog, chords: ChordState,
    choices: Map<Int, String> = emptyMap()): List<HymnMatch> {
    val names = catalog.registrations.associate { row -> row.id to
        (setOf(chordKey(row.name)) + chords.matchingSongs(row).flatMap { it.nameKeys() }) }
    return requests.map { request ->
        val key = chordKey(request.name)
        val pool = catalog.registrations.filter { request.sectionId == null || it.sectionId == request.sectionId }
        val exact = pool.filter { key.isNotEmpty() && key in names.getValue(it.id) }
        val suggestions = if (exact.isNotEmpty()) exact else pool.filter { row ->
            key.length >= 4 && names.getValue(row.id).any { it.length >= 4 && (key in it || it in key) }
        }.take(12)
        val chosenId = choices[request.position]
        val selected = if (chosenId != null) catalog.registrations.find { it.id == chosenId }
            else exact.singleOrNull()
        HymnMatch(request, selected, suggestions, exact.isNotEmpty(), chosenId)
    }
}

data class RetrievalChord(val song: ChordSong, val positions: List<Int>)
data class ChordRetrieval(val songs: List<RetrievalChord>, val unavailable: List<String>)
data class HymnResources(val offline: List<HymnMatch>, val missing: List<HymnMatch>, val drive: List<HymnMatch>,
    val printed: ChordRetrieval)

fun hymnResources(matches: List<HymnMatch>, chords: ChordState): HymnResources {
    val offline = matches.filter { it.selected?.let(chords::available) == true }
    val without = matches.filterNot { it in offline }
    return HymnResources(offline, without.filter { it.selected?.links.isNullOrEmpty() },
        without.filter { !it.selected?.links.isNullOrEmpty() }, chordRetrieval(offline, chords))
}

fun chordRetrieval(matches: List<HymnMatch>, chords: ChordState): ChordRetrieval {
    val found = linkedMapOf<Pair<String, String>, Pair<ChordSong, MutableList<Int>>>()
    val missing = mutableListOf<String>()
    matches.forEach { match ->
        val row = match.selected ?: return@forEach
        val mapped = chords.matchingSongs(row)
        val reason = when {
            chords.setting(row).mode == ChordMode.DISABLED -> "Disabled by host"
            mapped.isEmpty() && chords.setting(row).mode == ChordMode.ENABLED -> "Waiting for chord-list sync"
            mapped.isEmpty() -> "No chord-list match"
            else -> null
        }
        if (reason != null) missing += "${match.request.position}. ${row.name} — $reason"
        else mapped.forEach { song ->
            found.getOrPut(song.sectionId to song.name) { song to mutableListOf() }.second.add(match.request.position)
        }
    }
    return ChordRetrieval(found.values.map { RetrievalChord(it.first, it.second.distinct()) }, missing)
}

fun hymnPlanText(matches: List<HymnMatch>, retrieval: ChordRetrieval, sections: Map<String, String>,
    resources: HymnResources? = null): String = buildString {
    appendLine("HYMN LIST")
    matches.forEach { item ->
        appendLine("${item.request.position}. ${item.request.name}")
        val row = item.selected
        if (row == null) appendLine("   ${item.status}") else {
            appendLine("   ${sections[row.sectionId]} / ${row.name}")
            appendLine("   ${row.style} · ${row.tempo} BPM · Transpose ${row.transposeLabel}")
            row.links.forEach { appendLine("   ${it.title}: ${it.url}") }
        }
    }
    appendLine(); appendLine("1. OFFLINE CHORDS / HARD-COPY CHORDS")
    retrieval.songs.forEach { item -> appendLine("${sections[item.song.sectionId]} / ${item.song.name} (hymns ${item.positions.joinToString()})") }
    if (retrieval.songs.isEmpty()) appendLine("No available chord-list matches for the selected registrations.")
    retrieval.unavailable.forEach { appendLine(it) }
    resources?.let { groups ->
        appendLine(); appendLine("2. WITHOUT OFFLINE CHORDS OR DRIVE LINKS")
        groups.missing.forEach { appendLine("${it.request.position}. ${it.selected?.name ?: it.request.name}${if (it.selected == null) " — ${it.status}" else ""}") }
        if (groups.missing.isEmpty()) appendLine("None in this list.")
        appendLine(); appendLine("3. WITHOUT OFFLINE CHORDS, WITH DRIVE LINKS")
        groups.drive.forEach { item ->
            appendLine("${item.request.position}. ${item.selected!!.name}")
            item.selected.links.forEach { appendLine("   ${it.title}: ${it.url}") }
        }
        if (groups.drive.isEmpty()) appendLine("None in this list.")
    }
}
