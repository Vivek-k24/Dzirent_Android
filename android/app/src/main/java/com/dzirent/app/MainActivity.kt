package com.dzirent.app

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.model.Roles
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.ui.*
import com.dzirent.app.ui.theme.DzirentTheme
import kotlinx.coroutines.launch

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

    LaunchedEffect(Unit) {
        authState = try {
            // Supabase restores a saved session in the background; wait for it before deciding.
            SupabaseConfig.auth.awaitInitialization()
            if (SupabaseConfig.auth.currentSessionOrNull() == null) {
                AuthState.Unauthenticated
            } else {
                // Load the real profile so role and city come from the database, not metadata.
                val profile = repository.getCurrentProfile()
                if (profile != null) AuthState.Authenticated(profile) else AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Log.e("Dzirent", "Session check failed: ${e.javaClass.simpleName}")
            AuthState.Unauthenticated
        }
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

/** A bottom-bar destination. */
private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TabBrowse = TabItem("browse", "Browse", Icons.Default.Search)
private val TabInbox = TabItem("inbox", "Inbox", Icons.Default.MailOutline)
private val TabList = TabItem("list", "List", Icons.Default.AddCircleOutline)
private val TabCloset = TabItem("closet", "My Closet", Icons.Default.Checkroom)
private val TabRentals = TabItem("rentals", "My Rentals", Icons.Default.ShoppingBag)
private val TabProfile = TabItem("profile", "Profile", Icons.Default.Person)

private fun tabsFor(role: String): List<TabItem> = when (role) {
    Roles.LENDER, Roles.BOTH -> listOf(TabBrowse, TabInbox, TabList, TabCloset, TabProfile)
    else -> listOf(TabBrowse, TabInbox, TabRentals, TabProfile)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    repository: DzirentRepository,
    userProfile: Profile,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Profile lives here so a role change on the Profile screen updates the bottom bar at once.
    var profile by remember { mutableStateOf(userProfile) }
    var currentTab by rememberSaveable { mutableStateOf(TabBrowse.route) }
    val tabs = tabsFor(profile.role)
    var showLocationSetup by remember { mutableStateOf(false) }
    val locationPrompts = remember { LocationPromptStore(context) }

    // Ask once, right after login, if the profile has no location yet.
    LaunchedEffect(profile.id) {
        if (!profile.hasLocation && !locationPrompts.wasAsked(profile.id)) {
            locationPrompts.markAsked(profile.id)
            showLocationSetup = true
        }
    }

    if (showLocationSetup) {
        LocationSetupDialog(
            repository = repository,
            profile = profile,
            onDismiss = { showLocationSetup = false },
            onSaved = { updated ->
                profile = updated
                showLocationSetup = false
            }
        )
    }

    fun openTab(route: String) {
        currentTab = route
        navController.navigate(route) {
            popUpTo(TabBrowse.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // If a role change removed the tab we're on, fall back to Browse.
    LaunchedEffect(profile.role) {
        if (tabs.none { it.route == currentTab }) {
            currentTab = TabBrowse.route
            navController.navigate(TabBrowse.route) {
                popUpTo(TabBrowse.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, maxLines = 1) },
                        selected = currentTab == tab.route,
                        onClick = { openTab(tab.route) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TabBrowse.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("browse") {
                BrowseScreen(
                    repository = repository,
                    profile = profile,
                    onSetLocation = { showLocationSetup = true },
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
                    currentUserId = profile.id,
                    onBack = { navController.popBackStack() },
                    onMessageLender = { targetListingId, lenderId ->
                        // Create the conversation (or reuse the existing one), then open it.
                        scope.launch {
                            try {
                                val conversation = repository.getOrCreateConversation(
                                    listingId = targetListingId,
                                    renterId = profile.id,
                                    lenderId = lenderId
                                )
                                navController.navigate("chat/${conversation.id}")
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    e.userMessage("Couldn't open the chat. Please try again."),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
            composable("inbox") {
                InboxScreen(
                    repository = repository,
                    currentUserId = profile.id,
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
                    currentUserId = profile.id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("list") {
                CreateListingScreen(
                    repository = repository,
                    profile = profile,
                    onSetLocation = { showLocationSetup = true },
                    onListingCreated = { openTab(TabCloset.route) }
                )
            }
            composable("closet") {
                MyClosetScreen(
                    repository = repository,
                    currentUserId = profile.id,
                    showRentalsTab = profile.role == Roles.BOTH,
                    onOpenChat = { conversationId -> navController.navigate("chat/$conversationId") }
                )
            }
            composable("rentals") {
                MyRentalsScreen(
                    repository = repository,
                    currentUserId = profile.id,
                    onOpenChat = { conversationId -> navController.navigate("chat/$conversationId") }
                )
            }
            composable("profile") {
                ProfileScreen(
                    repository = repository,
                    profile = profile,
                    onProfileUpdated = { updated -> profile = updated },
                    onSetLocation = { showLocationSetup = true },
                    onOpenPolicy = { navController.navigate("policy") },
                    onSignOut = onSignOut
                )
            }
            composable("policy") {
                HowDzirentWorksScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
