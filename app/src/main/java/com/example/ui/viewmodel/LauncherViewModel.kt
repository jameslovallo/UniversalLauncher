package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import com.example.data.service.GeForceNowService
import com.example.data.service.InstalledAppsManager
import com.example.data.service.MOONLIGHT_PACKAGES
import com.example.input.ControllerAction
import com.example.input.ControllerManager
import com.example.ui.theme.FavoritesLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainLibraryTab {
    APPS,
    GAMES
}

enum class AddModalSubView {
    OPTIONS,
    ADD_GFN,
    ADD_WEB_URL
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository.create(application)
    private val installedAppsManager = InstalledAppsManager(application)
    val controllerManager = ControllerManager(application)

    private val prefs = application.getSharedPreferences("nexus_launcher_prefs", Context.MODE_PRIVATE)

    // Main Library Tab: Games vs Apps
    private val _currentTab = MutableStateFlow(MainLibraryTab.APPS)
    val currentTab: StateFlow<MainLibraryTab> = _currentTab.asStateFlow()

    // Search query for filtering within the active tab
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Incremented whenever the Apps tab is (re)selected so the UI can return to the
    // favorites home even when the tab value itself did not change.
    private val _appsHomeReset = MutableStateFlow(0)
    val appsHomeReset: StateFlow<Int> = _appsHomeReset.asStateFlow()

    // Focused item ID for controller navigation
    private val _focusedItemId = MutableStateFlow<String?>(null)
    val focusedItemId: StateFlow<String?> = _focusedItemId.asStateFlow()

    // Context Menu for Long Press
    private val _contextMenuItem = MutableStateFlow<GameItem?>(null)
    val contextMenuItem: StateFlow<GameItem?> = _contextMenuItem.asStateFlow()

    // Item being customized (Name, Icon, Poster)
    private val _editingItem = MutableStateFlow<GameItem?>(null)
    val editingItem: StateFlow<GameItem?> = _editingItem.asStateFlow()

    // Favorite long-press menu and drag-to-reorder editor
    private val _favoriteActionItem = MutableStateFlow<GameItem?>(null)
    val favoriteActionItem: StateFlow<GameItem?> = _favoriteActionItem.asStateFlow()

    private val _showFavoritesEditor = MutableStateFlow(false)
    val showFavoritesEditor: StateFlow<Boolean> = _showFavoritesEditor.asStateFlow()

    // Add Content Modal
    private val _showAddModal = MutableStateFlow(false)
    val showAddModal: StateFlow<Boolean> = _showAddModal.asStateFlow()

    private val _addModalSubView = MutableStateFlow(AddModalSubView.OPTIONS)
    val addModalSubView: StateFlow<AddModalSubView> = _addModalSubView.asStateFlow()

    // Settings Modal
    private val _showSettingsModal = MutableStateFlow(false)
    val showSettingsModal: StateFlow<Boolean> = _showSettingsModal.asStateFlow()

    // Favorites home layout. Text list is the default.
    private val _favoritesLayout = MutableStateFlow(
        runCatching {
            FavoritesLayout.valueOf(
                prefs.getString("favorites_layout", FavoritesLayout.TEXT.name)!!
            )
        }.getOrDefault(FavoritesLayout.TEXT)
    )
    val favoritesLayout: StateFlow<FavoritesLayout> = _favoritesLayout.asStateFlow()

    private val _steamGridDbApiKey = MutableStateFlow(prefs.getString("sgdb_api_key", ""))
    val steamGridDbApiKey: StateFlow<String?> = _steamGridDbApiKey.asStateFlow()

