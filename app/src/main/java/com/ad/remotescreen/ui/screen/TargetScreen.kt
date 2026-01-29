package com.ad.remotescreen.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ad.remotescreen.data.model.ActivityType
import com.ad.remotescreen.ui.theme.EmergencyStopRed
import com.ad.remotescreen.ui.theme.SessionActiveGreen
import com.ad.remotescreen.ui.theme.SessionPausedOrange
import com.ad.remotescreen.ui.viewmodel.TargetViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Target screen showing session status and the prominent emergency stop button.
 * This is what the user being controlled sees.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetScreen(
    onSessionEnded: () -> Unit,
    viewModel: TargetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showStopConfirmation by remember { mutableStateOf(false) }
    
    LaunchedEffect(uiState.sessionEnded) {
        if (uiState.sessionEnded) {
            onSessionEnded()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Session Active") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (uiState.isConnected) 
                        SessionActiveGreen.copy(alpha = 0.2f) 
                    else 
                        MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status indicator
            StatusCard(
                isConnected = uiState.isConnected,
                isPaused = uiState.isPaused,
                partnerName = uiState.partnerName,
                sessionDuration = uiState.sessionDurationSeconds
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // EMERGENCY STOP BUTTON - Very prominent
            EmergencyStopButton(
                onClick = { showStopConfirmation = true },
                enabled = uiState.isConnected
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pause/Resume button
            if (uiState.isConnected) {
                OutlinedButton(
                    onClick = {
                        if (uiState.isPaused) {
                            viewModel.resumeSession()
                        } else {
                            viewModel.pauseSession()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (uiState.isPaused) 
                            Icons.Default.PlayArrow 
                        else 
                            Icons.Default.Pause,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isPaused) "Resume Control" else "Pause Control")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Activity log
            Text(
                text = "Activity Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.activityLog.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No activity yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.activityLog.reversed()) { entry ->
                            ActivityLogItem(
                                type = entry.type,
                                description = entry.description,
                                timestamp = entry.timestamp
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Stop confirmation dialog
    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = EmergencyStopRed
                )
            },
            title = { Text("Stop Remote Control?") },
            text = { 
                Text("This will immediately end the remote session. The controller will no longer be able to view or control your device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmation = false
                        viewModel.emergencyStop()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyStopRed
                    )
                ) {
                    Text("STOP NOW")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    isConnected: Boolean,
    isPaused: Boolean,
    partnerName: String,
    sessionDuration: Long
) {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !isConnected -> MaterialTheme.colorScheme.surfaceVariant
            isPaused -> SessionPausedOrange.copy(alpha = 0.2f)
            else -> SessionActiveGreen.copy(alpha = 0.2f)
        },
        label = "backgroundColor"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !isConnected -> Color.Gray
                            isPaused -> SessionPausedOrange
                            else -> SessionActiveGreen
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !isConnected -> Icons.Outlined.LinkOff
                        isPaused -> Icons.Default.Pause
                        else -> Icons.Outlined.ScreenShare
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = when {
                    !isConnected -> "Waiting for connection..."
                    isPaused -> "Control Paused"
                    else -> "Being Controlled"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "by $partnerName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatDuration(sessionDuration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmergencyStopButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(if (enabled) scale else 1f),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = EmergencyStopRed,
            disabledContainerColor = Color.Gray
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.StopCircle,
            contentDescription = null,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "EMERGENCY STOP",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActivityLogItem(
    type: ActivityType,
    description: String,
    timestamp: Long
) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (type) {
                ActivityType.GESTURE_RECEIVED -> Icons.Outlined.TouchApp
                ActivityType.SESSION_STARTED -> Icons.Outlined.PlayCircle
                ActivityType.SESSION_ENDED -> Icons.Outlined.StopCircle
                ActivityType.SESSION_PAUSED -> Icons.Default.Pause
                ActivityType.SESSION_RESUMED -> Icons.Default.PlayArrow
                ActivityType.EMERGENCY_STOP -> Icons.Default.Warning
                else -> Icons.Outlined.Info
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = when (type) {
                ActivityType.EMERGENCY_STOP -> EmergencyStopRed
                ActivityType.SESSION_STARTED, ActivityType.SESSION_RESUMED -> SessionActiveGreen
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = timeFormat.format(Date(timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%02d:%02d".format(minutes, secs)
    }
}
