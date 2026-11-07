package com.phantominnovation.choir

import org.junit.Assert.*
import org.junit.Test

class HymnResourcesTest {
    private fun row(id: String, links: List<DriveLink> = emptyList()) = Registration(id,"entrance",id,"Waltz",90,0,links)
    private fun match(position: Int, row: Registration?) = HymnMatch(HymnRequest(position,"Requested $position"),row,emptyList(),false)
    @Test fun eachHymnBelongsToExactlyOneResourceGroupAndOverridesWin() {
        val link=DriveLink("Score","https://drive.google.com/file/d/first_file_123/view")
        val printed=row("printed",listOf(link));val pending=row("pending");val missing=row("missing");val drive=row("drive",listOf(link))
        val chords=ChordState(listOf(ChordSong("entrance","printed"),ChordSong("entrance","drive")),
            mapOf("pending" to ChordOverride(ChordMode.ENABLED),"drive" to ChordOverride(ChordMode.DISABLED)))
        val matches=listOf(match(1,printed),match(2,missing),match(3,drive),match(4,null),match(5,pending),match(6,printed))
        val groups=hymnResources(matches,chords)
        assertEquals(listOf(1,5,6),groups.offline.map { it.request.position })
        assertEquals(listOf(2,4),groups.missing.map { it.request.position })
        assertEquals(listOf(3),groups.drive.map { it.request.position })
        assertEquals(listOf(1,6),groups.printed.songs.single().positions)
        assertTrue(groups.printed.unavailable.single().contains("Waiting for chord-list sync"))
        val text=hymnPlanText(matches,groups.printed,mapOf("entrance" to "Entrance"),groups)
        assertTrue(text.contains("2. WITHOUT OFFLINE CHORDS OR DRIVE LINKS"))
        assertTrue(text.contains("3. WITHOUT OFFLINE CHORDS, WITH DRIVE LINKS"))
    }
    @Test fun fileIdentityDeduplicatesAlternateUrlsWithoutChangingHymnOrder() {
        val a=DriveLink("First","https://drive.google.com/file/d/first_file_123/view")
        val b=DriveLink("Second","https://docs.google.com/document/d/second_file_123/edit")
        val alias=DriveLink("Same first","https://drive.google.com/open?id=first_file_123")
        val sources=pdfSources(listOf(match(1,row("a",listOf(a,b))),match(4,row("b",listOf(alias)))))
        assertEquals(listOf("first_file_123","second_file_123"),sources.map { it.id })
        assertEquals(listOf(1,4),sources[0].positions)
        assertTrue(sources[1].document)
        assertNull(pdfSource(DriveLink("Bad","https://example.com/file/d/first_file_123"),listOf(1)).id)
        assertNull(pdfSource(DriveLink("Folder","https://drive.google.com/drive/folders/first_file_123"),listOf(1)).id)
        assertEquals("SundayMass - Drive chords.pdf",pdfFilename("../../Sunday:Mass"))
    }
}
