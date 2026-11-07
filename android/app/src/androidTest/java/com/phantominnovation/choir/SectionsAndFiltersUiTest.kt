package com.phantominnovation.choir

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SectionsAndFiltersUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val host = TestHostAccess()
    private fun store() = RegistrationStore(context,host,"sections_test","sections_links_test")
    @After fun cleanup() { context.deleteSharedPreferences("sections_test"); context.deleteSharedPreferences("sections_links_test") }
    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(300) // Allow the emulator render thread to finish the current frame.
        val target=if (name == "add-section-dialog.png") compose.onNodeWithTag("section_editor") else compose.onRoot()
        val image=target.captureToImage().asAndroidBitmap()
        File(context.filesDir,name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun ready() { compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_search").fetchSemanticsNodes().isNotEmpty() } }
    private fun waitForNotices() { compose.waitUntil(12_000) {
        compose.onAllNodes(hasText("Section added for everyone") or hasText("Registration added")).fetchSemanticsNodes().isEmpty()
    } }

    @Test fun hostAddsOtherSectionAndRegistrationWhileGuestsCannotCreateSections() {
        compose.activityRule.scenario.onActivity { activity ->
            val model=ChoirViewModel(activity.application,host,store())
            activity.setContent { ChoirTheme { ChoirApp(model) } }
        }
        ready()
        compose.onNodeWithTag("sections_grid").performScrollToNode(hasTestTag("section_other"))
        compose.onNodeWithTag("section_other").performClick()
        compose.onNodeWithTag("add_section").performClick()
        compose.onNodeWithTag("section_name").performTextInput("Entrance")
        compose.onNodeWithTag("save_section").performClick()
        compose.onNodeWithTag("section_error").assertTextEquals("A section with this name already exists.")
        compose.onNodeWithTag("section_name").performTextReplacement("Christmas")
        capture("add-section-dialog.png")
        compose.onNodeWithTag("save_section").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("section_editor").fetchSemanticsNodes().isEmpty() }
        waitForNotices()
        val section = store().load().sections.single { it.title == "Christmas" }
        assertTrue(section.other)
        assertEquals(13,store().load().sections.count { !it.other })
        compose.onNodeWithText("Christmas").assertExists()
        compose.onNodeWithTag("add_registration").performClick()
        compose.onNodeWithTag("registration_section").assertTextContains("Christmas")
        compose.onNodeWithTag("registration_name").performTextInput("Christmas hymn")
        compose.onNodeWithTag("registration_style").performScrollTo().performTextInput("Waltz")
        compose.onNodeWithTag("save_registration").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("registration_editor").fetchSemanticsNodes().isEmpty() }
        waitForNotices()
        assertEquals(section.id,store().load().registrations.single { it.name == "Christmas hymn" }.sectionId)
        compose.onNodeWithText("Sections").performClick()
        compose.onNodeWithTag("sections_grid").performScrollToNode(hasTestTag("section_other"))
        compose.onNodeWithTag("section_other").performClick()
        compose.onNodeWithTag("other_grid").performScrollToNode(hasTestTag("add_section"))
        compose.onNodeWithTag("add_section").performClick()
        compose.runOnIdle { host.signOut() }
        compose.onNodeWithTag("section_editor").assertDoesNotExist()
        compose.onNodeWithTag("add_section").assertDoesNotExist()
        assertThrows(SecurityException::class.java) { store().addSection("Denied") }
    }

    @Test fun guestsSeeLiveCountsAndCanFilterWithOrWithoutOfflineChords() {
        host.signOut()
        val rows=store().load().registrations
        val first=rows[0]; val second=rows[1]
        val feed=MutableStateFlow(ChordState(listOf(ChordSong(first.sectionId,first.name)),
            mapOf(second.id to ChordOverride(ChordMode.ENABLED)),message=null))
        val chords=object: OfflineChordsRepository {
            override fun observe()=feed
            override suspend fun setMode(row: Registration,mode: ChordMode,expectedRevision: Long)=error("Guest cannot change chords")
        }
        compose.activityRule.scenario.onActivity { activity ->
            val model=ChoirViewModel(activity.application,host,LocalRegistrationLibrary(store()),chords)
            activity.setContent { ChoirTheme { ChoirApp(model) } }
        }
        ready()
        compose.onNodeWithTag("home_offline_count").assertTextEquals("2 registrations with Offline Chords")
        capture("offline-count-home.png")
        compose.onNodeWithText("All registrations").performClick()
        fun select(value: String) {
            compose.onNodeWithTag("chord_filter_selector").performClick()
            compose.onNodeWithTag("chord_filter_$value").performClick()
        }
        select("AVAILABLE")
        compose.onNodeWithText("2 REGISTRATIONS").assertExists()
        capture("offline-chord-filter.png")
        compose.runOnIdle { feed.value=feed.value.copy(overrides=feed.value.overrides+(first.id to ChordOverride(ChordMode.DISABLED))) }
        compose.onNodeWithText("1 REGISTRATIONS").assertExists()
        select("MISSING")
        compose.onNodeWithText("${rows.size-1} REGISTRATIONS").assertExists()
        compose.onNodeWithTag("search").performTextInput(second.name)
        compose.onNodeWithText("No registrations found").assertExists()
        compose.onNodeWithText("Clear filters").performScrollTo().performClick()
        compose.onNodeWithTag("chord_filter_selector").assertTextContains("All hymns")
        compose.onNodeWithText("${rows.size} REGISTRATIONS").assertExists()
    }

    @Test fun hostRenamesAndRemovesOriginalOtherSectionWithoutDeletingRegistrations() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { ChoirTheme { ChoirApp(ChoirViewModel(activity.application,host,store())) } }
        }
        ready()
        val original = store().load()
        compose.onNodeWithTag("sections_grid").performScrollToNode(hasTestTag("section_other"))
        compose.onNodeWithTag("section_other").performClick()
        compose.onNodeWithTag("manage_section_adoration").performScrollTo().performClick()
        compose.onNodeWithTag("rename_section_name").performTextReplacement("Adoration service")
        compose.onNodeWithTag("confirm_section_change").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("manage_section_dialog").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Adoration service").assertExists()
        compose.onNodeWithTag("manage_section_adoration").performScrollTo().performClick()
        compose.onNodeWithTag("remove_section").performClick()
        compose.onNodeWithTag("section_destination").performClick()
        compose.onNodeWithTag("section_destination_holy").performScrollTo().performClick()
        compose.onNodeWithTag("manage_section_dialog").captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.filesDir,"manage-section-removal.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        compose.onNodeWithTag("confirm_section_change").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("manage_section_dialog").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("section_adoration").assertDoesNotExist()
        val kept=store().load()
        assertEquals(original.registrations.size,kept.registrations.size)
        original.registrations.filter { it.sectionId == "adoration" }.forEach { row ->
            assertEquals(row.copy(sectionId="holy"),kept.registrations.single { it.id==row.id })
        }
    }
}
