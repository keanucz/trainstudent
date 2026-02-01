package com.trainstudent.ui.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

data class OnboardingUiState(
    val hasAudio: Boolean = false,
    val hasLocation: Boolean = false,
    val hasNotification: Boolean = false,
    val hasAccessibility: Boolean = false,
    val hasRailcard1625: Boolean = false,
    val canVerifyStudent: Boolean = false,
    val isComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    
    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val HAS_RAILCARD_1625 = booleanPreferencesKey("has_railcard_1625")
        val CAN_VERIFY_STUDENT = booleanPreferencesKey("can_verify_student")
    }
    
    init {
        viewModelScope.launch {
            loadSavedState()
            checkAccessibilityEnabled(context)
        }
    }
    
    private suspend fun loadSavedState() {
        val prefs = context.onboardingDataStore.data.first()
        _uiState.update { it.copy(
            hasRailcard1625 = prefs[Keys.HAS_RAILCARD_1625] ?: false,
            canVerifyStudent = prefs[Keys.CAN_VERIFY_STUDENT] ?: false,
            isComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false
        )}
    }
    
    fun updatePermissionStates(
        hasAudio: Boolean,
        hasLocation: Boolean,
        hasNotification: Boolean
    ) {
        _uiState.update { it.copy(
            hasAudio = hasAudio,
            hasLocation = hasLocation,
            hasNotification = hasNotification
        )}
    }
    
    fun checkAccessibilityEnabled(context: Context) {
        // Use the Settings.Secure method which is more reliable
        val isEnabled = com.trainstudent.accessibility.AccessibilityUtils.isAccessibilityServiceEnabled(context)
        _uiState.update { it.copy(hasAccessibility = isEnabled) }
    }
    
    fun setHasRailcard(has: Boolean) {
        _uiState.update { it.copy(hasRailcard1625 = has) }
        viewModelScope.launch {
            context.onboardingDataStore.edit { prefs ->
                prefs[Keys.HAS_RAILCARD_1625] = has
            }
        }
    }
    
    fun setCanVerifyStudent(can: Boolean) {
        _uiState.update { it.copy(canVerifyStudent = can) }
        viewModelScope.launch {
            context.onboardingDataStore.edit { prefs ->
                prefs[Keys.CAN_VERIFY_STUDENT] = can
            }
        }
    }
    
    fun completeOnboarding() {
        viewModelScope.launch {
            context.onboardingDataStore.edit { prefs ->
                prefs[Keys.ONBOARDING_COMPLETE] = true
            }
            _uiState.update { it.copy(isComplete = true) }
        }
    }
    
    companion object {
        suspend fun isOnboardingComplete(context: Context): Boolean {
            return context.onboardingDataStore.data.first()[
                booleanPreferencesKey("onboarding_complete")
            ] ?: false
        }
    }
}
