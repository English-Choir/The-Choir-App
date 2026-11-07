package com.phantominnovation.choir

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HymnPlansUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val catalog = Catalog(listOf(ChoirSection("entrance","Entrance",false), ChoirSection("communion","Communion",false)),
        listOf(Registration("one","entrance","Come on celebrate","Waltz",90,-2,emptyList()),
            Registration("two","entrance","Trust in me","Piano",80,0,emptyList()),
            Registration("three","communion","Trust in me","Ballad",75,2,emptyList())))
    private val chords = ChordState(listOf(ChordSong("entrance","Come on and celebrate", listOf("Come on celebrate")),
        ChordSong("communion","Trust in me")), message = "Chord list saved offline")

    @Test fun threeResourceGroupsBelongToTheCurrentListAndGuestsCanCollectFiles() {
        val linked=Registration("file","entrance","Drive hymn","Ballad",90,0,
            listOf(DriveLink("Score","https://drive.google.com/file/d/example_file_123/view")))
        val data=catalog.copy(registrations=catalog.registrations+linked)
        val state=HymnPlansState(plans=listOf(HymnPlan("1",text="Come on celebrate\nMissing hymn\nDrive hymn"),HymnPlan("2"),HymnPlan("3")),loaded=true)
        compose.activityRule.scenario.onActivity { activity -> activity.setContent { ChoirTheme {
            HymnPlansScreen(data,chords,state,false,onSave={_,_->WriteResult()},onOpen={},onNotice={})
        } } }
        fun reveal(tag: String) { compose.onNodeWithTag("hymn_list_content").performScrollToNode(hasTestTag(tag)) }
        reveal("resources_offline");compose.onNodeWithTag("resources_offline").assertIsDisplayed()
        reveal("resources_missing");compose.onNodeWithTag("resources_missing").assertIsDisplayed()
        reveal("resources_drive");compose.onNodeWithTag("resources_drive").assertIsDisplayed()
        reveal("collect_pdfs");compose.onNodeWithTag("collect_pdfs").performClick()
        compose.onNodeWithTag("pdf_collection_dialog").assertIsDisplayed()
        compose.onNodeWithText("1 distinct files, in hymn order. Repeated links appear once. Google Docs become PDFs; merging happens on this device.").assertExists()
        compose.onNodeWithText("Close").performClick()
        reveal("hymn_slot_2");compose.onNodeWithTag("hymn_slot_2").performClick()
        compose.onNodeWithTag("resources_drive").assertDoesNotExist()
        compose.onNodeWithText("No hymns in this list yet.").assertIsDisplayed()
    }

    @Test fun hostBuildsOrderedListChoosesAmbiguousSongAndReadsExactChordNames() {
        val state = mutableStateOf(HymnPlansState(loaded = true, message = "Synced across all devices"))
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { ChoirTheme {
                HymnPlansScreen(catalog,chords,state.value,true,onSave = { plan, revision ->
                    val previous = state.value.plans.first { it.slot == plan.slot }
                    check(previous.revision == revision)
                    state.value = state.value.copy(plans = state.value.plans.map { if(it.slot == plan.slot) plan.copy(revision = revision + 1) else it })
                    WriteResult()
                }, onOpen = {}, onNotice = {})
            } }
        }
        compose.onNodeWithTag("edit_hymn_list").performClick()
        compose.onNodeWithTag("hymn_text_input").performTextInput("Come on and celebrate\nUnknown hymn\nTrust in me")
        compose.onNodeWithTag("save_hymn_list").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("hymn_editor").fetchSemanticsNodes().isEmpty() }
        assertEquals("one", state.value.plans[0].choices[1])
        fun reveal(tag: String) { compose.onNodeWithTag("hymn_list_content").performScrollToNode(hasTestTag(tag)) }
        reveal("hymn_status_2"); compose.onNodeWithTag("hymn_status_2").assertTextEquals("Not available")
        reveal("choose_hymn_3"); compose.onNodeWithTag("choose_hymn_3").performClick()
        compose.onNodeWithTag("pick_registration_three").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("hymn_picker").fetchSemanticsNodes().isEmpty() }
        assertEquals("three", state.value.plans[0].choices[3])
        reveal("hymn_result_1")
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(compose.activity.filesDir,"hymn-list-matches.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        reveal("retrieval_1_comeonandcelebrate")
        compose.onNodeWithText("Come on and celebrate", useUnmergedTree = true).assertIsDisplayed()
        reveal("retrieval_3_trustinme")
        compose.onNodeWithText("Communion · Hymns 3").assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(compose.activity.filesDir,"hymn-list-chords.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        assertEquals(3, state.value.plans.size)
    }

    @Test fun guestsCanAppendAndChooseButOnlyHostsCanConfirmClearingOneSlot() {
        val state = mutableStateOf(HymnPlansState(plans=listOf(HymnPlan("1", text="Come on celebrate", revision=1),
            HymnPlan("2", text="Trust in me", revision=4), HymnPlan("3")),loaded=true,message="Saved offline"))
        val host = mutableStateOf(false)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { ChoirTheme {
                HymnPlansScreen(catalog,chords,state.value,host.value,onSave={plan,revision->
                    val old = state.value.plans.first { it.slot == plan.slot }
                    check(host.value || isGuestHymnChange(old.title, old.text, plan))
                    state.value=state.value.copy(plans=state.value.plans.map { if(it.slot==plan.slot)plan.copy(revision=revision+1)else it })
                    WriteResult()
                },onOpen={},onNotice={})
            } }
        }
        compose.onNodeWithTag("edit_hymn_list").assertIsDisplayed()
        compose.onNodeWithTag("clear_hymn_list").assertDoesNotExist()
        compose.onNodeWithText("Saved offline").assertIsDisplayed()
        compose.onNodeWithTag("edit_hymn_list").performClick()
        compose.onNodeWithTag("hymn_title_input").assertDoesNotExist()
        compose.onNodeWithTag("hymn_text_input").performTextInput("Trust in me")
        compose.onNodeWithTag("save_hymn_list").performClick()
        compose.waitUntil(5_000) { state.value.plans[0].text.endsWith("\nTrust in me") }
        compose.onNodeWithTag("hymn_list_content").performScrollToNode(hasTestTag("choose_hymn_2"))
        compose.onNodeWithTag("choose_hymn_2").performClick()
        compose.onNodeWithTag("pick_registration_three").performClick()
        compose.waitUntil(5_000) { state.value.plans[0].choices[2] == "three" }
        assertEquals("Come on celebrate\nTrust in me", state.value.plans[0].text)
        compose.onNodeWithTag("hymn_list_content").performScrollToNode(hasTestTag("edit_hymn_list"))
        compose.onNodeWithTag("clear_hymn_list").assertDoesNotExist()
        compose.runOnIdle { host.value=true }
        compose.onNodeWithTag("clear_hymn_list").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("Come on celebrate\nTrust in me",state.value.plans[0].text)
        compose.onNodeWithTag("clear_hymn_list").performClick()
        compose.onNodeWithTag("confirm_clear_hymn_list").performClick()
        compose.waitUntil(5_000) { state.value.plans[0].text.isEmpty() }
        assertEquals("Trust in me",state.value.plans[1].text)
        compose.onNodeWithTag("hymn_slot_2").performClick()
        compose.onNodeWithTag("edit_hymn_list").performClick()
        compose.runOnIdle { host.value=false }
        compose.onNodeWithTag("hymn_editor").assertDoesNotExist()
    }
}
