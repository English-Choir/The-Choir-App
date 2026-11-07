package com.phantominnovation.choir

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class OfflineChordsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val host = TestHostAccess()
    private val prefs = "chords_ui_test"
    private val links = "chords_ui_links"
    @After fun cleanup() { context.deleteSharedPreferences(prefs); context.deleteSharedPreferences(links) }

    @Test fun amberIndicatorAndHostOverridesFollowSheetWithoutOpeningAFile() {
        val store = RegistrationStore(context,host,prefs,links)
        val row = store.load().registrations.single { it.sectionId == "entrance" && it.name == "Beautiful Sunday" }
        val feed = MutableStateFlow(ChordState(listOf(ChordSong("entrance",row.name)), message = null))
        val repository = object : OfflineChordsRepository {
            override fun observe() = feed
            override suspend fun setMode(row: Registration, mode: ChordMode, expectedRevision: Long) {
                check(host.session.value.canEdit)
                feed.value = feed.value.copy(overrides = mapOf(row.id to ChordOverride(mode,expectedRevision+1,mode==ChordMode.ENABLED)))
            }
        }
        compose.activityRule.scenario.onActivity { activity ->
            val model = ChoirViewModel(activity.application,host,LocalRegistrationLibrary(store),repository)
            activity.setContent { ChoirTheme { ChoirApp(model) } }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("section_entrance").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("section_entrance").performClick()
        compose.onNodeWithTag("search").performTextInput(row.name)
        fun indicator() = compose.onNodeWithTag("offline_chords_${row.id}")
        fun available(value: Boolean) { indicator().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,if(value) "Available" else "Not available")) }
        fun change(mode: String) {
            compose.onNodeWithTag("chord_control_${row.id}").performScrollTo().performClick()
            compose.onNodeWithTag("chord_mode_$mode").performClick()
            compose.onNodeWithTag("save_chord_mode").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("chord_controls").fetchSemanticsNodes().isEmpty() }
            // The transient success snackbar can cover this row before the next tap.
            compose.waitUntil(7_000) {
                compose.onAllNodes(hasText("Offline Chords set to", substring = true) or
                    hasText("Offline Chords enabled", substring = true)).fetchSemanticsNodes().isEmpty()
            }
        }
        available(true)
        indicator().assertHasNoClickAction()
        change("disabled"); available(false)
        compose.runOnIdle { feed.value = feed.value.copy(songs=listOf(ChordSong("entrance",row.name))) }
        available(false)
        change("automatic"); available(true)
        compose.runOnIdle { feed.value = feed.value.copy(songs=emptyList()) }
        available(false)
        change("enabled"); available(true)
        compose.onNodeWithTag("chord_control_${row.id}").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("chord_controls").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("chord_controls").captureToImage().asAndroidBitmap().let { image ->
            File(context.filesDir,"offline-chords-controls.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        host.signOut()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("chord_controls").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("chord_control_${row.id}").assertDoesNotExist()
        available(true)
    }
}
