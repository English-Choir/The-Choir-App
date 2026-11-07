package com.phantominnovation.choir

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HostEditorUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val host = TestHostAccess()
    private val prefs = "host_editor_ui_test"
    private val legacy = "host_editor_ui_legacy"
    private lateinit var model: ChoirViewModel
    private fun freshStore() = RegistrationStore(context, host, prefs, legacy)

    private fun showHostLibrary() {
        compose.activityRule.scenario.onActivity { activity ->
            model = ChoirViewModel(activity.application, host, freshStore())
            activity.setContent { ChoirTheme { ChoirApp(model) } }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("add_registration").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun saved() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("registration_editor").fetchSemanticsNodes().isEmpty() }
    }

    private fun captureEditor() {
        compose.onNodeWithTag("registration_name").performScrollTo()
        compose.waitForIdle()
        val image = compose.onNodeWithTag("registration_editor").captureToImage().asAndroidBitmap()
        File(context.filesDir, "host-editor-preview.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @After fun clean() {
        context.deleteSharedPreferences(prefs)
        context.deleteSharedPreferences(legacy)
    }

    @Test fun hostCanCreateEditMoveAndDeleteWithValidationAndConfirmation() {
        showHostLibrary()
        compose.onNodeWithTag("section_entrance").performClick()
        compose.onNodeWithTag("add_registration").performClick()
        compose.onNodeWithTag("save_registration").performClick()
        compose.onNodeWithText("Enter a registration name (up to 150 characters).").assertExists()
        compose.onNodeWithTag("registration_name").performTextInput("Host UI song")
        compose.onNodeWithTag("registration_style").performScrollTo().performTextInput("CountryWaltz")
        compose.onNodeWithTag("registration_tempo").performScrollTo().performTextReplacement("110")
        compose.onNodeWithTag("registration_transpose").performTextReplacement("-2")
        compose.onNodeWithTag("registration_section").performScrollTo().performClick()
        compose.onNodeWithTag("choose_holy").performScrollTo().performClick()
        compose.onNodeWithTag("editor_add_link").performScrollTo().performClick()
        compose.onNodeWithTag("registration_link_url_0").performScrollTo().performTextInput("https://example.com/bad")
        compose.onNodeWithTag("save_registration").performClick()
        compose.onNodeWithText("Every Drive file needs a valid HTTPS Google Drive or Google Docs link.").assertExists()
        compose.onNodeWithTag("registration_link_url_0").performTextReplacement("https://drive.google.com/file/d/ui-test/view")
        compose.onNodeWithTag("save_registration").performClick()
        saved()
        val row = freshStore().load().registrations.single { it.name == "Host UI song" }
        assertEquals("holy", row.sectionId)
        assertEquals(110, row.tempo)
        assertEquals(-2, row.transpose)
        assertEquals("Host UI song", row.links.single().title)
        compose.onNodeWithTag("edit_registration_${row.id}").performClick()
        compose.onNodeWithTag("registration_name").performTextReplacement("Host UI song revised")
        compose.onNodeWithTag("registration_tempo").performScrollTo().performTextReplacement("105")
        compose.onNodeWithTag("registration_transpose").performTextReplacement("+3")
        compose.onNodeWithTag("registration_section").performScrollTo().performClick()
        compose.onNodeWithTag("choose_entrance").performScrollTo().performClick()
        compose.onNodeWithTag("save_registration").performClick()
        saved()
        val edited = freshStore().load().registrations.single { it.id == row.id }
        assertEquals("entrance", edited.sectionId)
        assertEquals("Host UI song revised", edited.name)
        assertEquals(105, edited.tempo)
        assertEquals(3, edited.transpose)
        assertEquals(row.links, edited.links)
        compose.onNodeWithTag("edit_registration_${row.id}").performClick()
        compose.onNodeWithTag("delete_registration").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_delete").performClick()
        compose.onNodeWithTag("registration_editor").assertExists()
        assertTrue(freshStore().load().registrations.any { it.id == row.id })
        compose.onNodeWithTag("delete_registration").performClick()
        compose.onNodeWithTag("confirm_delete").performClick()
        saved()
        assertEquals(458, freshStore().load().registrations.size)
        assertFalse(freshStore().load().registrations.any { it.id == row.id })
    }

    @Test fun revokingHostAccessClosesAnOpenEditorAndRejectsStaleSave() {
        showHostLibrary()
        val row = freshStore().load().registrations.first { it.sectionId == "entrance" }
        compose.onNodeWithTag("section_entrance").performClick()
        compose.onNodeWithTag("edit_registration_${row.id}").performClick()
        captureEditor()
        compose.onNodeWithTag("registration_name").performTextReplacement("Blocked change")
        host.signOut()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("registration_editor").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("add_registration").assertDoesNotExist()
        compose.onNodeWithTag("edit_registration_${row.id}").assertDoesNotExist()
        val denied = runBlocking { model.saveRegistration(row.id, RegistrationInput.from(row).copy(name = "Blocked change")) }
        assertFalse(denied.saved)
        assertEquals(row, freshStore().load().registrations.single { it.id == row.id })
    }

    @Test fun hostCanSaveAMissingLinkAndCancelUnsavedRegistrationChanges() {
        showHostLibrary()
        val row = freshStore().load().registrations.single { it.sectionId == "entrance" && it.name == "As we gather may" }
        compose.onNodeWithTag("section_entrance").performClick()
        compose.onNodeWithTag("search").performTextInput(row.name)
        compose.onNodeWithTag("registration_list").performScrollToNode(hasTestTag("add_${row.id}"))
        compose.onNodeWithTag("add_${row.id}").performClick()
        compose.onNodeWithTag("link_url").performTextInput("https://drive.google.com/file/d/added-host-link/view")
        compose.onNodeWithTag("save_link").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("link_url").fetchSemanticsNodes().isEmpty() }
        assertEquals(1, freshStore().load().registrations.single { it.id == row.id }.links.size)
        compose.onNodeWithTag("edit_registration_${row.id}").performClick()
        compose.onNodeWithTag("registration_name").performTextReplacement("Unsaved name")
        compose.onNodeWithTag("cancel_registration").performClick()
        compose.onNodeWithText("Discard changes?").assertExists()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("registration_name").assertTextContains("Unsaved name")
        compose.onNodeWithTag("cancel_registration").performClick()
        compose.onNodeWithText("Discard", substring = false).performClick()
        saved()
        assertEquals(row.name, freshStore().load().registrations.single { it.id == row.id }.name)
    }
}
