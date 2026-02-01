package com.trainstudent.ui.save

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trainstudent.model.BookingPlan
import com.trainstudent.model.ParsingField
import com.trainstudent.ui.components.AnimatedInputChip
import com.trainstudent.ui.components.BookingPlanCard
import com.trainstudent.ui.components.SettingsBottomSheet
import com.trainstudent.ui.components.StationPickerDialog
import com.trainstudent.ui.components.TimePickerBottomSheet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun SaveMoneyScreen(
    viewModel: SaveMoneyViewModel = hiltViewModel(),
    onNavigateToLiveTrains: () -> Unit = {},
    onMicPermissionRequired: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val metricsSummary by viewModel.metricsSummary.collectAsState()
    val context = LocalContext.current
    
    var showDatePicker by remember { mutableStateOf(false) }
    var showDateExpanded by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    
    // Permission state using Accompanist
    val audioPermissionState = com.google.accompanist.permissions.rememberPermissionState(
        Manifest.permission.RECORD_AUDIO
    )
    
    // Handle mic button click with permission check
    val handleMicClick: () -> Unit = {
        when (audioPermissionState.status) {
            is com.google.accompanist.permissions.PermissionStatus.Granted -> {
                viewModel.startVoiceInput()
            }
            is com.google.accompanist.permissions.PermissionStatus.Denied -> {
                // Request permission
                audioPermissionState.launchPermissionRequest()
                Toast.makeText(
                    context,
                    "Microphone permission required for voice input",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Show latency toast
    LaunchedEffect(uiState.latencyToast) {
        uiState.latencyToast?.let { latency ->
            Toast.makeText(context, "Voice processed in $latency", Toast.LENGTH_SHORT).show()
            viewModel.clearLatencyToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Save Money") },
                actions = {
                    IconButton(onClick = { viewModel.showSettings(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Giant Mic Button / Stop Button
                if (uiState.isSearching) {
                    StopButton(onClick = { viewModel.cancelSearch() })
                } else {
                    MicButton(
                        isListening = uiState.isListening,
                        onClick = handleMicClick
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Input Fields
                InputFieldsSection(
                    origin = uiState.origin?.name,
                    destination = uiState.destination?.name,
                    departureTime = uiState.departureTime?.displayText,
                    parsingField = uiState.parsingField,
                    isListening = uiState.isListening,
                    onOriginClick = { viewModel.showStationPicker(StationPickerTarget.ORIGIN) },
                    onDestinationClick = { viewModel.showStationPicker(StationPickerTarget.DESTINATION) },
                    onTimeClick = { viewModel.showTimePicker(true) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Collapsible Date Picker
                DatePickerSection(
                    selectedDate = selectedDate,
                    isExpanded = showDateExpanded,
                    onExpandToggle = { showDateExpanded = !showDateExpanded },
                    onDateClick = { showDatePicker = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Results or Loading
                if (uiState.isSearching) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Searching for deals...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelSearch() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STOP")
                        }
                    }
                } else if (uiState.bookingPlans.isNotEmpty()) {
                    Text(
                        text = "RESULTS (${uiState.bookingPlans.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.bookingPlans.take(3)) { plan ->
                            BookingPlanCard(
                                plan = plan,
                                isFirst = plan == uiState.bookingPlans.first()
                            )
                        }
                    }
                } else if (uiState.origin != null || uiState.destination != null) {
                    Text(
                        text = "Speak or tap fields to complete your journey",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Error
                uiState.searchError?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: System.currentTimeMillis()
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
                            viewModel.updateDate(selectedDate!!)
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

    // Settings Bottom Sheet
    if (uiState.showSettingsSheet) {
        SettingsBottomSheet(
            selectedProvider = uiState.selectedProvider,
            metricsSummary = metricsSummary,
            onProviderSelected = { viewModel.setVoiceProvider(it) },
            onDismiss = { viewModel.showSettings(false) }
        )
    }

    // Time Picker Bottom Sheet
    if (uiState.showTimePickerSheet) {
        TimePickerBottomSheet(
            initialTime = uiState.departureTime,
            onTimeSelected = { viewModel.updateDepartureTime(it) },
            onDismiss = { viewModel.showTimePicker(false) }
        )
    }

    // Station Picker Dialog
    uiState.showStationPicker?.let { target ->
        StationPickerDialog(
            title = if (target == StationPickerTarget.ORIGIN) "Select Origin" else "Select Destination",
            currentStation = if (target == StationPickerTarget.ORIGIN) uiState.origin else uiState.destination,
            onStationSelected = { station ->
                if (target == StationPickerTarget.ORIGIN) {
                    viewModel.updateOrigin(station)
                } else {
                    viewModel.updateDestination(station)
                }
            },
            onDismiss = { viewModel.showStationPicker(null) }
        )
    }
}

@Composable
fun MicButton(
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.1f else 1f,
        animationSpec = tween(300),
        label = "mic_scale"
    )

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing ring when listening
        AnimatedVisibility(
            visible = isListening,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
            )
        }

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(96.dp)
                .scale(scale),
            shape = CircleShape,
            containerColor = if (isListening) 
                MaterialTheme.colorScheme.error 
            else 
                MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = if (isListening) "Stop listening" else "Start voice input",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun InputFieldsSection(
    origin: String?,
    destination: String?,
    departureTime: String?,
    parsingField: ParsingField?,
    isListening: Boolean,
    onOriginClick: () -> Unit,
    onDestinationClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // From
        AnimatedInputChip(
            label = "From",
            value = origin ?: "Tap or speak",
            isParsing = isListening && parsingField == ParsingField.ORIGIN,
            isConfirmed = origin != null,
            onClick = onOriginClick,
            icon = Icons.Default.Train
        )

        // To
        AnimatedInputChip(
            label = "To",
            value = destination ?: "Tap or speak",
            isParsing = isListening && parsingField == ParsingField.DESTINATION,
            isConfirmed = destination != null,
            onClick = onDestinationClick,
            icon = Icons.Default.Train
        )

        // When
        AnimatedInputChip(
            label = "When",
            value = departureTime ?: "Tap or speak",
            isParsing = isListening && parsingField == ParsingField.TIME,
            isConfirmed = departureTime != null,
            onClick = onTimeClick,
            icon = Icons.Default.AccessTime
        )
    }
}

@Composable
fun StopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1.05f,
        animationSpec = tween(500),
        label = "stop_scale"
    )

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pulsing ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                )
        )

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(96.dp)
                .scale(scale),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.error,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop search",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onError
            )
        }
    }
}

@Composable
fun DatePickerSection(
    selectedDate: LocalDate?,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Toggle button
        Surface(
            onClick = onExpandToggle,
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (selectedDate != null) {
                            "Travel date: ${selectedDate.format(dateFormatter)}"
                        } else {
                            "Select travel date (optional)"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }
        
        // Expanded content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick date buttons
                    listOf("Today", "Tomorrow", "Pick date").forEach { label ->
                        FilledTonalButton(
                            onClick = {
                                when (label) {
                                    "Today" -> { /* Set today */ }
                                    "Tomorrow" -> { /* Set tomorrow */ }
                                    "Pick date" -> onDateClick()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                softWrap = false
                            )
                        }
                    }
                }
                
                if (selectedDate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { /* Clear date */ }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear date")
                    }
                }
            }
        }
    }
}
