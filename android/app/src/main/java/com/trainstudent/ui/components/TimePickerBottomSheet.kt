package com.trainstudent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainstudent.model.ParsedDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimePickerBottomSheet(
    initialTime: ParsedDateTime?,
    onTimeSelected: (ParsedDateTime) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    
    // Parse initial time or default to now
    val initialDateTime = initialTime?.let {
        try {
            LocalDateTime.parse(it.isoString)
        } catch (e: Exception) {
            LocalDateTime.now()
        }
    } ?: LocalDateTime.now()

    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val timePickerState = rememberTimePickerState(
        initialHour = selectedTime.hour,
        initialMinute = selectedTime.minute
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Date & Time",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick select chips
            Text(
                text = "Quick Select",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickTimeChip(
                    label = "Now",
                    onClick = {
                        val now = LocalDateTime.now().plusMinutes(5)
                        onTimeSelected(createParsedDateTime(now, "Now"))
                    }
                )
                QuickTimeChip(
                    label = "In 1 hour",
                    onClick = {
                        val time = LocalDateTime.now().plusHours(1)
                        onTimeSelected(createParsedDateTime(time, "In 1 hour"))
                    }
                )
                QuickTimeChip(
                    label = "Tomorrow 9am",
                    onClick = {
                        val time = LocalDate.now().plusDays(1).atTime(9, 0)
                        onTimeSelected(createParsedDateTime(time, "Tomorrow 9am"))
                    }
                )
                QuickTimeChip(
                    label = "Tomorrow 5pm",
                    onClick = {
                        val time = LocalDate.now().plusDays(1).atTime(17, 0)
                        onTimeSelected(createParsedDateTime(time, "Tomorrow 5pm"))
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Date selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TimePicker(
                    state = timePickerState,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Confirm button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val finalTime = LocalDateTime.of(
                            selectedDate,
                            LocalTime.of(timePickerState.hour, timePickerState.minute)
                        )
                        val displayText = formatDisplayTime(finalTime)
                        onTimeSelected(createParsedDateTime(finalTime, displayText))
                    }
                ) {
                    Text("Confirm")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTimeChip(
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) }
    )
}

private fun createParsedDateTime(dateTime: LocalDateTime, displayText: String): ParsedDateTime {
    return ParsedDateTime(
        isoString = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        displayText = displayText,
        confidence = 1.0f,
        isRelative = false
    )
}

private fun formatDisplayTime(dateTime: LocalDateTime): String {
    val now = LocalDate.now()
    val tomorrow = now.plusDays(1)
    
    val datePrefix = when (dateTime.toLocalDate()) {
        now -> "Today"
        tomorrow -> "Tomorrow"
        else -> dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    
    val hour = dateTime.hour
    val minute = dateTime.minute
    val displayHour = if (hour > 12) hour - 12 else if (hour == 0) 12 else hour
    val period = if (hour >= 12) "pm" else "am"
    
    val timeStr = if (minute == 0) {
        "$displayHour$period"
    } else {
        "$displayHour:${minute.toString().padStart(2, '0')}$period"
    }
    
    return "$datePrefix $timeStr"
}
