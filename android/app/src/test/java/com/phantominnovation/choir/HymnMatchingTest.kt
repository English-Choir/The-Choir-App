package com.phantominnovation.choir

import org.junit.Assert.*
import org.junit.Test

class HymnMatchingTest {
    private val sections = listOf(ChoirSection("entrance", "Entrance", false), ChoirSection("response", "Response", false), ChoirSection("communion", "Communion", false))
    private fun row(id: String, name: String, section: String = "entrance") = Registration(id, section, name, "Waltz", 90, -2, emptyList())
    private val rows = listOf(row("one", "Come on celebrate"), row("two", "Trust in me", "response"), row("three", "Trust in me", "communion"), row("four", "The lord is my shepherd (waltz)", "response"))
    private val catalog = Catalog(sections, rows)
    private val chords = ChordState(listOf(ChordSong("entrance", "Come on and celebrate", listOf("Come on celebrate")),
        ChordSong("response", "Trust in me"), ChordSong("communion", "Trust in me"),
        ChordSong("response", "The Lord Is My Shepherd", listOf(rows[3].name))))

    @Test fun headingsNumbersRepeatedSongsAndInlineSectionsKeepTheirOrder() {
        val parsed = parseHymnList("## ENTRANCE\n1. Come on and celebrate\n\n• Unknown hymn\nResponse: Trust in me\n4) Come on and celebrate", sections)
        assertEquals(listOf(1,2,3,4), parsed.map { it.position })
        assertEquals(listOf("entrance","entrance","response","entrance"), parsed.map { it.sectionId })
        val matches = matchHymns(parsed, catalog, chords)
        assertEquals(listOf("one",null,"two","one"), matches.map { it.selected?.id })
        assertEquals("Not available", matches[1].status)
    }

    @Test fun exactAliasesResolveButDuplicateSectionsRequireChoiceAndUnknownsAreNotGuessed() {
        val matches = matchHymns(parseHymnList("Trust in me\nCome on and celebrate\nCome on\nNever heard of this", sections), catalog, chords)
        assertNull(matches[0].selected); assertEquals(2, matches[0].candidates.size)
        assertEquals("Choose a registration", matches[0].status)
        assertEquals("one", matches[1].selected?.id)
        assertNull(matches[2].selected); assertTrue(matches[2].candidates.isNotEmpty())
        assertNull(matches[3].selected); assertTrue(matches[3].candidates.isEmpty())
        assertEquals("three", matchHymns(listOf(matches[0].request), catalog, chords, mapOf(1 to "three")).single().selected?.id)
    }

    @Test fun removedManualSelectionsStayUnavailableAndMusicalVariantsAreNotGuessed() {
        val request = HymnRequest(1, "The lord is my shepherd (Dm)", "response")
        assertNull(matchHymns(listOf(request), catalog, chords).single().selected)
        val removed = matchHymns(listOf(HymnRequest(1, "Come on celebrate")), catalog, chords, mapOf(1 to "deleted-id")).single()
        assertNull(removed.selected); assertEquals("Not available", removed.status)
        assertNull(matchHymns(listOf(HymnRequest(1, "Come on celebrate")), catalog, chords, mapOf(1 to "")).single().selected)
    }

    @Test fun aliasAndLiteralMatchesForDifferentVersionsRequireAChoice() {
        val literal = row("standard", "The lord is my shepherd", "response")
        val result = matchHymns(listOf(HymnRequest(1, literal.name, "response")),
            catalog.copy(registrations = catalog.registrations + literal), chords).single()
        assertNull(result.selected)
        assertEquals(setOf("standard","four"), result.candidates.map { it.id }.toSet())
    }

    @Test fun retrievalUsesExactSheetNamesDeduplicatesAndRespectsDisabledAndPendingChords() {
        val matches = matchHymns(parseHymnList("Come on celebrate\nResponse: Trust in me\nCome on celebrate\nCommunion: Trust in me", sections), catalog, chords)
        val settings = chords.copy(overrides = mapOf("two" to ChordOverride(ChordMode.DISABLED), "three" to ChordOverride(ChordMode.ENABLED)),
            songs = chords.songs.filterNot { it.sectionId == "communion" })
        val retrieval = chordRetrieval(matches, settings)
        assertEquals(listOf("Come on and celebrate"), retrieval.songs.map { it.song.name })
        assertEquals(listOf(1,3), retrieval.songs.single().positions)
        assertTrue(retrieval.unavailable.any { "Disabled by host" in it })
        assertTrue(retrieval.unavailable.any { "Waiting for chord-list sync" in it })
        assertTrue(hymnPlanText(matches, retrieval, sections.associate { it.id to it.title }).contains("Transpose -2"))
    }

    @Test fun listValidationCapsInputAndKeepsExactlyThreeSlots() {
        validateHymnPlan(HymnPlan("1", text = "A hymn"), sections)
        listOf(HymnPlan("4"), HymnPlan("1", title = " "), HymnPlan("1", text = "Song\n".repeat(201)),
            HymnPlan("1", choices = mapOf(0 to "one"))).forEach { assertTrue(runCatching { validateHymnPlan(it, sections) }.isFailure) }
    }
}
