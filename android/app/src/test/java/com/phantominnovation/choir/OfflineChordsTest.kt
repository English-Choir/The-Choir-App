package com.phantominnovation.choir

import org.junit.Assert.*
import org.junit.Test

class OfflineChordsTest {
    private val row = Registration("song","entrance","Beautiful Sunday","Waltz",90,0,emptyList())
    @Test fun matchingIgnoresPunctuationButNeverCrossesSectionsOrChoosesVariants() {
        val state = ChordState(listOf(ChordSong("entrance","BEAUTIFUL Sunday!")))
        assertTrue(state.available(row))
        assertFalse(state.available(row.copy(sectionId="communion")))
        assertFalse(state.available(row.copy(name="Beautiful Sunday (waltz)")))
        assertEquals(chordKey("I’ve forgotten"),chordKey("Ive forgotten"))
        assertEquals(chordKey("Aicha (Holy)"),chordKey("Aicha"))
        assertNotEquals(chordKey("Blessed be God (Dm)"),chordKey("Blessed be God"))
    }
    @Test fun suppliedAlternateTitlesAndExplicitAliasesMatchWithoutFuzzyGuessing() {
        val state = ChordState(listOf(ChordSong("entrance","I, the lord (here I am lord)",listOf("I am here"))))
        assertTrue(state.available(row.copy(name="I, the lord")))
        assertTrue(state.available(row.copy(name="Here I am Lord")))
        assertTrue(state.available(row.copy(name="I am here")))
        assertFalse(state.available(row.copy(name="I the lord is here")))
    }
    @Test fun disableWinsOverSheetAndEnableWinsWithoutMatchAndAutomaticResets() {
        val listed = ChordState(listOf(ChordSong("entrance",row.name)))
        assertFalse(listed.copy(overrides=mapOf(row.id to ChordOverride(ChordMode.DISABLED))).available(row))
        assertTrue(ChordState(overrides=mapOf(row.id to ChordOverride(ChordMode.ENABLED))).available(row))
        assertFalse(ChordState(overrides=mapOf(row.id to ChordOverride(ChordMode.AUTOMATIC))).available(row))
        assertTrue(listed.copy(overrides=mapOf(row.id to ChordOverride(ChordMode.AUTOMATIC))).available(row))
    }
}
