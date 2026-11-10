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
import com.example.data.service.MoonlightService
import com.example.input.ControllerAction
import com.example.input.ControllerManager
import com.example.ui.theme.AppIconShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainLibraryTab {
    GAMES,
    APPS
}

enum class AddModalSubView {
    OPTIONS,
    ADD_APP,
    ADD_SHORTCUT,
    ADD_GFN,
    ADD_MOONLIGHT
}

data class MoonlightUiState(
    val isLoading: Boolean = false,
    val games: List<GameItem> = emptyList(),
    val message: String? = null,
    val shortcutsBlocked: Boolean = false,
    val hasScanned: Boolean = false
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository.create(application)
    private val installedAppsManager = InstalledAppsManager(application)
    private val moonlightService = MoonlightService(application)
    val controllerManager = ControllerManager(application)

    private val prefs = application.getSharedPreferences("nexus_launcher_prefs", Context.MODE_PRIVATE)

    // Main Library Tab: Games vs Apps
    private val _currentTab = MutableStateFlow(MainLibraryTab.GAMES)
    val currentTab: StateFlow<MainLibraryTab> = _currentTab.asStateFlow()

    // Search query for filtering within the active tab
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Focused item ID for controller navigation
    private val _focusedItemId = MutableStateFlow<String?>(null)
    val focusedItemId: StateFlow<String?> = _focusedItemId.asStateFlow()

    // Context Menu for Long Press
    private val _contextMenuItem = MutableStateFlow<GameItem?>(null)
    val contextMenuItem: StateFlow<GameItem?> = _contextMenuItem.asStateFlow()

    // Item being customized (Name, Icon, Poster)
    private val _editingItem = MutableStateFlow<GameItem?>(null)
    val editingItem: StateFlow<GameItem?> = _editingItem.asStateFlow()

    // Add Content Modal
    private val _showAddModal = MutableStateFlow(false)
    val showAddModal: StateFlow<Boolean> = _showAddModal.asStateFlow()

    private val _addModalSubView = MutableStateFlow(AddModalSubView.OPTIONS)
    val addModalSubView: StateFlow<AddModalSubView> = _addModalSubView.asStateFlow()

    // Settings Modal
    private val _showSettingsModal = MutableStateFlow(false)
    val showSettingsModal: StateFlow<Boolean> = _showSettingsModal.asStateFlow()

    // Icon Customization Preferences
    private val _iconShape = MutableStateFlow(
        runCatching {
            AppIconShape.valueOf(prefs.getString("icon_shape", AppIconShape.SQUIRCLE.name)!!)
        }.getOrDefault(AppIconShape.SQUIRCLE)
    )
    val iconShape: StateFlow<AppIconShape> = _iconShape.asStateFlow()

    private val _useThemedIcons = MutableStateFlow(prefs.getBoolean("themed_icons", false))
    val useThemedIcons: StateFlow<Boolean> = _useThemedIcons.asStateFlow()

    private val _steamGridDbApiKey = MutableStateFlow(prefs.getString("sgdb_api_key", ""))
    val steamGridDbApiKey: StateFlow<String?> = _steamGridDbApiKey.asStateFlow()

    // Installed apps cache for the Add App modal
    private val _installedApps = MutableStateFlow<List<GameItem>>(emptyList())
    val installedApps: StateFlow<List<GameItem>> = _installedApps.asStateFlow()

    // Moonlight game import (TV channel + pinned shortcuts)
    private val _moonlightState = MutableStateFlow(MoonlightUiState())
    val moonlightState: StateFlow<MoonlightUiState> = _moonlightState.asStateFlow()

    // All library items from Room
    val allItems: StateFlow<List<GameItem>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Games Tab: poster grid (all cloud games, game apps, shortcuts)
    val gamesList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { it.isGame }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Apps Tab: all other applications using their icons
    val appsList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { !it.isGame }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initializeLibraryIfEmpty()
            refreshInstalledAppsList()
            repository.backfillGfnArtwork()
        }
    }

    private suspend fun refreshInstalledAppsList() {
        val apps = installedAppsManager.getInstalledApps()
        _installedApps.value = apps
    }

    fun setTab(tab: MainLibraryTab) {
        _currentTab.value = tab
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFocusedItem(id: String) {
        _focusedItemId.value = id
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
            GameType.SHORTCUT -> {
                val url = item.launchUrl
                when {
                    url == null -> item.packageName?.let { installedAppsManager.launchApp(it) }
                    url.startsWith("intent:") -> {
                        // Moonlight-style persisted launch intent. If the target activity is
                        // gone, at least open the app so the user can pick the game there.
                        if (!installedAppsManager.launchIntentUri(url)) {
                            item.packageName?.let { installedAppsManager.launchApp(it) }
                        }
                    }
                    url.startsWith("http://") || url.startsWith("https://") ->
                        installedAppsManager.openExternally(url)
                    else -> item.packageName?.let { installedAppsManager.launchApp(it) }
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
        }
    }

    fun moveItemToGames(item: GameItem) {
        viewModelScope.launch {
            repository.setIsGame(item.id, true)
            closeContextMenu()
        }
    }

    fun toggleFavorite(item: GameItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, item.isFavorite)
        }
    }

    fun deleteItem(item: GameItem) {
        viewModelScope.launch {
            repository.deleteGame(item.id)
            closeContextMenu()
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
        if (view == AddModalSubView.ADD_MOONLIGHT) {
            loadMoonlightGames()
        }
    }

    fun closeAddModal() {
        _showAddModal.value = false
        _addModalSubView.value = AddModalSubView.OPTIONS
    }

    fun addAppToGames(app: GameItem) {
        viewModelScope.launch {
            val existing = allItems.value.find { it.id == app.id }
            if (existing != null) {
                repository.setIsGame(app.id, true)
            } else {
                repository.addCustomShortcut(
                    title = app.title,
                    url = app.packageName ?: "",
                    posterUrl = app.iconUrl,
                    type = GameType.INSTALLED_APP
                )
            }
            closeAddModal()
            _currentTab.value = MainLibraryTab.GAMES
        }
    }

    fun addCustomShortcutToGames(title: String, url: String, posterUrl: String?) {
        viewModelScope.launch {
            val type = if (url.contains("geforcenow.com") || url.contains("xbox.com/play")) {
                GameType.CUSTOM_CLOUD
            } else {
                GameType.SHORTCUT
            }
            repository.addCustomShortcut(title, url, posterUrl, type)
            closeAddModal()
            _currentTab.value = MainLibraryTab.GAMES
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

    /**
     * Scan Moonlight's TV channel and pinned shortcuts. Called when the Moonlight sub-view
     * opens and again after the TV-listings permission dialog resolves.
     */
    fun loadMoonlightGames() {
        if (_moonlightState.value.isLoading) return
        viewModelScope.launch {
            _moonlightState.value = _moonlightState.value.copy(
                isLoading = true,
                message = null
            )
            val result = moonlightService.scan()
            _moonlightState.value = MoonlightUiState(
                isLoading = false,
                games = result.games,
                message = moonlightMessage(result),
                shortcutsBlocked = result.shortcutsBlocked,
                hasScanned = true
            )
        }
    }

    /** Persist a Moonlight game as a direct-launch tile (stable id, so re-adding is safe). */
    fun addMoonlightGameToGames(game: GameItem) {
        viewModelScope.launch {
            val id = "user_${game.id}"
            val existing = allItems.value.find { it.id == id }
            if (existing != null) {
                repository.setIsGame(id, true)
            } else {
                repository.insertUserGame(game.copy(id = id, isGame = true))
            }
            closeAddModal()
            _currentTab.value = MainLibraryTab.GAMES
        }
    }

    private fun moonlightMessage(result: MoonlightService.ScanResult): String? {
        if (!result.installed) {
            return "Moonlight isn't installed on this device."
        }
        if (result.unsupportedAndroidVersion) {
            return "Moonlight's game shortcuts need Android 8.0 or newer. Add a custom shortcut instead."
        }
        if (result.games.isNotEmpty()) {
            return null
        }
        return when {
            result.shortcutsBlocked && !result.tvProviderAvailable ->
                "Make Universal Launcher the default Home app so it can read Moonlight's game shortcuts, then tap Refresh."
            result.shortcutsBlocked && result.tvListingsDenied ->
                "Grant TV listings access to read Moonlight's game channel, or make this launcher the default Home app to import its shortcuts."
            result.shortcutsBlocked ->
                "Moonlight hasn't listed any games yet. Make this launcher the default Home app to import its shortcuts, then tap Refresh."
            result.tvListingsDenied && result.tvProviderAvailable ->
                "Grant TV listings access to read Moonlight's game channel, or pin games in Moonlight, then tap Refresh."
            else ->
                "Moonlight hasn't listed any games yet. Launch a game in Moonlight once, then tap Refresh."
        }
    }

    // Settings Modal
    fun openSettingsModal() {
        _showSettingsModal.value = true
    }

    fun closeSettingsModal() {
        _showSettingsModal.value = false
    }

    fun setIconShape(shape: AppIconShape) {
        prefs.edit().putString("icon_shape", shape.name).apply()
        _iconShape.value = shape
    }

    fun setUseThemedIcons(useThemed: Boolean) {
        prefs.edit().putBoolean("themed_icons", useThemed).apply()
        _useThemedIcons.value = useThemed
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
            refreshInstalledAppsList()
        }
    }

    // Controller navigation
    fun onControllerAction(action: ControllerAction): Boolean {
        // Close open modals on Back (B)
        if (_showAddModal.value || _showSettingsModal.value || _editingItem.value != null || _contextMenuItem.value != null) {
            if (action == ControllerAction.BACK_B) {
                if (_addModalSubView.value != AddModalSubView.OPTIONS && _showAddModal.value) {
                    _addModalSubView.value = AddModalSubView.OPTIONS
                    return true
                }
                closeAddModal()
                closeSettingsModal()
                closeEditCustomizer()
                closeContextMenu()
                return true
            }
            return false
        }

        // Bumper navigation: L2 / L1 switches to Games, R2 / R1 switches to Apps
        when (action) {
            ControllerAction.PREV_TAB_LB -> {
                setTab(MainLibraryTab.GAMES)
                return true
            }
            ControllerAction.NEXT_TAB_RB -> {
                setTab(MainLibraryTab.APPS)
                return true
            }
            ControllerAction.CONFIRM_A -> {
                val currentList = if (_currentTab.value == MainLibraryTab.GAMES) gamesList.value else appsList.value
                val item = currentList.find { it.id == _focusedItemId.value } ?: currentList.firstOrNull()
                item?.let { launchItem(it) }
                return true
            }
            ControllerAction.CUSTOMIZE_X -> {
                val currentList = if (_currentTab.value == MainLibraryTab.GAMES) gamesList.value else appsList.value
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
                val currentList = if (_currentTab.value == MainLibraryTab.GAMES) gamesList.value else appsList.value
                if (currentList.isEmpty()) return false
                val curIndex = currentList.indexOfFirst { it.id == _focusedItemId.value }.let { if (it >= 0) it else 0 }
                val nextIndex = when (action) {
                    ControllerAction.RIGHT -> (curIndex + 1).coerceAtMost(currentList.lastIndex)
                    ControllerAction.LEFT -> (curIndex - 1).coerceAtLeast(0)
                    ControllerAction.DOWN -> (curIndex + 4).coerceAtMost(currentList.lastIndex)
                    ControllerAction.UP -> (curIndex - 4).coerceAtLeast(0)
                    else -> curIndex
                }
                _focusedItemId.value = currentList[nextIndex].id
                return true
            }
            else -> return false
        }
    }
}
