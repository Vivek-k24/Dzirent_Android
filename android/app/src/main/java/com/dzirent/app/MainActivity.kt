package com.dzirent.app

import android.os.Bundle
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
import com.dzirent.app.ui.theme.DzirentTheme

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DzirentApp() {
    val navController = rememberNavController()
    var currentTab by remember { mutableStateOf("browse") }
    var userRole by remember { mutableStateOf("both") } // 'renter', 'lender', 'both'

    Scaffold(
        bottomBar = {
            NavigationBar {
                // 1. Browse
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Browse") },
                    label = { Text("Browse") },
                    selected = currentTab == "browse",
                    onClick = {
                        currentTab = "browse"
                        navController.navigate("browse")
                    }
                )

                // 2. Inbox
                NavigationBarItem(
                    icon = { Icon(Icons.Default.MailOutline, contentDescription = "Inbox") },
                    label = { Text("Inbox") },
                    selected = currentTab == "inbox",
                    onClick = {
                        currentTab = "inbox"
                        navController.navigate("inbox")
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
                            navController.navigate("list")
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
                        navController.navigate("closet")
                    }
                )

                // 5. Profile
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = currentTab == "profile",
                    onClick = {
                        currentTab = "profile"
                        navController.navigate("profile")
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
                // Browse Screen
                Box(modifier = Modifier.fillMaxSize())
            }
            composable("inbox") {
                // Inbox Screen
                Box(modifier = Modifier.fillMaxSize())
            }
            composable("list") {
                // Create Listing Screen
                Box(modifier = Modifier.fillMaxSize())
            }
            composable("closet") {
                // My Closet Screen
                Box(modifier = Modifier.fillMaxSize())
            }
            composable("profile") {
                // Profile Screen
                Box(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
