package com.travelsouvenirs.main.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.travelsouvenirs.main.auth.AuthRepository
import com.travelsouvenirs.main.auth.isGoogleSignInAvailable
import com.travelsouvenirs.main.platform.rememberAppStyle
import com.travelsouvenirs.main.theme.AppStyle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import travelsouvenirs.composeapp.generated.resources.Res
import travelsouvenirs.composeapp.generated.resources.cd_back

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(onBack: () -> Unit = {}, onSignedIn: () -> Unit = {}) {
    val vm: SignInViewModel = koinViewModel()
    val authRepository: AuthRepository = koinInject()
    val state by vm.uiState.collectAsState()
    val currentUser by authRepository.currentUser.collectAsState()

    val isPolaroid = rememberAppStyle() == AppStyle.POLAROID
    val buttonShape = if (isPolaroid) RoundedCornerShape(2.dp) else RoundedCornerShape(50.dp)
    val fieldShape  = if (isPolaroid) RoundedCornerShape(2.dp) else RoundedCornerShape(16.dp)

    LaunchedEffect(currentUser) {
        if (currentUser != null) onSignedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = if (isPolaroid) TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) else TopAppBarDefaults.topAppBarColors(),
                title = { Text(if (state.isCreateMode) "Create account" else "Sign in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                text = "Travel Souvenirs",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.isCreateMode) "Create an account to sync your collection" else "Sign in to sync your collection",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = vm::onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                shape = fieldShape,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = vm::onEmailPasswordSubmit,
                enabled = !state.isLoading,
                shape = buttonShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (state.isCreateMode) "Create account" else "Sign in")
                }
            }

            TextButton(onClick = vm::toggleMode) {
                Text(if (state.isCreateMode) "Already have an account? Sign in" else "No account? Create one")
            }

            if (isGoogleSignInAvailable) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = vm::onGoogleSignIn,
                    enabled = !state.isLoading,
                    shape = buttonShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Continue with Google")
                }
            }
        }
    }
}
