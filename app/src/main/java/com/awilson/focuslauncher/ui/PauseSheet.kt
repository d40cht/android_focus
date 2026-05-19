package com.awilson.focuslauncher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar

sealed class PauseOption {
    data object SnapBack : PauseOption()
    data class For(val durationMs: Long) : PauseOption()
    data class UntilEpoch(val epochMs: Long, val hour: Int, val minute: Int) : PauseOption()
    data object Indefinite : PauseOption()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(
    onDismiss: () -> Unit,
    onPick: (PauseOption) -> Unit,
    initialHour: Int? = null,
    initialMinute: Int? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showTimePicker by remember { mutableStateOf(false) }

    fun dismissThen(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Unlock full phone",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            PauseRow("Until next lock", "Snap back to Focus on the next screen-off") {
                dismissThen { onPick(PauseOption.SnapBack) }
            }
            val untilSubtitle = if (initialHour != null && initialMinute != null) {
                "Last used: ${formatHourMinute(initialHour, initialMinute)}"
            } else {
                "Pick when to resume"
            }
            PauseRow("Until a time...", untilSubtitle) {
                showTimePicker = true
            }
            PauseRow("For 1 hour", "Auto-resume Focus after 60 minutes") {
                dismissThen { onPick(PauseOption.For(60 * 60 * 1000L)) }
            }
            PauseRow("For 2 hours", "Auto-resume after 2 hours") {
                dismissThen { onPick(PauseOption.For(2 * 60 * 60 * 1000L)) }
            }
            PauseRow("For 4 hours", "Auto-resume after 4 hours") {
                dismissThen { onPick(PauseOption.For(4 * 60 * 60 * 1000L)) }
            }
            PauseRow("Indefinitely", "Stay on the full phone until you come back to Focus") {
                dismissThen { onPick(PauseOption.Indefinite) }
            }
        }
    }

    if (showTimePicker) {
        UntilTimePicker(
            initialHour = initialHour,
            initialMinute = initialMinute,
            onCancel = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                dismissThen {
                    onPick(
                        PauseOption.UntilEpoch(
                            epochMs = epochForHourMinuteToday(hour, minute),
                            hour = hour,
                            minute = minute,
                        ),
                    )
                }
            },
        )
    }
}

private fun formatHourMinute(hour: Int, minute: Int): String {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(cal.time)
}

@Composable
private fun PauseRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Column {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UntilTimePicker(
    initialHour: Int?,
    initialMinute: Int?,
    onCancel: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val now = Calendar.getInstance()
    val state = rememberTimePickerState(
        initialHour = initialHour ?: now.get(Calendar.HOUR_OF_DAY),
        initialMinute = initialMinute ?: now.get(Calendar.MINUTE),
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Resume Focus at") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

private fun epochForHourMinuteToday(hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= System.currentTimeMillis()) {
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return cal.timeInMillis
}
