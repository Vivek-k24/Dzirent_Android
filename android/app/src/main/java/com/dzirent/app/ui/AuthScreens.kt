package com.dzirent.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.repository.DzirentRepository
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToSignUp: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Checkroom,
                contentDescription = "Dzirent Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Dzirent",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Rent outfits from closets near you",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onNavigateToLogin,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Log In")
            }
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onNavigateToSignUp,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Sign Up")
            }
        }
    }
}

@Composable
fun LoginScreen(
    repository: DzirentRepository,
    onLoginSuccess: (Profile) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToWelcome: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resetSentTo by remember { mutableStateOf<String?>(null) }
    var isSendingReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (isLoading) {
        LoadingView(message = "Signing in...")
        return
    }

    resetSentTo?.let { sentTo ->
        AlertDialog(
            onDismissRequest = { resetSentTo = null },
            icon = { Icon(Icons.Default.Mail, contentDescription = null) },
            title = { Text("Check your email") },
            text = { Text("If an account exists for $sentTo, we sent a link to reset your password.") },
            confirmButton = { TextButton(onClick = { resetSentTo = null }) { Text("OK") } }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Log in to browse or list clothing nearby",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                errorMessage = null
                                isLoading = false
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(
                onClick = {
                    val trimmedEmail = email.trim()
                    if (trimmedEmail.isBlank()) {
                        errorMessage = "Enter your email above, then tap Forgot password."
                        return@TextButton
                    }
                    scope.launch {
                        isSendingReset = true
                        errorMessage = null
                        try {
                            repository.resetPassword(trimmedEmail)
                            resetSentTo = trimmedEmail
                        } catch (e: Exception) {
                            errorMessage = e.userMessage("Couldn't send the reset email. Please try again.")
                        }
                        isSendingReset = false
                    }
                },
                enabled = !isSendingReset,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (isSendingReset) "Sending..." else "Forgot password?")
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter both email and password."
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            repository.signIn(email.trim(), password.trim())
                            val profile = repository.getCurrentProfile()
                            isLoading = false
                            if (profile != null) {
                                onLoginSuccess(profile)
                            } else {
                                errorMessage = "Profile not found. Please try again."
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = e.userMessage("Failed to log in. Please check your credentials.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Log In")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Don't have an account? ")
                TextButton(onClick = onNavigateToSignUp) {
                    Text("Sign Up")
                }
            }
            TextButton(onClick = onNavigateToWelcome) {
                Text("Back to Welcome")
            }
        }
    }
}

@Composable
fun SignUpScreen(
    repository: DzirentRepository,
    onSignUpSuccess: (Profile) -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToWelcome: () -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("both") } // 'renter', 'lender', 'both'
    var adultConfirmed by remember { mutableStateOf(false) }
    var policyAccepted by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Set when the account is created but Supabase needs the email confirmed before login.
    var confirmationSentTo by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (isLoading) {
        LoadingView(message = "Creating account...")
        return
    }

    val sentTo = confirmationSentTo
    if (sentTo != null) {
        EmailConfirmationView(email = sentTo, onGoToLogin = onNavigateToLogin)
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Create Account",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Join Dzirent to rent or share clothes nearby",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                errorMessage = null
                                isLoading = false
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City / Area") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "I want to:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedRole == "renter",
                        onClick = { selectedRole = "renter" }
                    )
                    Text("Rent")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedRole == "lender",
                        onClick = { selectedRole = "lender" }
                    )
                    Text("Lend")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedRole == "both",
                        onClick = { selectedRole = "both" }
                    )
                    Text("Both")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = adultConfirmed,
                    onCheckedChange = { adultConfirmed = it }
                )
                Text(
                    text = "I confirm I am 18 years of age or older",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = policyAccepted,
                    onCheckedChange = { policyAccepted = it }
                )
                Text(
                    text = DzirentPolicy.SIGN_UP_CONSENT,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(
                onClick = { showPolicy = true },
                modifier = Modifier.align(Alignment.Start).padding(start = 36.dp)
            ) {
                Text("Read how Dzirent works")
            }
            if (showPolicy) {
                HowDzirentWorksDialog(onDismiss = { showPolicy = false })
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (displayName.isBlank() || email.isBlank() || password.isBlank()) {
                        errorMessage = "Please fill in all required fields."
                        return@Button
                    }
                    if (password.trim().length < 6) {
                        errorMessage = "Password must be at least 6 characters."
                        return@Button
                    }
                    if (!adultConfirmed) {
                        errorMessage = "You must confirm you are 18+ to proceed."
                        return@Button
                    }
                    if (!policyAccepted) {
                        errorMessage = "Please confirm you understand Dzirent only connects people."
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val trimmedEmail = email.trim()
                        try {
                            val profile = repository.signUp(
                                emailStr = trimmedEmail,
                                passwordStr = password.trim(),
                                displayName = displayName.trim(),
                                role = selectedRole,
                                adultConfirmed = true,
                                city = if (city.isBlank()) "Nearby" else city.trim(),
                                lat = 0.0,
                                lng = 0.0
                            )
                            isLoading = false
                            if (profile != null) {
                                // Email confirmation is off: the user is already logged in.
                                onSignUpSuccess(profile)
                            } else {
                                // Email confirmation is on: show the "check your email" screen.
                                confirmationSentTo = trimmedEmail
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = e.userMessage("Failed to sign up. Please try again.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Sign Up")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Already have an account? ")
                TextButton(onClick = onNavigateToLogin) {
                    Text("Log In")
                }
            }
            TextButton(onClick = onNavigateToWelcome) {
                Text("Back to Welcome")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun EmailConfirmationView(
    email: String,
    onGoToLogin: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mail,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Check your email",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "We sent a confirmation link to $email. Open it, then come back and log in.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onGoToLogin,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Go to Log In")
            }
        }
    }
}
