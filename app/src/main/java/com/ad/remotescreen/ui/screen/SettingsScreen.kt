package com.ad.remotescreen.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ad.remotescreen.service.RemoteAccessibilityService
import com.ad.remotescreen.ui.viewmodel.OnboardingViewModel

/**
 * Settings screen for app configuration and permission management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isAccessibilityEnabled by RemoteAccessibilityService.isServiceEnabled.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
        ) {
            // Permissions Section
            SettingsSection(title = "Permissions") {
                SettingsItem(
                    icon = Icons.Outlined.TouchApp,
                    title = "Accessibility Service",
                    subtitle = if (isAccessibilityEnabled) 
                        "Enabled - Remote gestures can be performed" 
                    else 
                        "Disabled - Enable to allow remote control",
                    trailing = {
                        if (isAccessibilityEnabled) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) {
                                Text("Enable")
                            }
                        }
                    }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "Manage notification settings",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            
            // Security Section
            SettingsSection(title = "Security") {
                var requireApproval by remember { mutableStateOf(true) }
                
                SettingsItem(
                    icon = Icons.Outlined.Lock,
                    title = "Require Connection Approval",
                    subtitle = "Ask for confirmation before allowing remote access",
                    trailing = {
                        Switch(
                            checked = requireApproval,
                            onCheckedChange = { requireApproval = it }
                        )
                    }
                )
                
                var showActivityLog by remember { mutableStateOf(true) }
                
                SettingsItem(
                    icon = Icons.Outlined.History,
                    title = "Show Activity Log",
                    subtitle = "Display all remote actions in the log",
                    trailing = {
                        Switch(
                            checked = showActivityLog,
                            onCheckedChange = { showActivityLog = it }
                        )
                    }
                )
            }
            
            // Connection Section
            SettingsSection(title = "Connection") {
                SettingsItem(
                    icon = Icons.Outlined.Speed,
                    title = "Stream Quality",
                    subtitle = "High quality (more data usage)",
                    onClick = { /* Show quality picker */ }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.Wifi,
                    title = "Wi-Fi Only",
                    subtitle = "Only connect when on Wi-Fi",
                    trailing = {
                        var wifiOnly by remember { mutableStateOf(true) }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { wifiOnly = it }
                        )
                    }
                )
            }
            
            // About Section
            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.Policy,
                    title = "Privacy Policy",
                    subtitle = "Learn how we protect your data",
                    onClick = { /* Open privacy policy */ }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.Description,
                    title = "Terms of Service",
                    subtitle = "Read our terms and conditions",
                    onClick = { /* Open ToS */ }
                )
                
                SettingsItem(
                    icon = Icons.Outlined.Help,
                    title = "Help & Support",
                    subtitle = "Get help with using the app",
                    onClick = { /* Open help */ }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null && trailing == null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
