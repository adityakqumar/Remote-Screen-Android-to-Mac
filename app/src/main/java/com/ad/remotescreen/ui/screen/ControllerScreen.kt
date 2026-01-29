package com.ad.remotescreen.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ad.remotescreen.data.model.GestureCommand
import com.ad.remotescreen.ui.theme.SessionActiveGreen
import com.ad.remotescreen.ui.viewmodel.ControllerViewModel

/**
 * Controller screen displaying the remote device's screen stream
 * and capturing touch gestures to send to the target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerScreen(
    pairingCode: String,
    onDisconnect: () -> Unit,
    viewModel: ControllerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDisconnectDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isConnected) SessionActiveGreen else Color.Gray
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remote View")
                    }
                },
                actions = {
                    // Navigation buttons
                    IconButton(onClick = { viewModel.sendGesture(GestureCommand.Back()) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(onClick = { viewModel.sendGesture(GestureCommand.Home()) }) {
                        Icon(Icons.Outlined.Home, contentDescription = "Home")
                    }
                    IconButton(onClick = { viewModel.sendGesture(GestureCommand.Recents()) }) {
                        Icon(Icons.Default.Menu, contentDescription = "Recents")
                    }
                    IconButton(onClick = { showDisconnectDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Disconnect")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Screen viewer area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                viewModel.sendGesture(
                                    GestureCommand.Tap(
                                        x = offset.x,
                                        y = offset.y
                                    )
                                )
                            },
                            onLongPress = { offset ->
                                viewModel.sendGesture(
                                    GestureCommand.LongPress(
                                        x = offset.x,
                                        y = offset.y
                                    )
                                )
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var startX = 0f
                        var startY = 0f
                        
                        detectDragGestures(
                            onDragStart = { offset ->
                                startX = offset.x
                                startY = offset.y
                            },
                            onDragEnd = { },
                            onDrag = { change, dragAmount ->
                                viewModel.sendGesture(
                                    GestureCommand.Swipe(
                                        startX = startX,
                                        startY = startY,
                                        endX = change.position.x,
                                        endY = change.position.y,
                                        duration = 200
                                    )
                                )
                                startX = change.position.x
                                startY = change.position.y
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Placeholder for video stream
                if (!uiState.isConnected) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Connecting to remote device...",
                            color = Color.White
                        )
                    }
                } else if (uiState.isStreaming) {
                    // In a real app, WebRTC video would be rendered here
                    Text(
                        text = "Screen stream would display here\n\nTap, swipe, or long-press to control",
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }
            
            // Control bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quality indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.SignalCellular4Bar,
                            contentDescription = null,
                            tint = if (uiState.connectionQuality > 0.7f) 
                                SessionActiveGreen else Color.Yellow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${(uiState.connectionQuality * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Session time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDuration(uiState.sessionDurationSeconds),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Text input button
                    IconButton(
                        onClick = { viewModel.showTextInputDialog() }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Keyboard,
                            contentDescription = "Text Input"
                        )
                    }
                }
            }
        }
    }
    
    // Disconnect confirmation dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("End Session?") },
            text = { Text("Are you sure you want to disconnect from the remote device?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnect()
                        onDisconnect()
                    }
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Text input dialog
    if (uiState.showTextInput) {
        var text by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { viewModel.hideTextInputDialog() },
            title = { Text("Send Text") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Enter text to send...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.sendGesture(GestureCommand.TextInput(text))
                        viewModel.hideTextInputDialog()
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideTextInputDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Initialize connection
    LaunchedEffect(pairingCode) {
        viewModel.connect(pairingCode)
    }
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(minutes, secs)
}
