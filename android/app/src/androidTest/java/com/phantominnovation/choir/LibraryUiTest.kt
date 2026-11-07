package com.phantominnovation.choir

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun waitForHome() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_search").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun homeSearchAcceptsFullPhraseAndFiltersStyle() {
        waitForHome()
        compose.onNodeWithTag("home_search").performClick()
        compose.onNodeWithTag("search").performTextInput("COUNTRYWALTZ")
        compose.onNodeWithTag("search").assertTextContains("COUNTRYWALTZ")
        compose.onNodeWithText("31 REGISTRATIONS").assertExists()
        compose.onNodeWithTag("filter_MISSING").performClick()
        compose.onAllNodesWithText("Host adds Drive link").onFirst().assertExists()
    }

    @Test fun sectionNavigationAndBothAlternateDriveFilesAreAvailable() {
        waitForHome()
        compose.onNodeWithTag("section_lhm").performClick()
        compose.onNodeWithTag("search").performTextInput("Ribbions of blue")
        compose.onNodeWithTag("registration_list").performScrollToNode(hasText("View 2 Drive files"))
        compose.onNodeWithText("View 2 Drive files").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ribbons of blue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ribbons of blue").assertExists()
        compose.onNodeWithText("Ribbons of blue.pdf").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithTag("sections_grid").performScrollToNode(hasTestTag("section_other"))
        compose.onNodeWithTag("section_other").performClick()
        compose.onNodeWithTag("section_mission-wedding").performClick()
        compose.onNodeWithText("Mission Wedding").assertExists()
        compose.onNodeWithTag("back").performClick()
        compose.onNodeWithText("Other registrations").assertExists()
    }

    @Test fun viewerCannotEditEvenAfterActivityRecreation() {
        waitForHome()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val registration = CatalogRepository(context).load().registrations.single { it.sectionId == "entrance" && it.name == "As we gather may" }
        compose.onNodeWithTag("add_registration").assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            waitForHome()
            compose.onNodeWithTag("section_entrance").performClick()
            compose.onNodeWithTag("search").performTextInput("As we gather may")
            compose.onNodeWithTag("registration_list").performScrollToNode(hasTestTag("add_${registration.id}"))
            compose.onNodeWithTag("add_${registration.id}").performClick()
            compose.onNodeWithText("Host editing is locked").assertExists()
            compose.onNodeWithTag("link_url").assertDoesNotExist()
            compose.onNodeWithTag("registration_editor").assertDoesNotExist()
            compose.onNodeWithTag("edit_registration_${registration.id}").assertDoesNotExist()
    }

    @Test fun landscapeKeepsTheDriveActionReachable() {
        waitForHome()
        compose.onNodeWithTag("section_entrance").performClick()
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitForIdle()
            compose.onNodeWithTag("registration_list").performScrollToNode(hasText("Open Drive file"))
            // A registration can be taller than a landscape viewport; scroll its action into view.
            compose.onAllNodesWithText("Open Drive file").onFirst().performScrollTo().assertIsDisplayed()
        } finally {
            compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }
}
