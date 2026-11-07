package com.phantominnovation.choir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.net.URI

data class AppRelease(val versionCode: Long, val versionName: String, val downloadUrl: String) {
    fun isNewerThan(installedCode: Int) = versionCode > installedCode
}

/** Reject malformed release metadata instead of offering an untrusted download. */
internal fun parseAppRelease(data: Map<String, Any>?): AppRelease? {
    if (data == null || data["enabled"] == false) return null
    require(data["enabled"] == true)
    require(data["packageName"] == "com.phantominnovation.choir")
    val code = (data["versionCode"] as? Long)?.takeIf { it > 0 } ?: error("Invalid release version")
    val name = (data["versionName"] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 40 }
        ?: error("Invalid release name")
    val url = (data["downloadUrl"] as? String)?.trim() ?: error("Missing download")
    val uri = URI(url)
    require(uri.scheme == "https" && uri.host == "drive.google.com" && uri.userInfo == null
        && (uri.port == -1 || uri.port == 443) && uri.query == null && uri.fragment == null
        && Regex("/file/d/[A-Za-z0-9_-]+/view").matches(uri.path))
    return AppRelease(code, name, url)
}

interface AppUpdatesRepository { suspend fun latest(): AppRelease? }
object NoAppUpdates : AppUpdatesRepository { override suspend fun latest(): AppRelease? = null }
class FirebaseAppUpdates(private val database: FirebaseFirestore) : AppUpdatesRepository {
    override suspend fun latest(): AppRelease? =
        parseAppRelease(database.document("appReleases/android").get(Source.SERVER).await().data)
}

data class AppUpdateState(val checking: Boolean = false, val checked: Boolean = false,
    val release: AppRelease? = null, val error: String? = null)

@Composable
fun AppUpdateDialog(state: AppUpdateState, onDismiss: () -> Unit, onRetry: () -> Unit,
    onDownload: (String) -> Unit, installedCode: Int = BuildConfig.VERSION_CODE,
    installedName: String = BuildConfig.VERSION_NAME) {
    val newer = state.release?.takeIf { it.isNewerThan(installedCode) }
    AlertDialog(
        modifier = Modifier.testTag("app_update_dialog"), containerColor = Panel,
        onDismissRequest = onDismiss,
        title = { Text(if (newer != null && state.error == null && !state.checking) "Update available" else "App updates") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Installed version $installedName", color = Muted)
                when {
                    state.checking -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blue)
                        Text("Checking for updates…")
                    }
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error)
                    newer != null -> {
                        Text("Version ${newer.versionName} is ready.")
                        Text("Download the APK from Google Drive, then open it to install the update. Your saved data will stay in place.")
                    }
                    state.checked && state.release != null -> Text("You're up to date.")
                    state.checked -> Text("No update is published yet.")
                    else -> Text("Check for the latest version of The Choir.")
                }
            }
        },
        confirmButton = {
            if (!state.checking && state.error == null && newer != null) {
                Button(onClick = { onDownload(newer.downloadUrl) }, modifier = Modifier.testTag("download_update")) { Text("Download APK") }
            } else if (!state.checking) {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_update")) {
                    Text(if (state.error != null) "Retry" else "Check again")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
