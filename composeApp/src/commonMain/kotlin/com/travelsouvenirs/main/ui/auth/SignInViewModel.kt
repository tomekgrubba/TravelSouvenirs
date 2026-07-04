package com.travelsouvenirs.main.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.auth.GoogleSignInHelper
import com.travelsouvenirs.main.util.ErrorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isCreateMode: Boolean = false,
)

class SignInViewModel(
    private val authRepository: AuthRepository,
    private val googleSignInHelper: GoogleSignInHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, error = null, successMessage = null) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, error = null, successMessage = null) }
    fun toggleMode() { _uiState.value = _uiState.value.copy(isCreateMode = !_uiState.value.isCreateMode, error = null, successMessage = null) }

    fun onEmailPasswordSubmit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password are required.", successMessage = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null, successMessage = null)
            try {
                if (state.isCreateMode) {
                    authRepository.createAccount(state.email.trim(), state.password)
                } else {
                    authRepository.signInWithEmail(state.email.trim(), state.password)
                }
                _uiState.value = SignInUiState()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = ErrorUtils.getFriendlyErrorMessage(e), successMessage = null)
            }
        }
    }

    fun onGoogleSignIn() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
            try {
                val idToken = googleSignInHelper.getIdToken()
                authRepository.signInWithGoogle(idToken)
                _uiState.value = SignInUiState()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = ErrorUtils.getFriendlyErrorMessage(e), successMessage = null)
            }
        }
    }

    fun onForgotPasswordClick() {
        val state = _uiState.value
        val emailVal = state.email.trim()
        if (emailVal.isBlank()) {
            _uiState.value = state.copy(error = "Please enter your email address to reset your password.", successMessage = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null, successMessage = null)
            try {
                authRepository.sendPasswordResetEmail(emailVal)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = null,
                    successMessage = "Password reset email sent. Please check your inbox."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = ErrorUtils.getFriendlyErrorMessage(e),
                    successMessage = null
                )
            }
        }
    }
}
