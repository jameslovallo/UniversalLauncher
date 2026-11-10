package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GameItem
import com.example.ui.components.AddShortcutDialog
import com.example.ui.components.ArtworkCustomizerDialog
import com.example.ui.components.ConsoleTopBar
import com.example.ui.components.ControllerActionBar
import com.example.ui.components.ControllerKeyBadge
import com.example.ui.components.GamePosterCard
import com.example.ui.components.HeroBanner
import com.example.ui.components.LauncherSettingsDialog
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonGfnGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import com.example.ui.viewmodel.LauncherCategory
import com.example.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val filteredGames by viewModel.filteredGames.collectAsStateWithLifecycle()
    val allGames by viewModel.allGames.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val focusedGameId by viewModel.focusedGameId.collectAsStateWithLifecycle()
    val controllerState by viewModel.controllerManager.controllerState.collectAsStateWithLifecycle()

    val customizingGame by viewModel.customizingGame.collectAsStateWithLifecycle()
    val showAddDialog by viewModel.showAddShortcutDialog.collectAsStateWithLifecycle()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()
    val showSearchDialog by viewModel.showSearchDialog.collectAsStateWithLifecycle()
    val steamGridDbApiKey by viewModel.steamGridDbApiKey.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Determine current focused game (or first game in filtered list)
    val currentFocusedGame = filteredGames.find { it.id == focusedGameId } ?: filteredGames.firstOrNull()

    // When focused game changes via controller, ensure it is scrolled into view
    LaunchedEffect(focusedGameId) {
        if (focusedGameId != null) {
            val index = filteredGames.indexOfFirst { it.id == focusedGameId }
            if (index >= 0) {
                scope.launch {
                    gridState.animateScrollToItem(index)
                }
            }
        }
    }

    // Set initial focused game if none selected
    LaunchedEffect(filteredGames) {
        if (focusedGameId == null && filteredGames.isNotEmpty()) {
            viewModel.setFocusedGame(filteredGames.first().id)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(VoidBlack)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // System Top Bar
            ConsoleTopBar(
                controllerState = controllerState,
                isLauncherDefault = false,
                onOpenSearch = { viewModel.openSearchDialog() },
                onOpenSettings = { viewModel.openSettingsDialog() },
                onAddShortcut = { viewModel.openAddShortcutDialog() },
                onSyncLibrary = { viewModel.syncGfnCatalog() },
                modifier = Modifier.statusBarsPadding()
            )

            // Dynamic Hero Banner with ambient game backdrop
            HeroBanner(
                game = currentFocusedGame,
                onPlay = { currentFocusedGame?.let { viewModel.launchGame(it) } },
                onCustomizeArtwork = { currentFocusedGame?.let { viewModel.openArtworkCustomizer(it) } },
                onToggleFavorite = { currentFocusedGame?.let { viewModel.toggleFavorite(it) } }
            )

            // Category Filter Tabs with Controller Bumper Badges [LB] and [RB]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControllerKeyBadge(label = "LB", bgColor = Color(0xFF1E293B), textColor = ElectricCyan)
                Spacer(modifier = Modifier.width(8.dp))

                val categories = LauncherCategory.values()
                ScrollableTabRow(
                    selectedTabIndex = categories.indexOf(selectedCategory),
                    containerColor = Color.Transparent,
                    contentColor = NeonGfnGreen,
                    edgePadding = 0.dp,
                    divider = {},
                    modifier = Modifier.weight(1f)
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(category) },
                            text = {
                                Text(
                                    text = category.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) NeonGfnGreen else TextSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                ControllerKeyBadge(label = "RB", bgColor = Color(0xFF1E293B), textColor = ElectricCyan)
            }

            // Search Filter Bar (if searching)
            AnimatedVisibility(visible = showSearchDialog || searchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search GeForce NOW titles, installed apps, genres...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = NeonGfnGreen)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGfnGreen,
                            unfocusedBorderColor = SurfaceCardHighlight
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dashboard_search_input")
                    )
                }
            }

            // Game Posters Grid
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (filteredGames.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No games matching \"$searchQuery\"" else "No titles in this section",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sync GeForce NOW titles or add custom shortcuts to get started.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.syncGfnCatalog() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGfnGreen)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync GeForce NOW", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.openAddShortcutDialog() },
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Shortcut", color = Color.White)
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 135.dp),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize().testTag("games_poster_grid")
                    ) {
                        items(filteredGames, key = { it.id }) { game ->
                            val isFocused = game.id == focusedGameId
                            GamePosterCard(
                                game = game,
                                isFocused = isFocused,
                                onClick = {
                                    if (isFocused) {
                                        viewModel.launchGame(game)
                                    } else {
                                        viewModel.setFocusedGame(game.id)
                                    }
                                },
                                onCustomizeArtwork = {
                                    viewModel.openArtworkCustomizer(game)
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(game)
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Controller Action Guide Bar
            ControllerActionBar()
        }

        // Dialogs
        if (customizingGame != null) {
            ArtworkCustomizerDialog(
                game = customizingGame!!,
                steamGridDbApiKey = steamGridDbApiKey,
                onSaveArtwork = { posterUrl, heroUrl, source ->
                    viewModel.saveArtwork(posterUrl, heroUrl, source)
                },
                onDismiss = { viewModel.closeArtworkCustomizer() }
            )
        }

        if (showAddDialog) {
            AddShortcutDialog(
                onAdd = { title, url, posterUrl, type ->
                    viewModel.addCustomShortcut(title, url, posterUrl, type)
                },
                onDismiss = { viewModel.closeAddShortcutDialog() }
            )
        }

        if (showSettingsDialog) {
            LauncherSettingsDialog(
                steamGridDbApiKey = steamGridDbApiKey,
                onSaveApiKey = { key -> viewModel.saveSteamGridDbApiKey(key) },
                onSetDefaultLauncher = { viewModel.openDefaultLauncherSettings() },
                onResyncGfn = { viewModel.syncGfnCatalog() },
                onResyncApps = { viewModel.syncInstalledApps() },
                onDismiss = { viewModel.closeSettingsDialog() }
            )
        }
    }
}
