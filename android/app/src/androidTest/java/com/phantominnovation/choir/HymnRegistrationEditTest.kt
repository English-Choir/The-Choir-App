package com.phantominnovation.choir

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HymnRegistrationEditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun hostEditsSharedRegistrationAndStaysInListWhileGuestsCannotEdit() {
        val host = TestHostAccess()
        val context = compose.activity
        val store = RegistrationStore(context, host, "hymn_edit_test", "hymn_edit_links")
        context.deleteSharedPreferences("hymn_edit_test")
        context.deleteSharedPreferences("hymn_edit_links")
        try {
            val row = store.load().registrations.first()
            val original = HymnPlan("2", text = row.name + "\nUnknown hymn", choices = mapOf(1 to row.id))
            val plans = MutableStateFlow(HymnPlansState(listOf(HymnPlan("1"), original, HymnPlan("3")), loaded = true))
            val repository = object : HymnPlansRepository {
                override fun observe() = plans
                override suspend fun save(plan: HymnPlan, expectedRevision: Long) = error("Editing a registration must not rewrite the hymn list")
            }
            compose.activityRule.scenario.onActivity { activity ->
                val model = ChoirViewModel(activity.application, host, LocalRegistrationLibrary(store), hymnPlans = repository)
                activity.setContent { ChoirTheme { ChoirApp(model) } }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_search").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav_hymns").performClick()
            compose.onNodeWithTag("hymn_slot_2").performClick()
            fun reveal(tag: String) { compose.onNodeWithTag("hymn_list_content").performScrollToNode(hasTestTag(tag)) }
            reveal("edit_hymn_registration_1")
            compose.onNodeWithTag("edit_hymn_registration_1").performClick()
            compose.onNodeWithTag("registration_name").assertTextContains(row.name)
            compose.onNodeWithTag("registration_name").performTextReplacement("Updated hymn registration")
            compose.onNodeWithTag("registration_tempo").performScrollTo().performTextReplacement("107")
            compose.onNodeWithTag("registration_transpose").performScrollTo().performTextReplacement("-3")
            compose.onNodeWithTag("save_registration").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("registration_editor").fetchSemanticsNodes().isEmpty() }
            reveal("hymn_result_1")
            compose.onNodeWithText("${store.load().sections.first { it.id == row.sectionId }.title} / Updated hymn registration").assertExists()
            compose.onNodeWithText("${row.style}\n107 BPM · Transpose -3").assertExists()
            val saved = store.load().registrations.single { it.id == row.id }
            assertEquals(107, saved.tempo)
            assertEquals(-3, saved.transpose)
            assertEquals(original, plans.value.plans[1])
            reveal("hymn_slot_2"); compose.onNodeWithTag("hymn_slot_2").assertIsSelected()
            reveal("hymn_result_2"); compose.onNodeWithTag("edit_hymn_registration_2").assertDoesNotExist()
            reveal("edit_hymn_registration_1"); compose.onNodeWithTag("edit_hymn_registration_1").performClick()
            compose.runOnIdle { host.signOut() }
            compose.onNodeWithTag("registration_editor").assertDoesNotExist()
            compose.onNodeWithTag("edit_hymn_registration_1").assertDoesNotExist()
            compose.onNodeWithTag("choose_hymn_1").assertExists()
        } finally {
            context.deleteSharedPreferences("hymn_edit_test")
            context.deleteSharedPreferences("hymn_edit_links")
        }
    }
}
