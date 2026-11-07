package com.phantominnovation.choir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Amber = Color(0xFFFFBA45)

@Composable
fun OfflineChordsIndicator(available: Boolean, id: String) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = if (available) Color(0xFF30200A) else Color(0xFF15171B), shape = shape,
        border = BorderStroke(1.dp, if (available) Amber.copy(alpha = .75f) else Color(0xFF353943)),
        modifier = Modifier.testTag("offline_chords_$id").semantics { stateDescription = if (available) "Available" else "Not available" }
            .then(if (available) Modifier.shadow(12.dp, shape, ambientColor = Amber, spotColor = Amber) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(18.dp)) {
                if (available) drawCircle(Brush.radialGradient(listOf(Amber.copy(alpha = .6f), Color.Transparent)))
                drawCircle(if (available) Amber else Color(0xFF626975), radius = 4.dp.toPx())
            }
            Spacer(Modifier.width(7.dp))
            Text("Offline Chords", color = if (available) Amber else Color(0xFF858C98),
                style = MaterialTheme.typography.labelLarge, fontWeight = if (available) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
fun OfflineChordsDialog(
    row: Registration, setting: ChordOverride, matched: Boolean, syncMessage: String?,
    onDismiss: () -> Unit, onSave: suspend (ChordMode) -> WriteResult,
) {
    var selected by remember { mutableStateOf(setting.mode) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, containerColor = Panel,
        modifier = Modifier.testTag("chord_controls"),
        title = { Text("Offline Chords") },
        text = {
            Column {
                Text(row.name, color = Ink, style = MaterialTheme.typography.titleSmall)
                Text(if (matched) "A matching song is in the chord list." else "No matching song in this section of the chord list.",
                    color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                ChordMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().selectable(selected == mode, enabled = !saving, role = Role.RadioButton,
                        onClick = { selected = mode }).testTag("chord_mode_${mode.value}").padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected == mode, onClick = null, enabled = !saving)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(mode.label, color = Ink)
                            Text(when(mode) {
                                ChordMode.AUTOMATIC -> "Follow this section’s song list"
                                ChordMode.ENABLED -> "Glow amber and add the song to the sheet"
                                ChordMode.DISABLED -> "Stay dim even if the song is listed"
                            }, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("Disabling keeps the song in the sheet. This indicator does not open a file.", color = Muted,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                if (setting.appendPending) Text("Adding this song to the sheet is pending.", color = Amber, style = MaterialTheme.typography.bodySmall)
                syncMessage?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, modifier = Modifier.testTag("save_chord_mode"), onClick = {
                saving = true; error = null
                scope.launch { val result = onSave(selected); saving = false; error = result.error }
            }) { Text(if (saving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