    // All library items from Room
    val allItems: StateFlow<List<GameItem>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Games Tab: poster grid (all cloud games, game apps, shortcuts)
    val gamesList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { it.isGame }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Apps Tab: all non-game applications, alphabetised for counts and controller use
    val appsList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { !it.isGame }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Favorites home: favorited apps in the user's custom order
    val favoriteApps: StateFlow<List<GameItem>> = repository.favoriteApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Full app list shown after swiping up: no favorites and no games
    val libraryApps: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { !it.isGame && !it.isFavorite }.sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hidden items for the Settings restore list
    val hiddenItems: StateFlow<List<GameItem>> = repository.hiddenGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initializeLibraryIfEmpty()
            repository.backfillGfnArtwork()
            repository.matchMissingGamePosters()
        }
    }

    fun setTab(tab: MainLibraryTab) {
        _currentTab.value = tab
        _searchQuery.value = ""
        // Each tab starts with no focused row so the Apps tab always opens on favorites.
        _focusedItemId.value = null
        if (tab == MainLibraryTab.APPS) {
            _appsHomeReset.value += 1
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFocusedItem(id: String) {
        _focusedItemId.value = id
    }

    fun clearFocus() {
        _focusedItemId.value = null
    }

    /** True when the Games tab is rendered as a single horizontal row (compact landscape). */
    private var gamesRowMode = false

    fun setGamesRowMode(enabled: Boolean) {
        gamesRowMode = enabled
    }

    /** Actual column count of the Games poster grid, used for D-pad up/down steps. */
    private var gamesColumnCount = 4

    fun setGamesColumnCount(count: Int) {
        if (count > 0) gamesColumnCount = count
    }

    /** Actual column count of the Apps icon grid / favorites, used for 2D directional navigation. */
    private var appsColumnCount = 4

    fun setAppsColumnCount(count: Int) {
        if (count > 0) appsColumnCount = count
    }

    fun launchItem(item: GameItem) {
        viewModelScope.launch {
            repository.recordPlay(item.id)
        }

        when (item.type) {
            GameType.INSTALLED_APP -> {
                item.packageName?.let { pkg ->
                    installedAppsManager.launchApp(pkg)
                }
            }
            GameType.GEFORCE_NOW -> {
                val url = item.launchUrl ?: GeForceNowService.createGfnLaunchUrl(item.gfnCmsId)
                // GeForce NOW cannot stream in an embedded browser (browser allowlist and
                // no Widevine), so hand the deep link to the native app or a real browser.
                installedAppsManager.openExternally(url)
            }
            GameType.CUSTOM_CLOUD -> {
                val url = item.launchUrl ?: "https://play.geforcenow.com"
                installedAppsManager.openExternally(url)
            }
            GameType.SHORTCUT, GameType.MOONLIGHT -> {
                val url = item.launchUrl
                val pkg = item.packageName
                val shortcutId = item.activityName
                // Pinned shortcuts launch through LauncherApps so the publisher's intent
                // (including extras) is preserved. Anything else falls back to the stored
                // serialized intent or a plain app launch.
                val launchedAsPinnedShortcut =
                    pkg != null && shortcutId != null &&
                        installedAppsManager.launchPinnedShortcut(pkg, shortcutId)
                when {
                    launchedAsPinnedShortcut -> Unit
                    url == null -> pkg?.let { installedAppsManager.launchApp(it) }
                    url.startsWith("intent:") -> {
                        // Persisted launch intent (Moonlight-style). If the target activity
                        // is gone, at least open the app so the user can pick the game there.
                        if (!installedAppsManager.launchIntentUri(url)) {
                            pkg?.let { installedAppsManager.launchApp(it) }
                        }
                    }
                    url.startsWith("http://") || url.startsWith("https://") ->
                        installedAppsManager.openExternally(url)
                    else -> pkg?.let { installedAppsManager.launchApp(it) }
                }
            }
        }
    }

    // Context Menu Handlers
    fun openContextMenu(item: GameItem) {
        _contextMenuItem.value = item
    }

    fun closeContextMenu() {
        _contextMenuItem.value = null
    }

    fun moveItemToApps(item: GameItem) {
        viewModelScope.launch {
            repository.setIsGame(item.id, false)
            closeContextMenu()
            setTab(MainLibraryTab.APPS)
        }
    }

    fun moveItemToGames(item: GameItem) {
        viewModelScope.launch {
            repository.setIsGame(item.id, true)
            repository.ensureAutoPoster(item.copy(isGame = true))
            closeContextMenu()
            setTab(MainLibraryTab.GAMES)
        }
    }

    fun toggleFavorite(item: GameItem) {
        viewModelScope.launch {
            val addingToFavorites = !item.isFavorite
            repository.toggleFavorite(item.id, item.isFavorite)
            if (addingToFavorites) {
                // New favorites go to the end of the favorites view.
                repository.setFavoriteOrder(item.id, repository.nextFavoriteOrder())
            }
            closeContextMenu()
        }
    }

    fun reorderFavorites(items: List<GameItem>) {
        viewModelScope.launch {
            repository.saveFavoriteOrder(items)
        }
    }

    fun removeFavorite(item: GameItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, true)
        }
    }

    fun openFavoriteActions(item: GameItem) {
        _favoriteActionItem.value = item
    }

    fun closeFavoriteActions() {
        _favoriteActionItem.value = null
    }

    fun openFavoritesEditor() {
        _favoriteActionItem.value = null
        _showFavoritesEditor.value = true
    }

    fun closeFavoritesEditor() {
        _showFavoritesEditor.value = false
    }

    fun openClockApp() {
        installedAppsManager.openClock()
    }

    fun openCalendarApp() {
        installedAppsManager.openCalendar()
    }

    /** Pull down the notification shade from the favorites home. */
    fun expandNotificationShade() {
        installedAppsManager.expandNotificationShade()
    }

    /** Open the system app-info page for an installed app. */
    fun openAppInfo(item: GameItem) {
        item.packageName?.let { installedAppsManager.openAppInfo(it) }
        closeContextMenu()
    }

    fun deleteItem(item: GameItem) {
        viewModelScope.launch {
            // Native apps in the Apps tab get a real uninstall prompt; shortcuts and
            // other library entries are simply removed. Deleting records a tombstone so
            // the app sync cannot bring the tile back on the next launch.
            if (item.type == GameType.INSTALLED_APP && !item.isGame) {
                item.packageName?.let { installedAppsManager.uninstallApp(it) }
            }
            repository.deleteGame(item.id)
            if (_focusedItemId.value == item.id) {
                _focusedItemId.value = null
            }
            closeContextMenu()
        }
    }

    fun hideItem(item: GameItem) {
        viewModelScope.launch {
            repository.setHidden(item.id, true)
            if (_focusedItemId.value == item.id) {
                _focusedItemId.value = null
            }
            closeContextMenu()
        }
    }

    fun unhideItem(item: GameItem) {
        viewModelScope.launch {
            repository.setHidden(item.id, false)
        }
    }

    // Customize Name and Icon
    fun openEditCustomizer(item: GameItem) {
        _contextMenuItem.value = null
        _editingItem.value = item
    }

    fun closeEditCustomizer() {
        _editingItem.value = null
    }

    fun saveItemCustomization(id: String, newTitle: String, newPosterUrl: String?, newIconUrl: String?) {
        viewModelScope.launch {
            repository.updateTitleAndArtwork(id, newTitle, newPosterUrl, newIconUrl)
            _editingItem.value = null
        }
    }

    // Add Modal Flow
    fun openAddModal() {
        _addModalSubView.value = AddModalSubView.OPTIONS
        _showAddModal.value = true
    }

    fun setAddModalSubView(view: AddModalSubView) {
        _addModalSubView.value = view
    }

    fun closeAddModal() {
        _showAddModal.value = false
        _addModalSubView.value = AddModalSubView.OPTIONS
    }

    /** Open the Play Store games section. */
    fun openPlayStore() {
        installedAppsManager.openPlayStore()
        closeAddModal()
    }

    /** Open Moonlight if installed, otherwise its Play Store listing. */
    fun openMoonlight() {
        val packageName = MOONLIGHT_PACKAGES.firstOrNull { installedAppsManager.isPackageInstalled(it) }
        if (packageName != null) {
            installedAppsManager.launchApp(packageName)
        } else {
            installedAppsManager.openExternally(
                "https://play.google.com/store/search?q=moonlight%20game%20streaming"
            )
        }
        closeAddModal()
    }

    /**
     * Custom web/cloud shortcuts are library entries like any other app. Only Moonlight
     * creates game tiles, so new shortcuts are added to the app list.
     */
    fun addCustomShortcutToApps(title: String, url: String, posterUrl: String?) {
        viewModelScope.launch {
            val type = if (url.contains("geforcenow.com") || url.contains("xbox.com/play")) {
                GameType.CUSTOM_CLOUD
            } else {
                GameType.SHORTCUT
            }
            repository.addCustomShortcut(title, url, posterUrl, type, isGame = false)
            closeAddModal()
            _currentTab.value = MainLibraryTab.APPS
        }
    }

    fun addGfnGameToGames(gfnGame: GameItem) {
        viewModelScope.launch {
            // Never persist the square catalog icon as a poster: if the search result
            // fell back to it, resolve the real box art before saving.
            val poster = gfnGame.posterUrl
            val needsBoxArt = poster.isNullOrBlank() || poster.contains("GAME_ICON_01")
            val resolvedPoster = if (needsBoxArt) {
                GeForceNowService.resolveBoxArtForCmsId(gfnGame.gfnCmsId) ?: poster
            } else {
                poster
            }
            val userGame = gfnGame.copy(
                id = "user_gfn_${gfnGame.gfnCmsId ?: System.currentTimeMillis()}",
                posterUrl = resolvedPoster,
                isGame = true
            )
            repository.insertUserGame(userGame)
            closeAddModal()
            _currentTab.value = MainLibraryTab.GAMES
        }
    }

    // Settings Modal
    fun openSettingsModal() {
        _showSettingsModal.value = true
    }

    fun closeSettingsModal() {
        _showSettingsModal.value = false
    }

    fun setFavoritesLayout(layout: FavoritesLayout) {
        prefs.edit().putString("favorites_layout", layout.name).apply()
        _favoritesLayout.value = layout
    }

    fun saveSteamGridDbApiKey(key: String) {
        prefs.edit().putString("sgdb_api_key", key).apply()
        _steamGridDbApiKey.value = key
    }

    fun openDefaultLauncherSettings() {
        installedAppsManager.openHomeSettings()
    }

    fun syncLibrary() {
        viewModelScope.launch {
            repository.syncGfnCatalog()
            repository.syncInstalledApps()
            repository.matchMissingGamePosters()
        }
    }

    /**
     * Items the directional pad moves through: the games grid, or favorites followed by
     * the complete app list on the Apps tab.
     */
    private fun currentTabItems(): List<GameItem> =
        if (_currentTab.value == MainLibraryTab.GAMES) {
            gamesList.value
        } else {
            favoriteApps.value + libraryApps.value
        }

    // Controller navigation
    fun onControllerAction(action: ControllerAction): Boolean {
        // Close open modals on Back (B)
        if (_showAddModal.value || _showSettingsModal.value || _editingItem.value != null ||
            _contextMenuItem.value != null || _favoriteActionItem.value != null ||
            _showFavoritesEditor.value
        ) {
            if (action == ControllerAction.BACK_B) {
                if (_addModalSubView.value != AddModalSubView.OPTIONS && _showAddModal.value) {
                    _addModalSubView.value = AddModalSubView.OPTIONS
                    return true
                }
                closeAddModal()
                closeSettingsModal()
                closeEditCustomizer()
                closeContextMenu()
                closeFavoriteActions()
                closeFavoritesEditor()
                return true
            }
            return false
        }

        // Bumper navigation: Apps is the first tab, Games the second.
        when (action) {
            ControllerAction.PREV_TAB_LB -> {
                setTab(MainLibraryTab.APPS)
                return true
            }
            ControllerAction.NEXT_TAB_RB -> {
                setTab(MainLibraryTab.GAMES)
                return true
            }
            ControllerAction.CONFIRM_A -> {
                val currentList = currentTabItems()
                val item = currentList.find { it.id == _focusedItemId.value } ?: currentList.firstOrNull()
                item?.let { launchItem(it) }
                return true
            }
            ControllerAction.CUSTOMIZE_X -> {
                val currentList = currentTabItems()
                val item = currentList.find { it.id == _focusedItemId.value } ?: currentList.firstOrNull()
                item?.let { openContextMenu(it) }
                return true
            }
            ControllerAction.MENU_START -> {
                openSettingsModal()
                return true
            }
            ControllerAction.QUICK_SELECT -> {
                openAddModal()
                return true
            }
            ControllerAction.RIGHT,
            ControllerAction.LEFT,
            ControllerAction.UP,
            ControllerAction.DOWN -> {
                val currentList = currentTabItems()
                if (currentList.isEmpty()) return false
                val isAppsTab = _currentTab.value == MainLibraryTab.APPS
                val isIconsLayout = _favoritesLayout.value == FavoritesLayout.ICONS
                // 2D grid navigation: Up/Down moves a full row (column count). Left/Right moves 1 item.
                val step = when {
                    isAppsTab && isIconsLayout -> maxOf(1, appsColumnCount)
                    isAppsTab -> 1 // Text layout is a vertical single list on phones
                    gamesRowMode -> 1 // Compact landscape horizontal row
                    else -> maxOf(1, gamesColumnCount) // Poster grid
                }
                val hasFocus = currentList.any { it.id == _focusedItemId.value }
                val curIndex = currentList.indexOfFirst { it.id == _focusedItemId.value }
                    .let { if (it >= 0) it else 0 }
                val nextIndex = if (!hasFocus) {
                    0
                } else when (action) {
                    ControllerAction.RIGHT -> (curIndex + 1).coerceAtMost(currentList.lastIndex)
                    ControllerAction.LEFT -> (curIndex - 1).coerceAtLeast(0)
                    ControllerAction.DOWN ->
                        (curIndex + step).coerceAtMost(currentList.lastIndex)
                    ControllerAction.UP ->
                        (curIndex - step).coerceAtLeast(0)
                    else -> curIndex
                }
                _focusedItemId.value = currentList[nextIndex].id
                return true
            }
            else -> return false
        }
    }
}
