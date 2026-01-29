package com.ad.remotescreen.ui.screen

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ad.remotescreen.data.PairingCodeGenerator
import com.ad.remotescreen.ui.viewmodel.PairingViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Pairing screen for establishing connection between devices.
 * - Target: Shows pairing code and QR code
 * - Controller: Enter pairing code or scan QR
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    isController: Boolean,
    onConnected: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            onConnected(uiState.pairingCode)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (isController) "Connect to Device" else "Share Access")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
            if (isController) {
                ControllerPairingContent(
                    pairingCode = uiState.enteredCode,
                    onCodeChange = { viewModel.updateEnteredCode(it) },
                    onConnect = { viewModel.connectAsController() },
                    isConnecting = uiState.isConnecting,
                    error = uiState.error
                )
            } else {
                TargetPairingContent(
                    pairingCode = uiState.pairingCode,
                    isWaiting = uiState.isWaiting,
                    onGenerateNew = { viewModel.generateNewCode() }
                )
            }
        }
    }
    
    // Initialize as target if not controller
    LaunchedEffect(isController) {
        if (!isController) {
            viewModel.initializeAsTarget()
        }
    }
}

@Composable
private fun ControllerPairingContent(
    pairingCode: String,
    onCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
    isConnecting: Boolean,
    error: String?
) {
    Spacer(modifier = Modifier.height(32.dp))
    
    Icon(
        imageVector = Icons.Outlined.QrCodeScanner,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Enter Pairing Code",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Enter the 6-character code shown on the target device",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(32.dp))
    
    // Code input field
    OutlinedTextField(
        value = pairingCode,
        onValueChange = { 
            if (it.length <= 6) {
                onCodeChange(it.uppercase())
            }
        },
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            letterSpacing = 8.sp,
            textAlign = TextAlign.Center
        ),
        placeholder = {
            Text(
                "A B C 1 2 3",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    letterSpacing = 8.sp
                )
            )
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { if (pairingCode.length == 6) onConnect() }
        ),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        isError = error != null
    )
    
    // Error message
    error?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Connect button
    Button(
        onClick = onConnect,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = pairingCode.length == 6 && !isConnecting,
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = if (isConnecting) "Connecting..." else "Connect",
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ColumnScope.TargetPairingContent(
    pairingCode: String,
    isWaiting: Boolean,
    onGenerateNew: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "Share this code",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Text(
        text = "Ask the controller to enter this code or scan the QR code",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Pairing code display
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pairingCode.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // QR Code
    val qrBitmap = remember(pairingCode) {
        generateQRCode(pairingCode)
    }
    
    qrBitmap?.let { bitmap ->
        Card(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    // Waiting indicator
    if (isWaiting) {
        val rotation by rememberInfiniteTransition(label = "rotation").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing)
            ),
            label = "rotation"
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Waiting for controller to connect...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Spacer(modifier = Modifier.weight(1f))
    
    // Generate new code button
    TextButton(onClick = onGenerateNew) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Generate New Code")
    }
}

/**
 * Generates a QR code bitmap from the pairing code.
 */
private fun generateQRCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        
        bitmap
    } catch (e: Exception) {
        null
    }
}
