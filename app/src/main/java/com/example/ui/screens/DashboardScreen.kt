package com.example.ui.screens

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GameItem
import com.example.data.service.IconPackOverridesStore
import com.example.ui.components.AddContentModal
import com.example.ui.components.AppIconTile
import com.example.ui.components.AppTextRow
import com.example.ui.components.CustomizeItemDialog
import com.example.ui.components.EditFavoritesSheet
import com.example.ui.components.FavoriteActionsSheet
import com.example.ui.components.FavoritesHome
import com.example.ui.components.ItemContextMenuSheet
import com.example.ui.components.LocalThemedAppIcons
import com.example.ui.components.SettingsModal
import com.example.ui.components.SteamGamePosterCard
import com.example.ui.components.TopNavBar
import com.example.ui.components.hasCustomAppIconUrl
import com.example.ui.viewmodel.AddModalSubView
import com.example.ui.viewmodel.LauncherViewModel
import com.example.ui.viewmodel.MainLibraryTab
import com.example.ui.theme.FavoritesLayout
import com.example.ui.theme.ThemeStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
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
    val canDeleteContextMenuItem by viewModel.canDeleteContextMenuItem.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val showAddModal by viewModel.showAddModal.collectAsStateWithLifecycle()
    val addModalSubView by viewModel.addModalSubView.collectAsStateWithLifecycle()
    val showSettingsModal by viewModel.showSettingsModal.collectAsStateWithLifecycle()
    val hiddenItems by viewModel.hiddenItems.collectAsStateWithLifecycle()
    val favoriteActionItem by viewModel.favoriteActionItem.collectAsStateWithLifecycle()
    val showFavoritesEditor by viewModel.showFavoritesEditor.collectAsStateWithLifecycle()

    val favoritesLayout by viewModel.favoritesLayout.collectAsStateWithLifecycle()
    val steamGridDbApiKey by viewModel.steamGridDbApiKey.collectAsStateWithLifecycle()
    val steamGridDbIncludeNsfw by viewModel.steamGridDbIncludeNsfw.collectAsStateWithLifecycle()
    val installedIconPacks by viewModel.installedIconPacks.collectAsStateWithLifecycle()
    val selectedIconPack by viewModel.selectedIconPack.collectAsStateWithLifecycle()
    val iconPackIcons by viewModel.iconPackIcons.collectAsStateWithLifecycle()
    val selectedPackOverrides by viewModel.selectedPackOverrides.collectAsStateWithLifecycle()
    val selectedCustomUrlOverrides by viewModel.selectedCustomUrlOverrides.collectAsStateWithLifecycle()
    val themedAppIcons by viewModel.themedAppIcons.collectAsStateWithLifecycle()

    val controllerState by viewModel.controllerManager.controllerState.collectAsStateWithLifecycle()

    val appsBackgroundOpacity by viewModel.appsBackgroundOpacity.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
    val weatherEnabled by viewModel.weatherEnabled.collectAsStateWithLifecycle()
    val weatherMode by viewModel.weatherMode.collectAsStateWithLifecycle()
    val weatherZip by viewModel.weatherZip.collectAsStateWithLifecycle()
    val weatherInfo by viewModel.weatherInfo.collectAsStateWithLifecycle()
    val weatherError by viewModel.weatherError.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val weatherApps by viewModel.weatherApps.collectAsStateWithLifecycle()
    val weatherAppPackage by viewModel.weatherAppPackage.collectAsStateWithLifecycle()
    val weatherAppChooser by viewModel.weatherAppChooser.collectAsStateWithLifecycle()
    val librarySyncBusy by viewModel.librarySyncBusy.collectAsStateWithLifecycle()
    val librarySyncStatus by viewModel.librarySyncStatus.collectAsStateWithLifecycle()
    val librarySyncConfigured by viewModel.librarySyncConfigured.collectAsStateWithLifecycle()
    val librarySyncTarget by viewModel.librarySyncTarget.collectAsStateWithLifecycle()
    val librarySyncLastSyncedAt by viewModel.librarySyncLastSyncedAt.collectAsStateWithLifecycle()
    val gamesRefreshing by viewModel.gamesRefreshing.collectAsStateWithLifecycle()
    val gamesRefreshFeedback by viewModel.gamesRefreshFeedback.collectAsStateWithLifecycle()

    val gamesSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(gamesRefreshFeedback) {
        val message = gamesRefreshFeedback ?: return@LaunchedEffect
        viewModel.clearGamesRefreshFeedback()
        gamesSnackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short
        )
    }

    var isLauncherActive by remember {
        mutableStateOf(viewModel.isDefaultLauncher() || viewModel.isHomeOverrideEnabled())
    }

    // Re-scan installed apps whenever the launcher comes back to the foreground, so a
    // freshly installed app appears without restarting.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isLauncherActive = viewModel.isDefaultLauncher() || viewModel.isHomeOverrideEnabled()
        viewModel.refreshInstalledApps()
        viewModel.refreshWeather()
        viewModel.refreshWeatherApps()
    }

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

    // Requesters for the first item of each context. When a context changes we move
    // controller focus to its first item instead of letting Compose pick whichever item
    // happens to sit at the old focus position.
    val favoritesFirstFocusRequester = remember { FocusRequester() }
    val appsListFirstFocusRequester = remember { FocusRequester() }

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

    // Keep swipe-back inside the launcher: close search, return from the app drawer or
    // Games tab to favorites, and stay on favorites when acting as the home screen so
    // Back does not finish and relaunch MainActivity (which briefly flashed unthemed icons).
    val shouldHandleHomeBack = showSearchBar ||
        searchQuery.isNotEmpty() ||
        currentTab != MainLibraryTab.APPS ||
        appsPagerState.currentPage != 0 ||
        isLauncherActive
    BackHandler(enabled = shouldHandleHomeBack) {
        when {
            showSearchBar || searchQuery.isNotEmpty() -> {
                viewModel.setSearchQuery("")
                showSearchBar = false
            }
            currentTab == MainLibraryTab.APPS && appsPagerState.currentPage != 0 -> {
                scope.launch { appsPagerState.animateScrollToPage(0) }
            }
            currentTab == MainLibraryTab.GAMES -> {
                viewModel.setTab(MainLibraryTab.APPS)
            }
            else -> Unit
        }
    }

    BindLibraryPagerAndScrollEffects(
        currentTab = currentTab,
        appsHomeReset = appsHomeReset,
        pagerState = pagerState,
        appsPagerState = appsPagerState,
        gridState = gridState,
        gamesRowState = gamesRowState,
        appsListState = appsListState,
        appsGridState = appsGridState,
        onTabFromPager = { tab ->
            if (currentTab != tab) viewModel.setTab(tab)
        }
    )

    // With no controller, the Games tab starts with nothing focused. A connected
    // controller focuses the first game so an active element is visibly highlighted.
    LaunchedEffect(controllerState.isConnected, currentTab) {
        if (currentTab == MainLibraryTab.GAMES) {
            if (controllerState.isConnected) {
                if (focusedItemId == null || gamesList.none { it.id == focusedItemId }) {
                    gamesList.firstOrNull()?.let { viewModel.setFocusedItem(it.id) }
                }
            } else if (focusedItemId != null && gamesList.any { it.id == focusedItemId }) {
                viewModel.clearFocus()
            }
        }
    }

    // Move controller focus to the first item of the new context. Without this, focus
    // search keeps the old spatial position and lands on whatever item happens to be
    // below the previously selected element.
    var focusedAppsContext by remember { mutableStateOf(Int.MIN_VALUE) }
    LaunchedEffect(
        currentTab,
        appsPagerState.currentPage,
        appsPagerState.isScrollInProgress,
        controllerState.isConnected,
        favoriteApps.firstOrNull()?.id,
        appListEntries.firstOrNull { it is AppListEntry.App },
    ) {
        if (!controllerState.isConnected || currentTab != MainLibraryTab.APPS) {
            focusedAppsContext = Int.MIN_VALUE
            return@LaunchedEffect
        }
        // Wait until the vertical pager has settled so the focus request is not lost
        // while the old page is still on screen.
        if (appsPagerState.isScrollInProgress) return@LaunchedEffect

        val context = appsPagerState.currentPage
        if (context == focusedAppsContext) return@LaunchedEffect

        if (context == 0) {
            val firstFavorite = favoriteApps.firstOrNull() ?: return@LaunchedEffect
            focusedAppsContext = context
            viewModel.setFocusedItem(firstFavorite.id)
            delay(60)
            runCatching { favoritesFirstFocusRequester.requestFocus() }
        } else {
            val firstAppIndex = appListEntries.indexOfFirst { it is AppListEntry.App }
            val firstApp = appListEntries.getOrNull(firstAppIndex) as? AppListEntry.App
                ?: return@LaunchedEffect
            focusedAppsContext = context
            viewModel.setFocusedItem(firstApp.item.id)
            // The first row may not be composed if the drawer was left scrolled down.
            val firstRowVisible = if (favoritesLayout == FavoritesLayout.ICONS) {
                appsGridState.layoutInfo.visibleItemsInfo.any { it.index == firstAppIndex }
            } else {
                appsListState.layoutInfo.visibleItemsInfo.any { it.index == firstAppIndex }
            }
            if (!firstRowVisible) {
                if (favoritesLayout == FavoritesLayout.ICONS) {
                    runCatching { appsGridState.scrollToItem(0) }
                } else {
                    runCatching { appsListState.scrollToItem(0) }
                }
            }
            delay(60)
            runCatching { appsListFirstFocusRequester.requestFocus() }
        }
    }

    // 0f on the Apps page, 1f on the Games page. Drives the wallpaper scrim so the
    // native wallpaper is visible behind Apps and fades into a dark Material surface
    // for Games, following the swipe continuously.
    val darkAmount = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, 1f)

    // While a controller is connected and the Games tab is showing, the poster grid owns
    // focus. The top bar must not keep it, and overlays (search, dialogs, sheets) are
    // exempt so they can take focus normally.
    val holdGameGridFocus = controllerState.isConnected &&
        currentTab == MainLibraryTab.GAMES &&
        !pagerState.isScrollInProgress &&
        !showSearchBar &&
        searchQuery.isBlank() &&
        contextMenuItem == null &&
        favoriteActionItem == null &&
        !showFavoritesEditor &&
        editingItem == null &&
        !showAddModal &&
        !showSettingsModal &&
        weatherAppChooser == null

    CompositionLocalProvider(LocalThemedAppIcons provides themedAppIcons) {
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
                            alpha = appsBackgroundOpacity * (1f - darkAmount)
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
                    // In the compact landscape game row the cutout inset is handled inside
                    // the row, so the row can draw the previous poster underneath the camera.
                    .then(
                        if (isCompactLandscape) Modifier else Modifier.displayCutoutPadding()
                    )
            ) {
                // Top Navigation Bar (+ Icon Left, Apps/Games Center, Settings Gear Right)
                TopNavBar(
                    currentTab = currentTab,
                    alignEnd = isCompactLandscape,
                    // On compact landscape the favorites clock/date block is hidden, so the
                    // date and conditions live in the bar itself. They stay there for both
                    // Apps and Games so switching tabs never makes them pop in or out.
                    showDateWeather = isCompactLandscape,
                    weatherEnabled = weatherEnabled,
                    weather = weatherInfo,
                    temperatureUnit = temperatureUnit,
                    onOpenCalendar = { viewModel.openCalendarApp() },
                    onOpenWeather = { viewModel.openWeatherApp() },
                    onTabSelected = { viewModel.setTab(it) },
                    onOpenAddModal = { viewModel.openAddModal() },
                    onOpenSettings = { viewModel.openSettingsModal() },
                    modifier = if (isCompactLandscape) Modifier.displayCutoutPadding() else Modifier
                )

                // Optional Search Bar
                AnimatedVisibility(visible = showSearchBar || searchQuery.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isCompactLandscape) Modifier.displayCutoutPadding() else Modifier
                            )
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
                    // On the compact landscape Games page the poster row owns horizontal
                    // drags. Disabling pager dragging here stops its overscroll from
                    // revealing the Apps page; a drag above/below the row still switches.
                    userScrollEnabled = !(isCompactLandscape &&
                        gamesDisplay.isNotEmpty() &&
                        pagerState.currentPage == 1 &&
                        !pagerState.isScrollInProgress),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("library_pager")
                ) { page ->
                    if (page == 1) {
                        // Games View: Steam poster aspect ratio (2:3) with pull-to-refresh.
                        // Confine directional focus navigation within the Games view so D-pad /
                        // left stick left cannot escape to page 0 (Apps view).
                        val pullToRefreshState = rememberPullToRefreshState()
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .focusGroup()
                                .focusProperties {
                                    exit = { direction ->
                                        // Never let controller navigation escape into the top
                                        // bar. Up on the first row stays on that row, and
                                        // Left/Previous cannot jump to the Apps page.
                                        if (
                                            direction == FocusDirection.Left ||
                                            direction == FocusDirection.Up ||
                                            direction == FocusDirection.Previous
                                        ) {
                                            FocusRequester.Cancel
                                        } else {
                                            FocusRequester.Default
                                        }
                                    }
                                }
                        ) {
                            val pageMaxHeight = maxHeight
                            PullToRefreshBox(
                                isRefreshing = gamesRefreshing,
                                onRefresh = { viewModel.refreshGamesLibrary() },
                                state = pullToRefreshState,
                                indicator = {
                                    PullToRefreshDefaults.Indicator(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .testTag("games_pull_to_refresh_indicator"),
                                        isRefreshing = gamesRefreshing,
                                        state = pullToRefreshState,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("games_pull_to_refresh")
                            ) {
                                if (gamesDisplay.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = pageMaxHeight)
                                            .verticalScroll(rememberScrollState())
                                            .then(
                                                if (isCompactLandscape) {
                                                    Modifier.displayCutoutPadding()
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) {
                                        EmptyLibraryState(
                                            isGamesTab = true,
                                            searchQuery = searchQuery,
                                            onAddClicked = { viewModel.openAddModal() },
                                            modifier = Modifier.heightIn(min = pageMaxHeight)
                                        )
                                    }
                                } else {
                                    GamesPosterGrid(
                                        games = gamesDisplay,
                                        focusedItemId = focusedItemId,
                                        showFocusEffects = controllerState.isConnected,
                                        showNsfwPoster = steamGridDbIncludeNsfw,
                                        isGamesTabOpen = currentTab == MainLibraryTab.GAMES &&
                                            pagerState.currentPage == 1 &&
                                            !pagerState.isScrollInProgress,
                                        isGamesPage = currentTab == MainLibraryTab.GAMES,
                                        controllerConnected = controllerState.isConnected,
                                        holdFocus = holdGameGridFocus,
                                        gridState = gridState,
                                        rowState = gamesRowState,
                                        compactLandscape = isCompactLandscape,
                                        compactViewportHeight = pageMaxHeight,
                                        onSwipeToApps = {
                                            scope.launch { pagerState.animateScrollToPage(0) }
                                        },
                                        onGameClick = { game ->
                                            viewModel.setFocusedItem(game.id)
                                            viewModel.launchItem(game)
                                        },
                                        onGameLongClick = { game ->
                                            viewModel.openContextMenu(game)
                                        },
                                        onGameFocus = { game ->
                                            viewModel.setFocusedItem(game.id)
                                        }
                                    )
                                }
                            }

                            SnackbarHost(
                                hostState = gamesSnackbarHostState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("games_refresh_snackbar")
                            )
                        }
                    } else {
                        // Apps View: a full-height favorites home. Swiping up reveals the
                        // complete app list, which excludes favorites and games.
                        // Confine directional focus navigation so D-pad / stick right cannot
                        // escape to page 1 (Games view).
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isCompactLandscape) Modifier.displayCutoutPadding() else Modifier
                                )
                                .focusGroup()
                                .focusProperties {
                                    exit = { direction ->
                                        if (direction == FocusDirection.Right || direction == FocusDirection.Next) {
                                            FocusRequester.Cancel
                                        } else {
                                            FocusRequester.Default
                                        }
                                    }
                                }
                        ) {
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
                                        weatherEnabled = weatherEnabled,
                                        weather = weatherInfo,
                                        temperatureUnit = temperatureUnit,
                                        firstItemFocusRequester = favoritesFirstFocusRequester,
                                        onLaunchFavorite = { app ->
                                            viewModel.setFocusedItem(app.id)
                                            viewModel.launchItem(app)
                                        },
                                        onLongPressFavorite = { app ->
                                            viewModel.openFavoriteActions(app)
                                        },
                                        onOpenClock = { viewModel.openClockApp() },
                                        onOpenCalendar = { viewModel.openCalendarApp() },
                                        onOpenWeather = { viewModel.openWeatherApp() },
                                        onPullDown = { viewModel.expandNotificationShade() },
                                        onFocusFavorite = { app -> viewModel.setFocusedItem(app.id) },
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
                                            firstItemFocusRequester = appsListFirstFocusRequester,
                                            onAppClick = { app ->
                                                viewModel.setFocusedItem(app.id)
                                                viewModel.launchItem(app)
                                            },
                                            onAppLongClick = { app ->
                                                viewModel.openContextMenu(app)
                                            },
                                            onAppFocus = { app ->
                                                viewModel.setFocusedItem(app.id)
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
            }

            // Context Menu Modal (Tap and Hold on any card)
            if (contextMenuItem != null) {
                ItemContextMenuSheet(
                    item = contextMenuItem!!,
                    showNsfwPoster = steamGridDbIncludeNsfw,
                    canDelete = canDeleteContextMenuItem,
                    onCustomize = { viewModel.openEditCustomizer(contextMenuItem!!) },
                    onMove = {
                        if (contextMenuItem!!.isGame) {
                            viewModel.moveItemToApps(contextMenuItem!!)
                        } else {
                            viewModel.moveItemToGames(contextMenuItem!!)
                        }
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(contextMenuItem!!) },
                    onRemoveFromRecents = { viewModel.removeFromRecents(contextMenuItem!!) },
                    onOpenAppInfo = { viewModel.openAppInfo(contextMenuItem!!) },
                    onHide = { viewModel.hideItem(contextMenuItem!!) },
                    onDelete = { viewModel.deleteItem(contextMenuItem!!) },
                    onDismiss = { viewModel.closeContextMenu() }
                )
            }

            // Long-press menu for favorites (Customize, App Info, Edit favorites, Remove)
            favoriteActionItem?.let { favorite ->
                FavoriteActionsSheet(
                    item = favorite,
                    onCustomize = { viewModel.openEditCustomizer(favorite) },
                    onOpenAppInfo = { viewModel.openAppInfo(favorite) },
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

            // Customize Name & Artwork Dialog
            if (editingItem != null) {
                val item = editingItem!!
                val selectedPackDrawable = if (!item.isGame && selectedIconPack != null) {
                    selectedPackOverrides[item.id]
                } else {
                    null
                }
                val canResetIcon = !item.isGame && (
                    selectedPackDrawable != null ||
                        selectedCustomUrlOverrides.containsKey(item.id) ||
                        hasCustomAppIconUrl(item.iconUrl)
                    )
                CustomizeItemDialog(
                    item = item,
                    steamGridDbApiKey = steamGridDbApiKey,
                    includeNsfw = steamGridDbIncludeNsfw,
                    iconPack = installedIconPacks.firstOrNull {
                        it.packageName == selectedIconPack
                    },
                    activeThemeKey = IconPackOverridesStore.themeKey(selectedIconPack),
                    iconPackIcons = iconPackIcons,
                    selectedPackDrawable = selectedPackDrawable,
                    canResetIcon = canResetIcon,
                    onApplyTitle = { id, newTitle ->
                        viewModel.applyItemTitle(id, newTitle)
                    },
                    onApplyPoster = { id, newTitle, isNsfwSlot, artworkUrl ->
                        viewModel.applyItemPoster(id, newTitle, isNsfwSlot, artworkUrl)
                    },
                    onSetOverlayLogo = { id, newTitle, isNsfwSlot, enabled ->
                        viewModel.setItemLogoOverlay(id, newTitle, isNsfwSlot, enabled)
                    },
                    onUpdateLogoUrl = { id, isNsfwSlot, logoUrl ->
                        viewModel.updateItemLogoUrl(id, isNsfwSlot, logoUrl)
                    },
                    onSelectIconPackIcon = { id, newTitle, drawableName ->
                        viewModel.applyItemIconFromPack(id, newTitle, drawableName)
                    },
                    onResetIcon = { id ->
                        viewModel.resetItemIcon(id)
                    },
                    onDismiss = { viewModel.closeEditCustomizer() }
                )
            }

            // Full Page Discover Screen (+ Button)
            AnimatedVisibility(
                visible = showAddModal,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it })
            ) {
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

            // Full Page Settings Screen
            AnimatedVisibility(
                visible = showSettingsModal,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                SettingsModal(
                    favoritesLayout = favoritesLayout,
                    steamGridDbApiKey = steamGridDbApiKey,
                    steamGridDbIncludeNsfw = steamGridDbIncludeNsfw,
                    iconPacks = installedIconPacks,
                    selectedIconPack = selectedIconPack,
                    hiddenItems = hiddenItems,
                    appsBackgroundOpacity = appsBackgroundOpacity,
                    themeStyle = themeStyle,
                    weatherEnabled = weatherEnabled,
                    weatherMode = weatherMode,
                    weatherZip = weatherZip,
                    weatherInfo = weatherInfo,
                    weatherError = weatherError,
                    temperatureUnit = temperatureUnit,
                    weatherApps = weatherApps,
                    weatherAppPackage = weatherAppPackage,
                    librarySyncBusy = librarySyncBusy,
                    librarySyncStatus = librarySyncStatus,
                    librarySyncConfigured = librarySyncConfigured,
                    librarySyncTarget = librarySyncTarget,
                    librarySyncLastSyncedAt = librarySyncLastSyncedAt,
                    isDefaultLauncher = { viewModel.isDefaultLauncher() },
                    isHomeOverrideEnabled = { viewModel.isHomeOverrideEnabled() },
                    onSelectFavoritesLayout = { viewModel.setFavoritesLayout(it) },
                    onSaveApiKey = { viewModel.saveSteamGridDbApiKey(it) },
                    onSteamGridDbIncludeNsfwChange = { viewModel.setSteamGridDbIncludeNsfw(it) },
                    onSelectIconPack = { viewModel.setIconPack(it) },
                    onSetDefaultLauncher = { viewModel.openDefaultLauncherSettings() },
                    onSetAppsBackgroundOpacity = { viewModel.setAppsBackgroundOpacity(it) },
                    onSelectThemeStyle = { viewModel.setThemeStyle(it) },
                    onUnhideItem = { viewModel.unhideItem(it) },
                    onOpenHiddenItem = { viewModel.launchItem(it) },
                    onWeatherEnabledChange = { viewModel.setWeatherEnabled(it) },
                    onWeatherModeChange = { viewModel.setWeatherMode(it) },
                    onWeatherZipChange = { viewModel.setWeatherZip(it) },
                    onTemperatureUnitChange = { viewModel.setTemperatureUnit(it) },
                    onSelectWeatherApp = { viewModel.setWeatherAppPackage(it) },
                    onOpenAccessibilitySettings = { viewModel.openAccessibilitySettings() },
                    onChooseSyncFolder = { viewModel.chooseSyncFolder(it) },
                    onSyncLibraryNow = { viewModel.syncLibraryNow() },
                    onDisconnectLibrarySync = { viewModel.disconnectLibrarySync() },
                    onDismiss = { viewModel.closeSettingsModal() }
                )
            }

            // Shown when the weather shortcut is tapped and several weather apps are
            // installed without a saved choice. The selection is remembered in Settings.
            weatherAppChooser?.let { apps ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissWeatherAppChooser() },
                    title = { Text("Open weather with") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            apps.forEach { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.chooseWeatherApp(app.packageName) }
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                        .testTag("weather_chooser_${app.packageName}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = app.label,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissWeatherAppChooser() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun BindLibraryPagerAndScrollEffects(
    currentTab: MainLibraryTab,
    appsHomeReset: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    appsPagerState: androidx.compose.foundation.pager.PagerState,
    gridState: LazyGridState,
    gamesRowState: LazyListState,
    appsListState: LazyListState,
    appsGridState: LazyGridState,
    onTabFromPager: (MainLibraryTab) -> Unit
) {
    // Track whether the Games page or the Apps drawer is any part visible in its pager.
    val isGamesInView by remember {
        derivedStateOf {
            pagerState.currentPage == 1 ||
                (pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction > 0f)
        }
    }
    val isAppsInView by remember {
        derivedStateOf {
            pagerState.currentPage == 0 ||
                (pagerState.currentPage == 1 && pagerState.currentPageOffsetFraction < 0f)
        }
    }
    val isAppDrawerInView by remember {
        derivedStateOf {
            isAppsInView && (
                appsPagerState.currentPage == 1 ||
                    (appsPagerState.currentPage == 0 && appsPagerState.currentPageOffsetFraction > 0f)
                )
        }
    }

    // Anytime the Games grid leaves or enters view, ensure both its grid and compact row
    // are positioned at the top so it is already at index 0 as soon as it appears.
    LaunchedEffect(isGamesInView) {
        if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
            gridState.scrollToItem(0)
        }
        if (gamesRowState.firstVisibleItemIndex != 0 || gamesRowState.firstVisibleItemScrollOffset != 0) {
            gamesRowState.scrollToItem(0)
        }
    }

    // Anytime the App drawer leaves or enters view, ensure both list and icon-grid states
    // are positioned at the top so the drawer is already scrolled to the top when opened.
    LaunchedEffect(isAppDrawerInView) {
        if (appsListState.firstVisibleItemIndex != 0 || appsListState.firstVisibleItemScrollOffset != 0) {
            appsListState.scrollToItem(0)
        }
        if (appsGridState.firstVisibleItemIndex != 0 || appsGridState.firstVisibleItemScrollOffset != 0) {
            appsGridState.scrollToItem(0)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val tab = if (pagerState.currentPage == 0) MainLibraryTab.APPS else MainLibraryTab.GAMES
        onTabFromPager(tab)
    }

    // Close the app drawer whenever switching to/from Games (by tap or horizontal swipe).
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage == 1 && appsPagerState.currentPage != 0) {
            appsPagerState.scrollToPage(0)
        }
    }

    LaunchedEffect(currentTab) {
        val target = if (currentTab == MainLibraryTab.GAMES) 1 else 0
        // Close the app drawer immediately before sliding back to Apps, or close it right away
        // when switching tabs so switching between Apps and Games never leaves the drawer open.
        if (appsPagerState.currentPage != 0) {
            appsPagerState.scrollToPage(0)
        }
        if (target == 1) {
            if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
                gridState.scrollToItem(0)
            }
            if (gamesRowState.firstVisibleItemIndex != 0 || gamesRowState.firstVisibleItemScrollOffset != 0) {
                gamesRowState.scrollToItem(0)
            }
        }
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Selecting the Apps tab always returns to the favorites home, even if the vertical
    // pager was left on the complete app list. Jump rather than animate so the favorites
    // page is present on the first frame of the transition.
    LaunchedEffect(appsHomeReset) {
        if (appsHomeReset > 0 && appsPagerState.currentPage != 0) {
            appsPagerState.scrollToPage(0)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GamesPosterGrid(
    games: List<GameItem>,
    focusedItemId: String?,
    showFocusEffects: Boolean,
    showNsfwPoster: Boolean,
    isGamesTabOpen: Boolean,
    isGamesPage: Boolean = false,
    controllerConnected: Boolean = false,
    holdFocus: Boolean = false,
    gridState: LazyGridState,
    rowState: LazyListState,
    compactLandscape: Boolean,
    compactViewportHeight: Dp = 0.dp,
    onSwipeToApps: () -> Unit = {},
    onGameClick: (GameItem) -> Unit,
    onGameLongClick: (GameItem) -> Unit,
    onGameFocus: (GameItem) -> Unit = {}
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val focusedItemFocusRequester = remember { FocusRequester() }
    // Tracks whether any poster in this grid currently owns focus, so the grid can pull
    // focus back if the top bar takes it while a controller is connected.
    var hasGridFocus by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val navigationBarInset =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // The grid draws behind the app-switcher bar, so keep focused posters that extra
    // distance above the viewport edge in addition to a small scale/outline margin.
    val focusMarginPx = with(density) { (10.dp + navigationBarInset).toPx() }

    // Reserve the focus margin as part of the scroll container's own bring-into-view
    // behaviour. This makes focus scrolling settle at the margin in one spring animation
    // instead of snapping there with a second scrollBy pass after focus search finishes.
    // Only the trailing (bottom) edge gets the margin: keeping the leading edge flush
    // stops an extra row peeking in when focus moves upwards.
    val focusBringIntoViewSpec = remember(focusMarginPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float {
                val leadingEdge = offset
                val trailingEdge = offset + size
                val safeTrailingEdge = containerSize - focusMarginPx
                return when {
                    // Already fully inside the safe area.
                    leadingEdge >= 0f && trailingEdge <= safeTrailingEdge -> 0f
                    // Taller than the viewport: don't chase it.
                    leadingEdge < 0f && trailingEdge > containerSize -> 0f
                    // The top is clipped, so reveal it (default behaviour, no top margin).
                    leadingEdge < 0f -> leadingEdge
                    // The bottom would sit under the app-switcher bar: scroll just enough.
                    trailingEdge > safeTrailingEdge -> trailingEdge - safeTrailingEdge
                    else -> 0f
                }
            }
        }
    }

    // When the Games grid opens, focus the first game so an active element is visibly
    // highlighted and controller navigation starts from the top-left item. The pager
    // only reports isGamesTabOpen after it has settled, but retry briefly in case the
    // focus system briefly parks on the navigation bar while the page changes.
    var focusedForCurrentOpen by remember { mutableStateOf(false) }
    LaunchedEffect(
        isGamesPage,
        isGamesTabOpen,
        games.isNotEmpty(),
        controllerConnected,
        holdFocus
    ) {
        if (!isGamesPage || !controllerConnected) {
            // Left the Games view (or no controller), so the next visit focuses the
            // first poster again.
            focusedForCurrentOpen = false
            return@LaunchedEffect
        }
        if (!holdFocus || !isGamesTabOpen || games.isEmpty() || focusedForCurrentOpen) {
            return@LaunchedEffect
        }
        focusedForCurrentOpen = true
        val firstGame = games.first()
        onGameFocus(firstGame)
        if (compactLandscape) {
            runCatching { rowState.scrollToItem(0) }
        } else {
            runCatching { gridState.scrollToItem(0) }
        }
        repeat(3) {
            delay(80)
            runCatching { firstItemFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(focusedItemId, compactLandscape) {
        val idx = focusedItemId?.let { id -> games.indexOfFirst { it.id == id } } ?: -1
        if (idx < 0) return@LaunchedEffect

        if (compactLandscape) {
            // The content padding keeps the focused poster just to the right of the
            // camera cutout while earlier posters can scroll underneath it.
            runCatching { rowState.animateScrollToItem(idx) }
        }
        // In the grid, the custom BringIntoViewSpec above already keeps the focused
        // poster clear of the app-switcher bar as part of the focus scroll animation.
    }

    // The Games grid owns focus while a controller is connected. Initial composition can
    // park focus on the top bar (its buttons are focusable), so pull it back to the
    // poster that had it, or to the first poster on a fresh entry.
    LaunchedEffect(holdFocus, hasGridFocus, focusedItemId, games) {
        if (!holdFocus || hasGridFocus || games.isEmpty()) return@LaunchedEffect
        delay(60)
        // A focus move between posters can briefly report no focus; re-check before
        // pulling focus back so normal navigation is never interrupted.
        if (hasGridFocus) return@LaunchedEffect
        val targetId = focusedItemId?.takeIf { id -> games.any { it.id == id } }
            ?: games.first().id
        val targetIndex = games.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return@LaunchedEffect
        if (compactLandscape) {
            runCatching { rowState.scrollToItem(targetIndex) }
        } else {
            runCatching { gridState.scrollToItem(targetIndex) }
        }
        val requester = if (targetId == games.first().id) {
            firstItemFocusRequester
        } else {
            focusedItemFocusRequester
        }
        repeat(3) {
            runCatching { requester.requestFocus() }
            delay(80)
            if (hasGridFocus) return@LaunchedEffect
        }
    }

    if (compactLandscape) {
        // Small landscape screens: a single horizontal row that steps one poster at a
        // time. The row is centered vertically and does not fill the screen height, so a
        // horizontal drag above/below the posters can still switch between Apps/Games.
        val snapFling = rememberSnapFlingBehavior(lazyListState = rowState)
        val cutoutPadding = WindowInsets.displayCutout.asPaddingValues()
        val navigationPadding = WindowInsets.navigationBars.asPaddingValues()
        val startInset = cutoutPadding.calculateLeftPadding(layoutDirection) +
            navigationPadding.calculateLeftPadding(layoutDirection)
        val endInset = cutoutPadding.calculateRightPadding(layoutDirection) +
            navigationPadding.calculateRightPadding(layoutDirection)
        val topInset = navigationPadding.calculateTopPadding()
        val bottomInset = navigationPadding.calculateBottomPadding()
        val swipeThreshold = with(density) { 48.dp.toPx() }
        val swipeToApps by rememberUpdatedState(onSwipeToApps)
        // Bounds of the poster row relative to this Box. Drags that start on the row
        // belong to the row's own scrolling, even when it cannot scroll further.
        var rowBounds by remember { mutableStateOf(Rect.Zero) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .onFocusChanged { hasGridFocus = it.hasFocus }
                .then(
                    if (compactViewportHeight > 0.dp) {
                        Modifier.height(compactViewportHeight)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Never turn a drag on the posters into a page swipe.
                        if (rowBounds.contains(down.position)) return@awaitEachGesture
                        var totalX = 0f
                        var totalY = 0f
                        var claimed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // Another child (e.g. a scrollable) owns this gesture.
                            if (change.isConsumed) break
                            totalX += change.position.x - change.previousPosition.x
                            totalY += change.position.y - change.previousPosition.y
                            if (!claimed &&
                                totalX > swipeThreshold &&
                                abs(totalX) > abs(totalY)
                            ) {
                                claimed = true
                                change.consume()
                            } else if (claimed) {
                                change.consume()
                            }
                        }
                        if (claimed && totalX > 0f) swipeToApps()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            LazyRow(
                state = rowState,
                flingBehavior = snapFling,
                contentPadding = PaddingValues(
                    start = 20.dp + startInset,
                    top = 10.dp + topInset,
                    end = 28.dp + endInset,
                    bottom = 10.dp + bottomInset
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { rowBounds = it.boundsInParent() }
                    .testTag("games_poster_row")
            ) {
                itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                    val isFocused = game.id == focusedItemId && showFocusEffects
                    val focusRequesterModifier = when {
                        index == 0 -> Modifier.focusRequester(firstItemFocusRequester)
                        game.id == focusedItemId ->
                            Modifier.focusRequester(focusedItemFocusRequester)
                        else -> Modifier
                    }

                    SteamGamePosterCard(
                        game = game,
                        isFocused = isFocused,
                        showFocusOutline = false,
                        showNsfwPoster = showNsfwPoster,
                        onClick = { onGameClick(game) },
                        onLongClick = { onGameLongClick(game) },
                        onFocus = { onGameFocus(game) },
                        modifier = Modifier
                            .then(focusRequesterModifier)
                            .then(if (index == 0) Modifier.focusProperties { left = FocusRequester.Cancel } else Modifier)
                            .width(if (isFocused) 168.dp else 112.dp)
                    )
                }
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .onFocusChanged { hasGridFocus = it.hasFocus }
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

        CompositionLocalProvider(LocalBringIntoViewSpec provides focusBringIntoViewSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                state = gridState,
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 12.dp,
                    // Keep the last row clear of the app-switcher bar. The extra margin
                    // also leaves room for the focus scale/outline when the row is focused.
                    bottom = 20.dp + navigationBarInset
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("games_poster_grid")
            ) {
                itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                    val isLeftmost = index % columnCount == 0
                    val focusRequesterModifier = when {
                        index == 0 -> Modifier.focusRequester(firstItemFocusRequester)
                        game.id == focusedItemId ->
                            Modifier.focusRequester(focusedItemFocusRequester)
                        else -> Modifier
                    }
                    SteamGamePosterCard(
                        game = game,
                        isFocused = game.id == focusedItemId && showFocusEffects,
                        showNsfwPoster = showNsfwPoster,
                        onClick = { onGameClick(game) },
                        onLongClick = { onGameLongClick(game) },
                        onFocus = { onGameFocus(game) },
                        modifier = Modifier
                            .then(focusRequesterModifier)
                            .then(if (isLeftmost) Modifier.focusProperties { left = FocusRequester.Cancel } else Modifier)
                    )
                }
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
    firstItemFocusRequester: FocusRequester? = null,
    onAppClick: (GameItem) -> Unit,
    onAppLongClick: (GameItem) -> Unit,
    onLetterSelected: (String) -> Unit,
    onAppFocus: (GameItem) -> Unit = {},
) {
    val letters = remember(entries) {
        entries.filterIsInstance<AppListEntry.Letter>().map { it.letter }
    }
    val firstAppId = remember(entries) {
        entries.filterIsInstance<AppListEntry.App>().firstOrNull()?.item?.id
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
                                onFocus = { onAppFocus(entry.item) },
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (entry.item.id == firstAppId) {
                                            firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                ?: Modifier
                                        } else {
                                            Modifier
                                        }
                                    )
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
                                onFocus = { onAppFocus(entry.item) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (entry.item.id == firstAppId) {
                                            firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                ?: Modifier
                                        } else {
                                            Modifier
                                        }
                                    )
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
    val letterSpacing = 6.dp
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
    onAddClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
