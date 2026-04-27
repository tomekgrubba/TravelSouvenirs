package com.travelsouvenirs.main.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.auth.GoogleSignInHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isCreateMode: Boolean = false,
)

class SignInViewModel(
    private val authRepository: AuthRepository,
    private val googleSignInHelper: GoogleSignInHelper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, error = null) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, error = null) }
    fun toggleMode() { _uiState.value = _uiState.value.copy(isCreateMode = !_uiState.value.isCreateMode, error = null) }

    fun onEmailPasswordSubmit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password are required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                if (state.isCreateMode) {
                    authRepository.createAccount(state.email.trim(), state.password)
                } else {
                    authRepository.signInWithEmail(state.email.trim(), state.password)
                }
                _uiState.value = SignInUiState()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Sign-in failed.")
            }
        }
    }

    fun onGoogleSignIn() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val idToken = googleSignInHelper.getIdToken()
                authRepository.signInWithGoogle(idToken)
                _uiState.value = SignInUiState()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Google Sign-In failed.")
            }
        }
    }
}
