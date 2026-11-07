package com.phantominnovation.choir

import org.junit.Assert.*
import org.junit.Test

class SectionsAndChordFiltersTest {
    @Test fun sectionMovesPreserveRegistrationsAndChordMatchesAcrossChains() {
        val source = ChoirSection("adoration", "Adoration", true)
        val target = ChoirSection("holy", "Holy", false)
        val row = Registration("stable", source.id, "Aicha", "Waltz", 90, -2, emptyList())
        val settings = mapOf(source.id to SectionSetting("Adoration service", true, "ash", 2),
            "ash" to SectionSetting("Ash", true, target.id, 1))
        val catalog = Catalog(listOf(source, target), listOf(row)).withSectionSettings(settings)
        assertEquals(listOf(target), catalog.sections)
        assertEquals(row.copy(sectionId = target.id), catalog.registrations.single())
        assertTrue(ChordState(listOf(ChordSong(source.id, row.name))).forCatalog(catalog).available(catalog.registrations.single()))
        assertThrows(IllegalArgumentException::class.java) { renamedSection(target, "Changed", listOf(source, target)) }
        assertThrows(IllegalArgumentException::class.java) { resolveSection("ash", mapOf("ash" to SectionSetting("Ash", true, "ash"))) }
    }

    @Test fun customSectionsNormalizeNamesAndCannotDuplicateExistingSections() {
        val section = newOtherSection("  Easter   Vigil  ", emptyList())
        assertEquals("Easter Vigil", section.title)
        assertTrue(section.other)
        assertEquals(section.id, newOtherSection("easter vigil", emptyList()).id)
        listOf("", " ", "a".repeat(61), "easter  vigil").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { newOtherSection(name, listOf(section)) }
        }
        assertThrows(IllegalArgumentException::class.java) { newOtherSection("ENTRANCE", listOf(ChoirSection("entrance","Entrance",false))) }
    }

    @Test fun availabilityFiltersFollowSheetAndHostSettingsAndCombineWithSearchAndLinks() {
        val linked = listOf(DriveLink("Score", "https://drive.google.com/file/d/test/view"))
        val rows = listOf(Registration("one","entrance","First hymn","Waltz",90,0,linked),
            Registration("two","entrance","Second hymn","Waltz",90,0,emptyList()),
            Registration("three","entrance","Third hymn","Waltz",90,0,linked))
        val chords = ChordState(listOf(ChordSong("entrance","First hymn"),ChordSong("entrance","Third hymn")),
            mapOf("two" to ChordOverride(ChordMode.ENABLED), "three" to ChordOverride(ChordMode.DISABLED)))
        fun filter(c: ChordFilter, q: String = "", l: LinkFilter = LinkFilter.ALL) =
            filterRegistrations(rows,q,l,mapOf("entrance" to "Entrance"),chords,c).map { it.id }
        assertEquals(listOf("one","two"),filter(ChordFilter.AVAILABLE))
        assertEquals(listOf("three"),filter(ChordFilter.MISSING))
        assertEquals(listOf("two"),filter(ChordFilter.AVAILABLE,"hymn",LinkFilter.MISSING))
        assertEquals(emptyList<String>(),filter(ChordFilter.MISSING,"First"))
        assertEquals(3,filter(ChordFilter.ALL).size)
    }
}
