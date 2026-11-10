package com.example.ui.screens

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.GameItem
import com.example.ui.components.AddContentModal
import com.example.ui.components.CustomizeItemDialog
import com.example.ui.components.EditFavoritesSheet
import com.example.ui.components.FavoriteActionsSheet
import com.example.ui.components.FavoritesHome
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
    val favoriteApps by viewModel.favoriteApps.collectAsStateWithLifecycle()
    val libraryApps by viewModel.libraryApps.collectAsStateWithLifecycle()
    val appsHomeReset by viewModel.appsHomeReset.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val focusedItemId by viewModel.focusedItemId.collectAsStateWithLifecycle()

    val contextMenuItem by viewModel.contextMenuItem.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val showAddModal by viewModel.showAddModal.collectAsStateWithLifecycle()
    val addModalSubView by viewModel.addModalSubView.collectAsStateWithLifecycle()
    val showSettingsModal by viewModel.showSettingsModal.collectAsStateWithLifecycle()
    val moonlightState by viewModel.moonlightState.collectAsStateWithLifecycle()
    val hiddenItems by viewModel.hiddenItems.collectAsStateWithLifecycle()
    val favoriteActionItem by viewModel.favoriteActionItem.collectAsStateWithLifecycle()
    val showFavoritesEditor by viewModel.showFavoritesEditor.collectAsStateWithLifecycle()

    val favoritesLayout by viewModel.favoritesLayout.collectAsStateWithLifecycle()
    val steamGridDbApiKey by viewModel.steamGridDbApiKey.collectAsStateWithLifecycle()

    val controllerState by viewModel.controllerManager.controllerState.collectAsStateWithLifecycle()

    // Phones held sideways get a compact horizontal poster row instead of a grid.
    val configuration = LocalConfiguration.current
    val isCompactLandscape =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.screenHeightDp < 480

    var showSearchBar by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val gamesRowState = rememberLazyListState()
    val appsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isCompactLandscape) {
        viewModel.setGamesRowMode(isCompactLandscape)
    }

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

    // Filter each page by the search query. Keeping both lists separate means swiping
    // between pages shows the correct results even before the tab state catches up.
    fun matchesQuery(item: GameItem): Boolean =
        item.title.contains(searchQuery, ignoreCase = true) ||
            item.genre.contains(searchQuery, ignoreCase = true)

    val gamesDisplay = if (searchQuery.isBlank()) gamesList else gamesList.filter { matchesQuery(it) }
    // The complete app list excludes favorites and games.
    val libraryDisplay = if (searchQuery.isBlank()) libraryApps else libraryApps.filter { matchesQuery(it) }

    // Horizontal pager: page 0 = Apps, page 1 = Games. It stays in sync with the tab
    // selected in the top navigation bar in both directions.
    val pagerState = rememberPagerState(
        initialPage = if (currentTab == MainLibraryTab.GAMES) 1 else 0,
        pageCount = { 2 }
    )

    // Vertical pager inside the Apps tab: favorites home, then the complete app list.
    val appsPagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(pagerState.currentPage) {
        val tab = if (pagerState.currentPage == 0) MainLibraryTab.APPS else MainLibraryTab.GAMES
        if (currentTab != tab) viewModel.setTab(tab)
    }

    LaunchedEffect(currentTab) {
        val target = if (currentTab == MainLibraryTab.GAMES) 1 else 0
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    // Selecting the Apps tab always returns to the favorites home, even if the vertical
    // pager was left on the complete app list.
    LaunchedEffect(appsHomeReset) {
        if (appsHomeReset > 0 && appsPagerState.currentPage != 0) {
            appsPagerState.animateScrollToPage(0)
        }
    }

    // With no controller, the Games tab should start with nothing focused. A connected
    // controller focuses the first game so the D-pad has somewhere to start.
    LaunchedEffect(controllerState.isConnected, currentTab, gamesList, focusedItemId) {
        if (currentTab == MainLibraryTab.GAMES) {
            if (controllerState.isConnected) {
                if (focusedItemId == null) {
                    gamesList.firstOrNull()?.let { viewModel.setFocusedItem(it.id) }
                }
            } else if (focusedItemId != null && gamesList.any { it.id == focusedItemId }) {
                viewModel.clearFocus()
            }
        }
    }

    // 0f on the Apps page, 1f on the Games page. Drives the wallpaper scrim so the
    // native wallpaper is visible behind Apps and fades into a dark Material surface
    // for Games, following the swipe continuously.
    val darkAmount = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, 1f)

    // Scroll the focused item into view when navigating with a controller (or after
    // switching tabs). Favorites live on the home page; everything else is in the app
    // list that opens when swiping up.
    LaunchedEffect(focusedItemId, currentTab, isCompactLandscape) {
        val focusedId = focusedItemId ?: return@LaunchedEffect
        if (currentTab == MainLibraryTab.GAMES) {
            val idx = gamesDisplay.indexOfFirst { it.id == focusedId }
            if (idx >= 0) {
                if (isCompactLandscape) {
                    // Keep the focused poster pinned to the left edge of the row.
                    gamesRowState.animateScrollToItem(idx)
                } else {
                    gridState.animateScrollToItem(idx)
                }
            }
        } else {
            val isFavorite = favoriteApps.any { it.id == focusedId }
            if (isFavorite) {
                if (appsPagerState.currentPage != 0) {
                    appsPagerState.animateScrollToPage(0)
                }
            } else {
                val idx = libraryDisplay.indexOfFirst { it.id == focusedId }
                if (idx >= 0) {
                    if (appsPagerState.currentPage != 1) {
                        appsPagerState.animateScrollToPage(1)
                    }
                    appsListState.animateScrollToItem(idx)
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // The native wallpaper shows through the transparent window. This translucent
        // Material scrim keeps the Apps list readable over any wallpaper.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background.copy(
                        alpha = 0.55f * (1f - darkAmount)
                    )
                )
        )

        // Fades to an opaque Material background as the pager settles on the Games page.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.background.copy(alpha = darkAmount)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Navigation Bar (+ Icon Left, Apps/Games Center, Settings Gear Right)
            TopNavBar(
                currentTab = currentTab,
                alignEnd = isCompactLandscape,
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

            // Main Content Area: swipe horizontally between the Games grid and Apps list.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("library_pager")
            ) { page ->
                if (page == 1) {
                    // Games View: Steam poster aspect ratio (2:3)
                    if (gamesDisplay.isEmpty()) {
                        EmptyLibraryState(
                            isGamesTab = true,
                            searchQuery = searchQuery,
                            onAddClicked = { viewModel.openAddModal() }
                        )
                    } else {
                        GamesPosterGrid(
                            games = gamesDisplay,
                            focusedItemId = focusedItemId,
                            gridState = gridState,
                            rowState = gamesRowState,
                            compactLandscape = isCompactLandscape,
                            onGameClick = { game ->
                                viewModel.setFocusedItem(game.id)
                                viewModel.launchItem(game)
                            },
                            onGameLongClick = { game ->
                                viewModel.openContextMenu(game)
                            }
                        )
                    }
                } else {
                    // Apps View: a full-height favorites home. Swiping up reveals the
                    // complete app list, which excludes favorites and games.
                    VerticalPager(
                        state = appsPagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("apps_vertical_pager")
                    ) { page ->
                        if (page == 0) {
                            FavoritesHome(
                                favorites = favoriteApps,
                                focusedItemId = focusedItemId,
                                layout = favoritesLayout,
                                onLaunchFavorite = { app ->
                                    viewModel.setFocusedItem(app.id)
                                    viewModel.launchItem(app)
                                },
                                onLongPressFavorite = { app ->
                                    viewModel.openFavoriteActions(app)
                                },
                                onOpenClock = { viewModel.openClockApp() },
                                onOpenCalendar = { viewModel.openCalendarApp() }
                            )
                        } else {
                            if (libraryDisplay.isEmpty()) {
                                EmptyAppDrawerState(hasFavorites = favoriteApps.isNotEmpty())
                            } else {
                                AppsAlphabeticalList(
                                    apps = libraryDisplay,
                                    focusedItemId = focusedItemId,
                                    listState = appsListState,
                                    onAppClick = { app ->
                                        viewModel.setFocusedItem(app.id)
                                        viewModel.launchItem(app)
                                    },
                                    onAppLongClick = { app ->
                                        viewModel.openContextMenu(app)
                                    },
                                    onLetterSelected = { letter ->
                                        val index = libraryDisplay.indexOfFirst {
                                            alphabetSection(it.title) == letter
                                        }
                                        if (index >= 0) {
                                            scope.launch { appsListState.animateScrollToItem(index) }
                                        }
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
                onHide = { viewModel.hideItem(contextMenuItem!!) },
                onDelete = { viewModel.deleteItem(contextMenuItem!!) },
                onDismiss = { viewModel.closeContextMenu() }
            )
        }

        // Long-press menu for favorites (Open, Edit favorites, Remove)
        favoriteActionItem?.let { favorite ->
            FavoriteActionsSheet(
                item = favorite,
                onOpen = {
                    viewModel.closeFavoriteActions()
                    viewModel.launchItem(favorite)
                },
                onEditFavorites = { viewModel.openFavoritesEditor() },
                onRemoveFavorite = {
                    viewModel.removeFavorite(favorite)
                    viewModel.closeFavoriteActions()
                },
                onDismiss = { viewModel.closeFavoriteActions() }
            )
        }

        // Drag-to-reorder favorites editor
        if (showFavoritesEditor) {
            EditFavoritesSheet(
                favorites = favoriteApps,
                onReorder = { viewModel.reorderFavorites(it) },
                onRemoveFavorite = { viewModel.removeFavorite(it) },
                onDismiss = { viewModel.closeFavoritesEditor() }
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
                favoritesLayout = favoritesLayout,
                steamGridDbApiKey = steamGridDbApiKey,
                hiddenItems = hiddenItems,
                onSelectFavoritesLayout = { viewModel.setFavoritesLayout(it) },
                onSaveApiKey = { viewModel.saveSteamGridDbApiKey(it) },
                onSetDefaultLauncher = { viewModel.openDefaultLauncherSettings() },
                onSyncLibrary = { viewModel.syncLibrary() },
                onUnhideItem = { viewModel.unhideItem(it) },
                onDismiss = { viewModel.closeSettingsModal() }
            )
        }
    }
}

@Composable
private fun GamesPosterGrid(
    games: List<GameItem>,
    focusedItemId: String?,
    gridState: LazyGridState,
    rowState: LazyListState,
    compactLandscape: Boolean,
    onGameClick: (GameItem) -> Unit,
    onGameLongClick: (GameItem) -> Unit
) {
    if (compactLandscape) {
        // Small landscape screens: a single horizontal row that steps one poster at a
        // time. The focused poster sits at the left edge and is visibly larger.
        val snapFling = rememberSnapFlingBehavior(lazyListState = rowState)

        LazyRow(
            state = rowState,
            flingBehavior = snapFling,
            contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 28.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .testTag("games_poster_row")
        ) {
            itemsIndexed(games, key = { _, game -> game.id }) { _, game ->
                val isFocused = game.id == focusedItemId

                SteamGamePosterCard(
                    game = game,
                    isFocused = isFocused,
                    showFocusOutline = false,
                    onClick = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) },
                    modifier = Modifier.width(if (isFocused) 168.dp else 112.dp)
                )
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        val horizontalPadding = 16.dp
        val spacing = 10.dp
        // Aim for ~96dp-wide posters, but never drop below three columns. A 1080x2400
        // phone is roughly 360-400dp wide, so this yields 3-4 portrait posters per row.
        val availableWidth = maxWidth - horizontalPadding * 2
        val columnCount = maxOf(
            3,
            ((availableWidth + spacing) / (96.dp + spacing)).toInt()
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            state = gridState,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .fillMaxSize()
                .testTag("games_poster_grid")
        ) {
            items(games, key = { it.id }) { game ->
                SteamGamePosterCard(
                    game = game,
                    isFocused = game.id == focusedItemId,
                    onClick = { onGameClick(game) },
                    onLongClick = { onGameLongClick(game) }
                )
            }
        }
    }
}

@Composable
private fun AppsAlphabeticalList(
    apps: List<GameItem>,
    focusedItemId: String?,
    listState: LazyListState,
    onAppClick: (GameItem) -> Unit,
    onAppLongClick: (GameItem) -> Unit,
    onLetterSelected: (String) -> Unit
) {
    val letters = remember(apps) { apps.map { alphabetSection(it.title) }.distinct() }
    val activeLetter = remember(apps, focusedItemId) {
        apps.firstOrNull { it.id == focusedItemId }?.let { alphabetSection(it.title) }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("apps_list")
        ) {
            items(apps, key = { it.id }) { app ->
                AppListRow(
                    app = app,
                    isFocused = app.id == focusedItemId,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongClick(app) }
                )
            }
        }

        AlphabetRail(
            letters = letters,
            activeLetter = activeLetter,
            onLetterSelected = onLetterSelected
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListRow(
    app: GameItem,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = app.iconUrl ?: app.posterUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("app_row_${app.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${app.title} icon",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = app.title,
            fontSize = 16.sp,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isFocused) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AlphabetRail(
    letters: List<String>,
    activeLetter: String?,
    onLetterSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = 8.dp)
            .testTag("apps_alphabet_rail"),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            val isActive = letter == activeLetter
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onLetterSelected(letter) }
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter,
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun alphabetSection(title: String): String {
    val first = title.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first in 'A'..'Z') first.toString() else "#"
}

@Composable
private fun EmptyAppDrawerState(hasFavorites: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (hasFavorites) "All apps are in your favorites" else "No apps found",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasFavorites) "Swipe down to return to your favorites."
            else "Resync installed apps from Settings.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
