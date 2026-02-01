package com.trainstudent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.trainstudent.model.ParsedStation
import com.trainstudent.model.StationCandidate

// Static station list for search
private val allStations = listOf(
    StationCandidate("Sheffield", "SHF", 1f),
    StationCandidate("Manchester Piccadilly", "MAN", 1f),
    StationCandidate("Manchester Victoria", "MCV", 1f),
    StationCandidate("Manchester Oxford Road", "MCO", 1f),
    StationCandidate("Leeds", "LDS", 1f),
    StationCandidate("London Euston", "EUS", 1f),
    StationCandidate("London Kings Cross", "KGX", 1f),
    StationCandidate("London St Pancras", "STP", 1f),
    StationCandidate("London Paddington", "PAD", 1f),
    StationCandidate("London Victoria", "VIC", 1f),
    StationCandidate("London Liverpool Street", "LST", 1f),
    StationCandidate("Liverpool Lime Street", "LIV", 1f),
    StationCandidate("Birmingham New Street", "BHM", 1f),
    StationCandidate("Edinburgh Waverley", "EDB", 1f),
    StationCandidate("Glasgow Central", "GLC", 1f),
    StationCandidate("Bristol Temple Meads", "BRI", 1f),
    StationCandidate("Newcastle", "NCL", 1f),
    StationCandidate("York", "YRK", 1f),
    StationCandidate("Doncaster", "DON", 1f),
    StationCandidate("Nottingham", "NOT", 1f),
    StationCandidate("Derby", "DBY", 1f),
    StationCandidate("Leicester", "LEI", 1f),
    StationCandidate("Peterborough", "PBO", 1f),
    StationCandidate("Cambridge", "CBG", 1f),
    StationCandidate("Oxford", "OXF", 1f),
    StationCandidate("Reading", "RDG", 1f),
    StationCandidate("Southampton Central", "SOU", 1f),
    StationCandidate("Brighton", "BTN", 1f),
    StationCandidate("Coventry", "COV", 1f),
    StationCandidate("Milton Keynes Central", "MKC", 1f),
    StationCandidate("Crewe", "CRE", 1f),
    StationCandidate("Preston", "PRE", 1f),
    StationCandidate("Lancaster", "LAN", 1f),
    StationCandidate("Carlisle", "CAR", 1f),
    StationCandidate("Sunderland", "SUN", 1f),
    StationCandidate("Bradford Interchange", "BDI", 1f),
    StationCandidate("Huddersfield", "HUD", 1f),
    StationCandidate("Wakefield Westgate", "WKF", 1f),
    StationCandidate("Stockport", "SPT", 1f),
    StationCandidate("Chester", "CTR", 1f),
    StationCandidate("Cardiff Central", "CDF", 1f),
    StationCandidate("Swansea", "SWA", 1f),
    StationCandidate("Plymouth", "PLY", 1f),
    StationCandidate("Norwich", "NRW", 1f),
    StationCandidate("Ipswich", "IPS", 1f),
    StationCandidate("Gatwick Airport", "GTW", 1f),
    StationCandidate("Heathrow", "HXX", 1f),
    StationCandidate("Stansted Airport", "SSD", 1f),
    StationCandidate("Luton Airport Parkway", "LTN", 1f),
)

@Composable
fun StationPickerDialog(
    title: String,
    currentStation: ParsedStation?,
    onStationSelected: (ParsedStation) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Filter stations based on search query
    val filteredStations = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allStations.take(10)
        } else {
            val query = searchQuery.lowercase()
            allStations.filter { station ->
                station.name.lowercase().contains(query) ||
                station.crs.lowercase().contains(query)
            }.take(10)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search stations...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Alternatives from voice input
                currentStation?.alternatives?.takeIf { it.isNotEmpty() }?.let { alternatives ->
                    Text(
                        text = "Did you mean:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    alternatives.forEach { alt ->
                        StationItem(
                            station = alt,
                            isSelected = false,
                            onClick = {
                                onStationSelected(ParsedStation(
                                    name = alt.name,
                                    crs = alt.crs,
                                    confidence = 1f,
                                    alternatives = emptyList()
                                ))
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Search results
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredStations) { station ->
                        StationItem(
                            station = station,
                            isSelected = currentStation?.crs == station.crs,
                            onClick = {
                                onStationSelected(ParsedStation(
                                    name = station.name,
                                    crs = station.crs,
                                    confidence = 1f,
                                    alternatives = emptyList()
                                ))
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StationItem(
    station: StationCandidate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Train,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = station.crs,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
