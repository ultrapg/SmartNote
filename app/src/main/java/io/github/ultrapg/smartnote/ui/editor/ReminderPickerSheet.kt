package io.github.ultrapg.smartnote.ui.editor

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ultrapg.smartnote.domain.model.ReminderInfo
import io.github.ultrapg.smartnote.domain.model.RepeatRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderPickerSheet(
    existingReminder: ReminderInfo?,
    onSaveReminder: (triggerAtMs: Long, repeatRule: RepeatRule, useLiveUpdate: Boolean) -> Unit,
    onDeleteReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    val initialDateTime = remember(existingReminder) {
        existingReminder?.let {
            Instant.ofEpochMilli(it.triggerAtMs).atZone(zone).toLocalDateTime()
        } ?: LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0)
    }

    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }
    var selectedRepeat by remember { mutableStateOf(existingReminder?.repeatRule ?: RepeatRule.NONE) }
    var useLiveUpdate by remember { mutableStateOf(existingReminder?.useLiveUpdate ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Note Reminder", style = MaterialTheme.typography.titleLarge)
            }

            // Quick presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val next = LocalDateTime.now().plusHours(2).withMinute(0)
                        selectedDate = next.toLocalDate()
                        selectedTime = next.toLocalTime()
                    },
                    label = { Text("In 2 hours") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        selectedDate = LocalDate.now().plusDays(1)
                        selectedTime = LocalTime.of(9, 0)
                    },
                    label = { Text("Tomorrow 9:00 AM") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        selectedDate = LocalDate.now().plusWeeks(1)
                        selectedTime = LocalTime.of(9, 0)
                    },
                    label = { Text("Next week") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                }
            }

            Text("Repeat", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RepeatRule.entries.forEach { rule ->
                    val label = when (rule) {
                        RepeatRule.NONE -> "Does not repeat"
                        RepeatRule.DAILY -> "Daily"
                        RepeatRule.WEEKLY -> "Weekly"
                        RepeatRule.MONTHLY -> "Monthly"
                        RepeatRule.WEEKDAYS -> "Weekdays"
                    }
                    FilterChip(
                        selected = selectedRepeat == rule,
                        onClick = { selectedRepeat = rule },
                        label = { Text(label) }
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= 36) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Update when due", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Requests Android 16 Live Update / Now Bar visibility only when this reminder is active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = useLiveUpdate, onCheckedChange = { useLiveUpdate = it })
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existingReminder != null) {
                    TextButton(onClick = onDeleteReminder) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
                        Text(" Remove")
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
                Button(
                    onClick = {
                        val triggerMs = LocalDateTime.of(selectedDate, selectedTime)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        onSaveReminder(triggerMs, selectedRepeat, useLiveUpdate)
                    }
                ) {
                    Text("Save Reminder")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = selectedTime.hour,
            initialMinute = selectedTime.minute
        )
        ModalBottomSheet(onDismissRequest = { showTimePicker = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimePicker(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                    Button(
                        onClick = {
                            selectedTime = LocalTime.of(timeState.hour, timeState.minute)
                            showTimePicker = false
                        }
                    ) { Text("Confirm") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
