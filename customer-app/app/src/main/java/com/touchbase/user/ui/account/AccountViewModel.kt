package com.touchbase.user.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for managing user account operations including password changes.
 */
class AccountViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()
    
    fun updateCurrentPassword(password: String) {
        _uiState.update { it.copy(currentPassword = password, error = null) }
    }
    
    fun updateNewPassword(password: String) {
        _uiState.update { it.copy(newPassword = password, error = null) }
    }
    
    fun updateConfirmPassword(password: String) {
        _uiState.update { it.copy(confirmPassword = password, error = null) }
    }
    
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
    
    fun resetPasswordChanged() {
        _uiState.update { it.copy(passwordChanged = false) }
    }
    
    /**
     * Simulates password change functionality.
     * In a real implementation, this would call the repository to change the password.
     */
    fun changePassword(accountId: String, phoneNumber: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Validate inputs
            val currentPassword = _uiState.value.currentPassword
            val newPassword = _uiState.value.newPassword
            val confirmPassword = _uiState.value.confirmPassword
            
            when {
                currentPassword.isBlank() -> {
                    _uiState.update { it.copy(isLoading = false, error = "Current password is required") }
                    return@launch
                }
                newPassword.isBlank() -> {
                    _uiState.update { it.copy(isLoading = false, error = "New password is required") }
                    return@launch
                }
                newPassword.length < 4 -> {
                    _uiState.update { it.copy(isLoading = false, error = "Password must be at least 4 characters") }
                    return@launch
                }
                newPassword != confirmPassword -> {
                    _uiState.update { it.copy(isLoading = false, error = "Passwords do not match") }
                    return@launch
                }
            }
            
            // Simulate API call delay
            try {
                // In production, this would call:
                // repository.changePassword(accountId, currentPassword, newPassword)
                // For now, we'll simulate success
                _uiState.update { it.copy(isLoading = false, passwordChanged = true, message = "Password changed successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to change password") }
            }
        }
    }
}

/**
 * UI state for the account screen.
 */
data class AccountUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val passwordChanged: Boolean = false
)
