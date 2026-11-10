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
import com.example.input.ControllerAction
import com.example.input.ControllerManager
import com.example.ui.theme.AppIconShape
import com.example.webview.GamepadJsBridge
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
    ADD_GFN
}

data class ActiveGameSession(
    val game: GameItem,
    val playUrl: String
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository.create(application)
    private val installedAppsManager = InstalledAppsManager(application)
    val controllerManager = ControllerManager(application)
    val gamepadBridge = GamepadJsBridge()

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

    // Fullscreen streaming session
    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    private val _showTouchControlsInSession = MutableStateFlow(true)
    val showTouchControlsInSession: StateFlow<Boolean> = _showTouchControlsInSession.asStateFlow()

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
            GameType.GEFORCE_NOW,
            GameType.CUSTOM_CLOUD -> {
                val url = item.launchUrl ?: "https://play.geforcenow.com"
                _activeSession.value = ActiveGameSession(item, url)
            }
            GameType.SHORTCUT -> {
                val url = item.launchUrl
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    _activeSession.value = ActiveGameSession(item, url)
                } else if (item.packageName != null) {
                    installedAppsManager.launchApp(item.packageName)
                }
            }
        }
    }

    fun closeActiveSession() {
        _activeSession.value = null
    }

    fun toggleTouchControlsInSession() {
        _showTouchControlsInSession.value = !_showTouchControlsInSession.value
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
            val userGame = gfnGame.copy(
                id = "user_gfn_${gfnGame.gfnCmsId ?: System.currentTimeMillis()}",
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
        // Forward to WebView during active session
        if (_activeSession.value != null) {
            return false
        }

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
