package com.example.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GameItem
import com.example.ui.components.AddContentModal
import com.example.ui.components.AppIconCard
import com.example.ui.components.CustomizeItemDialog
import com.example.ui.components.ItemContextMenuSheet
import com.example.ui.components.SettingsModal
import com.example.ui.components.SteamGamePosterCard
import com.example.ui.components.TopNavBar
import com.example.ui.viewmodel.AddModalSubView
import com.example.ui.viewmodel.LauncherViewModel
import com.example.ui.viewmodel.MainLibraryTab
import kotlinx.coroutines.launch

private const val READ_TV_LISTINGS_PERMISSION = "android.permission.READ_TV_LISTINGS"

@Composable
fun DashboardScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val gamesList by viewModel.gamesList.collectAsStateWithLifecycle()
    val appsList by viewModel.appsList.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val focusedItemId by viewModel.focusedItemId.collectAsStateWithLifecycle()

    val contextMenuItem by viewModel.contextMenuItem.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val showAddModal by viewModel.showAddModal.collectAsStateWithLifecycle()
    val addModalSubView by viewModel.addModalSubView.collectAsStateWithLifecycle()
    val showSettingsModal by viewModel.showSettingsModal.collectAsStateWithLifecycle()
    val moonlightState by viewModel.moonlightState.collectAsStateWithLifecycle()

    val iconShape by viewModel.iconShape.collectAsStateWithLifecycle()
    val useThemedIcons by viewModel.useThemedIcons.collectAsStateWithLifecycle()
    val steamGridDbApiKey by viewModel.steamGridDbApiKey.collectAsStateWithLifecycle()

    var showSearchBar by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // Moonlight's TV channel is behind a runtime permission on Android TV. Ask for it the
    // first time the Moonlight add view opens, then re-run the scan with the answer.
    val context = LocalContext.current
    var tvListingsRequested by remember { mutableStateOf(false) }
    val tvListingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.loadMoonlightGames()
    }

    LaunchedEffect(showAddModal, addModalSubView) {
        if (showAddModal &&
            addModalSubView == AddModalSubView.ADD_MOONLIGHT &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !tvListingsRequested &&
            context.checkSelfPermission(READ_TV_LISTINGS_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            tvListingsRequested = true
            tvListingsLauncher.launch(READ_TV_LISTINGS_PERMISSION)
        }
    }

    // Filter current list by search query if any
    val activeList = if (currentTab == MainLibraryTab.GAMES) gamesList else appsList
    val displayList = if (searchQuery.isBlank()) activeList else activeList.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.genre.contains(searchQuery, ignoreCase = true)
    }

    // Scroll focused item into view when navigating with controller
    LaunchedEffect(focusedItemId) {
        if (focusedItemId != null) {
            val idx = displayList.indexOfFirst { it.id == focusedItemId }
            if (idx >= 0) {
                scope.launch { gridState.animateScrollToItem(idx) }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Navigation Bar (+ Icon Left, Games/Apps Center, Settings Gear Right)
            TopNavBar(
                currentTab = currentTab,
                gamesCount = gamesList.size,
                appsCount = appsList.size,
                onTabSelected = { viewModel.setTab(it) },
                onOpenAddModal = { viewModel.openAddModal() },
                onOpenSettings = { viewModel.openSettingsModal() }
            )

            // Optional Search Bar
            AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(if (currentTab == MainLibraryTab.GAMES) "Search games & cloud titles..." else "Search installed apps...")
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("search_text_input")
                    )
                }
            }

            // Main Content Area: Responsive Grid (Games: Steam Poster Grid, Apps: App Icon Grid)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (displayList.isEmpty()) {
                    EmptyLibraryState(
                        isGamesTab = currentTab == MainLibraryTab.GAMES,
                        searchQuery = searchQuery,
                        onAddClicked = { viewModel.openAddModal() }
                    )
                } else {
                    if (currentTab == MainLibraryTab.GAMES) {
                        // Games View: Steam Poster Aspect Ratio (2:3)
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 135.dp),
                            state = gridState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .testTag("games_poster_grid")
                        ) {
                            items(displayList, key = { it.id }) { game ->
                                SteamGamePosterCard(
                                    game = game,
                                    isFocused = game.id == focusedItemId,
                                    onClick = {
                                        viewModel.setFocusedItem(game.id)
                                        viewModel.launchItem(game)
                                    },
                                    onLongClick = {
                                        viewModel.openContextMenu(game)
                                    }
                                )
                            }
                        }
                    } else {
                        // Apps View: App Icons Grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 80.dp),
                            state = gridState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding()
                                .testTag("apps_icon_grid")
                        ) {
                            items(displayList, key = { it.id }) { app ->
                                AppIconCard(
                                    app = app,
                                    iconShape = iconShape,
                                    useThemedIcons = useThemedIcons,
                                    isFocused = app.id == focusedItemId,
                                    onClick = {
                                        viewModel.setFocusedItem(app.id)
                                        viewModel.launchItem(app)
                                    },
                                    onLongClick = {
                                        viewModel.openContextMenu(app)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Context Menu Modal (Tap and Hold on any card)
        if (contextMenuItem != null) {
            ItemContextMenuSheet(
                item = contextMenuItem!!,
                onCustomize = { viewModel.openEditCustomizer(contextMenuItem!!) },
                onMove = {
                    if (contextMenuItem!!.isGame) {
                        viewModel.moveItemToApps(contextMenuItem!!)
                    } else {
                        viewModel.moveItemToGames(contextMenuItem!!)
                    }
                },
                onToggleFavorite = { viewModel.toggleFavorite(contextMenuItem!!) },
                onLaunch = {
                    val it = contextMenuItem!!
                    viewModel.closeContextMenu()
                    viewModel.launchItem(it)
                },
                onDelete = { viewModel.deleteItem(contextMenuItem!!) },
                onDismiss = { viewModel.closeContextMenu() }
            )
        }

        // Customize Name & Icon Dialog
        if (editingItem != null) {
            CustomizeItemDialog(
                item = editingItem!!,
                steamGridDbApiKey = steamGridDbApiKey,
                onSave = { id, newTitle, newPoster, newIcon ->
                    viewModel.saveItemCustomization(id, newTitle, newPoster, newIcon)
                },
                onDismiss = { viewModel.closeEditCustomizer() }
            )
        }

        // Multi-Step Add Content Modal (+ Button)
        if (showAddModal) {
            AddContentModal(
                subView = addModalSubView,
                installedApps = installedApps,
                iconShape = iconShape,
                useThemedIcons = useThemedIcons,
                moonlightState = moonlightState,
                onNavigateSubView = { viewModel.setAddModalSubView(it) },
                onAddApp = { viewModel.addAppToGames(it) },
                onAddShortcut = { title, url, poster -> viewModel.addCustomShortcutToGames(title, url, poster) },
                onAddGfnGame = { viewModel.addGfnGameToGames(it) },
                onRefreshMoonlight = { viewModel.loadMoonlightGames() },
                onAddMoonlightGame = { viewModel.addMoonlightGameToGames(it) },
                onDismiss = { viewModel.closeAddModal() }
            )
        }

        // Settings Modal (Gear Icon)
        if (showSettingsModal) {
            SettingsModal(
                iconShape = iconShape,
                useThemedIcons = useThemedIcons,
                steamGridDbApiKey = steamGridDbApiKey,
                onSelectIconShape = { viewModel.setIconShape(it) },
                onToggleThemedIcons = { viewModel.setUseThemedIcons(it) },
                onSaveApiKey = { viewModel.saveSteamGridDbApiKey(it) },
                onSetDefaultLauncher = { viewModel.openDefaultLauncherSettings() },
                onSyncLibrary = { viewModel.syncLibrary() },
                onDismiss = { viewModel.closeSettingsModal() }
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(
    isGamesTab: Boolean,
    searchQuery: String,
    onAddClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (searchQuery.isNotBlank()) "No results matching \"$searchQuery\""
            else if (isGamesTab) "Your Games Library is Empty"
            else "No Applications Found",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isGamesTab) "Tap the + button to add installed apps, shortcuts, or GeForce NOW games."
            else "Sync installed apps or check app permissions.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (isGamesTab) {
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onAddClicked,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Content")
            }
        }
    }
}
