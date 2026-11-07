package com.phantominnovation.choir

import org.junit.Assert.*
import org.junit.Test

class LibraryLogicTest {
    @Test fun registrationValidationAllowsKeyboardLimitsAndRejectsInvalidOrDuplicateData() {
        val existing = Registration("1", "entrance", "Existing song", "CountryWaltz", 104, -2, emptyList())
        val catalog = Catalog(listOf(ChoirSection("entrance", "Entrance", false), ChoirSection("holy", "Holy", false)), listOf(existing))
        val valid = RegistrationInput("New song", "entrance", "PowerBallad", "5", "-12")
        assertEquals(5, valid.toRegistration("new", catalog).tempo)
        assertEquals(12, valid.copy(tempo = "500", transpose = "+12").toRegistration("new", catalog).transpose)
        listOf(valid.copy(name = " "), valid.copy(style = ""), valid.copy(tempo = "4"), valid.copy(tempo = "501"),
            valid.copy(tempo = "100.5"), valid.copy(transpose = "13"), valid.copy(transpose = "-13"),
            valid.copy(sectionId = "missing"), valid.copy(name = " existing SONG "),
            valid.copy(links = listOf(DriveLink("Invalid", "https://example.com/file"))))
            .forEach { assertThrows(IllegalArgumentException::class.java) { it.toRegistration("new", catalog) } }
        assertEquals("holy", valid.copy(name = existing.name, sectionId = "holy").toRegistration("new", catalog).sectionId)
        assertEquals(existing, RegistrationInput.from(existing).toRegistration(existing.id, catalog))
    }

    @Test fun onlyRealHttpsDriveHostsAreAccepted() {
        listOf(
            "https://drive.google.com/file/d/123/view?usp=sharing",
            "https://docs.google.com/document/d/123/edit",
            "https://drive.google.com/open?id=123",
            "https://drive.google.com/drive/folders/123",
        ).forEach { assertEquals(it, validDriveUrl("  $it  ")) }
        listOf(
            "http://drive.google.com/file/d/123/view", "javascript:alert(1)",
            "https://drive.google.com.evil.test/file/d/123", "https://evil.test/?drive.google.com",
            "https://evil@drive.google.com/file/d/123", "https://drive.google.com:8443/file/d/123",
            "https://drive.google.com/", "file:///etc/passwd", "", "not a link",
        ).forEach { assertNull(it, validDriveUrl(it)) }
    }

    @Test fun searchCombinesTermsAcrossNameStyleAndSectionWithoutLosingMissingLinks() {
        val linked = Registration("1", "entrance", "Évery day", "CountryWaltz", 110, -2, listOf(DriveLink("File", "https://drive.google.com/file/d/1/view")))
        val missing = linked.copy(id = "2", name = "Every moment", links = emptyList())
        val source = listOf(linked, missing)
        val titles = mapOf("entrance" to "Entrance")
        assertEquals(source, filterRegistrations(source, " EVERY country ENTRANCE ", LinkFilter.ALL, titles))
        assertEquals(listOf(missing), filterRegistrations(source, "every", LinkFilter.MISSING, titles))
        assertEquals(listOf(linked), filterRegistrations(source, "", LinkFilter.LINKED, titles))
        assertTrue(filterRegistrations(source, "glory", LinkFilter.ALL, titles).isEmpty())
        assertEquals("-2", linked.transposeLabel)
        assertEquals("+2", linked.copy(transpose = 2).transposeLabel)
        assertEquals("0", linked.copy(transpose = 0).transposeLabel)
    }
}
