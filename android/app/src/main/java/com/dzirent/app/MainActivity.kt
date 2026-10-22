package com.dzirent.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.ui.*
import com.dzirent.app.ui.theme.DzirentTheme

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val profile: Profile) : AuthState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DzirentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DzirentApp()
                }
            }
        }
    }
}

@Composable
fun DzirentApp() {
    val repository = remember { DzirentRepository() }
    var authState by remember { mutableStateOf<AuthState>(AuthState.Loading) }

    fun checkSession() {
        try {
            val session = SupabaseConfig.auth.currentSessionOrNull()
            if (session != null) {
                val userId = session.user?.id ?: ""
                val role = session.user?.userMetadata?.get("role")?.toString() ?: "both"
                val displayName = session.user?.userMetadata?.get("display_name")?.toString() ?: "User"
                val profile = Profile(
                    id = userId,
                    displayName = displayName,
                    role = role,
                    city = "Nearby",
                    approxLat = 0.0,
                    approxLng = 0.0,
                    adultConfirmed = true
                )
                authState = AuthState.Authenticated(profile)
            } else {
                authState = AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Log.e("Dzirent", "Supabase error checking session: ${e.javaClass.simpleName} - ${e.message}")
            authState = AuthState.Unauthenticated
        }
    }

    LaunchedEffect(Unit) {
        checkSession()
    }

    when (val state = authState) {
        is AuthState.Loading -> {
            LoadingView(message = "Checking session...")
        }
        is AuthState.Unauthenticated -> {
            AuthNavHost(
                repository = repository,
                onLoginSuccess = { profile -> authState = AuthState.Authenticated(profile) }
            )
        }
        is AuthState.Authenticated -> {
            MainAppScaffold(
                repository = repository,
                userProfile = state.profile,
                onSignOut = { authState = AuthState.Unauthenticated }
            )
        }
    }
}

@Composable
fun AuthNavHost(
    repository: DzirentRepository,
    onLoginSuccess: (Profile) -> Unit
) {
    val authNavController = rememberNavController()

    NavHost(
        navController = authNavController,
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(
                onNavigateToLogin = { authNavController.navigate("login") },
                onNavigateToSignUp = { authNavController.navigate("signup") }
            )
        }
        composable("login") {
            LoginScreen(
                repository = repository,
                onLoginSuccess = onLoginSuccess,
                onNavigateToSignUp = { authNavController.navigate("signup") },
                onNavigateToWelcome = { authNavController.popBackStack("welcome", inclusive = false) }
            )
        }
        composable("signup") {
            SignUpScreen(
                repository = repository,
                onSignUpSuccess = onLoginSuccess,
                onNavigateToLogin = { authNavController.navigate("login") },
                onNavigateToWelcome = { authNavController.popBackStack("welcome", inclusive = false) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    repository: DzirentRepository,
    userProfile: Profile,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    var currentTab by remember { mutableStateOf("browse") }
    val userRole = userProfile.role

    Scaffold(
        bottomBar = {
            NavigationBar {
                // 1. Browse (Default)
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Browse") },
                    label = { Text("Browse") },
                    selected = currentTab == "browse",
                    onClick = {
                        currentTab = "browse"
                        navController.navigate("browse") {
                            popUpTo("browse") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                // 2. Inbox
                NavigationBarItem(
                    icon = { Icon(Icons.Default.MailOutline, contentDescription = "Inbox") },
                    label = { Text("Inbox") },
                    selected = currentTab == "inbox",
                    onClick = {
                        currentTab = "inbox"
                        navController.navigate("inbox") {
                            popUpTo("browse") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                // 3. List (only if lender or both)
                if (userRole == "lender" || userRole == "both") {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.AddCircleOutline, contentDescription = "List") },
                        label = { Text("List") },
                        selected = currentTab == "list",
                        onClick = {
                            currentTab = "list"
                            navController.navigate("list") {
                                popUpTo("browse") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                // 4. My Closet
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Checkroom, contentDescription = "My Closet") },
                    label = { Text("My Closet") },
                    selected = currentTab == "closet",
                    onClick = {
                        currentTab = "closet"
                        navController.navigate("closet") {
                            popUpTo("browse") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                // 5. Profile
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = currentTab == "profile",
                    onClick = {
                        currentTab = "profile"
                        navController.navigate("profile") {
                            popUpTo("browse") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "browse",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("browse") {
                BrowseScreen(
                    repository = repository,
                    onListingClick = { listingId ->
                        navController.navigate("detail/$listingId")
                    }
                )
            }
            composable("detail/{listingId}") { backStackEntry ->
                val listingId = backStackEntry.arguments?.getString("listingId") ?: ""
                ListingDetailScreen(
                    repository = repository,
                    listingId = listingId,
                    currentUserId = userProfile.id,
                    onBack = { navController.popBackStack() },
                    onMessageLender = { _, _ ->
                        navController.navigate("inbox")
                    }
                )
            }
            composable("inbox") {
                InboxScreen(
                    repository = repository,
                    currentUserId = userProfile.id,
                    onConversationClick = { conv ->
                        navController.navigate("chat/${conv.id}")
                    }
                )
            }
            composable("chat/{conversationId}") { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                ChatScreen(
                    repository = repository,
                    conversationId = conversationId,
                    currentUserId = userProfile.id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("list") {
                CreateListingScreen(
                    repository = repository,
                    currentUserId = userProfile.id,
                    onListingCreated = {
                        currentTab = "closet"
                        navController.navigate("closet")
                    }
                )
            }
            composable("closet") {
                MyClosetScreen(
                    repository = repository,
                    currentUserId = userProfile.id
                )
            }
            composable("profile") {
                ProfileScreen(
                    repository = repository,
                    onSignOut = onSignOut
                )
            }
        }
    }
}
