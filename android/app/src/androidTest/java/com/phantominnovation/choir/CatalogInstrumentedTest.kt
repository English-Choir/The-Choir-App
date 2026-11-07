package com.phantominnovation.choir

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun reviewedCatalogKeepsAllSectionsCorrectionsAndAlternateFiles() {
        val catalog = CatalogRepository(context).load()
        assertEquals(458, catalog.registrations.size)
        assertEquals(listOf("Entrance", "LHM", "Glory", "Response", "Alleluia", "Offertory", "Holy", "Proclamation", "Our father", "LOG", "Communion", "Thanksgiving", "Recessional"), catalog.sections.filterNot { it.other }.map { it.title })
        val others = catalog.sections.filter { it.other }.map { it.id }.toSet()
        assertEquals(7, others.size)
        assertEquals(39, catalog.registrations.count { it.sectionId in others })
        assertEquals(292, catalog.registrations.count { it.links.isNotEmpty() })
        assertEquals(298, catalog.registrations.sumOf { it.links.size })
        assertEquals(6, catalog.registrations.count { it.links.size == 2 })
        listOf("entrance" to "As we gather may", "log" to "Peace I give to you", "our-father" to "Stanes").forEach { (folder, name) ->
            assertTrue(catalog.registrations.single { it.sectionId == folder && it.name == name }.links.isEmpty())
        }
        assertTrue(catalog.registrations.single { it.sectionId == "recessional" && it.name == "Irish" }.links.single().url.contains("1bbjzfNMGLuGf-5IoGMdmv5FvFga3U0hN"))
        val source = context.assets.open("registrations.json").bufferedReader().use { it.readText() }
        listOf("right1", "right2", "right3", "active_parts", "voice_codes", "voice_paths").forEach { assertFalse(source.contains("\"$it\"")) }
    }

    @Test fun editsAndRemovalsSurviveStoreRecreationWithoutChangingSourceLinks() {
        val row = CatalogRepository(context).load().registrations.first { it.links.size == 2 }
        val host = TestHostAccess()
        val store = RegistrationStore(context, host, "instrumentation_link_tests", "instrumentation_empty_legacy")
        val original = row.links
        try {
            val added = DriveLink("Added file", "https://drive.google.com/file/d/test-file/view")
            store.saveLinks(row.id, original + added)
            assertEquals(original + added, RegistrationStore(context, host, "instrumentation_link_tests", "instrumentation_empty_legacy").load().registrations.single { it.id == row.id }.links)
            store.saveLinks(row.id, emptyList())
            assertTrue(RegistrationStore(context, host, "instrumentation_link_tests", "instrumentation_empty_legacy").load().registrations.single { it.id == row.id }.links.isEmpty())
            assertEquals(original, CatalogRepository(context).load().registrations.single { it.id == row.id }.links)
        } finally { context.deleteSharedPreferences("instrumentation_link_tests") }
    }
}
