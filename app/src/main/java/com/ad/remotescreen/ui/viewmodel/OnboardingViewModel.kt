package com.ad.remotescreen.ui.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ad.remotescreen.service.RemoteAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isAccessibilityEnabled: Boolean = false,
    val isOnboardingComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private val accessibilityManager = 
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    
    init {
        // Poll accessibility status
        viewModelScope.launch {
            while (true) {
                checkAccessibilityEnabled()
                delay(1000) // Check every second
            }
        }
    }
    
    private fun checkAccessibilityEnabled() {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        
        val isEnabled = enabledServices.any { info ->
            info.resolveInfo.serviceInfo.packageName == context.packageName &&
            info.resolveInfo.serviceInfo.name == RemoteAccessibilityService::class.java.name
        }
        
        _uiState.update { it.copy(isAccessibilityEnabled = isEnabled) }
    }
    
    fun markOnboardingComplete() {
        _uiState.update { it.copy(isOnboardingComplete = true) }
        // In a real app, save this preference to DataStore
    }
}
