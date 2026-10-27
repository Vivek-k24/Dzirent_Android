package com.dzirent.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Profile
import com.dzirent.app.ui.theme.DzirentAccent

/**
 * The navigation drawer: profile, Settings, then "All categories" with the Filter button
 * and the category list. Picking a category filters Home in one tap.
 */
@Composable
fun SidebarContent(
    profile: Profile,
    selectedCategory: String?,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectCategory: (String?) -> Unit,
    onOpenFilter: () -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenProfile)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Avatar(name = profile.displayName, size = 48.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName.ifBlank { "Your profile" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = profile.city.ifBlank { "No city set" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Settings", style = MaterialTheme.typography.bodyLarge)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)

            // Heading and the one Filter button share a line. The heading also clears the category.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 24.dp, end = 12.dp)
            ) {
                CategoryText(
                    text = "All categories",
                    selected = selectedCategory == null,
                    heading = true,
                    onClick = { onSelectCategory(null) },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenFilter) {
                    Icon(Icons.Default.Tune, contentDescription = "Filter")
                }
            }
            Categories.ALL.forEach { category ->
                CategoryText(
                    text = category,
                    selected = selectedCategory == category,
                    heading = false,
                    onClick = { onSelectCategory(category) },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CategoryText(
    text: String,
    selected: Boolean,
    heading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = if (heading) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
        fontWeight = if (heading || selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) DzirentAccent else MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}
