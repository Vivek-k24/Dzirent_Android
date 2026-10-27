package com.dzirent.app

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.model.Roles
import com.dzirent.app.data.repository.DzirentRepository
import com.dzirent.app.ui.*
import com.dzirent.app.ui.theme.DzirentAccent
import com.dzirent.app.ui.theme.DzirentTheme
import com.dzirent.app.util.HomeRow
import com.dzirent.app.util.datesQuestion
import kotlinx.coroutines.delay
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

/** A bottom-bar destination. Labels are one short word so five tabs fit on narrow phones. */
private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val TabHome = TabItem("home", "Home", Icons.Default.Home)
private val TabInbox = TabItem("inbox", "Inbox", Icons.Default.MailOutline)
private val TabSearch = TabItem("search", "Search", Icons.Default.Search)
private val TabCloset = TabItem("closet", "Closet", Icons.Default.Checkroom)
private val TabBag = TabItem("bag", "Bag", Icons.Default.BookmarkBorder)

/** How often the Inbox dot re-checks for news while the app is open. */
private const val INBOX_REFRESH_MS = 60_000L

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
    // Profile lives here so a role change on the Profile screen updates the app at once.
    var profile by remember { mutableStateOf(userProfile) }
    var currentTab by rememberSaveable { mutableStateOf(TabHome.route) }
    var showLocationSetup by remember { mutableStateOf(false) }
    val locationPrompts = remember { LocationPromptStore(context) }
    var inboxHasNews by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Filters (set from the sidebar), dates and nearby listings, shared by Home and Search.
    val session = remember(profile.id) { BrowseSession(repository, profile.id) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showFilterSheet by remember { mutableStateOf(false) }
    val onHome = backStackEntry?.destination?.route == TabHome.route

    // Ask once, right after login, if the profile has no location yet.
    LaunchedEffect(profile.id) {
        if (!profile.hasLocation && !locationPrompts.wasAsked(profile.id)) {
            locationPrompts.markAsked(profile.id)
            showLocationSetup = true
        }
    }

    // The Inbox dot: refresh on every screen change and once a minute.
    suspend fun refreshInboxDot() {
        runCatching { repository.getInboxSnapshot(profile.id).hasNews }.onSuccess { inboxHasNews = it }
    }
    LaunchedEffect(backStackEntry?.destination?.route) { refreshInboxDot() }
    LaunchedEffect(profile.id) {
        while (true) {
            delay(INBOX_REFRESH_MS)
            refreshInboxDot()
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
            popUpTo(TabHome.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openChat(conversationId: String, draft: String? = null) =
        navController.navigate("chat/$conversationId" + (draft?.let { "?draft=${Uri.encode(it)}" } ?: ""))

    /** Opens the chat about a listing (creating it if needed), optionally with a draft message. */
    fun startChat(listingId: String, lenderId: String, draft: String? = null) {
        scope.launch {
            try {
                val conversation = repository.getOrCreateConversation(
                    listingId = listingId,
                    renterId = profile.id,
                    lenderId = lenderId
                )
                openChat(conversation.id, draft)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.userMessage("Couldn't open the chat. Please try again."),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    /** Sidebar rows that leave Home close the drawer first, so Back returns to Home. */
    fun openFromDrawer(route: String) {
        closeDrawer()
        navController.navigate(route)
    }

    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    if (showFilterSheet) {
        FilterSheet(
            current = session.filters,
            onApply = { applied ->
                session.filters = applied
                showFilterSheet = false
                closeDrawer()
                if (!onHome) openTab(TabHome.route)
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    // Swipe to open only on Home, where the avatar opens the same drawer.
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = onHome || drawerState.isOpen,
        drawerContent = {
            SidebarContent(
                profile = profile,
                selectedCategory = session.filters.category,
                onOpenProfile = { openFromDrawer("profile") },
                onOpenSettings = { openFromDrawer("settings") },
                onSelectCategory = { category ->
                    session.filters = session.filters.withCategory(category)
                    closeDrawer()
                    if (!onHome) openTab(TabHome.route)
                },
                onOpenFilter = { showFilterSheet = true }
            )
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf(TabHome, TabInbox).forEach { tab ->
                        BottomTab(tab, selected = currentTab == tab.route, dot = tab == TabInbox && inboxHasNews) {
                            openTab(tab.route)
                        }
                    }
                    SearchTabButton(onClick = { openTab(TabSearch.route) })
                    listOf(TabCloset, TabBag).forEach { tab ->
                        BottomTab(tab, selected = currentTab == tab.route, dot = false) { openTab(tab.route) }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TabHome.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("home") {
                    HomeScreen(
                        session = session,
                        profile = profile,
                        onOpenSidebar = { scope.launch { drawerState.open() } },
                        onSetLocation = { showLocationSetup = true },
                        onOpenListing = { listingId -> navController.navigate("detail/$listingId") },
                        onOpenStudio = { lenderId -> navController.navigate("studio/$lenderId") },
                        onSeeAll = { row -> navController.navigate("seeall/${row.name}") },
                        onOpenMeasurements = { navController.navigate("measurements") }
                    )
                }
                composable("seeall/{row}") { backStackEntry ->
                    val row = HomeRow.entries.firstOrNull { it.name == backStackEntry.arguments?.getString("row") } ?: HomeRow.NEW
                    SeeAllScreen(
                        session = session,
                        row = row,
                        onBack = { navController.popBackStack() },
                        onOpenListing = { listingId -> navController.navigate("detail/$listingId") }
                    )
                }
                composable("search") {
                    SearchScreen(
                        session = session,
                        profile = profile,
                        onSetLocation = { showLocationSetup = true },
                        onOpenListing = { listingId -> navController.navigate("detail/$listingId") }
                    )
                }
                composable("detail/{listingId}") { backStackEntry ->
                    val listingId = backStackEntry.arguments?.getString("listingId") ?: ""
                    ListingDetailScreen(
                        repository = repository,
                        listingId = listingId,
                        currentUserId = profile.id,
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate("edit/$id") },
                        onMessageLender = { targetListingId, lenderId -> startChat(targetListingId, lenderId) },
                        onOpenStudio = { lenderId -> navController.navigate("studio/$lenderId") }
                    )
                }
                composable("studio/{lenderId}") { backStackEntry ->
                    val lenderId = backStackEntry.arguments?.getString("lenderId") ?: ""
                    StudioScreen(
                        repository = repository,
                        lenderId = lenderId,
                        currentUserId = profile.id,
                        onBack = { navController.popBackStack() },
                        onOpenListing = { id -> navController.navigate("detail/$id") },
                        onMessageAbout = { piece ->
                            startChat(piece.id, piece.lenderId, datesQuestion(piece.title, session.dates))
                        },
                        // "From studios you saved" changes.
                        onSavedChanged = { session.invalidate() }
                    )
                }
                composable("inbox") {
                    InboxScreen(
                        repository = repository,
                        currentUserId = profile.id,
                        onConversationClick = { conversationId -> openChat(conversationId) },
                        onNewsChanged = { inboxHasNews = it }
                    )
                }
                composable(
                    "chat/{conversationId}?draft={draft}",
                    arguments = listOf(navArgument("draft") { type = NavType.StringType; nullable = true; defaultValue = null })
                ) { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
                    ChatScreen(
                        repository = repository,
                        conversationId = conversationId,
                        currentUserId = profile.id,
                        onBack = { navController.popBackStack() },
                        onOpenListing = { id -> navController.navigate("detail/$id") },
                        initialDraft = backStackEntry.arguments?.getString("draft")
                    )
                }
                composable("list") {
                    ListingEditorScreen(
                        repository = repository,
                        profile = profile,
                        listingId = null,
                        onSetLocation = { showLocationSetup = true },
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
                composable("edit/{listingId}") { backStackEntry ->
                    val listingId = backStackEntry.arguments?.getString("listingId") ?: ""
                    ListingEditorScreen(
                        repository = repository,
                        profile = profile,
                        listingId = listingId,
                        onSetLocation = { showLocationSetup = true },
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() }
                    )
                }
                composable("closet") {
                    // Lenders see their listings (and "List an item"); renters see their rentals.
                    if (Roles.canLend(profile.role)) {
                        MyClosetScreen(
                            repository = repository,
                            currentUserId = profile.id,
                            showRentalsTab = profile.role == Roles.BOTH,
                            onListItem = { navController.navigate("list") },
                            onOpenListing = { id -> navController.navigate("detail/$id") },
                            onEditListing = { id -> navController.navigate("edit/$id") },
                            onOpenChat = { conversationId -> openChat(conversationId) }
                        )
                    } else {
                        MyRentalsScreen(
                            repository = repository,
                            currentUserId = profile.id,
                            onOpenChat = { conversationId -> openChat(conversationId) }
                        )
                    }
                }
                composable("bag") {
                    EmptyView(
                        title = "Your Bag is empty",
                        description = "Pieces you save will show up here.",
                        icon = Icons.Default.BookmarkBorder
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        repository = repository,
                        profile = profile,
                        onProfileUpdated = { updated -> profile = updated },
                        onBack = { navController.popBackStack() },
                        onOpenMeasurements = { navController.navigate("measurements") },
                        onOpenMyStudio = { navController.navigate("mystudio") }
                    )
                }
                composable("mystudio") {
                    MyStudioScreen(
                        repository = repository,
                        profile = profile,
                        onBack = { navController.popBackStack() },
                        onSaved = { updated ->
                            profile = updated
                            navController.popBackStack()
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        repository = repository,
                        profile = profile,
                        onBack = { navController.popBackStack() },
                        onSetLocation = { showLocationSetup = true },
                        onOpenBlockedUsers = { navController.navigate("blocked") },
                        onOpenCommunityPolicy = { navController.navigate("community") },
                        onOpenHowItWorks = { navController.navigate("policy") },
                        onSignOut = onSignOut
                    )
                }
                composable("measurements") {
                    BodyMeasurementsScreen(
                        repository = repository,
                        userId = profile.id,
                        onBack = {
                            // The fit row depends on these, so Home loads them again.
                            session.invalidate()
                            navController.popBackStack()
                        }
                    )
                }
                composable("blocked") {
                    BlockedUsersScreen(
                        repository = repository,
                        currentUserId = profile.id,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("policy") {
                    HowDzirentWorksScreen(onBack = { navController.popBackStack() })
                }
                composable("community") {
                    CommunityPolicyScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomTab(tab: TabItem, selected: Boolean, dot: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        icon = {
            BadgedBox(badge = { if (dot) AccentDot() }) {
                Icon(tab.icon, contentDescription = if (dot) "${tab.label}, new" else tab.label)
            }
        },
        label = {
            Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        },
        selected = selected,
        onClick = onClick
    )
}

/** The raised, accent-colored Search button in the middle of the bottom bar. */
@Composable
private fun RowScope.SearchTabButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = DzirentAccent,
            contentColor = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Search, contentDescription = TabSearch.label, modifier = Modifier.size(28.dp))
            }
        }
    }
}
