package com.ad.remotescreen.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ad.remotescreen.ui.theme.PrimaryBlue
import com.ad.remotescreen.ui.theme.SecondaryTeal
import com.ad.remotescreen.ui.viewmodel.OnboardingViewModel

/**
 * Onboarding screen explaining permissions and getting user consent.
 * This is MANDATORY for Google Play compliance.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var currentStep by remember { mutableIntStateOf(0) }
    
    val steps = listOf(
        OnboardingStep(
            icon = Icons.Outlined.Security,
            title = "Welcome to Remote Assist",
            description = "A secure way to provide remote support and assistance. " +
                    "This app allows you to help friends, family, or customers by viewing and controlling their device remotely.",
            action = null
        ),
        OnboardingStep(
            icon = Icons.Outlined.TouchApp,
            title = "Accessibility Service",
            description = "To perform remote gestures (tap, swipe, type), this app needs the Accessibility Service permission. " +
                    "This allows the app to execute touch commands from the controller device.\n\n" +
                    "⚠️ This permission is ONLY used during active support sessions and can be disabled at any time.",
            action = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            actionLabel = "Open Accessibility Settings",
            isComplete = uiState.isAccessibilityEnabled
        ),
        OnboardingStep(
            icon = Icons.Outlined.ScreenShare,
            title = "Screen Sharing",
            description = "To share your screen with the controller, this app needs permission to capture your display.\n\n" +
                    "⚠️ You will see a system dialog asking for permission when you start a session. " +
                    "A notification will always show when screen sharing is active.",
            action = null
        ),
        OnboardingStep(
            icon = Icons.Outlined.Notifications,
            title = "Notifications",
            description = "A persistent notification will show whenever remote control is active. " +
                    "This is for your safety and transparency - you'll always know when your device is being controlled.\n\n" +
                    "The notification includes a quick STOP button to end the session immediately.",
            action = null
        ),
        OnboardingStep(
            icon = Icons.Outlined.Lock,
            title = "Privacy & Security",
            description = "• All connections are encrypted\n" +
                    "• Sessions require manual approval\n" +
                    "• Activity logs are visible to you\n" +
                    "• Emergency stop available at all times\n" +
                    "• No data stored without your permission\n\n" +
                    "You are always in control.",
            action = null
        )
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / steps.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Step content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val step = steps[currentStep]
                
                // Icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PrimaryBlue, SecondaryTeal)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Title
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Description
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Action button (if applicable)
                step.action?.let { action ->
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (step.isComplete) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permission Granted",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = action,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(step.actionLabel ?: "Enable")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                AnimatedVisibility(
                    visible = currentStep > 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(onClick = { currentStep-- }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Back")
                    }
                }
                
                if (currentStep == 0) {
                    Spacer(modifier = Modifier.weight(1f))
                }
                
                // Next/Finish button
                Button(
                    onClick = {
                        if (currentStep < steps.size - 1) {
                            currentStep++
                        } else {
                            viewModel.markOnboardingComplete()
                            onComplete()
                        }
                    },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        if (currentStep < steps.size - 1) "Next" else "Get Started",
                        fontWeight = FontWeight.Medium
                    )
                    if (currentStep < steps.size - 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Data class representing an onboarding step.
 */
private data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val action: (() -> Unit)?,
    val actionLabel: String? = null,
    val isComplete: Boolean = false
)
