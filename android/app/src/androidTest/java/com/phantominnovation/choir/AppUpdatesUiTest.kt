package com.phantominnovation.choir

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.lifecycle.Lifecycle

class AppUpdatesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun guestCanCheckAndRetryWithoutHostLogin() {
        var calls = 0
        val repository = object : AppUpdatesRepository {
            override suspend fun latest(): AppRelease {
                calls++
                if (calls <= 2) error("Offline")
                return AppRelease(BuildConfig.VERSION_CODE.toLong(), "1.4", "https://drive.google.com/file/d/id/view")
            }
        }
        compose.activityRule.scenario.onActivity { activity ->
            val access = TestHostAccess().apply { signOut() }
            val model = ChoirViewModel(activity.application, access,
                LocalRegistrationLibrary(RegistrationStore(activity, access)), updates = repository)
            activity.setContent { ChoirTheme { ChoirApp(model) } }
        }
        compose.onNodeWithTag("add_registration").assertDoesNotExist()
        compose.onNodeWithText("BUTTON 1").assertDoesNotExist()
        compose.waitUntil(5_000) { calls == 1 }
        compose.onNodeWithTag("app_update_dialog").assertDoesNotExist()
        compose.onNodeWithTag("app_update").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("download_update").assertDoesNotExist()
        compose.onNodeWithTag("retry_update").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("You're up to date.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("download_update").assertDoesNotExist()
        assertEquals(3, calls)
    }

    @Test fun openingTheAppAutomaticallyPromptsForANewerVersionOnlyOncePerSession() {
        var calls = 0
        val repository = object : AppUpdatesRepository {
            override suspend fun latest(): AppRelease {
                calls++
                return AppRelease(BuildConfig.VERSION_CODE + 1L, "Next", "https://drive.google.com/file/d/id/view")
            }
        }
        compose.activityRule.scenario.onActivity { activity ->
            val access = TestHostAccess().apply { signOut() }
            val model = ChoirViewModel(activity.application, access,
                LocalRegistrationLibrary(RegistrationStore(activity, access)), updates = repository)
            activity.setContent { ChoirTheme { ChoirApp(model) } }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Update available").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("download_update").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { calls >= 2 }
        compose.onNodeWithTag("app_update_dialog").assertDoesNotExist()
    }

    @Test fun newerReleaseOffersTheVerifiedDownloadUrl() {
        val url = "https://drive.google.com/file/d/verified-id/view"
        var opened: String? = null
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { ChoirTheme {
                AppUpdateDialog(AppUpdateState(checked = true, release = AppRelease(6, "1.5", url)),
                    onDismiss = {}, onRetry = {}, onDownload = { opened = it }, installedCode = 5, installedName = "1.4")
            } }
        }
        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNodeWithTag("download_update").performClick()
        assertEquals(url, opened)
    }
}
