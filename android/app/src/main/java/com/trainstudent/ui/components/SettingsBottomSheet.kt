package com.trainstudent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.trainstudent.data.ProviderMetricsSummary
import com.trainstudent.voice.CactusModelDownloader
import com.trainstudent.voice.CactusModelSize
import com.trainstudent.voice.DownloadStatus
import com.trainstudent.voice.VoiceProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    selectedProvider: VoiceProvider,
    metricsSummary: List<ProviderMetricsSummary>,
    onProviderSelected: (VoiceProvider) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val modelDownloader = viewModel.modelDownloader
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val downloadProgress by modelDownloader.downloadProgress.collectAsState()
    val modelsPath by modelDownloader.getModelsDirectoryPath().collectAsState(initial = "")

    // Note: Cactus SDK manages model storage internally, no custom directory needed

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
                text = "Voice Provider Settings",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Provider selection
            Column(Modifier.selectableGroup()) {
                VoiceProvider.values().forEach { provider ->
                    val progress = when (provider) {
                        VoiceProvider.CACTUS_7B -> downloadProgress[CactusModelSize.SEVEN_B]
                        VoiceProvider.CACTUS_13B -> downloadProgress[CactusModelSize.THIRTEEN_B]
                        else -> null
                    }
                    
                    val metrics = metricsSummary.find { it.provider == provider.name }

                    ProviderOption(
                        provider = provider,
                        isSelected = selectedProvider == provider,
                        downloadProgress = progress,
                        metrics = metrics,
                        onSelect = { onProviderSelected(provider) },
                        onDownload = {
                            scope.launch {
                                val modelSize = when (provider) {
                                    VoiceProvider.CACTUS_7B -> CactusModelSize.SEVEN_B
                                    VoiceProvider.CACTUS_13B -> CactusModelSize.THIRTEEN_B
                                    else -> return@launch
                                }
                                modelDownloader.startDownload(modelSize)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Download location
            Text(
                text = "Model Storage Location",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (modelsPath.length > 40) "..." + modelsPath.takeLast(37) else modelsPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // Note: Cactus SDK manages storage internally, no custom directory option
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderOption(
    provider: VoiceProvider,
    isSelected: Boolean,
    downloadProgress: com.trainstudent.voice.DownloadProgress?,
    metrics: ProviderMetricsSummary?,
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    val isDownloading = downloadProgress?.status == DownloadStatus.RUNNING
    val isDownloaded = downloadProgress?.status == DownloadStatus.COMPLETED ||
            (provider == VoiceProvider.SPEECH_RECOGNIZER)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = { if (isDownloaded) onSelect() },
                role = Role.RadioButton
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        enabled = isDownloaded
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        provider.modelSize?.let { size ->
                            Text(
                                text = size,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                when {
                    provider == VoiceProvider.SPEECH_RECOGNIZER -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Available",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    isDownloading -> {
                        // Show progress
                    }
                    isDownloaded -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.size(height = 32.dp, width = 100.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Download progress
            if (isDownloading && downloadProgress != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = downloadProgress.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${(downloadProgress.progress * 100).toInt()}% - ${formatBytes(downloadProgress.bytesDownloaded)} / ${formatBytes(downloadProgress.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Metrics summary
            metrics?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricChip("${it.avgLatencyMs.toInt()}ms", "Latency")
                    MetricChip("${(it.avgAccuracy * 100).toInt()}%", "Accuracy")
                    MetricChip("${it.totalSessions}", "Sessions")
                }
            }
        }
    }
}

@Composable
private fun MetricChip(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}

// Helper ViewModel for accessing the downloader
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val modelDownloader: CactusModelDownloader
) : ViewModel()
