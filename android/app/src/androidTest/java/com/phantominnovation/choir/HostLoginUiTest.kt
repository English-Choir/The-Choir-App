package com.phantominnovation.choir

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HostLoginUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun signInErrorsAreVisiblePasswordsClearAndSignedInViewersCanSignOut() {
        val session = mutableStateOf(HostSession(configured=true))
        var signedOut = false
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { ChoirTheme {
                HostAccessDialog(session.value,onDismiss={},onSignOut={signedOut=true},
                    onSignIn={_,_->"Couldn't sign in. Check your host email and app password."},
                    onResetPassword={null},onRefresh={})
            } }
        }
        compose.onNodeWithTag("host_email").assertTextContains("englishchoir00@gmail.com")
        val image=compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("host_email"))).captureToImage().asAndroidBitmap()
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"host-login-preview.png")
            .outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithTag("host_password").performTextInput("test-only-invalid-password")
        compose.onNodeWithTag("host_sign_in").performClick()
        compose.onNodeWithTag("host_login_error").assertExists()
        compose.onNodeWithTag("host_password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
        compose.runOnIdle { session.value=HostSession("viewer",configured=true,email="viewer@example.test") }
        compose.onNodeWithText("This account doesn't have host permission.").assertExists()
        compose.onNodeWithTag("host_sign_out").performClick()
        assertTrue(signedOut)
    }
}
