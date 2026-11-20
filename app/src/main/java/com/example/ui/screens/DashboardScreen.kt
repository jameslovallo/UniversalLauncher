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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import androidx.compose.ui.layout.ContentScale
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.IconPackOverridesStore
import com.example.ui.components.AddContentModal
import com.example.ui.components.AppIconTile
import com.example.ui.components.AppTextRow
import com.example.ui.components.AppTvCard
import com.example.ui.components.CustomizeItemDialog
import com.example.ui.components.EditFavoritesSheet
import com.example.ui.components.FavoriteActionsSheet
import com.example.ui.components.FavoritesHome
import com.example.ui.components.FolderAppsManageSheet
import com.example.ui.components.FolderContentsSheet
import com.example.ui.components.FolderPickerSheet
import com.example.ui.components.ItemContextMenuSheet
import com.example.ui.components.LocalFolderPreviewItems
import com.example.ui.components.LocalThemedAppIcons
import com.example.ui.components.SettingsModal
import com.example.ui.components.SteamGamePosterCard
import com.example.ui.components.TV_FAVORITES_MAX_COLUMNS
import com.example.ui.components.TopNavBar
import com.example.ui.components.buildAppBannerImageRequest
import com.example.ui.components.buildAppIconImageRequest
import com.example.ui.components.buildPosterImageRequest
import com.example.ui.components.buildPosterLogoImageRequest
import com.example.ui.components.flatGridDownNextRowFallback
import com.example.ui.components.hasCustomAppIconUrl
import com.example.ui.components.isTelevisionMode
import com.example.ui.components.resolveAppIconUrl
import com.example.ui.components.shouldShowFocusEffects
import com.example.ui.components.tvFocusBorder
import com.example.ui.viewmodel.AddModalSubView
import com.example.ui.viewmodel.LauncherViewModel
import com.example.ui.viewmodel.MainLibraryTab
import com.example.ui.theme.FavoritesLayout
import com.example.ui.theme.ThemeStyle
import com.example.ui.theme.resolveEffectiveFavoritesLayout
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Applied around the library pagers so focus can never scroll them. On TV the platform
 * bring-into-view spec always pivots a focused child to 30% of the container, and the pager
 * rounds that request up to a whole page. That turned every focus request into a page
 * scroll, which is what bounced Apps and Games back and forth at startup. Page content
 * re-provides the ambient spec, so the lists inside each page keep their normal focus
 * scrolling; only the pager itself refuses to move.
 */
@OptIn(ExperimentalFoundationApi::class)
private val NoOpBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float = 0f
}

/**
 * The TV default spec pivots a focused item to 30% of its container even when that item is
 * already fully visible. In the app list that produces a pointless scroll on every focus
 * change, and because the list is at its top the leftover scroll is dispatched as a nested
 * scroll that the vertical pager consumes by flipping back a page. Only scroll when the item
 * actually needs to be revealed.
 */
@OptIn(ExperimentalFoundationApi::class)
private class VisibleAwareBringIntoViewSpec(
    private val delegate: BringIntoViewSpec
) : BringIntoViewSpec {
    override val scrollAnimationSpec get() = delegate.scrollAnimationSpec

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float
    ): Float {
        val alreadyVisible = offset >= 0f && offset + size <= containerSize
        return if (alreadyVisible) {
            0f
        } else {
            delegate.calculateScrollDistance(offset, size, containerSize)
        }
    }
}

/** The library views that can own D-pad focus. Exactly one is focusable at a time. */
private enum class LibraryFocusTarget { FAVORITES, APP_LIST, GAMES }

/** The library item an overlay was opened from, with its position when it opened. */
private data class LibraryFocusReturn(val itemId: String, val index: Int)

/**
 * Focus requesters for individual library cards, keyed by item id. They let focus return to
 * the card an overlay was opened from, or to a neighbouring card when that item left the
 * view (for example after "Add to favorites").
 */
private class ItemFocusRequesters {
    private val requesters = mutableMapOf<String, FocusRequester>()

    fun requesterFor(id: String): FocusRequester = requesters.getOrPut(id) { FocusRequester() }

