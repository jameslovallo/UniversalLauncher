package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import com.example.data.service.InstalledAppsManager
import com.example.input.ControllerAction
import com.example.input.ControllerManager
import com.example.webview.GamepadJsBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LauncherCategory(val label: String) {
    ALL("All Games & Apps"),
    GEFORCE_NOW("GeForce NOW"),
    INSTALLED_APPS("Installed Apps"),
    FAVORITES("Favorites"),
    RECENT("Recently Played"),
    SHORTCUTS("Shortcuts")
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

    private val _selectedCategory = MutableStateFlow(LauncherCategory.ALL)
    val selectedCategory: StateFlow<LauncherCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _focusedGameId = MutableStateFlow<String?>(null)
    val focusedGameId: StateFlow<String?> = _focusedGameId.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    private val _showTouchControlsInSession = MutableStateFlow(true)
    val showTouchControlsInSession: StateFlow<Boolean> = _showTouchControlsInSession.asStateFlow()

    private val _customizingGame = MutableStateFlow<GameItem?>(null)
    val customizingGame: StateFlow<GameItem?> = _customizingGame.asStateFlow()

    private val _showAddShortcutDialog = MutableStateFlow(false)
    val showAddShortcutDialog: StateFlow<Boolean> = _showAddShortcutDialog.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showSearchDialog = MutableStateFlow(false)
    val showSearchDialog: StateFlow<Boolean> = _showSearchDialog.asStateFlow()

    private val _steamGridDbApiKey = MutableStateFlow(prefs.getString("sgdb_api_key", ""))
    val steamGridDbApiKey: StateFlow<String?> = _steamGridDbApiKey.asStateFlow()

    val allGames: StateFlow<List<GameItem>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGames: StateFlow<List<GameItem>> = combine(
        allGames,
        _selectedCategory,
        _searchQuery
    ) { games, category, query ->
        val categoryFiltered = when (category) {
            LauncherCategory.ALL -> games
            LauncherCategory.GEFORCE_NOW -> games.filter { it.type == GameType.GEFORCE_NOW }
            LauncherCategory.INSTALLED_APPS -> games.filter { it.type == GameType.INSTALLED_APP }
            LauncherCategory.FAVORITES -> games.filter { it.isFavorite }
            LauncherCategory.RECENT -> games.filter { it.lastPlayedTimestamp > 0 }.sortedByDescending { it.lastPlayedTimestamp }
            LauncherCategory.SHORTCUTS -> games.filter { it.type == GameType.SHORTCUT || it.type == GameType.CUSTOM_CLOUD }
        }

        if (query.isBlank()) {
            categoryFiltered
        } else {
            val q = query.lowercase().trim()
            categoryFiltered.filter {
                it.title.lowercase().contains(q) ||
                        it.genre.lowercase().contains(q) ||
                        it.tags.lowercase().contains(q) ||
                        it.publisher.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initializeLibraryIfEmpty()
        }
    }

    fun selectCategory(category: LauncherCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFocusedGame(id: String) {
        _focusedGameId.value = id
    }

    fun launchGame(game: GameItem) {
        viewModelScope.launch {
            repository.recordPlay(game.id)
        }

        when (game.type) {
            GameType.INSTALLED_APP -> {
                game.packageName?.let { pkg ->
                    installedAppsManager.launchApp(pkg)
                }
            }
            GameType.GEFORCE_NOW,
            GameType.CUSTOM_CLOUD -> {
                val url = game.launchUrl ?: "https://play.geforcenow.com"
                _activeSession.value = ActiveGameSession(game, url)
            }
            GameType.SHORTCUT -> {
                val url = game.launchUrl
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    _activeSession.value = ActiveGameSession(game, url)
                } else if (game.packageName != null) {
                    installedAppsManager.launchApp(game.packageName)
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

    fun openArtworkCustomizer(game: GameItem) {
        _customizingGame.value = game
    }

    fun closeArtworkCustomizer() {
        _customizingGame.value = null
    }

    fun saveArtwork(posterUrl: String, heroUrl: String?, source: String) {
        val game = _customizingGame.value ?: return
        viewModelScope.launch {
            repository.updateArtwork(game.id, posterUrl, heroUrl, source)
        }
    }

    fun toggleFavorite(game: GameItem) {
        viewModelScope.launch {
            repository.toggleFavorite(game.id, game.isFavorite)
        }
    }

    fun openAddShortcutDialog() {
        _showAddShortcutDialog.value = true
    }

    fun closeAddShortcutDialog() {
        _showAddShortcutDialog.value = false
    }

    fun addCustomShortcut(title: String, url: String, posterUrl: String?, type: GameType) {
        viewModelScope.launch {
            repository.addCustomShortcut(title, url, posterUrl, type)
        }
    }

    fun openSettingsDialog() {
        _showSettingsDialog.value = true
    }

    fun closeSettingsDialog() {
        _showSettingsDialog.value = false
    }

    fun openSearchDialog() {
        _showSearchDialog.value = true
    }

    fun closeSearchDialog() {
        _showSearchDialog.value = false
    }

    fun saveSteamGridDbApiKey(key: String) {
        prefs.edit().putString("sgdb_api_key", key).apply()
        _steamGridDbApiKey.value = key
    }

    fun openDefaultLauncherSettings() {
        installedAppsManager.openHomeSettings()
    }

    fun syncGfnCatalog() {
        viewModelScope.launch {
            repository.syncGfnCatalog()
        }
    }

    fun syncInstalledApps() {
        viewModelScope.launch {
            repository.syncInstalledApps()
        }
    }

    /**
     * Dispatch controller action for navigation across items & tabs
     */
    fun onControllerAction(action: ControllerAction): Boolean {
        // If an active cloud game session is open, handle in-game shortcuts
        if (_activeSession.value != null) {
            if (action == ControllerAction.QUICK_SELECT || action == ControllerAction.BACK_B) {
                // Exit or quick pause menu handled in GameSessionScreen
                return false
            }
            return false // Forward rest to WebView
        }

        // If a dialog is open, handle B to dismiss
        if (_customizingGame.value != null || _showAddShortcutDialog.value || _showSettingsDialog.value || _showSearchDialog.value) {
            if (action == ControllerAction.BACK_B) {
                _customizingGame.value = null
                _showAddShortcutDialog.value = false
                _showSettingsDialog.value = false
                _showSearchDialog.value = false
                return true
            }
            return false
        }

        val currentList = filteredGames.value
        if (currentList.isEmpty()) return false

        val currentFocusedId = _focusedGameId.value
        val currentIndex = currentList.indexOfFirst { it.id == currentFocusedId }.let { if (it >= 0) it else 0 }

        when (action) {
            ControllerAction.RIGHT -> {
                val nextIndex = (currentIndex + 1).coerceAtMost(currentList.lastIndex)
                _focusedGameId.value = currentList[nextIndex].id
                return true
            }
            ControllerAction.LEFT -> {
                val prevIndex = (currentIndex - 1).coerceAtLeast(0)
                _focusedGameId.value = currentList[prevIndex].id
                return true
            }
            ControllerAction.DOWN -> {
                // Jump 4 cards forward in grid
                val nextIndex = (currentIndex + 4).coerceAtMost(currentList.lastIndex)
                _focusedGameId.value = currentList[nextIndex].id
                return true
            }
            ControllerAction.UP -> {
                // Jump 4 cards back
                val prevIndex = (currentIndex - 4).coerceAtLeast(0)
                _focusedGameId.value = currentList[prevIndex].id
                return true
            }
            ControllerAction.CONFIRM_A -> {
                currentList.getOrNull(currentIndex)?.let { launchGame(it) }
                return true
            }
            ControllerAction.CUSTOMIZE_X -> {
                currentList.getOrNull(currentIndex)?.let { openArtworkCustomizer(it) }
                return true
            }
            ControllerAction.FAVORITE_Y -> {
                currentList.getOrNull(currentIndex)?.let { toggleFavorite(it) }
                return true
            }
            ControllerAction.PREV_TAB_LB -> {
                val allCategories = LauncherCategory.values()
                val currentCategoryIndex = allCategories.indexOf(_selectedCategory.value)
                val newIndex = if (currentCategoryIndex > 0) currentCategoryIndex - 1 else allCategories.lastIndex
                _selectedCategory.value = allCategories[newIndex]
                return true
            }
            ControllerAction.NEXT_TAB_RB -> {
                val allCategories = LauncherCategory.values()
                val currentCategoryIndex = allCategories.indexOf(_selectedCategory.value)
                val newIndex = if (currentCategoryIndex < allCategories.lastIndex) currentCategoryIndex + 1 else 0
                _selectedCategory.value = allCategories[newIndex]
                return true
            }
            ControllerAction.MENU_START -> {
                _showSettingsDialog.value = true
                return true
            }
            ControllerAction.QUICK_SELECT -> {
                _showSearchDialog.value = true
                return true
            }
            ControllerAction.BACK_B -> {
                if (_searchQuery.value.isNotEmpty()) {
                    _searchQuery.value = ""
                    return true
                }
            }
        }
        return false
    }
}
