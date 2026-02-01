package com.trainstudent.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trainstudent.accessibility.NorthernAutomation
import com.trainstudent.accessibility.TrainStudentAccessibilityService
import com.trainstudent.model.BookingAction
import com.trainstudent.model.BookingPlan
import com.trainstudent.model.RiskLevel
import kotlinx.coroutines.launch

@Composable
fun BookingPlanCard(
    plan: BookingPlan,
    isFirst: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isAccessibilityEnabled by TrainStudentAccessibilityService.isServiceEnabled.collectAsState()
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showNorthernNotInstalledDialog by remember { mutableStateOf(false) }
    
    // Northern automation instance
    val northernAutomation = remember { NorthernAutomation(context) }
    val automationState by northernAutomation.state.collectAsState()
    val automationError by northernAutomation.errorMessage.collectAsState()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFirst)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFirst) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header row with badge and price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge
                if (isFirst) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "CHEAPEST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = plan.operatorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Price
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "£${String.format("%.2f", plan.netCost)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isFirst)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (plan.originalCost > plan.netCost) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "£${String.format("%.2f", plan.originalCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary line
            Text(
                text = plan.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Required proof chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                plan.requiredProof.forEach { proof ->
                    ProofChip(text = proof)
                }
            }

            // Risk level warning
            if (plan.riskLevel != RiskLevel.LOW) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = when (plan.riskLevel) {
                            RiskLevel.MEDIUM -> Color(0xFFFF9800)
                            RiskLevel.HIGH -> MaterialTheme.colorScheme.error
                            else -> Color.Transparent
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (plan.riskLevel) {
                            RiskLevel.MEDIUM -> "Usually works"
                            RiskLevel.HIGH -> "Experimental"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Get the typed action from the DTO
            val action = plan.getAction()
            
            // Book button - different appearance for Northern automation
            val isNorthernAutomation = action is BookingAction.NorthernAppAutomation
            
            Button(
                onClick = {
                    when (action) {
                        is BookingAction.DeepLink -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                            context.startActivity(intent)
                        }
                        is BookingAction.WebView -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
                            context.startActivity(intent)
                        }
                        is BookingAction.NativeFlow -> {
                            // Handle native flow (e.g., Northern code generation)
                        }
                        is BookingAction.NorthernAppAutomation -> {
                            // Check if Northern app is installed
                            if (!northernAutomation.isNorthernAppInstalled()) {
                                showNorthernNotInstalledDialog = true
                                return@Button
                            }
                            
                            // Check if accessibility service is enabled
                            if (!isAccessibilityEnabled) {
                                showAccessibilityDialog = true
                                return@Button
                            }
                            
                            // Register the pending automation with the accessibility service
                            TrainStudentAccessibilityService.setPendingNorthernAutomation(northernAutomation)
                            
                            // Start the automation
                            scope.launch {
                                val params = NorthernAutomation.NorthernSearchParams(
                                    promoCode = action.promoCode,
                                    origin = action.origin,
                                    originCrs = action.originCrs,
                                    destination = action.destination,
                                    destinationCrs = action.destinationCrs,
                                    departureTime = action.departureTime
                                )
                                northernAutomation.startAutomation(params)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isNorthernAutomation) {
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B3FA0) // Northern purple
                    )
                } else if (isFirst) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {
                if (isNorthernAutomation) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "OPEN NORTHERN APP")
                } else {
                    Text(text = "BOOK NOW")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
    
    // Accessibility Service Dialog
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("Enable Accessibility Service") },
            text = {
                Column {
                    Text("To auto-fill the promo code in the Northern app, we need accessibility permission.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "This allows TrainStudent to:",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("• Open the Northern app")
                    Text("• Enter the promo code automatically")
                    Text("• Fill in your journey details")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccessibilityDialog = false
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Northern Not Installed Dialog
    if (showNorthernNotInstalledDialog) {
        AlertDialog(
            onDismissRequest = { showNorthernNotInstalledDialog = false },
            title = { Text("Northern App Required") },
            text = {
                Text("The Northern Railway app is required for this booking. Would you like to install it from the Play Store?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNorthernNotInstalledDialog = false
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("market://details?id=org.northernrail.android")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Play Store not available, try browser
                            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://play.google.com/store/apps/details?id=org.northernrail.android")
                            }
                            context.startActivity(webIntent)
                        }
                    }
                ) {
                    Text("Install App")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNorthernNotInstalledDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Show toast for automation errors
    automationError?.let { error ->
        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ProofChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