    /** Requests focus for [id]; false when that card is not composed or not focusable. */
    fun requestFocus(id: String): Boolean {
        val requester = requesters[id] ?: return false
        return runCatching { requester.requestFocus() }.isSuccess
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val contextMenuItem by viewModel.contextMenuItem.collectAsStateWithLifecycle()
    val canDeleteContextMenuItem by viewModel.canDeleteContextMenuItem.collectAsStateWithLifecycle()
    val editingItem by viewModel.editingItem.collectAsStateWithLifecycle()
    val showAddModal by viewModel.showAddModal.collectAsStateWithLifecycle()
    val showSettingsModal by viewModel.showSettingsModal.collectAsStateWithLifecycle()
    val favoriteActionItem by viewModel.favoriteActionItem.collectAsStateWithLifecycle()
    val showFavoritesEditor by viewModel.showFavoritesEditor.collectAsStateWithLifecycle()
    val foldersList by viewModel.foldersList.collectAsStateWithLifecycle()
    val folderMembersMap by viewModel.folderMembersMap.collectAsStateWithLifecycle()
    val openFolderId by viewModel.openFolderId.collectAsStateWithLifecycle()
    val folderPickerTargetItem by viewModel.folderPickerTargetItem.collectAsStateWithLifecycle()
    val manageFolderId by viewModel.manageFolderId.collectAsStateWithLifecycle()

    val favoritesLayout by viewModel.favoritesLayout.collectAsStateWithLifecycle()
    val steamGridDbIncludeNsfw by viewModel.steamGridDbIncludeNsfw.collectAsStateWithLifecycle()
    val themedAppIcons by viewModel.themedAppIcons.collectAsStateWithLifecycle()

    val controllerState by viewModel.controllerManager.controllerState.collectAsStateWithLifecycle()

    val appsBackgroundOpacity by viewModel.appsBackgroundOpacity.collectAsStateWithLifecycle()
    val tvWallpaperUrl by viewModel.tvWallpaperUrl.collectAsStateWithLifecycle()
    val appGridColumns by viewModel.appGridColumns.collectAsStateWithLifecycle()
    val weatherEnabled by viewModel.weatherEnabled.collectAsStateWithLifecycle()
    val weatherInfo by viewModel.weatherInfo.collectAsStateWithLifecycle()
    val temperatureUnit by viewModel.temperatureUnit.collectAsStateWithLifecycle()
    val weatherAppChooser by viewModel.weatherAppChooser.collectAsStateWithLifecycle()
    val gamesRefreshing by viewModel.gamesRefreshing.collectAsStateWithLifecycle()
    val gamesRefreshFeedback by viewModel.gamesRefreshFeedback.collectAsStateWithLifecycle()

    // Artwork backfills and the weather lookup are not needed to draw the library, so hold
    // them until a frame is on screen. Running them during startup stole IO from the first
    // render and made cold start feel slow on TV hardware.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        viewModel.onHomeScreenReady()
    }

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
    // Skip the very first ON_RESUME on cold start so it does not defeat onHomeScreenReady()
    // or duplicate LauncherViewModel.init's initial app scan. Live package installs/removals
    // are handled via LauncherApps.Callback; subsequent resumes only run a 60s fallback check.
    var hasCompletedInitialResume by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isLauncherActive = viewModel.isDefaultLauncher() || viewModel.isHomeOverrideEnabled()
        if (hasCompletedInitialResume) {
            viewModel.refreshInstalledApps(LauncherViewModel.RESUME_FALLBACK_SCAN_INTERVAL_MS)
            viewModel.refreshWeather()
            viewModel.refreshWeatherAppsIfStale()
        } else {
            hasCompletedInitialResume = true
        }
    }

    // Phones held sideways get a compact horizontal poster row instead of a grid; TVs keep
    // centered clock/weather/16:9 cards and always use TV_CARDS layout exclusively.
    val configuration = LocalConfiguration.current
    val isTelevision = isTelevisionMode()
    val effectiveFavoritesLayout = resolveEffectiveFavoritesLayout(favoritesLayout, isTelevision)
    val showFocusEffects = shouldShowFocusEffects(controllerState.isConnected)
    val isCompactLandscape =
        !isTelevision &&
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            configuration.screenHeightDp < 480

    var showSearchBar by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val appsGridState = rememberLazyGridState()
    val gamesRowState = rememberLazyListState()
    val appsListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Requesters for the first item of each view. Focus is requested only after the pager
    // has settled so BringIntoView never fights an in-flight page transition.
    val favoritesFirstFocusRequester = remember { FocusRequester() }
    val appsListFirstFocusRequester = remember { FocusRequester() }
    val gamesFirstFocusRequester = remember { FocusRequester() }
    val navFirstFocusRequester = remember { FocusRequester() }

    // Filter each page by the search query. Keeping both lists separate means swiping
    // between pages shows the correct results even before the tab state catches up.
    val gamesDisplay = remember(gamesList, searchQuery) {
        if (searchQuery.isBlank()) {
            gamesList
        } else {
            gamesList.filter { item ->
                item.title.contains(searchQuery, ignoreCase = true) ||
                    item.genre.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    // The complete app list excludes favorites and games.
    val libraryDisplay = remember(libraryApps, searchQuery) {
        if (searchQuery.isBlank()) {
            libraryApps
        } else {
            libraryApps.filter { item ->
                item.title.contains(searchQuery, ignoreCase = true) ||
                    item.genre.contains(searchQuery, ignoreCase = true)
            }
        }
    }
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

    // Captured before the pagers override it with [NoOpBringIntoViewSpec] so each page can
    // hand the real focus-scrolling behaviour back to its inner lists.
    val ambientBringIntoViewSpec = LocalBringIntoViewSpec.current
    val drawerBringIntoViewSpec = remember(ambientBringIntoViewSpec) {
        VisibleAwareBringIntoViewSpec(ambientBringIntoViewSpec)
    }

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
                if (appsPagerState.targetPage != 0) {
                    scope.launch { appsPagerState.animateScrollToPage(0) }
                }
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

    val noOverlayOpen = contextMenuItem == null &&
        favoriteActionItem == null &&
        !showFavoritesEditor &&
        openFolderId == null &&
        folderPickerTargetItem == null &&
        manageFolderId == null &&
        editingItem == null &&
        !showAddModal &&
        !showSettingsModal &&
        weatherAppChooser == null

    // Per-card focus requesters, so focus can return to the item an overlay was opened from.
    val favoritesItemRequesters = remember { ItemFocusRequesters() }
    val appsItemRequesters = remember { ItemFocusRequesters() }
    val gamesItemRequesters = remember { ItemFocusRequesters() }
    val favoritesItemFocusRequester = remember {
        { id: String -> favoritesItemRequesters.requesterFor(id) }
    }
    val appsItemFocusRequester = remember {
        { id: String -> appsItemRequesters.requesterFor(id) }
    }
    val gamesItemFocusRequester = remember {
        { id: String -> gamesItemRequesters.requesterFor(id) }
    }

    val activeLibraryItems = when {
        pagerState.settledPage == 1 -> gamesDisplay
        appsPagerState.settledPage == 1 -> libraryDisplay
        else -> favoriteApps
    }

    // The item an overlay was opened from, plus its position at that time. When the overlay
    // closes, focus returns to that item, or to its neighbour when the item left the view
    // (for example after "Add to favorites"). Without this the focus system falls back to the
    // first focusable node in the tree, which is the top bar's Add button.
    val overlaySourceItemId = manageFolderId
        ?: openFolderId
        ?: folderPickerTargetItem?.id
        ?: editingItem?.id
        ?: contextMenuItem?.id
        ?: favoriteActionItem?.id
    var focusReturn by remember { mutableStateOf<LibraryFocusReturn?>(null) }
    LaunchedEffect(overlaySourceItemId) {
        val id = overlaySourceItemId ?: return@LaunchedEffect
        focusReturn = LibraryFocusReturn(id, activeLibraryItems.indexOfFirst { it.id == id })
    }

    fun requestLibraryFocus(target: LibraryFocusReturn): Boolean {
        val requesters = when {
            pagerState.settledPage == 1 -> gamesItemRequesters
            appsPagerState.settledPage == 1 -> appsItemRequesters
            else -> favoritesItemRequesters
        }
        val candidates = buildList {
            // The item itself, then the previous item, the one that moved into its slot, and
            // finally the first item of the view.
            if (activeLibraryItems.any { it.id == target.itemId }) add(target.itemId)
            activeLibraryItems.getOrNull(target.index - 1)?.let { add(it.id) }
            activeLibraryItems.getOrNull(target.index)?.let { add(it.id) }
            activeLibraryItems.firstOrNull()?.let { add(it.id) }
        }
        if (candidates.any { requesters.requestFocus(it) }) return true
        val fallback = when {
            pagerState.settledPage == 1 -> gamesFirstFocusRequester
            appsPagerState.settledPage == 1 -> appsListFirstFocusRequester
            else -> favoritesFirstFocusRequester
        }
        return runCatching { fallback.requestFocus() }.isSuccess
    }

    // Room writes are asynchronous, so the list may still show the old contents when the
    // overlay closes. Repeat the request briefly until a card in the active view reports
    // focus (the page containers clear the pending target when that happens).
    LaunchedEffect(noOverlayOpen, focusReturn, activeLibraryItems) {
        val target = focusReturn ?: return@LaunchedEffect
        if (!noOverlayOpen) return@LaunchedEffect
        // Ask straight away: the frame that removes the sheet and the item must already show
        // the neighbour focused, otherwise that frame falls back to the top bar's Add button.
        requestLibraryFocus(target)
        repeat(7) {
            withFrameNanos { }
            if (focusReturn !== target) return@LaunchedEffect
            requestLibraryFocus(target)
        }
        if (focusReturn === target) focusReturn = null
    }

    // Move focus to the first item of the active view once the pager transition is complete.
    // Focusing mid-transition would make BringIntoView fight the pager animation, which is
    // what caused the left/right shake on TV at first open.
    var focusedViewKey by remember { mutableStateOf<Triple<Int, Int, String?>?>(null) }
    // Which library view owns focus. Exactly one view is focusable once the pagers are idle.
    var focusedLibraryView by remember { mutableStateOf(LibraryFocusTarget.FAVORITES) }
    // The view that is giving up focus ownership. It stays focusable until the incoming view
    // actually holds focus, otherwise the focus system has no valid target for one frame and
    // falls back to the top navigation bar.
    var handingOffFrom by remember { mutableStateOf<LibraryFocusTarget?>(null) }
    // True while the top navigation bar has focus. The library views remember the last
    // focused tile in the ViewModel (for context-menu actions and scroll position), so
    // without this flag they keep drawing the focus highlight after focus moves up to the
    // navigation. The id itself is left untouched.
    var navBarFocused by remember { mutableStateOf(false) }
    // Any pager motion invalidates the current view's focus claim. Focus is re-requested by
    // the settle effect below once both pagers are idle again.
    LaunchedEffect(pagerState.isScrollInProgress, appsPagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress || appsPagerState.isScrollInProgress) {
            focusedViewKey = null
            // While a pager moves, focusability follows focusedLibraryView, so a pending
            // handoff is no longer needed.
            handingOffFrom = null
        }
    }
    // Switch focus ownership between library views. The outgoing view stays focusable until
    // the incoming view reports focus, so focus is never orphaned onto the top navigation bar.
    fun handFocusTo(target: LibraryFocusTarget) {
        if (focusedLibraryView != target) {
            handingOffFrom = focusedLibraryView
            focusedLibraryView = target
        }
    }
    LaunchedEffect(
        showFocusEffects,
        noOverlayOpen,
        pagerState.settledPage,
        pagerState.isScrollInProgress,
        appsPagerState.settledPage,
        appsPagerState.isScrollInProgress,
        effectiveFavoritesLayout,
        favoriteApps.firstOrNull()?.id,
        gamesDisplay.firstOrNull()?.id,
        appListEntries.firstOrNull { it is AppListEntry.App },
    ) {
        if (!showFocusEffects || !noOverlayOpen) return@LaunchedEffect
        if (pagerState.isScrollInProgress || appsPagerState.isScrollInProgress) return@LaunchedEffect
        if (abs(pagerState.currentPageOffsetFraction) > 0.01f ||
            abs(appsPagerState.currentPageOffsetFraction) > 0.01f
        ) {
            return@LaunchedEffect
        }

        if (pagerState.settledPage == 1) {
            val firstGame = gamesDisplay.firstOrNull() ?: return@LaunchedEffect
            val key = Triple(1, 0, firstGame.id)
            if (key == focusedViewKey) return@LaunchedEffect
            focusedViewKey = key
            handFocusTo(LibraryFocusTarget.GAMES)
            viewModel.setFocusedItem(firstGame.id)
            withFrameNanos { }
            if (pagerState.isScrollInProgress || appsPagerState.isScrollInProgress) {
                return@LaunchedEffect
            }
            runCatching { gamesFirstFocusRequester.requestFocus() }
        } else if (appsPagerState.settledPage == 0) {
            val firstFavorite = favoriteApps.firstOrNull()
            val key = Triple(0, 0, firstFavorite?.id)
            if (key == focusedViewKey) return@LaunchedEffect
            focusedViewKey = key
            handFocusTo(LibraryFocusTarget.FAVORITES)
            if (firstFavorite != null) {
                viewModel.setFocusedItem(firstFavorite.id)
            } else {
                viewModel.clearFocus()
            }
            withFrameNanos { }
            if (pagerState.isScrollInProgress || appsPagerState.isScrollInProgress) {
                return@LaunchedEffect
            }
            runCatching { favoritesFirstFocusRequester.requestFocus() }
        } else {
            val firstApp = appListEntries.firstOrNull { it is AppListEntry.App } as? AppListEntry.App
                ?: return@LaunchedEffect
            val key = Triple(0, 1, firstApp.item.id)
            if (key == focusedViewKey) return@LaunchedEffect
            focusedViewKey = key
            handFocusTo(LibraryFocusTarget.APP_LIST)
            viewModel.setFocusedItem(firstApp.item.id)
            withFrameNanos { }
            if (pagerState.isScrollInProgress || appsPagerState.isScrollInProgress) {
                return@LaunchedEffect
            }
            runCatching { appsListFirstFocusRequester.requestFocus() }
        }
    }

    // Exactly one library view may accept focus. A pending handoff also keeps the outgoing
    // view focusable until the incoming view has taken focus, so the focus system always has
    // a valid target and never falls back to the top navigation bar.
    val pagerIdle = !pagerState.isScrollInProgress && !appsPagerState.isScrollInProgress
    val favoritesCanFocus = focusedLibraryView == LibraryFocusTarget.FAVORITES ||
        (pagerIdle && handingOffFrom == LibraryFocusTarget.FAVORITES)
    val appDrawerCanFocus = focusedLibraryView == LibraryFocusTarget.APP_LIST ||
        (pagerIdle && handingOffFrom == LibraryFocusTarget.APP_LIST)
    val gamesCanFocus = focusedLibraryView == LibraryFocusTarget.GAMES ||
        (pagerIdle && handingOffFrom == LibraryFocusTarget.GAMES)

    val disablePagerSwipeInCompactGames by remember(isCompactLandscape, gamesDisplay.isEmpty()) {
        derivedStateOf {
            isCompactLandscape &&
                gamesDisplay.isNotEmpty() &&
                pagerState.currentPage == 1 &&
                !pagerState.isScrollInProgress
        }
    }

    val onGameClick = remember(viewModel) {
        { game: GameItem ->
            viewModel.setFocusedItem(game.id)
            viewModel.launchItem(game)
        }
    }
    val onGameLongClick = remember(viewModel) {
        { game: GameItem -> viewModel.openContextMenu(game) }
    }
    val onGameFocus = remember(viewModel) {
        { game: GameItem -> viewModel.setFocusedItem(game.id) }
    }
    val onAppClick = remember(viewModel) {
        { app: GameItem ->
            viewModel.setFocusedItem(app.id)
            viewModel.launchItem(app)
        }
    }
    val onAppLongClick = remember(viewModel) {
        { app: GameItem -> viewModel.openContextMenu(app) }
    }
    val onAppFocus = remember(viewModel) {
        { app: GameItem -> viewModel.setFocusedItem(app.id) }
    }

    val appsScrimColor = MaterialTheme.colorScheme.background
    val gamesScrimColor = MaterialTheme.colorScheme.surfaceContainerHighest

    // Bottom edge of the top bar in root coordinates. The Apps list fades its background in
    // behind the bar, and that fade has to start at the very top of the screen so the
    // status-bar strip joins the bar instead of showing the wallpaper above it.
    var topBarBottomPx by remember { mutableStateOf(0f) }

    val isGamesInView by remember {
        derivedStateOf {
            pagerState.currentPage == 1 ||
                (pagerState.currentPage == 0 && pagerState.currentPageOffsetFraction > 0f)
        }
    }
    val isAppDrawerInView by remember {
        derivedStateOf {
            (pagerState.currentPage == 0 ||
                (pagerState.currentPage == 1 && pagerState.currentPageOffsetFraction < 0f)) &&
                (appsPagerState.currentPage == 1 ||
                    (appsPagerState.currentPage == 0 && appsPagerState.currentPageOffsetFraction > 0f))
        }
    }

    CompositionLocalProvider(
        LocalThemedAppIcons provides themedAppIcons,
        LocalFolderPreviewItems provides folderMembersMap
    ) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize()
        ) {
            if (isTelevision && !tvWallpaperUrl.isNullOrBlank()) {
                val context = LocalContext.current
                val density = LocalDensity.current
                val wallpaperWidthPx = remember(density, maxWidth) {
                    with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
                }
                val wallpaperHeightPx = remember(density, maxHeight) {
                    with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
                }
                val wallpaperRequest = remember(
                    context,
                    tvWallpaperUrl,
                    wallpaperWidthPx,
                    wallpaperHeightPx
                ) {
                    ImageRequest.Builder(context)
                        .data(tvWallpaperUrl)
                        .size(wallpaperWidthPx, wallpaperHeightPx)
                        .precision(Precision.INEXACT)
                        .allowRgb565(true)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = wallpaperRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("tv_wallpaper_image")
                )
            }

            // Read pagerState offset in the draw phase so horizontal swiping and
            // overscroll fade the wallpaper scrim without recomposing DashboardScreen.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val darkAmount = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .coerceIn(0f, 1f)
                        val appsAlpha = appsBackgroundOpacity * (1f - darkAmount)
                        if (appsAlpha > 0f) {
                            drawRect(appsScrimColor.copy(alpha = appsAlpha))
                        }
                        if (darkAmount > 0f) {
                            drawRect(gamesScrimColor.copy(alpha = darkAmount))
                        }
                        // As the app list scrolls in, fade the same surface the Games screen
                        // uses behind the top bar. Painting from y = 0 keeps the status-bar
                        // strip the same color instead of leaving the wallpaper visible.
                        if (currentTab == MainLibraryTab.APPS) {
                            val reveal = (
                                appsPagerState.currentPage +
                                    appsPagerState.currentPageOffsetFraction
                                ).coerceIn(0f, 1f)
                            if (reveal > 0f && topBarBottomPx > 0f) {
                                clipRect(right = size.width, bottom = topBarBottomPx) {
                                    drawRect(gamesScrimColor.copy(alpha = reveal))
                                }
                            }
                        }
                    }
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
                    showFocusEffects = showFocusEffects,
                    onOpenCalendar = { viewModel.openCalendarApp() },
                    onOpenWeather = { viewModel.openWeatherApp() },
                    onTabSelected = { viewModel.setTab(it) },
                    onOpenAddModal = { viewModel.openAddModal() },
                    onOpenSettings = { viewModel.openSettingsModal() },
                    firstItemFocusRequester = navFirstFocusRequester,
                    onNavFocusChanged = { navBarFocused = it },
                    onNavigateDown = {
                        // Down from the bar returns to the first item of whichever library
                        // view is on screen, so the bar never traps focus on the Games tab.
                        val target = when {
                            pagerState.settledPage == 1 -> gamesFirstFocusRequester
                            appsPagerState.settledPage == 1 -> appsListFirstFocusRequester
                            else -> favoritesFirstFocusRequester
                        }
                        runCatching { target.requestFocus() }
                    },
                    modifier = Modifier
                        // Measure where the bar ends so the Apps background fade can start at
                        // the top of the screen and stop exactly at the bar's bottom edge.
                        .onGloballyPositioned { coordinates ->
                            topBarBottomPx =
                                coordinates.positionInRoot().y + coordinates.size.height
                        }
                        .then(if (isCompactLandscape) Modifier.displayCutoutPadding() else Modifier)
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
                CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        // On the compact landscape Games page the poster row owns horizontal
                        // drags. Disabling pager dragging here stops its overscroll from
                        // revealing the Apps page; a drag above/below the row still switches.
                        userScrollEnabled = !disablePagerSwipeInCompactGames,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("library_pager")
                    ) { page ->
                        CompositionLocalProvider(LocalBringIntoViewSpec provides ambientBringIntoViewSpec) {
                            if (page == 1) {
                                // Games View: Steam poster aspect ratio (2:3) with pull-to-refresh.
                                // Confine left/previous navigation so D-pad Left cannot jump to Apps,
                                // while allowing Up from row 0 to reach the top navigation bar.
                                val pullToRefreshState = rememberPullToRefreshState()
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        // Once this page holds focus the handoff is complete, so
                                        // the page that lost focus can stop accepting it.
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                handingOffFrom = null
                                                focusReturn = null
                                            }
                                        }
                                        .focusGroup()
                                        .focusProperties {
                                            exit = { direction ->
                                                if (
                                                    direction == FocusDirection.Left ||
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
                                                    focusEnabled = gamesCanFocus,
                                                    modifier = Modifier.heightIn(min = pageMaxHeight)
                                                )
                                            }
                                        } else {
                                            FocusedItemState(
                                                viewModel,
                                                suppressFocus = navBarFocused
                                            ) { focusedItemId ->
                                                GamesPosterGrid(
                                                    games = gamesDisplay,
                                                    focusedItemId = focusedItemId,
                                                    showFocusEffects = showFocusEffects,
                                                    showNsfwPoster = steamGridDbIncludeNsfw,
                                                    prefetchEnabled = isGamesInView,
                                                    firstItemFocusRequester = gamesFirstFocusRequester,
                                                    itemFocusRequester = gamesItemFocusRequester,
                                                    focusEnabled = gamesCanFocus,
                                                    gridState = gridState,
                                                    rowState = gamesRowState,
                                                    compactLandscape = isCompactLandscape,
                                                    compactViewportHeight = pageMaxHeight,
                                                    onSwipeToApps = {
                                                        scope.launch { pagerState.animateScrollToPage(0) }
                                                    },
                                                    onNavigateUpToNav = {
                                                        runCatching { navFirstFocusRequester.requestFocus() }
                                                    },
                                                    onGameClick = onGameClick,
                                                    onGameLongClick = onGameLongClick,
                                                    onGameFocus = onGameFocus
                                                )
                                            }
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
                                // Apps View: a full-height favorites home. Swiping up or pressing Down
                                // reveals the complete app list, which excludes favorites and games.
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
                                    CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
                                        VerticalPager(
                                            state = appsPagerState,
                                            beyondViewportPageCount = 1,
                                            flingBehavior = appsPagerFling,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("apps_vertical_pager")
                                        ) { appsPage ->
                                            CompositionLocalProvider(
                                                // The favorites page never scrolls (its grid is
                                                // sized to fit and its layouts are FlowRows), so
                                                // it keeps the no-op spec. The app list still
                                                // needs focus scrolling, but the plain TV pivot
                                                // spec scrolls even for visible rows and the
                                                // leftover nested scroll pulls the pager back a
                                                // page, so it uses a visible-aware variant.
                                                LocalBringIntoViewSpec provides if (appsPage == 0) {
                                                    NoOpBringIntoViewSpec
                                                } else {
                                                    drawerBringIntoViewSpec
                                                }
                                            ) {
                                                if (appsPage == 0) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .onFocusChanged {
                                                                if (it.hasFocus) {
                                                                    handingOffFrom = null
                                                                    focusReturn = null
                                                                }
                                                            }
                                                            .focusGroup()
                                                    ) {
                                                        FocusedItemState(
                                                            viewModel,
                                                            suppressFocus = navBarFocused
                                                        ) { focusedItemId ->
                                                            FavoritesHome(
                                                                favorites = favoriteApps,
                                                                focusedItemId = focusedItemId,
                                                                layout = effectiveFavoritesLayout,
                                                                showFocusEffects = showFocusEffects,
                                                                weatherEnabled = weatherEnabled,
                                                                weather = weatherInfo,
                                                                temperatureUnit = temperatureUnit,
                                                                firstItemFocusRequester = favoritesFirstFocusRequester,
                                                                itemFocusRequester = favoritesItemFocusRequester,
                                                                focusEnabled = favoritesCanFocus,
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
                                                                onNavigateDownToAppList = {
                                                                    if (appsPagerState.targetPage != 1) {
                                                                        scope.launch { appsPagerState.animateScrollToPage(1) }
                                                                    }
                                                                },
                                                                onFocusFavorite = { app -> viewModel.setFocusedItem(app.id) },
                                                                configuredColumns = if (isTelevision) appGridColumns else 0
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .onFocusChanged {
                                                                if (it.hasFocus) {
                                                                    handingOffFrom = null
                                                                    focusReturn = null
                                                                }
                                                            }
                                                            .focusGroup()
                                                    ) {
                                                        if (libraryDisplay.isEmpty()) {
                                                            EmptyAppDrawerState(
                                                                hasFavorites = favoriteApps.isNotEmpty(),
                                                                focusEnabled = appDrawerCanFocus,
                                                                onNavigateUpToFavorites = {
                                                                    if (appsPagerState.targetPage != 0) {
                                                                        scope.launch { appsPagerState.animateScrollToPage(0) }
                                                                    }
                                                                }
                                                            )
                                                        } else {
                                                            val effectiveAppGridColumns = if (isTelevision) appGridColumns else 0
                                                            FocusedItemState(
                                                                viewModel,
                                                                suppressFocus = navBarFocused
                                                            ) { focusedItemId ->
                                                                AppsAlphabeticalList(
                                                                    entries = appListEntries,
                                                                    focusedItemId = focusedItemId,
                                                                    layout = effectiveFavoritesLayout,
                                                                    showFocusEffects = showFocusEffects,
                                                                    prefetchEnabled = isAppDrawerInView,
                                                                    listState = appsListState,
                                                                    gridState = appsGridState,
                                                                    firstItemFocusRequester = appsListFirstFocusRequester,
                                                                    itemFocusRequester = appsItemFocusRequester,
                                                                    focusEnabled = appDrawerCanFocus,
                                                                    configuredColumns = effectiveAppGridColumns,
                                                                    showAlphabetRail = !isTelevision,
                                                                    onAppClick = onAppClick,
                                                                    onAppLongClick = onAppLongClick,
                                                                    onAppFocus = onAppFocus,
                                                                    onNavigateUpToFavorites = {
                                                                        if (appsPagerState.targetPage != 0) {
                                                                            scope.launch { appsPagerState.animateScrollToPage(0) }
                                                                        }
                                                                    },
                                                                    onLetterSelected = { letter ->
                                                                        val index = appListEntries.indexOfFirst {
                                                                            it is AppListEntry.Letter && it.letter == letter
                                                                        }
                                                                        if (index >= 0) {
                                                                            val usesAppsGrid = effectiveFavoritesLayout != FavoritesLayout.TEXT || effectiveAppGridColumns > 1
                                                                            scope.launch {
                                                                                if (usesAppsGrid) {
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
                                }
                            }
                        }
                    }
                }
            }

            // Folder Contents Bottom Sheet (rendered before context/customize sheets so actions stack on top)
            val openFolder = openFolderId?.let { id -> foldersList.firstOrNull { it.id == id } }
            if (openFolder != null) {
                FocusedItemState(viewModel) { focusedItemId ->
                    FolderContentsSheet(
                        folder = openFolder,
                        apps = folderMembersMap[openFolder.id].orEmpty(),
                        layout = effectiveFavoritesLayout,
                        focusedItemId = focusedItemId,
                        showFocusEffects = showFocusEffects,
                        onAppClick = onAppClick,
                        onAppLongClick = onAppLongClick,
                        onAppFocus = onAppFocus,
                        onCustomizeFolder = { viewModel.openEditCustomizer(openFolder) },
                        onManageFolderApps = { viewModel.openManageFolderApps(openFolder.id) },
                        onDismiss = { viewModel.closeFolder() },
                        configuredColumns = if (isTelevision) appGridColumns else 0
                    )
                }
            }

            // Context Menu Modal (Tap and Hold on any card)
            if (contextMenuItem != null) {
                val target = contextMenuItem!!
                ItemContextMenuSheet(
                    item = target,
                    showNsfwPoster = steamGridDbIncludeNsfw,
                    canDelete = canDeleteContextMenuItem,
                    onCustomize = { viewModel.openEditCustomizer(target) },
                    onMove = {
                        if (target.isGame) {
                            viewModel.moveItemToApps(target)
                        } else {
                            viewModel.moveItemToGames(target)
                        }
                    },
                    onMoveToFolder = { viewModel.openFolderPicker(target) },
                    onRemoveFromFolder = { viewModel.moveItemToFolder(target.id, null) },
                    onManageFolderApps = { viewModel.openManageFolderApps(target.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(target) },
                    onRemoveFromRecents = { viewModel.removeFromRecents(target) },
                    onOpenAppInfo = { viewModel.openAppInfo(target) },
                    onHide = { viewModel.hideItem(target) },
                    onDelete = { viewModel.deleteItem(target) },
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
                    onMoveToFolder = { viewModel.openFolderPicker(favorite) },
                    onManageFolderApps = { viewModel.openManageFolderApps(favorite.id) },
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

            // Move-to-folder picker sheet
            folderPickerTargetItem?.let { target ->
                FolderPickerSheet(
                    targetItem = target,
                    folders = foldersList,
                    folderMembersMap = folderMembersMap,
                    onSelectFolder = { folderId -> viewModel.moveItemToFolder(target.id, folderId) },
                    onCreateFolder = { title -> viewModel.createFolder(title, initialMemberId = target.id) },
                    onRemoveFromFolder = { viewModel.moveItemToFolder(target.id, null) },
                    onDismiss = { viewModel.closeFolderPicker() }
                )
            }

            // Batch-manage apps inside a folder
            val manageFolder = manageFolderId?.let { id -> foldersList.firstOrNull { it.id == id } }
            if (manageFolder != null) {
                val allItemsSnapshot by viewModel.allItems.collectAsStateWithLifecycle()
                val candidateApps = remember(allItemsSnapshot) {
                    allItemsSnapshot
                        .filter { !it.isGame && it.type != GameType.FOLDER }
                        .sortedBy { it.title.lowercase() }
                }
                val currentMembers = remember(folderMembersMap, manageFolder.id) {
                    folderMembersMap[manageFolder.id].orEmpty().mapTo(HashSet()) { it.id }
                }
                FolderAppsManageSheet(
                    folder = manageFolder,
                    candidateApps = candidateApps,
                    currentMemberIds = currentMembers,
                    onSave = { selectedIds -> viewModel.setFolderMembers(manageFolder.id, selectedIds) },
                    onDismiss = { viewModel.closeManageFolderApps() }
                )
            }

            // Customize Name & Artwork Dialog
            editingItem?.let { item ->
                DashboardCustomizeOverlay(
                    viewModel = viewModel,
                    item = item,
                    includeNsfw = steamGridDbIncludeNsfw
                )
            }

            // Full Page Discover Screen (+ Button)
            AnimatedVisibility(
                visible = showAddModal,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it })
            ) {
                val addModalSubView by viewModel.addModalSubView.collectAsStateWithLifecycle()
                AddContentModal(
                    subView = addModalSubView,
                    onNavigateSubView = { viewModel.setAddModalSubView(it) },
                    onOpenPlayStore = { viewModel.openPlayStore() },
                    onOpenMoonlight = { viewModel.openMoonlight() },
                    onAddShortcut = { title, url, poster -> viewModel.addCustomShortcutToApps(title, url, poster) },
                    onAddGfnGame = { viewModel.addGfnGameToGames(it) },
                    onAddLunaGame = { viewModel.addLunaGameToGames(it) },
                    onDismiss = { viewModel.closeAddModal() }
                )
            }

            // Full Page Settings Screen
            AnimatedVisibility(
                visible = showSettingsModal,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                DashboardSettingsOverlay(
                    viewModel = viewModel,
                    favoritesLayout = favoritesLayout,
                    steamGridDbIncludeNsfw = steamGridDbIncludeNsfw,
                    appsBackgroundOpacity = appsBackgroundOpacity,
                    weatherEnabled = weatherEnabled,
                    weatherInfo = weatherInfo,
                    temperatureUnit = temperatureUnit,
                    isTelevision = isTelevision,
                    tvWallpaperUrl = tvWallpaperUrl,
                    appGridColumns = appGridColumns
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
                                val interactionSource = remember { MutableInteractionSource() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .tvFocusBorder(interactionSource, shape = RoundedCornerShape(10.dp))
                                        .clickable(interactionSource = interactionSource, indication = null) {
                                            viewModel.chooseWeatherApp(app.packageName)
                                        }
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

    // Update the selected tab from horizontal swipes only once the pager settles so a
    // half-completed drag or bounce never triggers a competing animateScrollToPage.
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            val settledTab = if (pagerState.settledPage == 0) MainLibraryTab.APPS else MainLibraryTab.GAMES
            onTabFromPager(settledTab)
        }
    }

    // Recovery guard: if an external interrupt or fast cancellation leaves either pager
    // non-idle between pages, settle cleanly once scrolling stops.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPageOffsetFraction }
            .collect { (scrolling, offset) ->
                if (!scrolling && abs(offset) > 0.005f) {
                    withFrameNanos { }
                    if (!pagerState.isScrollInProgress && abs(pagerState.currentPageOffsetFraction) > 0.005f) {
                        pagerState.animateScrollToPage(pagerState.currentPage)
                    }
                }
            }
    }
    LaunchedEffect(appsPagerState) {
        snapshotFlow { appsPagerState.isScrollInProgress to appsPagerState.currentPageOffsetFraction }
            .collect { (scrolling, offset) ->
                if (!scrolling && abs(offset) > 0.005f) {
                    withFrameNanos { }
                    if (!appsPagerState.isScrollInProgress && abs(appsPagerState.currentPageOffsetFraction) > 0.005f) {
                        appsPagerState.animateScrollToPage(appsPagerState.currentPage)
                    }
                }
            }
    }

    // Close the app drawer whenever switching to Games (once settled offscreen).
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress &&
            pagerState.settledPage == 1 &&
            appsPagerState.currentPage != 0
        ) {
            appsPagerState.scrollToPage(0)
        }
    }

    LaunchedEffect(currentTab) {
        val target = if (currentTab == MainLibraryTab.GAMES) 1 else 0
        if (target == 0 && appsPagerState.currentPage != 0) {
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
        if (pagerState.currentPage != target || abs(pagerState.currentPageOffsetFraction) > 0.005f) {
            pagerState.animateScrollToPage(target)
        }
    }

    // Selecting the Apps tab always returns to the favorites home, even if the vertical
    // pager was left on the complete app list.
    LaunchedEffect(appsHomeReset) {
        if (appsHomeReset > 0 && appsPagerState.currentPage != 0) {
            if (pagerState.settledPage == 0 && !pagerState.isScrollInProgress) {
                appsPagerState.animateScrollToPage(0)
            } else {
                appsPagerState.scrollToPage(0)
            }
        }
    }
}

/**
 * Up from the first poster row leaves the Games grid for the top navigation bar. Lower rows
 * keep the implicit vertical move, so focus still steps up one row at a time.
 */
private fun Modifier.navigateUpToNavFromTopRow(
    isTopRow: Boolean,
    onNavigateUpToNav: (() -> Unit)?
): Modifier = if (isTopRow && onNavigateUpToNav != null) {
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
            onNavigateUpToNav()
            true
        } else {
            false
        }
    }
} else {
    this
}

/**
 * Reads the focused library item inside its own composition scope and hands it to [content].
 *
 * `DashboardScreen` reads almost sixty state flows at its root; when the focused item was
 * collected there, every D-pad move recomposed that entire scope. Collecting it per view
 * keeps focus changes local to the grid, favorites list, app list or folder sheet that
 * actually highlights something.
 *
 * [suppressFocus] hides the highlight while focus is outside the library, for example on
 * the top navigation bar. The stored id is not cleared, because the context-menu action and
 * the scroll position still need to know the last item the user selected.
 */
@Composable
private fun FocusedItemState(
    viewModel: LauncherViewModel,
    suppressFocus: Boolean = false,
    content: @Composable (String?) -> Unit
) {
    val focusedItemId by viewModel.focusedItemId.collectAsStateWithLifecycle()
    content(focusedItemId.takeUnless { suppressFocus })
}

@Composable
private fun DashboardCustomizeOverlay(
    viewModel: LauncherViewModel,
    item: GameItem,
    includeNsfw: Boolean
) {
    val steamGridDbApiKey by viewModel.steamGridDbApiKey.collectAsStateWithLifecycle()
    val installedIconPacks by viewModel.installedIconPacks.collectAsStateWithLifecycle()
    val selectedIconPack by viewModel.selectedIconPack.collectAsStateWithLifecycle()
    val iconPackIcons by viewModel.iconPackIcons.collectAsStateWithLifecycle()
    val isLoadingIconPackIcons by viewModel.iconPackIconsLoading.collectAsStateWithLifecycle()
    val selectedPackOverrides by viewModel.selectedPackOverrides.collectAsStateWithLifecycle()
    val selectedCustomUrlOverrides by viewModel.selectedCustomUrlOverrides.collectAsStateWithLifecycle()

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
        includeNsfw = includeNsfw,
        iconPack = installedIconPacks.firstOrNull {
            it.packageName == selectedIconPack
        },
        activeThemeKey = IconPackOverridesStore.themeKey(selectedIconPack),
        iconPackIcons = iconPackIcons,
        isLoadingIconPackIcons = isLoadingIconPackIcons,
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

@Composable
private fun DashboardSettingsOverlay(
    viewModel: LauncherViewModel,
    favoritesLayout: FavoritesLayout,
    steamGridDbIncludeNsfw: Boolean,
    appsBackgroundOpacity: Float,
    weatherEnabled: Boolean,
    weatherInfo: com.example.data.service.WeatherInfo?,
    temperatureUnit: com.example.data.service.TemperatureUnit,
    isTelevision: Boolean,
    tvWallpaperUrl: String?,
    appGridColumns: Int
) {
    val steamGridDbApiKey by viewModel.steamGridDbApiKey.collectAsStateWithLifecycle()
    val installedIconPacks by viewModel.installedIconPacks.collectAsStateWithLifecycle()
    val selectedIconPack by viewModel.selectedIconPack.collectAsStateWithLifecycle()
    val hiddenItems by viewModel.hiddenItems.collectAsStateWithLifecycle()
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
    val weatherMode by viewModel.weatherMode.collectAsStateWithLifecycle()
    val weatherZip by viewModel.weatherZip.collectAsStateWithLifecycle()
    val weatherError by viewModel.weatherError.collectAsStateWithLifecycle()
    val weatherApps by viewModel.weatherApps.collectAsStateWithLifecycle()
    val weatherAppPackage by viewModel.weatherAppPackage.collectAsStateWithLifecycle()
    val librarySyncBusy by viewModel.librarySyncBusy.collectAsStateWithLifecycle()
    val librarySyncStatus by viewModel.librarySyncStatus.collectAsStateWithLifecycle()
    val librarySyncConfigured by viewModel.librarySyncConfigured.collectAsStateWithLifecycle()
    val librarySyncMode by viewModel.librarySyncMode.collectAsStateWithLifecycle()
    val librarySyncTarget by viewModel.librarySyncTarget.collectAsStateWithLifecycle()
    val librarySyncLastSyncedAt by viewModel.librarySyncLastSyncedAt.collectAsStateWithLifecycle()
    val librarySyncNeedsReauth by viewModel.librarySyncNeedsReauth.collectAsStateWithLifecycle()
    val pendingDriveAuthIntent by viewModel.pendingDriveAuthIntent.collectAsStateWithLifecycle()
    val pendingDriveFallbackIntent by viewModel.pendingDriveFallbackIntent.collectAsStateWithLifecycle()
    val pendingDriveDeviceSession by viewModel.pendingDriveDeviceSession.collectAsStateWithLifecycle()

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
        librarySyncMode = librarySyncMode,
        librarySyncTarget = librarySyncTarget,
        librarySyncLastSyncedAt = librarySyncLastSyncedAt,
        librarySyncNeedsReauth = librarySyncNeedsReauth,
        pendingDriveAuthIntent = pendingDriveAuthIntent,
        pendingDriveFallbackIntent = pendingDriveFallbackIntent,
        pendingDriveDeviceSession = pendingDriveDeviceSession,
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
        onConnectGoogleDrive = { viewModel.connectGoogleDrive() },
        onGoogleDriveAuthorizationResult = { code, data ->
            viewModel.onGoogleDriveAuthorizationResult(code, data)
        },
        onGoogleDriveFallbackResult = { code, data, email, isPicker ->
            viewModel.onGoogleDriveFallbackResult(code, data, email, isPicker)
        },
        onConsumePendingDriveAuthIntent = { viewModel.consumePendingDriveAuthIntent() },
        onConsumePendingDriveFallbackIntent = { viewModel.consumePendingDriveFallbackIntent() },
        onMarkDriveAuthLaunched = { viewModel.markDriveAuthLaunched() },
        onCancelDriveDeviceAuthorization = { viewModel.cancelGoogleDriveDeviceAuthorization() },
        onSyncLibraryNow = { viewModel.syncLibraryNow() },
        onDisconnectLibrarySync = { viewModel.disconnectLibrarySync() },
        onDismiss = { viewModel.closeSettingsModal() },
        isTelevision = isTelevision,
        tvWallpaperUrl = tvWallpaperUrl,
        appGridColumns = appGridColumns,
        onSetTvWallpaper = { viewModel.setTvWallpaper(it) },
        onClearTvWallpaper = { viewModel.clearTvWallpaper() },
        onSetAppGridColumns = { viewModel.setAppGridColumns(it) }
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GamesPosterGrid(
    games: List<GameItem>,
    focusedItemId: String?,
    showFocusEffects: Boolean,
    showNsfwPoster: Boolean,
    prefetchEnabled: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
    itemFocusRequester: ((String) -> FocusRequester)? = null,
    focusEnabled: Boolean = true,
    gridState: LazyGridState,
    rowState: LazyListState,
    compactLandscape: Boolean,
    compactViewportHeight: Dp = 0.dp,
    onSwipeToApps: () -> Unit = {},
    onNavigateUpToNav: (() -> Unit)? = null,
    onGameClick: (GameItem) -> Unit,
    onGameLongClick: (GameItem) -> Unit,
    onGameFocus: (GameItem) -> Unit = {}
) {
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

    // Id-keyed requester per poster so focus can come back to the exact game after a sheet
    // opened from it closes.
    fun itemRequesterModifier(id: String): Modifier =
        itemFocusRequester?.let { Modifier.focusRequester(it(id)) } ?: Modifier

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
            val compactPosterWidthPx = remember(density) {
                with(density) { 168.dp.roundToPx() }
            }
            val compactPosterHeightPx = (compactPosterWidthPx * 1.5f).roundToInt()

            PrefetchPosterImages(
                games = games,
                showNsfwPoster = showNsfwPoster,
                posterWidthPx = compactPosterWidthPx,
                posterHeightPx = compactPosterHeightPx,
                enabled = prefetchEnabled,
                visibleRangeProvider = {
                    val info = rowState.layoutInfo.visibleItemsInfo
                    val first = info.firstOrNull()?.index ?: -1
                    val last = info.lastOrNull()?.index ?: -1
                    first to last
                }
            )

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
                itemsIndexed(
                    items = games,
                    key = { _, game -> game.id },
                    contentType = { _, _ -> "game_poster" }
                ) { index, game ->
                    val isFocused = game.id == focusedItemId && showFocusEffects
                    val firstItemModifier = if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                    val handleClick = remember(game, onGameClick) { { onGameClick(game) } }
                    val handleLongClick = remember(game, onGameLongClick) { { onGameLongClick(game) } }
                    val handleFocus = remember(game, onGameFocus) { { onGameFocus(game) } }

                    SteamGamePosterCard(
                        game = game,
                        isFocused = isFocused,
                        showFocusEffects = showFocusEffects,
                        showFocusOutline = false,
                        showNsfwPoster = showNsfwPoster,
                        posterWidthPx = compactPosterWidthPx,
                        posterHeightPx = compactPosterHeightPx,
                        onClick = handleClick,
                        onLongClick = handleLongClick,
                        onFocus = handleFocus,
                        modifier = Modifier
                            .focusProperties { canFocus = focusEnabled }
                            .then(firstItemModifier)
                            .then(itemRequesterModifier(game.id))
                            .then(if (index == 0) Modifier.focusProperties { left = FocusRequester.Cancel } else Modifier)
                            .navigateUpToNavFromTopRow(true, onNavigateUpToNav)
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
        val cellWidthDp = ((availableWidth - spacing * (columnCount - 1)) / columnCount)
            .coerceAtLeast(1.dp)
        val posterWidthPx = with(density) { cellWidthDp.roundToPx().coerceAtLeast(1) }
        val posterHeightPx = (posterWidthPx * 1.5f).roundToInt().coerceAtLeast(1)
        val posterScope = rememberCoroutineScope()
        val gameFocusRequesters = remember(showFocusEffects, games.size) {
            if (showFocusEffects) List(games.size) { FocusRequester() } else emptyList()
        }

        PrefetchPosterImages(
            games = games,
            showNsfwPoster = showNsfwPoster,
            posterWidthPx = posterWidthPx,
            posterHeightPx = posterHeightPx,
            enabled = prefetchEnabled,
            visibleRangeProvider = {
                val info = gridState.layoutInfo.visibleItemsInfo
                val first = info.firstOrNull()?.index ?: -1
                val last = info.lastOrNull()?.index ?: -1
                first to last
            }
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
                itemsIndexed(
                    items = games,
                    key = { _, game -> game.id },
                    contentType = { _, _ -> "game_poster" }
                ) { index, game ->
                    val isLeftmost = index % columnCount == 0
                    val firstItemModifier = if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                    val focusGridModifier = if (showFocusEffects && gameFocusRequesters.isNotEmpty()) {
                        Modifier
                            .focusRequester(gameFocusRequesters[index])
                            .flatGridDownNextRowFallback(
                                index = index,
                                columns = columnCount,
                                itemCount = games.size,
                                gridState = gridState,
                                requesters = gameFocusRequesters,
                                scope = posterScope
                            )
                    } else {
                        Modifier
                    }
                    val handleClick = remember(game, onGameClick) { { onGameClick(game) } }
                    val handleLongClick = remember(game, onGameLongClick) { { onGameLongClick(game) } }
                    val handleFocus = remember(game, onGameFocus) { { onGameFocus(game) } }

                    SteamGamePosterCard(
                        game = game,
                        isFocused = game.id == focusedItemId && showFocusEffects,
                        showFocusEffects = showFocusEffects,
                        showNsfwPoster = showNsfwPoster,
                        posterWidthPx = posterWidthPx,
                        posterHeightPx = posterHeightPx,
                        onClick = handleClick,
                        onLongClick = handleLongClick,
                        onFocus = handleFocus,
                        modifier = Modifier
                            .focusProperties { canFocus = focusEnabled }
                            .then(focusGridModifier)
                            .then(firstItemModifier)
                            .then(itemRequesterModifier(game.id))
                            .then(if (isLeftmost) Modifier.focusProperties { left = FocusRequester.Cancel } else Modifier)
                            .navigateUpToNavFromTopRow(index < columnCount, onNavigateUpToNav)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrefetchPosterImages(
    games: List<GameItem>,
    showNsfwPoster: Boolean,
    posterWidthPx: Int,
    posterHeightPx: Int,
    enabled: Boolean,
    visibleRangeProvider: () -> Pair<Int, Int>,
) {
    val context = LocalContext.current
    LaunchedEffect(enabled, games, showNsfwPoster, posterWidthPx, posterHeightPx) {
        if (!enabled || games.isEmpty()) return@LaunchedEffect
        val loader = context.imageLoader
        val enqueuedUrls = HashSet<String>()
        snapshotFlow(visibleRangeProvider)
            .distinctUntilChanged()
            .collect { (firstVisible, lastVisible) ->
                if (firstVisible < 0 || lastVisible < 0) return@collect
                val start = (firstVisible - 4).coerceAtLeast(0)
                val end = (lastVisible + 12).coerceAtMost(games.lastIndex)
                for (i in start..end) {
                    val game = games[i]
                    val showingNsfw = showNsfwPoster && !game.nsfwPosterUrl.isNullOrBlank()
                    val activePoster = if (showingNsfw) game.nsfwPosterUrl else game.posterUrl
                    val imageUrl = activePoster ?: game.heroBannerUrl ?: game.iconUrl
                    if (!imageUrl.isNullOrBlank() && enqueuedUrls.add(imageUrl)) {
                        loader.enqueue(
                            buildPosterImageRequest(
                                context = context,
                                imageUrl = imageUrl,
                                posterWidthPx = posterWidthPx,
                                posterHeightPx = posterHeightPx
                            )
                        )
                    }
                    val activeLogoOverlay = if (showingNsfw) game.overlayNsfwLogo else game.overlayLogo
                    val activeLogoUrl = if (showingNsfw) game.nsfwLogoUrl else game.logoUrl
                    if (
                        activeLogoOverlay &&
                        !activeLogoUrl.isNullOrBlank() &&
                        !activePoster.isNullOrBlank() &&
                        enqueuedUrls.add("logo:$activeLogoUrl")
                    ) {
                        loader.enqueue(
                            buildPosterLogoImageRequest(
                                context = context,
                                logoUrl = activeLogoUrl,
                                posterWidthPx = posterWidthPx,
                                posterHeightPx = posterHeightPx
                            )
                        )
                    }
                }
            }
    }
}

@Immutable
internal sealed interface AppListEntry {
    @Immutable
    data class Letter(val letter: String) : AppListEntry
    @Immutable
    data class App(val item: GameItem) : AppListEntry
}

/** Flatten the sorted app list into letter/folder headers and rows. */
internal fun buildAppListEntries(apps: List<GameItem>): List<AppListEntry> {
    val entries = ArrayList<AppListEntry>(apps.size + 8)
    var section: String? = null
    for (app in apps) {
        val letter = sectionFor(app)
        if (letter != section) {
            entries += AppListEntry.Letter(letter)
            section = letter
        }
        entries += AppListEntry.App(app)
    }
    return entries
}

internal data class SectionDownTarget(
    val targetId: String,
    val targetEntryIndex: Int
)

/**
 * Maps each app item in a sectioned alphabetical list/grid to the app that should receive
 * focus when pressing [Key.DirectionDown]. Stepping to row `r + 1` picks the same column when
 * present, or falls back to the last item in row `r + 1` when row `r + 1` has fewer items
 * (including across section headers). Returns no mapping for items on the final row.
 */
internal fun computeSectionedDownTargets(
    entries: List<AppListEntry>,
    columns: Int
): Map<String, SectionDownTarget> {
    if (entries.isEmpty() || columns <= 0) return emptyMap()
    val rows = mutableListOf<List<Pair<String, Int>>>()
    val currentRow = mutableListOf<Pair<String, Int>>()
    entries.forEachIndexed { idx, entry ->
        when (entry) {
            is AppListEntry.Letter -> {
                if (currentRow.isNotEmpty()) {
                    rows += currentRow.toList()
                    currentRow.clear()
                }
            }
            is AppListEntry.App -> {
                currentRow += (entry.item.id to idx)
                if (currentRow.size == columns) {
                    rows += currentRow.toList()
                    currentRow.clear()
                }
            }
        }
    }
    if (currentRow.isNotEmpty()) {
        rows += currentRow.toList()
    }
    val targets = HashMap<String, SectionDownTarget>()
    for (r in 0 until rows.lastIndex) {
        val row = rows[r]
        val nextRow = rows[r + 1]
        for (c in row.indices) {
            val (itemId, _) = row[c]
            val (targetId, targetEntryIndex) = nextRow[minOf(c, nextRow.lastIndex)]
            targets[itemId] = SectionDownTarget(targetId, targetEntryIndex)
        }
    }
    return targets
}

@Composable
internal fun AppsAlphabeticalList(
    entries: List<AppListEntry>,
    focusedItemId: String?,
    layout: FavoritesLayout,
    showFocusEffects: Boolean,
    listState: LazyListState,
    gridState: LazyGridState,
    prefetchEnabled: Boolean = true,
    firstItemFocusRequester: FocusRequester? = null,
    focusEnabled: Boolean = true,
    configuredColumns: Int = 0,
    showAlphabetRail: Boolean = !isTelevisionMode(),
    onAppClick: (GameItem) -> Unit,
    onAppLongClick: (GameItem) -> Unit,
    onLetterSelected: (String) -> Unit,
    onAppFocus: (GameItem) -> Unit = {},
    onNavigateUpToFavorites: (() -> Unit)? = null,
    itemFocusRequester: ((String) -> FocusRequester)? = null,
) {
    val fallbackRequesters = remember { ItemFocusRequesters() }
    fun requesterForId(id: String): FocusRequester =
        itemFocusRequester?.invoke(id) ?: fallbackRequesters.requesterFor(id)
    val drawerScope = rememberCoroutineScope()

    val letters = remember(entries) {
        entries.filterIsInstance<AppListEntry.Letter>().map { it.letter }
    }
    val firstAppId = remember(entries) {
        entries.filterIsInstance<AppListEntry.App>().firstOrNull()?.item?.id
    }

    // Draw edge to edge behind the navigation bar / app-switcher drag handle. The bar
    // inset moves into each list's scrollable content padding instead, so rows can pass
    // under the handle while the final items still scroll fully into view.
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PrefetchAppIcons(
        entries = entries,
        layout = layout,
        configuredColumns = configuredColumns,
        listState = listState,
        gridState = gridState,
        enabled = prefetchEnabled
    )

    fun downNavigationModifier(
        target: SectionDownTarget?,
        useGrid: Boolean
    ): Modifier = if (target == null) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                val movedNow = runCatching {
                    requesterForId(target.targetId).requestFocus()
                }.isSuccess
                if (!movedNow) {
                    drawerScope.launch {
                        if (useGrid) {
                            gridState.animateScrollToItem(target.targetEntryIndex)
                        } else {
                            listState.animateScrollToItem(target.targetEntryIndex)
                        }
                        withFrameNanos { }
                        runCatching { requesterForId(target.targetId).requestFocus() }
                    }
                }
                true
            } else {
                false
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The rail is a fixed-height column of letters parked above the gesture bar.
        // In landscape a short window cannot fit the full A–Z column, so hide it rather
        // than letting it overflow or squeeze the app grid.
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val letterLineHeight = with(LocalDensity.current) { 13.sp.toDp() }
        val letterPitch = letterLineHeight + 6.dp
        val requiredRailHeight = letterPitch * (letters.size.coerceAtLeast(1) + 5) + 8.dp
        val railVisible = showAlphabetRail &&
            letters.isNotEmpty() &&
            (!isLandscape || maxHeight >= requiredRailHeight)

        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            if (layout == FavoritesLayout.TV_CARDS) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val availableWidth = maxWidth - if (railVisible) 24.dp else 32.dp
                    val spacing = 14.dp
                    val minCardWidth = 156.dp
                    val columns = if (configuredColumns > 0) {
                        configuredColumns
                    } else {
                        minOf(
                            TV_FAVORITES_MAX_COLUMNS,
                            maxOf(3, ((availableWidth + spacing) / (minCardWidth + spacing)).toInt())
                        )
                    }
                    val firstRowIds = remember(entries, columns) {
                        firstSectionTopRowIds(entries, columns)
                    }
                    val downTargets = remember(entries, columns) {
                        computeSectionedDownTargets(entries, columns)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = if (railVisible) 8.dp else 16.dp,
                            top = 20.dp,
                            bottom = 20.dp + navigationBarInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("apps_tv_grid")
                    ) {
                        items(
                            items = entries,
                            key = { entry ->
                                when (entry) {
                                    is AppListEntry.Letter -> "section_${entry.letter}"
                                    is AppListEntry.App -> entry.item.id
                                }
                            },
                            span = { entry ->
                                when (entry) {
                                    is AppListEntry.Letter -> GridItemSpan(maxLineSpan)
                                    is AppListEntry.App -> GridItemSpan(1)
                                }
                            },
                            contentType = { entry ->
                                when (entry) {
                                    is AppListEntry.Letter -> "letter"
                                    is AppListEntry.App -> "app_tv_card"
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is AppListEntry.Letter -> AppLetterHeader(letter = entry.letter)
                                is AppListEntry.App -> {
                                    val app = entry.item
                                    val handleClick = remember(app, onAppClick) { { onAppClick(app) } }
                                    val handleLongClick = remember(app, onAppLongClick) { { onAppLongClick(app) } }
                                    val handleFocus = remember(app, onAppFocus) { { onAppFocus(app) } }
                                    val upToFavoritesMod = if (app.id in firstRowIds && onNavigateUpToFavorites != null) {
                                        Modifier.onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown &&
                                                keyEvent.key == Key.DirectionUp
                                            ) {
                                                onNavigateUpToFavorites()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                    AppTvCard(
                                        app = app,
                                        isFocused = app.id == focusedItemId,
                                        showFocusEffects = showFocusEffects,
                                        testTag = "app_row_${app.id}",
                                        onClick = handleClick,
                                        onLongClick = handleLongClick,
                                        onFocus = handleFocus,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties { canFocus = focusEnabled }
                                            .focusRequester(requesterForId(app.id))
                                            .then(
                                                if (app.id == firstAppId) {
                                                    firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                        ?: Modifier
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .then(upToFavoritesMod)
                                            .then(downNavigationModifier(downTargets[app.id], useGrid = true))
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (layout == FavoritesLayout.ICONS) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    val availableWidth = maxWidth - if (railVisible) 12.dp else 24.dp
                    val tileMinSize = 78.dp
                    val columns = if (configuredColumns > 0) {
                        configuredColumns
                    } else {
                        maxOf(3, (availableWidth / tileMinSize).toInt())
                    }
                    val firstRowIds = remember(entries, columns) {
                        firstSectionTopRowIds(entries, columns)
                    }
                    val downTargets = remember(entries, columns) {
                        computeSectionedDownTargets(entries, columns)
                    }

                    // Icon grid drawer: cells are packed edge to edge; gaps live inside each
                    // tile's clickable content padding so the whole cell is an easy touch target.
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = if (railVisible) 0.dp else 12.dp,
                            top = 20.dp,
                            bottom = 16.dp + navigationBarInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("apps_grid")
                    ) {
                        items(
                            items = entries,
                            key = { entry ->
                                when (entry) {
                                    is AppListEntry.Letter -> "section_${entry.letter}"
                                    is AppListEntry.App -> entry.item.id
                                }
                            },
                            span = { entry ->
                                when (entry) {
                                    is AppListEntry.Letter -> GridItemSpan(maxLineSpan)
                                    is AppListEntry.App -> GridItemSpan(1)
                                }
                            },
                            contentType = { entry ->
                                when (entry) {
                                    is AppListEntry.Letter -> "letter"
                                    is AppListEntry.App -> "app_tile"
                                }
                            }
                        ) { entry ->
                            when (entry) {
                                is AppListEntry.Letter -> AppLetterHeader(letter = entry.letter)
                                is AppListEntry.App -> {
                                    val app = entry.item
                                    val handleClick = remember(app, onAppClick) { { onAppClick(app) } }
                                    val handleLongClick = remember(app, onAppLongClick) { { onAppLongClick(app) } }
                                    val handleFocus = remember(app, onAppFocus) { { onAppFocus(app) } }
                                    val upToFavoritesMod = if (app.id in firstRowIds && onNavigateUpToFavorites != null) {
                                        Modifier.onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown &&
                                                keyEvent.key == Key.DirectionUp
                                            ) {
                                                onNavigateUpToFavorites()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                    AppIconTile(
                                        app = app,
                                        isFocused = app.id == focusedItemId,
                                        showFocusEffects = showFocusEffects,
                                        testTag = "app_row_${app.id}",
                                        onClick = handleClick,
                                        onLongClick = handleLongClick,
                                        onFocus = handleFocus,
                                        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties { canFocus = focusEnabled }
                                            .focusRequester(requesterForId(app.id))
                                            .then(
                                                if (app.id == firstAppId) {
                                                    firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                        ?: Modifier
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .then(upToFavoritesMod)
                                            .then(downNavigationModifier(downTargets[app.id], useGrid = true))
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (configuredColumns > 1) {
                val firstRowIds = remember(entries, configuredColumns) {
                    firstSectionTopRowIds(entries, configuredColumns)
                }
                val downTargets = remember(entries, configuredColumns) {
                    computeSectionedDownTargets(entries, configuredColumns)
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(configuredColumns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = if (railVisible) 4.dp else 16.dp,
                        top = 20.dp,
                        bottom = 16.dp + navigationBarInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("apps_list")
                ) {
                    items(
                        items = entries,
                        key = { entry ->
                            when (entry) {
                                is AppListEntry.Letter -> "section_${entry.letter}"
                                is AppListEntry.App -> entry.item.id
                            }
                        },
                        span = { entry ->
                            when (entry) {
                                is AppListEntry.Letter -> GridItemSpan(maxLineSpan)
                                is AppListEntry.App -> GridItemSpan(1)
                            }
                        },
                        contentType = { entry ->
                            when (entry) {
                                is AppListEntry.Letter -> "letter"
                                is AppListEntry.App -> "app_row"
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is AppListEntry.Letter -> AppLetterHeader(letter = entry.letter)
                            is AppListEntry.App -> {
                                val app = entry.item
                                val handleClick = remember(app, onAppClick) { { onAppClick(app) } }
                                val handleLongClick = remember(app, onAppLongClick) { { onAppLongClick(app) } }
                                val handleFocus = remember(app, onAppFocus) { { onAppFocus(app) } }
                                val upToFavoritesMod = if (app.id in firstRowIds && onNavigateUpToFavorites != null) {
                                    Modifier.onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.key == Key.DirectionUp
                                        ) {
                                            onNavigateUpToFavorites()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                                AppTextRow(
                                    app = app,
                                    isFocused = app.id == focusedItemId,
                                    showFocusEffects = showFocusEffects,
                                    testTag = "app_row_${app.id}",
                                    onClick = handleClick,
                                    onLongClick = handleLongClick,
                                    onFocus = handleFocus,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusProperties { canFocus = focusEnabled }
                                        .focusRequester(requesterForId(app.id))
                                        .then(
                                            if (app.id == firstAppId) {
                                                firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                    ?: Modifier
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(upToFavoritesMod)
                                        .then(downNavigationModifier(downTargets[app.id], useGrid = true))
                                )
                            }
                        }
                    }
                }
            } else {
                val firstRowIds = remember(entries) {
                    firstSectionTopRowIds(entries, columns = 1)
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = if (railVisible) 4.dp else 16.dp,
                        top = 20.dp,
                        bottom = 16.dp + navigationBarInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("apps_list")
                ) {
                    items(
                        items = entries,
                        key = { entry ->
                            when (entry) {
                                is AppListEntry.Letter -> "section_${entry.letter}"
                                is AppListEntry.App -> entry.item.id
                            }
                        },
                        contentType = { entry ->
                            when (entry) {
                                is AppListEntry.Letter -> "letter"
                                is AppListEntry.App -> "app_row"
                            }
                        }
                    ) { entry ->
                        when (entry) {
                            is AppListEntry.Letter -> AppLetterHeader(letter = entry.letter)
                            is AppListEntry.App -> {
                                val app = entry.item
                                val handleClick = remember(app, onAppClick) { { onAppClick(app) } }
                                val handleLongClick = remember(app, onAppLongClick) { { onAppLongClick(app) } }
                                val handleFocus = remember(app, onAppFocus) { { onAppFocus(app) } }
                                val upToFavoritesMod = if (app.id in firstRowIds && onNavigateUpToFavorites != null) {
                                    Modifier.onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown &&
                                            keyEvent.key == Key.DirectionUp
                                        ) {
                                            onNavigateUpToFavorites()
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                                AppTextRow(
                                    app = app,
                                    isFocused = app.id == focusedItemId,
                                    showFocusEffects = showFocusEffects,
                                    testTag = "app_row_${app.id}",
                                    onClick = handleClick,
                                    onLongClick = handleLongClick,
                                    onFocus = handleFocus,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusProperties { canFocus = focusEnabled }
                                        .focusRequester(requesterForId(app.id))
                                        .then(
                                            if (app.id == firstAppId) {
                                                firstItemFocusRequester?.let { Modifier.focusRequester(it) }
                                                    ?: Modifier
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .then(upToFavoritesMod)
                                )
                            }
                        }
                    }
                }
            }

            if (railVisible) {
                AlphabetRail(
                    letters = letters,
                    onLetterSelected = onLetterSelected
                )
            }
        }
    }
}

private fun firstSectionTopRowIds(entries: List<AppListEntry>, columns: Int): Set<String> {
    val firstAppIdx = entries.indexOfFirst { it is AppListEntry.App }
    if (firstAppIdx < 0) return emptySet()
    val ids = HashSet<String>(columns)
    for (i in firstAppIdx until entries.size) {
        val entry = entries[i]
        if (entry is AppListEntry.Letter) break
        if (entry is AppListEntry.App) {
            ids += entry.item.id
            if (ids.size >= columns) break
        }
    }
    return ids
}

@Composable
private fun PrefetchAppIcons(
    entries: List<AppListEntry>,
    layout: FavoritesLayout,
    configuredColumns: Int,
    listState: LazyListState,
    gridState: LazyGridState,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val themedIcons = LocalThemedAppIcons.current
    val folderMembers = LocalFolderPreviewItems.current
    val iconSizePx = remember(density, layout) {
        val sizeDp = when (layout) {
            FavoritesLayout.TEXT -> 39.dp
            FavoritesLayout.ICONS -> 58.dp
            FavoritesLayout.TV_CARDS -> 44.dp
        }
        with(density) { sizeDp.roundToPx().coerceAtLeast(1) }
    }
    val miniIconSizePx = remember(iconSizePx) {
        (iconSizePx * 0.30f).roundToInt().coerceAtLeast(1)
    }
    LaunchedEffect(enabled, entries, layout, configuredColumns, themedIcons, folderMembers, iconSizePx) {
        if (!enabled || entries.isEmpty()) return@LaunchedEffect
        val loader = context.imageLoader
        val enqueuedUrls = HashSet<String>()
        val usesGrid = layout != FavoritesLayout.TEXT || configuredColumns > 1
        snapshotFlow {
            val visible = if (usesGrid) {
                gridState.layoutInfo.visibleItemsInfo.let { info ->
                    (info.firstOrNull()?.index ?: -1) to (info.lastOrNull()?.index ?: -1)
                }
            } else {
                listState.layoutInfo.visibleItemsInfo.let { info ->
                    (info.firstOrNull()?.index ?: -1) to (info.lastOrNull()?.index ?: -1)
                }
            }
            visible
        }
            .distinctUntilChanged()
            .collect { (firstVisible, lastVisible) ->
                if (firstVisible < 0 || lastVisible < 0) return@collect
                val start = (firstVisible - 6).coerceAtLeast(0)
                val end = (lastVisible + 15).coerceAtMost(entries.lastIndex)
                for (i in start..end) {
                    val appEntry = entries[i] as? AppListEntry.App ?: continue
                    val appItem = appEntry.item
                    val hasCustomIcon = hasCustomAppIconUrl(appItem.iconUrl) ||
                        (!appItem.isGame && (
                            themedIcons.containsKey(appItem.id) ||
                                appItem.packageName?.let { themedIcons.containsKey(it) } == true
                            ))
                    val tvBanner = if (layout == FavoritesLayout.TV_CARDS && !hasCustomIcon) {
                        appItem.heroBannerUrl?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    if (tvBanner != null) {
                        val bannerW = (iconSizePx * 4).coerceAtLeast(320)
                        val bannerH = (bannerW * 9) / 16
                        if (enqueuedUrls.add("banner:$tvBanner")) {
                            loader.enqueue(
                                buildAppBannerImageRequest(
                                    context = context,
                                    bannerUrl = tvBanner,
                                    widthPx = bannerW,
                                    heightPx = bannerH
                                )
                            )
                        }
                        continue
                    }
                    val imageUrl = resolveAppIconUrl(appItem, themedIcons)
                    if (!imageUrl.isNullOrBlank()) {
                        if (enqueuedUrls.add("$iconSizePx:$imageUrl")) {
                            loader.enqueue(
                                buildAppIconImageRequest(
                                    context = context,
                                    imageUrl = imageUrl,
                                    sizePx = iconSizePx
                                )
                            )
                        }
                    } else if (appItem.type == GameType.FOLDER) {
                        val previewChildren = folderMembers[appItem.id].orEmpty()
                            .filter { it.type != GameType.FOLDER }
                            .take(4)
                        for (child in previewChildren) {
                            val childUrl = resolveAppIconUrl(child, themedIcons)
                            if (!childUrl.isNullOrBlank() && enqueuedUrls.add("$miniIconSizePx:$childUrl")) {
                                loader.enqueue(
                                    buildAppIconImageRequest(
                                        context = context,
                                        imageUrl = childUrl,
                                        sizePx = miniIconSizePx
                                    )
                                )
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun AppLetterHeader(letter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 12.dp, bottom = 4.dp)
            .testTag("app_letter_header_$letter"),
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
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AlphabetRail(
    letters: List<String>,
    onLetterSelected: (String) -> Unit
) {
    // Measure the rail and the packed letter column inside it, so a touch maps onto the
    // letters the user can actually see (the column is bottom-aligned, not full height).
    var railHeight by remember { mutableIntStateOf(0) }
    var lettersHeight by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

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
                    if (letter == SECTION_FOLDERS) {
                        Box(
                            modifier = Modifier
                                .height(letterLineHeight)
                                .padding(horizontal = 4.dp)
                                .testTag("alphabet_rail_letter_folders"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Folders",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    } else {
                        Text(
                            text = letter,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag("alphabet_rail_letter_$letter")
                        )
                    }
                }
            }
        }
    }
}

private const val SECTION_FOLDERS = "Folders"

private fun sectionFor(item: GameItem): String =
    if (item.type == GameType.FOLDER) SECTION_FOLDERS else alphabetSection(item.title)

private fun alphabetSection(title: String): String {
    val first = title.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (first in 'A'..'Z') first.toString() else "#"
}

@Composable
private fun EmptyAppDrawerState(
    hasFavorites: Boolean,
    focusEnabled: Boolean = true,
    onNavigateUpToFavorites: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .onPreviewKeyEvent { keyEvent ->
                if (hasFavorites &&
                    onNavigateUpToFavorites != null &&
                    keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionUp
                ) {
                    onNavigateUpToFavorites()
                    true
                } else {
                    false
                }
            }
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
            text = if (hasFavorites) "Swipe down or press Back to return to your favorites."
            else "Resync installed apps from Settings.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (hasFavorites && onNavigateUpToFavorites != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateUpToFavorites,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .focusProperties { canFocus = focusEnabled }
                    .testTag("empty_app_drawer_back_button")
            ) {
                Text("Back to Favorites")
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    isGamesTab: Boolean,
    searchQuery: String,
    onAddClicked: () -> Unit,
    focusEnabled: Boolean = true,
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.focusProperties { canFocus = focusEnabled }
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Content")
            }
        }
    }
}

