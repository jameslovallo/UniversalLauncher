package com.example.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GameItem
import com.example.ui.components.AddContentModal
import com.example.ui.components.AppIconTile
import com.example.ui.components.AppTextRow
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
import com.example.ui.theme.FavoritesLayout
import kotlinx.coroutines.launch

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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val focusedItemId by viewModel.focusedItemId.collectAsStateWithLifecycle()

    val contextMenuItem by viewModel.contextMenuItem.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val showAddModal by viewModel.showAddModal.collectAsStateWithLifecycle()
    val addModalSubView by viewModel.addModalSubView.collectAsStateWithLifecycle()
    val showSettingsModal by viewModel.showSettingsModal.collectAsStateWithLifecycle()
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
    val appsGridState = rememberLazyGridState()
    val gamesRowState = rememberLazyListState()
    val appsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isCompactLandscape) {
        viewModel.setGamesRowMode(isCompactLandscape)
    }

    // Filter each page by the search query. Keeping both lists separate means swiping
    // between pages shows the correct results even before the tab state catches up.
    fun matchesQuery(item: GameItem): Boolean =
        item.title.contains(searchQuery, ignoreCase = true) ||
            item.genre.contains(searchQuery, ignoreCase = true)

    val gamesDisplay = if (searchQuery.isBlank()) gamesList else gamesList.filter { matchesQuery(it) }
    // The complete app list excludes favorites and games.
    val libraryDisplay = if (searchQuery.isBlank()) libraryApps else libraryApps.filter { matchesQuery(it) }
    // Flattened letter headers + rows. LazyColumn indices include the headers, so focus and
    // letter-rail jumps use the same list.
    val appListEntries = remember(libraryDisplay) { buildAppListEntries(libraryDisplay) }

    // Horizontal pager: page 0 = Apps, page 1 = Games. It stays in sync with the tab
    // selected in the top navigation bar in both directions.
    val pagerState = rememberPagerState(
        initialPage = if (currentTab == MainLibraryTab.GAMES) 1 else 0,
        pageCount = { 2 }
    )

    // Vertical pager inside the Apps tab: favorites home, then the complete app list.
    val appsPagerState = rememberPagerState(pageCount = { 2 })

    // Dismiss the drawer with a much shorter downward drag than the default half-page.
    val appsPagerFling = PagerDefaults.flingBehavior(
        state = appsPagerState,
        snapPositionalThreshold = 0.12f
    )

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
                val idx = appListEntries.indexOfFirst {
                    it is AppListEntry.App && it.item.id == focusedId
                }
                if (idx >= 0) {
                    if (appsPagerState.currentPage != 1) {
                        appsPagerState.animateScrollToPage(1)
                    }
                    if (favoritesLayout == FavoritesLayout.ICONS) {
                        appsGridState.animateScrollToItem(idx)
                    } else {
                        appsListState.animateScrollToItem(idx)
                    }
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

        // Fades to an opaque Material container as the pager settles on the Games page.
        // surfaceContainerHighest is a defined Material palette tone that sits between
        // the light and dark extremes instead of near-black.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = darkAmount)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
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
                            onColumnCountChanged = { viewModel.setGamesColumnCount(it) },
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
                        flingBehavior = appsPagerFling,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("apps_vertical_pager")
                    ) { page ->
                        if (page == 0) {
                            FavoritesHome(
                                favorites = favoriteApps,
                                focusedItemId = focusedItemId,
                                layout = favoritesLayout,
                                showFocusEffects = controllerState.isConnected,
                                onLaunchFavorite = { app ->
                                    viewModel.setFocusedItem(app.id)
                                    viewModel.launchItem(app)
                                },
                                onLongPressFavorite = { app ->
                                    viewModel.openFavoriteActions(app)
                                },
                                onOpenClock = { viewModel.openClockApp() },
                                onOpenCalendar = { viewModel.openCalendarApp() },
                                onPullDown = { viewModel.expandNotificationShade() }
                            )
                        } else {
                            if (libraryDisplay.isEmpty()) {
                                EmptyAppDrawerState(hasFavorites = favoriteApps.isNotEmpty())
                            } else {
                                AppsAlphabeticalList(
                                    entries = appListEntries,
                                    focusedItemId = focusedItemId,
                                    layout = favoritesLayout,
                                    showFocusEffects = controllerState.isConnected,
                                    listState = appsListState,
                                    gridState = appsGridState,
                                    onColumnCountChanged = { viewModel.setAppsColumnCount(it) },
                                    onAppClick = { app ->
                                        viewModel.setFocusedItem(app.id)
                                        viewModel.launchItem(app)
                                    },
                                    onAppLongClick = { app ->
                                        viewModel.openContextMenu(app)
                                    },
                                    onLetterSelected = { letter ->
                                        val index = appListEntries.indexOfFirst {
                                            it is AppListEntry.Letter && it.letter == letter
                                        }
                                        if (index >= 0) {
                                            scope.launch {
                                                if (favoritesLayout == FavoritesLayout.ICONS) {
                                                    appsGridState.scrollToItem(index)
                                                } else {
                                                    appsListState.scrollToItem(index)
                                                }
                                            }
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
                onOpenAppInfo = { viewModel.openAppInfo(contextMenuItem!!) },
                onHide = { viewModel.hideItem(contextMenuItem!!) },
                onDelete = { viewModel.deleteItem(contextMenuItem!!) },
                onDismiss = { viewModel.closeContextMenu() }
            )
        }

        // Long-press menu for favorites (Edit favorites, Remove)
        favoriteActionItem?.let { favorite ->
            FavoriteActionsSheet(
                item = favorite,
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
                onNavigateSubView = { viewModel.setAddModalSubView(it) },
                onOpenPlayStore = { viewModel.openPlayStore() },
                onOpenMoonlight = { viewModel.openMoonlight() },
                onAddShortcut = { title, url, poster -> viewModel.addCustomShortcutToApps(title, url, poster) },
                onAddGfnGame = { viewModel.addGfnGameToGames(it) },
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
    onColumnCountChanged: (Int) -> Unit,
    onGameClick: (GameItem) -> Unit,
    onGameLongClick: (GameItem) -> Unit
) {
    if (compactLandscape) {
        // Small landscape screens: a single horizontal row that steps one poster at a
        // time. The focused poster sits at the left edge and is visibly larger.
        val snapFling = rememberSnapFlingBehavior(lazyListState = rowState)

        LaunchedEffect(Unit) { onColumnCountChanged(1) }

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

        LaunchedEffect(columnCount) { onColumnCountChanged(columnCount) }

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

private sealed interface AppListEntry {
    data class Letter(val letter: String) : AppListEntry
    data class App(val item: GameItem) : AppListEntry
}

/** Flatten the sorted app list into letter headers and rows. */
private fun buildAppListEntries(apps: List<GameItem>): List<AppListEntry> {
    val entries = ArrayList<AppListEntry>(apps.size + 8)
    var section: String? = null
    for (app in apps) {
        val letter = alphabetSection(app.title)
        if (letter != section) {
            entries += AppListEntry.Letter(letter)
            section = letter
        }
        entries += AppListEntry.App(app)
    }
    return entries
}

@Composable
private fun AppsAlphabeticalList(
    entries: List<AppListEntry>,
    focusedItemId: String?,
    layout: FavoritesLayout,
    showFocusEffects: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    onColumnCountChanged: (Int) -> Unit = {},
    onAppClick: (GameItem) -> Unit,
    onAppLongClick: (GameItem) -> Unit,
    onLetterSelected: (String) -> Unit
) {
    val letters = remember(entries) {
        entries.filterIsInstance<AppListEntry.Letter>().map { it.letter }
    }
    val activeLetter = remember(entries, focusedItemId) {
        entries.filterIsInstance<AppListEntry.App>()
            .firstOrNull { it.item.id == focusedItemId }
            ?.let { alphabetSection(it.item.title) }
    }

    // Draw edge to edge behind the navigation bar / app-switcher drag handle. The bar
    // inset moves into each list's scrollable content padding instead, so rows can pass
    // under the handle while the final items still scroll fully into view.
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Row(
        modifier = Modifier.fillMaxSize()
    ) {
        if (layout == FavoritesLayout.ICONS) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val availableWidth = maxWidth - 12.dp
                val tileMinSize = 78.dp
                val columns = maxOf(3, (availableWidth / tileMinSize).toInt())

                LaunchedEffect(columns) {
                    onColumnCountChanged(columns)
                }

                // Icon grid drawer: cells are packed edge to edge; gaps live inside each
                // tile's clickable content padding so the whole cell is an easy touch target.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 0.dp,
                        top = 8.dp,
                        bottom = 16.dp + navigationBarInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("apps_grid")
                ) {
                entries.forEach { entry ->
                    when (entry) {
                        is AppListEntry.Letter -> item(
                            key = "section_${entry.letter}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            AppLetterHeader(letter = entry.letter)
                        }
                        is AppListEntry.App -> item(key = entry.item.id) {
                            AppIconTile(
                                app = entry.item,
                                isFocused = entry.item.id == focusedItemId,
                                showFocusEffects = showFocusEffects,
                                testTag = "app_row_${entry.item.id}",
                                onClick = { onAppClick(entry.item) },
                                onLongClick = { onAppLongClick(entry.item) },
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 4.dp,
                    top = 8.dp,
                    bottom = 16.dp + navigationBarInset
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("apps_list")
            ) {
                entries.forEach { entry ->
                    when (entry) {
                        is AppListEntry.Letter -> item(key = "section_${entry.letter}") {
                            AppLetterHeader(letter = entry.letter)
                        }
                        is AppListEntry.App -> item(key = entry.item.id) {
                            AppTextRow(
                                app = entry.item,
                                isFocused = entry.item.id == focusedItemId,
                                showFocusEffects = showFocusEffects,
                                testTag = "app_row_${entry.item.id}",
                                onClick = { onAppClick(entry.item) },
                                onLongClick = { onAppLongClick(entry.item) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        AlphabetRail(
            letters = letters,
            activeLetter = activeLetter,
            onLetterSelected = onLetterSelected
        )
    }
}

@Composable
private fun AppLetterHeader(letter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = letter,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
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
    // Measure the rail and the packed letter column inside it, so a touch maps onto the
    // letters the user can actually see (the column is bottom-aligned, not full height).
    var railHeight by remember { mutableIntStateOf(0) }
    var lettersHeight by remember { mutableIntStateOf(0) }
    var draggedLetter by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val highlightedLetter = draggedLetter ?: activeLetter

    // Letters sit in a tightly packed column with a little breathing room between
    // them, lifted off the bottom edge by roughly five letter heights so the rail does
    // not collide with the gesture bar or the last row of apps.
    val letterSpacing = 4.dp
    val letterLineHeight = with(LocalDensity.current) { 13.sp.toDp() }
    val letterPitch = letterLineHeight + letterSpacing
    val bottomInset = letterPitch * 5
    val density = LocalDensity.current
    val letterPitchPx = with(density) { letterPitch.toPx() }
    val bottomInsetPx = with(density) { bottomInset.toPx() }

    fun letterAt(y: Float): String? {
        if (letters.isEmpty() || lettersHeight <= 0) return null
        val bottom = (railHeight - bottomInsetPx).coerceAtLeast(lettersHeight.toFloat())
        val top = bottom - lettersHeight
        val local = (y - top).coerceIn(0f, lettersHeight - 1f)
        val index = (local / letterPitchPx)
            .toInt()
            .coerceIn(0, letters.lastIndex)
        return letters[index]
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(36.dp)
            .onSizeChanged { railHeight = it.height }
            .pointerInput(letters) {
                // Press and drag are a single continuous gesture: every letter the thumb
                // passes over is selected immediately, with no per-letter buttons.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var lastReported: String? = null

                    fun report(letter: String?) {
                        if (letter == null || letter == lastReported) return
                        lastReported = letter
                        draggedLetter = letter
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetterSelected(letter)
                    }

                    report(letterAt(down.position.y))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        report(letterAt(change.position.y))
                    }
                    draggedLetter = null
                }
            }
            .testTag("apps_alphabet_rail"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.padding(bottom = bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.onSizeChanged { lettersHeight = it.height },
                verticalArrangement = Arrangement.spacedBy(letterSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                letters.forEach { letter ->
                    val isActive = letter == highlightedLetter
                    Text(
                        text = letter,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("alphabet_rail_letter_$letter")
                    )
                }
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
