package com.phantominnovation.choir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun HostAccessDialog(
    session: HostSession, onDismiss: () -> Unit, onSignOut: () -> Unit,
    onSignIn: suspend (String, String) -> String?,
    onResetPassword: suspend (String) -> String?, onRefresh: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("englishchoir00@gmail.com") }
    // Passwords are never persisted in saved-instance state or preferences.
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.userId) { if (session.userId != null) password = "" }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Panel,
        icon = { Symbol(Mark.LOCK) },
        title = { Text(if (!session.configured) "Host editing is locked" else if (session.userId != null) "Host access" else "Host sign in") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                !session.configured -> Text("Firebase setup isn't complete yet. You can still browse registrations and open saved Drive files.")
                session.userId != null -> {
                    Text(session.email ?: "Signed in", color = Blue)
                    Text(when {
                        session.checking -> "Verifying host access…"
                        session.canEdit -> "You can add, edit and remove registrations. Changes are shared with everyone using the app."
                        else -> session.message ?: "This account doesn't have host permission."
                    })
                    if (!session.canEdit) OutlinedButton(onClick = onRefresh, enabled = !session.checking,
                        modifier = Modifier.testTag("refresh_host")) { Text("Check host access") }
                }
                else -> {
                    Text("Sign in with your host account to manage registrations.")
                    OutlinedTextField(email, { email = it; error = null }, label = { Text("Host email") },
                        singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("host_email"))
                    OutlinedTextField(password, { password = it; error = null }, label = { Text("App password") },
                        singleLine = true, enabled = !busy, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { Text("Use the password set for The Choir app.") },
                        modifier = Modifier.fillMaxWidth().testTag("host_password"))
                    TextButton(onClick = {
                        busy = true; error = null; notice = null
                        scope.launch {
                            try {
                                error = onResetPassword(email)
                                if (error == null) notice = "If this host account is registered, a password reset email has been sent."
                            } finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.testTag("host_reset_password")) { Text("Set or reset app password") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("host_login_error")) }
            notice?.let { Text(it, color = Blue) }
        } },
        confirmButton = {
            if (session.configured && session.userId == null) Button(onClick = {
                busy = true; error = null; notice = null
                scope.launch {
                    try { error = onSignIn(email, password) } finally { password = ""; busy = false }
                }
            }, enabled = !busy, modifier = Modifier.testTag("host_sign_in")) { Text(if (busy) "Signing in…" else "Sign in") }
            else TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") }
        },
        dismissButton = {
            if (session.userId != null) TextButton(onClick = { onSignOut(); password = ""; error = null }, enabled = !busy,
                modifier = Modifier.testTag("host_sign_out")) { Text("Sign out") }
            else TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        })
}
