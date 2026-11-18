package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import com.example.data.service.AmazonLunaService
import com.example.data.service.DriveAuthAccount
import com.example.data.service.DriveAuthorizationRequiredException
import com.example.data.service.DriveAuthorizationStep
import com.example.data.service.DriveDeviceCodeSession
import com.example.data.service.DriveDevicePollResult
import com.example.data.service.DriveOAuthConfigurationException
import com.example.data.service.GeForceNowService
import com.example.data.service.IconPackIcon
import com.example.data.service.IconPackInfo
import com.example.data.service.IconPackManager
import com.example.data.service.IconPackOverridesStore
import com.example.data.service.InstalledAppsManager
import com.example.data.service.LibrarySyncMode
import com.example.data.service.LibrarySyncOutcome
import com.example.data.service.LibrarySyncService
import com.example.data.service.MOONLIGHT_PACKAGES
import com.example.data.service.STREAMING_CLIENT_APP_PACKAGES
import com.example.data.service.SteamGridDbService
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherApp
import com.example.data.service.WeatherInfo
import com.example.data.service.WeatherLocationMode
import com.example.data.service.WeatherService
import com.example.input.ControllerAction
import com.example.input.ControllerManager
import com.example.ui.components.hasCustomAppIconUrl
import com.example.ui.theme.FavoritesLayout
import com.example.ui.theme.ThemeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class MainLibraryTab {
    APPS,
    GAMES
}

enum class AddModalSubView {
    OPTIONS,
    ADD_GFN,
    ADD_LUNA,
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

    // Focused item ID, used to highlight the active element when a controller is connected
    private val _focusedItemId = MutableStateFlow<String?>(null)
    val focusedItemId: StateFlow<String?> = _focusedItemId.asStateFlow()

    // Context Menu for Long Press
    private val _contextMenuItem = MutableStateFlow<GameItem?>(null)
    val contextMenuItem: StateFlow<GameItem?> = _contextMenuItem.asStateFlow()

    private val _canDeleteContextMenuItem = MutableStateFlow(true)
    val canDeleteContextMenuItem: StateFlow<Boolean> = _canDeleteContextMenuItem.asStateFlow()

    // Item being customized (Name, Icon, Poster)
    private val _editingItem = MutableStateFlow<GameItem?>(null)
    val editingItem: StateFlow<GameItem?> = _editingItem.asStateFlow()

    // Favorite long-press menu and drag-to-reorder editor
    private val _favoriteActionItem = MutableStateFlow<GameItem?>(null)
    val favoriteActionItem: StateFlow<GameItem?> = _favoriteActionItem.asStateFlow()

    private val _showFavoritesEditor = MutableStateFlow(false)
    val showFavoritesEditor: StateFlow<Boolean> = _showFavoritesEditor.asStateFlow()

    // Folder sheets
    private val _openFolderId = MutableStateFlow<String?>(null)
    val openFolderId: StateFlow<String?> = _openFolderId.asStateFlow()

    private val _folderPickerTargetItem = MutableStateFlow<GameItem?>(null)
    val folderPickerTargetItem: StateFlow<GameItem?> = _folderPickerTargetItem.asStateFlow()

    private val _manageFolderId = MutableStateFlow<String?>(null)
    val manageFolderId: StateFlow<String?> = _manageFolderId.asStateFlow()

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

    // Adult artwork is only fetched and shown on posters when explicitly enabled in Settings.
    private val _steamGridDbIncludeNsfw = MutableStateFlow(
        prefs.getBoolean("sgdb_include_nsfw", false)
    )
    val steamGridDbIncludeNsfw: StateFlow<Boolean> = _steamGridDbIncludeNsfw.asStateFlow()

    // Icon themes apply to the Apps view only (favorites, app list, context menus, hidden
    // apps). Games keep their own poster artwork.
    private val iconPackStore = IconPackOverridesStore(prefs)

    private val _installedIconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val installedIconPacks: StateFlow<List<IconPackInfo>> = _installedIconPacks.asStateFlow()

    private val _selectedIconPack = MutableStateFlow(iconPackStore.selectedIconPack)
    val selectedIconPack: StateFlow<String?> = _selectedIconPack.asStateFlow()

    private val _iconPackIcons = MutableStateFlow<List<IconPackIcon>>(emptyList())
    val iconPackIcons: StateFlow<List<IconPackIcon>> = _iconPackIcons.asStateFlow()

    private val iconThemeMutex = Mutex()

    // itemId -> drawableName overrides remembered for the currently selected pack.
    private val _selectedPackOverrides = MutableStateFlow(
        _selectedIconPack.value?.let { iconPackStore.getOverridesForPack(it) }.orEmpty()
    )
    val selectedPackOverrides: StateFlow<Map<String, String>> = _selectedPackOverrides.asStateFlow()

    // itemId -> custom image/URL overrides remembered for the currently active theme
    // (Default theme when selectedIconPack is null, or a custom icon pack).
    private val _selectedCustomUrlOverrides = MutableStateFlow(
        iconPackStore.getCustomUrlOverrides(_selectedIconPack.value)
    )
    val selectedCustomUrlOverrides: StateFlow<Map<String, String>> =
        _selectedCustomUrlOverrides.asStateFlow()

    // Hydrated synchronously from the cached map so Frame 0 already shows themed or custom
    // icons without briefly flashing original device icons.
    private val _themedAppIcons = MutableStateFlow(
        iconPackStore.loadCachedIconMap(_selectedIconPack.value)
    )
    val themedAppIcons: StateFlow<Map<String, String>> = _themedAppIcons.asStateFlow()

    private val weatherService = WeatherService(application)
    private val librarySyncService = LibrarySyncService(application, repository, prefs)

    // Opacity of the wallpaper scrim on the Apps screen. 0 = wallpaper only, 1 = solid.
    private val _appsBackgroundOpacity = MutableStateFlow(
        prefs.getFloat("apps_bg_opacity", DEFAULT_APPS_OPACITY).coerceIn(0f, 1f)
    )
    val appsBackgroundOpacity: StateFlow<Float> = _appsBackgroundOpacity.asStateFlow()

    // File-backed background wallpaper for TV devices (Android TV lacks a system live wallpaper).
    private val _tvWallpaperUrl = MutableStateFlow(
        prefs.getString(KEY_TV_WALLPAPER_URL, null)?.takeIf { it.isNotBlank() }
    )
    val tvWallpaperUrl: StateFlow<String?> = _tvWallpaperUrl.asStateFlow()

    // Custom column count for app rows across Favorites, App List, and Folders (0 = Auto).
    private val _appGridColumns = MutableStateFlow(
        prefs.getInt(KEY_APP_GRID_COLUMNS, 0).coerceIn(0, MAX_APP_GRID_COLUMNS)
    )
    val appGridColumns: StateFlow<Int> = _appGridColumns.asStateFlow()

    private val _themeStyle = MutableStateFlow(
        ThemeStyle.fromStorage(prefs.getString("theme_style", ThemeStyle.WALLPAPER.name))
    )
    val themeStyle: StateFlow<ThemeStyle> = _themeStyle.asStateFlow()

    private val _weatherEnabled = MutableStateFlow(prefs.getBoolean("weather_enabled", false))
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled.asStateFlow()

    private val _weatherMode = MutableStateFlow(
        runCatching {
            WeatherLocationMode.valueOf(
                prefs.getString("weather_mode", WeatherLocationMode.DEVICE_LOCATION.name)!!
            )
        }.getOrDefault(WeatherLocationMode.DEVICE_LOCATION)
    )
    val weatherMode: StateFlow<WeatherLocationMode> = _weatherMode.asStateFlow()

    private val _weatherZip = MutableStateFlow(prefs.getString("weather_zip", "").orEmpty())
    val weatherZip: StateFlow<String> = _weatherZip.asStateFlow()

    private val _temperatureUnit = MutableStateFlow(
        TemperatureUnit.fromStorage(prefs.getString("temperature_unit", null))
    )
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _weatherAppPackage = MutableStateFlow(
        prefs.getString("weather_app_package", "").orEmpty()
    )
    val weatherAppPackage: StateFlow<String> = _weatherAppPackage.asStateFlow()

    private val _weatherApps = MutableStateFlow<List<WeatherApp>>(emptyList())
    val weatherApps: StateFlow<List<WeatherApp>> = _weatherApps.asStateFlow()

    // Non-null while the user is choosing between multiple installed weather apps.
    private val _weatherAppChooser = MutableStateFlow<List<WeatherApp>?>(null)
    val weatherAppChooser: StateFlow<List<WeatherApp>?> = _weatherAppChooser.asStateFlow()

    private val _weatherInfo = MutableStateFlow<WeatherInfo?>(null)
    val weatherInfo: StateFlow<WeatherInfo?> = _weatherInfo.asStateFlow()

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError.asStateFlow()

    private val _librarySyncBusy = MutableStateFlow(false)
    val librarySyncBusy: StateFlow<Boolean> = _librarySyncBusy.asStateFlow()

    private val _librarySyncStatus = MutableStateFlow<String?>(null)
    val librarySyncStatus: StateFlow<String?> = _librarySyncStatus.asStateFlow()

    private val _librarySyncConfigured = MutableStateFlow(librarySyncService.isConfigured)
    val librarySyncConfigured: StateFlow<Boolean> = _librarySyncConfigured.asStateFlow()

    private val _librarySyncMode = MutableStateFlow(librarySyncService.syncMode)
    val librarySyncMode: StateFlow<LibrarySyncMode> = _librarySyncMode.asStateFlow()

    private val _librarySyncTarget = MutableStateFlow(librarySyncService.targetLabel())
    val librarySyncTarget: StateFlow<String?> = _librarySyncTarget.asStateFlow()

    private val _librarySyncLastSyncedAt = MutableStateFlow(librarySyncService.lastSyncedAt())
    val librarySyncLastSyncedAt: StateFlow<Long?> = _librarySyncLastSyncedAt.asStateFlow()

    private val _librarySyncNeedsReauth = MutableStateFlow(false)
    val librarySyncNeedsReauth: StateFlow<Boolean> = _librarySyncNeedsReauth.asStateFlow()

    data class PendingDriveFallbackIntent(
        val intent: Intent,
        val accountEmail: String? = null,
        val isAccountPicker: Boolean = false
    )

    private val _pendingDriveAuthIntent = MutableStateFlow<PendingIntent?>(null)
    val pendingDriveAuthIntent: StateFlow<PendingIntent?> = _pendingDriveAuthIntent.asStateFlow()

    private val _pendingDriveFallbackIntent = MutableStateFlow<PendingDriveFallbackIntent?>(null)
    val pendingDriveFallbackIntent: StateFlow<PendingDriveFallbackIntent?> =
        _pendingDriveFallbackIntent.asStateFlow()

    private val _pendingDriveDeviceSession = MutableStateFlow<DriveDeviceCodeSession?>(null)
    val pendingDriveDeviceSession: StateFlow<DriveDeviceCodeSession?> =
        _pendingDriveDeviceSession.asStateFlow()

    private var activeDriveFallbackContext: Pair<String?, Boolean>? = null
    private var driveDevicePollJob: Job? = null
    private var isInteractiveDriveAuthActive = false
    private var lastDriveAuthLaunchElapsedMs = 0L

    private val _gamesRefreshing = MutableStateFlow(false)
    val gamesRefreshing: StateFlow<Boolean> = _gamesRefreshing.asStateFlow()

    private val _gamesRefreshFeedback = MutableStateFlow<String?>(null)
    val gamesRefreshFeedback: StateFlow<String?> = _gamesRefreshFeedback.asStateFlow()

    // All library items from Room
    val allItems: StateFlow<List<GameItem>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Games Tab: poster grid (all cloud games, game apps, shortcuts)
    val gamesList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { it.isGame }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Apps Tab: all non-game applications (excluding folder members), folders sorted after Z
    val appsList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { !it.isGame && it.folderId == null }
            .sortedWith(compareBy<GameItem> { it.type == GameType.FOLDER }.thenBy { it.title.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All folders in alphabetical order
    val foldersList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { !it.isGame && it.type == GameType.FOLDER }
            .sortedBy { it.title.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // folderId -> sorted list of non-folder apps inside that folder
    val folderMembersMap: StateFlow<Map<String, List<GameItem>>> = allItems.map { list ->
        list.filter { !it.isGame && it.type != GameType.FOLDER && !it.folderId.isNullOrBlank() }
            .sortedBy { it.title.lowercase() }
            .groupBy { it.folderId!! }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Favorites home: favorited apps (and favorited folders) in the user's custom order
    val favoriteApps: StateFlow<List<GameItem>> = repository.favoriteApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Full app list shown after swiping up: no favorites, no games, no folder members;
    // unfavorited folders appear after Z.
    val libraryApps: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { !it.isGame && !it.isFavorite && it.folderId == null }
            .sortedWith(compareBy<GameItem> { it.type == GameType.FOLDER }.thenBy { it.title.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hidden items for the Settings restore list
    val hiddenItems: StateFlow<List<GameItem>> = repository.hiddenGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val pendingLegacyCleanup = !prefs.getBoolean(KEY_LEGACY_CLEANUP_DONE, false)
            repository.initializeLibraryIfEmpty(purgeLegacyCatalog = pendingLegacyCleanup)
            if (pendingLegacyCleanup) {
                prefs.edit().putBoolean(KEY_LEGACY_CLEANUP_DONE, true).apply()
            }

            if (!prefs.getBoolean(KEY_STREAMING_CLIENTS_AS_APPS_DONE, false)) {
                repository.recategorizeStreamingClientsAsApps(STREAMING_CLIENT_APP_PACKAGES)
                prefs.edit().putBoolean(KEY_STREAMING_CLIENTS_AS_APPS_DONE, true).apply()
            }

            // Restore the library file before anything else can write over it.
            if (librarySyncService.isConfigured) {
                runCatching { librarySyncService.syncNow() }
                    .onSuccess { outcome ->
                        _librarySyncNeedsReauth.value = false
                        if (outcome is LibrarySyncOutcome.Applied || outcome is LibrarySyncOutcome.Merged) {
                            _librarySyncStatus.value = formatSyncOutcomeStatus(
                                outcome,
                                librarySyncService.syncMode
                            )
                        }
                    }
                    .onFailure { error ->
                        if (error is DriveAuthorizationRequiredException) {
                            _librarySyncNeedsReauth.value = true
                        }
                        _librarySyncStatus.value = error.message ?: "Library sync failed"
                    }
            }
            refreshLibrarySyncState()
            librarySyncService.observeLibraryChanges(viewModelScope) { error ->
                if (error is DriveAuthorizationRequiredException) {
                    _librarySyncNeedsReauth.value = true
                }
                _librarySyncStatus.value = error.message ?: "Library sync failed"
            }

            repository.backfillGfnArtwork()
            repository.matchMissingGamePosters()
        }
        if (_weatherEnabled.value) refreshWeather()
        refreshWeatherApps()
        refreshIconPacks()
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

    fun launchItem(item: GameItem) {
        if (item.type == GameType.FOLDER) {
            openFolder(item.id)
            return
        }

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
            GameType.AMAZON_LUNA -> {
                installedAppsManager.launchLunaGame(item.launchUrl)
            }
            GameType.CUSTOM_CLOUD -> {
                val url = item.launchUrl ?: "https://play.geforcenow.com"
                installedAppsManager.openExternally(url)
            }
            GameType.SHORTCUT, GameType.MOONLIGHT -> {
                val url = item.launchUrl
                val pkg = item.packageName
                val shortcutId = item.activityName

                fun launchApp() {
                    pkg?.let { installedAppsManager.launchApp(it) }
                }

                when {
                    // Locally pinned shortcut: let the system launch it with the
                    // publisher's identity, which also works for non-exported targets.
                    item.isPinned && pkg != null && shortcutId != null &&
                        installedAppsManager.launchPinnedShortcut(pkg, shortcutId) -> Unit

                    // Moonlight games restored from another device are not pinned here.
                    // Rebuild the direct game intent from the shortcut id instead of
                    // falling back to Moonlight's home screen.
                    pkg != null && pkg in MOONLIGHT_PACKAGES && shortcutId != null &&
                        installedAppsManager.launchMoonlightShortcut(
                            pkg,
                            shortcutId,
                            item.title
                        ) -> Unit

                    // Other shortcuts can usually use their persisted intent directly.
                    url != null && url.startsWith("intent:") &&
                        installedAppsManager.launchIntentUri(url) -> Unit

                    // Custom web shortcuts store a plain URL.
                    url != null && (url.startsWith("http://") || url.startsWith("https://")) ->
                        installedAppsManager.openExternally(url)

                    // Legacy rows did not carry the pinned flag yet.
                    pkg != null && shortcutId != null &&
                        installedAppsManager.launchPinnedShortcut(pkg, shortcutId) -> Unit

                    else -> launchApp()
                }
            }
            GameType.FOLDER -> Unit
        }
    }

    // Context Menu Handlers
    fun openContextMenu(item: GameItem) {
        _canDeleteContextMenuItem.value = canDeleteLibraryItem(item)
        _contextMenuItem.value = item
    }

    private fun canDeleteLibraryItem(item: GameItem): Boolean {
        if (item.type != GameType.INSTALLED_APP) return true
        val pkg = item.packageName?.takeIf { it.isNotBlank() } ?: return true
        return !installedAppsManager.isProtectedApp(pkg)
    }

    /** Clear a game's last-played timestamp so it drops out of the recents ordering. */
    fun removeFromRecents(item: GameItem) {
        viewModelScope.launch {
            repository.clearPlayHistory(item.id)
            closeContextMenu()
        }
    }

    fun openContextMenuForFocusedItem(): Boolean {
        val focusedId = _focusedItemId.value
        val item = if (focusedId != null) {
            allItems.value.firstOrNull { it.id == focusedId }
        } else {
            if (_currentTab.value == MainLibraryTab.GAMES) {
                gamesList.value.firstOrNull()
            } else {
                favoriteApps.value.firstOrNull() ?: appsList.value.firstOrNull()
            }
        } ?: return false

        openContextMenu(item)
        return true
    }

    fun isAnyMenuOrModalOpen(): Boolean =
        _showAddModal.value ||
        _showSettingsModal.value ||
        _editingItem.value != null ||
        _contextMenuItem.value != null ||
        _favoriteActionItem.value != null ||
        _showFavoritesEditor.value ||
        _openFolderId.value != null ||
        _folderPickerTargetItem.value != null ||
        _manageFolderId.value != null

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
        if (item.type == GameType.FOLDER) return
        viewModelScope.launch {
            repository.setIsGame(item.id, true)
            repository.ensureAutoPoster(item.copy(isGame = true))
            closeContextMenu()
            setTab(MainLibraryTab.GAMES)
        }
    }

    // ---- Folder management ---------------------------------------------------------

    fun openFolder(folderId: String) {
        _openFolderId.value = folderId
    }

    fun closeFolder() {
        _openFolderId.value = null
    }

    fun openFolderPicker(item: GameItem) {
        _contextMenuItem.value = null
        _favoriteActionItem.value = null
        _folderPickerTargetItem.value = item
    }

    fun closeFolderPicker() {
        _folderPickerTargetItem.value = null
    }

    fun openManageFolderApps(folderId: String) {
        _contextMenuItem.value = null
        _favoriteActionItem.value = null
        _manageFolderId.value = folderId
    }

    fun closeManageFolderApps() {
        _manageFolderId.value = null
    }

    fun createFolder(title: String, initialMemberId: String? = null) {
        viewModelScope.launch {
            repository.createFolder(title, initialMemberId)
            _folderPickerTargetItem.value = null
        }
    }

    fun moveItemToFolder(itemId: String, folderId: String?) {
        viewModelScope.launch {
            repository.moveItemToFolder(itemId, folderId)
            _folderPickerTargetItem.value = null
            _contextMenuItem.value = null
        }
    }

    fun setFolderMembers(folderId: String, memberIds: Set<String>) {
        viewModelScope.launch {
            repository.setFolderMembers(folderId, memberIds)
            _manageFolderId.value = null
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
        closeFavoriteActions()
    }

    fun deleteItem(item: GameItem) {
        if (!canDeleteLibraryItem(item)) return
        viewModelScope.launch {
            if (item.type == GameType.FOLDER) {
                repository.deleteFolder(item.id)
                if (_openFolderId.value == item.id) _openFolderId.value = null
                if (_manageFolderId.value == item.id) _manageFolderId.value = null
            } else {
                // Native apps in the Apps tab get a real uninstall prompt; shortcuts and
                // other library entries are simply removed. Deleting records a tombstone so
                // the app sync cannot bring the tile back on the next launch.
                if (item.type == GameType.INSTALLED_APP && !item.isGame) {
                    item.packageName?.let { installedAppsManager.uninstallApp(it) }
                }
                repository.deleteGame(item.id)
            }
            if (_focusedItemId.value == item.id) {
                _focusedItemId.value = null
            }
            closeContextMenu()
        }
    }

    fun hideItem(item: GameItem) {
        viewModelScope.launch {
            repository.setHidden(item.id, true)
            if (_openFolderId.value == item.id) {
                _openFolderId.value = null
            }
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
        _favoriteActionItem.value = null
        _editingItem.value = item
    }

    fun closeEditCustomizer() {
        _editingItem.value = null
    }

    fun applyItemTitle(id: String, newTitle: String) {
        val current = _editingItem.value?.takeIf { it.id == id } ?: return
        val resolvedTitle = newTitle.trim().ifBlank { current.title }
        if (resolvedTitle == current.title) return
        _editingItem.value = current.copy(title = resolvedTitle, isCustomized = true)
        viewModelScope.launch {
            repository.updateTitle(id, resolvedTitle)
        }
    }

    fun applyItemPoster(id: String, newTitle: String, isNsfwSlot: Boolean, artworkUrl: String?) {
        val current = _editingItem.value?.takeIf { it.id == id } ?: return
        val resolvedTitle = newTitle.trim().ifBlank { current.title }
        val sanitizedUrl = artworkUrl?.trim()?.ifBlank { null }
        if (current.isGame && isNsfwSlot) {
            _editingItem.value = current.copy(
                title = resolvedTitle,
                nsfwPosterUrl = sanitizedUrl,
                isCustomized = true
            )
            viewModelScope.launch {
                repository.updateTitleAndNsfwPoster(id, resolvedTitle, sanitizedUrl)
            }
        } else {
            val updatedPoster = if (current.isGame) sanitizedUrl else current.posterUrl
            if (current.isGame) {
                _editingItem.value = current.copy(
                    title = resolvedTitle,
                    posterUrl = updatedPoster,
                    isCustomized = true
                )
                viewModelScope.launch {
                    repository.updateTitleAndArtwork(id, resolvedTitle, updatedPoster, current.iconUrl)
                }
            } else {
                val activePack = _selectedIconPack.value
                iconPackStore.saveBaseIconUrlIfUnset(id, current.iconUrl)
                if (sanitizedUrl != null) {
                    iconPackStore.setCustomUrlOverride(activePack, id, sanitizedUrl)
                } else {
                    iconPackStore.removeCustomUrlOverride(activePack, id)
                }
                _selectedPackOverrides.value =
                    activePack?.let { iconPackStore.getOverridesForPack(it) }.orEmpty()
                _selectedCustomUrlOverrides.value = iconPackStore.getCustomUrlOverrides(activePack)

                viewModelScope.launch {
                    iconThemeMutex.withLock {
                        val resolvedIcon = sanitizedUrl ?: baseIconUrlFor(current)
                        _editingItem.value?.takeIf { it.id == id }?.let { latest ->
                            _editingItem.value = latest.copy(
                                title = resolvedTitle,
                                posterUrl = updatedPoster,
                                iconUrl = resolvedIcon,
                                isCustomized = true
                            )
                        }
                        repository.updateTitleAndArtwork(id, resolvedTitle, updatedPoster, resolvedIcon)
                        if (sanitizedUrl != null && _selectedIconPack.value == activePack) {
                            val updatedMap = _themedAppIcons.value.toMutableMap()
                            updatedMap[id] = sanitizedUrl
                            if (current.type == GameType.INSTALLED_APP && !current.packageName.isNullOrBlank()) {
                                updatedMap[current.packageName] = sanitizedUrl
                            }
                            _themedAppIcons.value = updatedMap
                            iconPackStore.saveCachedIconMap(activePack, updatedMap)
                        } else if (_selectedIconPack.value == activePack) {
                            if (activePack != null) {
                                applyAndLoadIconPackLocked(activePack)
                            } else {
                                applyAndLoadDefaultThemeLocked()
                            }
                        }
                    }
                }
            }
        }
    }

    /** Toggles the logo-over-poster overlay for the safe or adult artwork slot. */
    fun setItemLogoOverlay(
        id: String,
        newTitle: String,
        isNsfwSlot: Boolean,
        enabled: Boolean
    ) {
        val current = _editingItem.value?.takeIf { it.id == id } ?: return
        val resolvedTitle = newTitle.trim().ifBlank { current.title }
        val existingLogo = if (isNsfwSlot) current.nsfwLogoUrl else current.logoUrl
        _editingItem.value = if (isNsfwSlot) {
            current.copy(title = resolvedTitle, overlayNsfwLogo = enabled, isCustomized = true)
        } else {
            current.copy(title = resolvedTitle, overlayLogo = enabled, isCustomized = true)
        }
        viewModelScope.launch {
            val logoUrl = if (enabled) {
                SteamGridDbService.findLogoForGame(resolvedTitle, _steamGridDbApiKey.value)
                    ?: existingLogo
            } else {
                existingLogo
            }
            val latest = _editingItem.value
            if (latest != null && latest.id == id) {
                _editingItem.value = if (isNsfwSlot) {
                    latest.copy(nsfwLogoUrl = logoUrl)
                } else {
                    latest.copy(logoUrl = logoUrl)
                }
            }
            if (isNsfwSlot) {
                repository.updateTitleAndNsfwOverlay(id, resolvedTitle, enabled, logoUrl)
            } else {
                repository.updateTitleAndLogoOverlay(id, resolvedTitle, enabled, logoUrl)
            }
        }
    }

    fun updateItemLogoUrl(id: String, isNsfwSlot: Boolean, logoUrl: String?) {
        val current = _editingItem.value?.takeIf { it.id == id }
        if (current != null) {
            val existing = if (isNsfwSlot) current.nsfwLogoUrl else current.logoUrl
            if (existing == logoUrl) return
            _editingItem.value = if (isNsfwSlot) {
                current.copy(nsfwLogoUrl = logoUrl)
            } else {
                current.copy(logoUrl = logoUrl)
            }
        }
        viewModelScope.launch {
            if (isNsfwSlot) {
                repository.updateNsfwLogoUrl(id, logoUrl)
            } else {
                repository.updateLogoUrl(id, logoUrl)
            }
        }
    }

    // ---- Icon themes ---------------------------------------------------------------

    /** Re-scans installed icon packs; called at startup and when Settings opens. */
    fun refreshIconPacks() {
        val initialSelected = _selectedIconPack.value
        if (initialSelected != null) {
            loadIconPack(initialSelected)
        } else {
            loadDefaultThemeIcons()
        }
        viewModelScope.launch {
            val packs = runCatching { IconPackManager.installedIconPacks(getApplication()) }
                .getOrDefault(emptyList())
            _installedIconPacks.value = packs
            val selected = _selectedIconPack.value
            if (selected != null && packs.none { it.packageName == selected }) {
                // The selected pack was uninstalled: fall back to the device icons.
                setIconPack(null)
            }
        }
    }

    fun setIconPack(packageName: String?) {
        val previousPack = _selectedIconPack.value
        val resolved = packageName?.takeIf { it.isNotBlank() }
        iconPackStore.selectedIconPack = resolved
        _selectedIconPack.value = resolved
        _selectedPackOverrides.value = resolved?.let { iconPackStore.getOverridesForPack(it) }.orEmpty()
        _selectedCustomUrlOverrides.value = iconPackStore.getCustomUrlOverrides(resolved)
        if (resolved == null) {
            _iconPackIcons.value = emptyList()
        }
        _themedAppIcons.value = iconPackStore.loadCachedIconMap(resolved)

        viewModelScope.launch {
            iconThemeMutex.withLock {
                if (previousPack == null && resolved != null) {
                    snapshotDefaultThemeCustomIconsLocked()
                }
                if (_selectedIconPack.value != resolved) return@withLock
                if (resolved == null) {
                    applyAndLoadDefaultThemeLocked()
                } else {
                    applyAndLoadIconPackLocked(resolved)
                }
            }
        }
    }

    private fun loadDefaultThemeIcons() {
        viewModelScope.launch {
            iconThemeMutex.withLock {
                if (_selectedIconPack.value != null) return@withLock
                applyAndLoadDefaultThemeLocked()
            }
        }
    }

    private fun loadIconPack(packPackage: String) {
        viewModelScope.launch {
            iconThemeMutex.withLock {
                if (_selectedIconPack.value != packPackage) return@withLock
                applyAndLoadIconPackLocked(packPackage)
            }
        }
    }

    private suspend fun snapshotDefaultThemeCustomIconsLocked() {
        if (iconPackStore.hasMigratedDefaultThemeIcons) return
        val nonGames = runCatching {
            repository.getAllItemsSnapshot().filter { !it.isGame && !it.isDeleted }
        }.getOrDefault(emptyList())
        val existing = iconPackStore.getCustomUrlOverrides(null)
        for (item in nonGames) {
            val iconUrl = item.iconUrl
            if (existing[item.id] == null && hasCustomAppIconUrl(iconUrl)) {
                iconPackStore.setCustomUrlOverride(null, item.id, iconUrl!!)
            }
        }
        iconPackStore.hasMigratedDefaultThemeIcons = true
    }

    private suspend fun baseIconUrlFor(item: GameItem): String? = when {
        item.type == GameType.INSTALLED_APP -> item.packageName?.let {
            installedAppsManager.getDefaultIconUrl(it)
        }
        else -> iconPackStore.getBaseIconUrl(item.id)
    }

    /**
     * Applies the Default theme across all non-game items: apps with a remembered custom
     * icon in the Default theme receive their custom icon, while all other apps receive
     * their stock device/shortcut icon. Custom icon pack memories remain untouched.
     */
    private suspend fun applyAndLoadDefaultThemeLocked() {
        val dbNonGames = runCatching {
            repository.getAllItemsSnapshot().filter { !it.isGame && !it.isDeleted }
        }.getOrDefault(emptyList())

        // Capture any pre-existing custom Room icons into the Default theme's memory once.
        if (!iconPackStore.hasMigratedDefaultThemeIcons) {
            val storedBeforeBackfill = iconPackStore.getCustomUrlOverrides(null)
            for (item in dbNonGames) {
                val iconUrl = item.iconUrl
                if (storedBeforeBackfill[item.id] == null && hasCustomAppIconUrl(iconUrl)) {
                    iconPackStore.setCustomUrlOverride(null, item.id, iconUrl!!)
                }
            }
            iconPackStore.hasMigratedDefaultThemeIcons = true
        }

        val defaultCustomUrls = iconPackStore.getCustomUrlOverrides(null)
        val resolvedMap = LinkedHashMap<String, String>()
        val itemsToUpdate = mutableListOf<GameItem>()
        for (item in dbNonGames) {
            val rememberedUrl = defaultCustomUrls[item.id]
            val targetUrl = rememberedUrl ?: baseIconUrlFor(item)
            val shouldUpdateRoom = when {
                targetUrl != null -> targetUrl != item.iconUrl
                item.type != GameType.INSTALLED_APP && item.iconUrl != null -> true
                else -> false
            }
            if (shouldUpdateRoom) {
                itemsToUpdate += item.copy(iconUrl = targetUrl)
                _editingItem.value?.takeIf { it.id == item.id }?.let { current ->
                    _editingItem.value = current.copy(iconUrl = targetUrl)
                }
            }
            if (rememberedUrl != null) {
                resolvedMap[item.id] = rememberedUrl
                if (item.type == GameType.INSTALLED_APP && !item.packageName.isNullOrBlank()) {
                    resolvedMap[item.packageName] = rememberedUrl
                }
            }
        }
        if (itemsToUpdate.isNotEmpty()) {
            repository.updateItemsBatch(itemsToUpdate)
        }

        if (_selectedIconPack.value == null) {
            _selectedPackOverrides.value = emptyMap()
            _selectedCustomUrlOverrides.value = defaultCustomUrls
            _themedAppIcons.value = resolvedMap
            _iconPackIcons.value = emptyList()
            iconPackStore.saveCachedIconMap(null, resolvedMap)
        }
    }

    /**
     * Resolves themed icons (including remembered per-app pack drawable overrides, pack
     * custom photo/URL overrides, and hidden apps) and loads the pack's picker catalog.
     */
    private suspend fun applyAndLoadIconPackLocked(packPackage: String) {
        iconPackStore.hasMigratedDefaultThemeIcons = true
        val context = getApplication<Application>()
        val installed = runCatching { installedAppsManager.getInstalledApps() }
            .getOrDefault(emptyList())
        val dbNonGames = runCatching {
            repository.getAllItemsSnapshot().filter { !it.isGame && !it.isDeleted }
        }.getOrDefault(emptyList())

        val packUrlOverrides = iconPackStore.getCustomUrlOverrides(packPackage)
        val itemsToUpdate = mutableListOf<GameItem>()
        for (item in dbNonGames) {
            iconPackStore.saveBaseIconUrlIfUnset(item.id, item.iconUrl)
            val targetUrl = packUrlOverrides[item.id] ?: baseIconUrlFor(item)
            val shouldUpdateRoom = when {
                targetUrl != null -> targetUrl != item.iconUrl
                item.type != GameType.INSTALLED_APP && item.iconUrl != null -> true
                else -> false
            }
            if (shouldUpdateRoom) {
                itemsToUpdate += item.copy(iconUrl = targetUrl)
                _editingItem.value?.takeIf { it.id == item.id }?.let { current ->
                    _editingItem.value = current.copy(iconUrl = targetUrl)
                }
            }
        }
        if (itemsToUpdate.isNotEmpty()) {
            repository.updateItemsBatch(itemsToUpdate)
        }

        val mergedById = LinkedHashMap<String, GameItem>()
        installed.forEach { mergedById[it.id] = it }
        dbNonGames.forEach { mergedById[it.id] = it }

        val drawableOverrides = iconPackStore.getOverridesForPack(packPackage)
        val resolvedMap = runCatching {
            IconPackManager.resolveIconsForPack(
                context = context,
                packPackage = packPackage,
                items = mergedById.values,
                customOverrides = drawableOverrides
            )
        }.getOrDefault(emptyMap()).toMutableMap()

        for ((itemId, url) in packUrlOverrides) {
            resolvedMap[itemId] = url
            val item = mergedById[itemId]
            if (item != null && item.type == GameType.INSTALLED_APP && !item.packageName.isNullOrBlank()) {
                resolvedMap[item.packageName] = url
            }
        }

        val packCatalog = runCatching {
            IconPackManager.icons(context, packPackage)
        }.getOrDefault(emptyList())

        if (_selectedIconPack.value == packPackage) {
            _selectedPackOverrides.value = drawableOverrides
            _selectedCustomUrlOverrides.value = packUrlOverrides
            _themedAppIcons.value = resolvedMap
            _iconPackIcons.value = packCatalog
            iconPackStore.saveCachedIconMap(packPackage, resolvedMap)
        }
    }

    /**
     * Applies one drawable from the active theme to this app and remembers the drawable ID
     * for the active icon pack so re-selecting this pack later re-applies it.
     */
    fun applyItemIconFromPack(id: String, newTitle: String, drawableName: String) {
        val pack = _selectedIconPack.value ?: return
        applyItemTitle(id, newTitle)
        viewModelScope.launch {
            iconThemeMutex.withLock {
                val context = getApplication<Application>()
                val item = _editingItem.value?.takeIf { it.id == id }
                    ?: repository.getGameById(id)
                    ?: return@withLock
                iconPackStore.saveBaseIconUrlIfUnset(id, item.iconUrl)
                val url = runCatching {
                    IconPackManager.renderPackChoice(context, pack, drawableName, id)
                }.getOrNull() ?: return@withLock

                iconPackStore.setOverride(pack, id, drawableName)

                val cleanedIconUrl = baseIconUrlFor(item)
                val shouldUpdateRoom = when {
                    cleanedIconUrl != null -> cleanedIconUrl != item.iconUrl
                    item.type != GameType.INSTALLED_APP && item.iconUrl != null -> true
                    else -> false
                }
                if (shouldUpdateRoom) {
                    repository.updateIconUrl(id, cleanedIconUrl)
                    _editingItem.value?.takeIf { it.id == id }?.let { current ->
                        _editingItem.value = current.copy(iconUrl = cleanedIconUrl)
                    }
                }

                if (_selectedIconPack.value == pack) {
                    _selectedPackOverrides.value = iconPackStore.getOverridesForPack(pack)
                    _selectedCustomUrlOverrides.value = iconPackStore.getCustomUrlOverrides(pack)
                    val updatedMap = _themedAppIcons.value.toMutableMap()
                    updatedMap[id] = url
                    if (item.type == GameType.INSTALLED_APP && !item.packageName.isNullOrBlank()) {
                        updatedMap[item.packageName] = url
                    }
                    _themedAppIcons.value = updatedMap
                    iconPackStore.saveCachedIconMap(pack, updatedMap)
                }
            }
        }
    }

    /**
     * Resets a single app's custom icon for the active theme (Default theme or custom icon
     * pack) without disturbing remembered customizations in other themes.
     */
    fun resetItemIcon(id: String) {
        val activePack = _selectedIconPack.value
        viewModelScope.launch {
            iconThemeMutex.withLock {
                val item = _editingItem.value?.takeIf { it.id == id }
                    ?: repository.getGameById(id)
                    ?: return@withLock

                if (activePack != null) {
                    iconPackStore.removeOverride(activePack, id)
                }
                iconPackStore.removeCustomUrlOverride(activePack, id)

                val defaultIconUrl = baseIconUrlFor(item)
                val shouldUpdateRoom = when {
                    defaultIconUrl != null -> defaultIconUrl != item.iconUrl
                    item.type != GameType.INSTALLED_APP && item.iconUrl != null -> true
                    else -> false
                }
                if (shouldUpdateRoom) {
                    repository.updateIconUrl(id, defaultIconUrl)
                    _editingItem.value?.takeIf { it.id == id }?.let { current ->
                        _editingItem.value = current.copy(iconUrl = defaultIconUrl)
                    }
                }

                if (_selectedIconPack.value == activePack) {
                    if (activePack != null) {
                        applyAndLoadIconPackLocked(activePack)
                    } else {
                        applyAndLoadDefaultThemeLocked()
                    }
                }
            }
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

    fun addLunaGameToGames(lunaGame: GameItem) {
        viewModelScope.launch {
            val resolvedPoster = lunaGame.posterUrl
                ?: AmazonLunaService.resolvePosterForTitle(
                    lunaGame.title,
                    allowFallbackSearch = true
                )
            val slugKey = lunaGame.id.removePrefix("luna_")
                .ifBlank { System.currentTimeMillis().toString() }
            val launchIntent = lunaGame.launchUrl
                ?.takeIf { it.isNotBlank() }
                ?: AmazonLunaService.createLunaLaunchIntentUri()
            val resolvedArtworkSource = when {
                resolvedPoster.isNullOrBlank() -> "LUNA"
                resolvedPoster.contains("steamgriddb", ignoreCase = true) -> "STEAMGRIDDB"
                lunaGame.posterUrl != null -> lunaGame.customArtworkSource ?: "LUNA"
                else -> "STEAMGRIDDB"
            }
            val userGame = lunaGame.copy(
                id = "user_luna_$slugKey",
                type = GameType.AMAZON_LUNA,
                isGame = true,
                launchUrl = launchIntent,
                posterUrl = resolvedPoster,
                customArtworkSource = resolvedArtworkSource
            )
            repository.insertUserGame(userGame)
            closeAddModal()
            _currentTab.value = MainLibraryTab.GAMES
        }
    }

    // Settings Modal
    fun openSettingsModal() {
        refreshLibrarySyncState()
        refreshWeatherApps()
        refreshIconPacks()
        _showSettingsModal.value = true
    }

    fun closeSettingsModal() {
        _showSettingsModal.value = false
    }

    fun setFavoritesLayout(layout: FavoritesLayout) {
        prefs.edit().putString("favorites_layout", layout.name).apply()
        _favoritesLayout.value = layout
    }

    fun setAppsBackgroundOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        prefs.edit().putFloat("apps_bg_opacity", clamped).apply()
        _appsBackgroundOpacity.value = clamped
    }

    fun setTvWallpaper(uri: Uri) {
        viewModelScope.launch {
            val storedUrl = withContext(Dispatchers.IO) {
                copyTvWallpaperImage(getApplication(), uri)
            } ?: return@launch
            prefs.edit().putString(KEY_TV_WALLPAPER_URL, storedUrl).apply()
            _tvWallpaperUrl.value = storedUrl
        }
    }

    fun clearTvWallpaper() {
        prefs.edit().remove(KEY_TV_WALLPAPER_URL).apply()
        _tvWallpaperUrl.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                File(getApplication<Application>().filesDir, TV_WALLPAPER_DIR)
                    .listFiles()
                    ?.forEach { it.delete() }
            }
        }
    }

    fun setAppGridColumns(columns: Int) {
        val normalized = columns.coerceIn(0, MAX_APP_GRID_COLUMNS)
        prefs.edit().putInt(KEY_APP_GRID_COLUMNS, normalized).apply()
        _appGridColumns.value = normalized
    }

    fun setThemeStyle(style: ThemeStyle) {
        prefs.edit().putString("theme_style", style.name).apply()
        _themeStyle.value = style
    }

    fun setWeatherEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("weather_enabled", enabled).apply()
        _weatherEnabled.value = enabled
        if (enabled) refreshWeather() else {
            _weatherInfo.value = null
            _weatherError.value = null
        }
    }

    fun setWeatherMode(mode: WeatherLocationMode) {
        prefs.edit().putString("weather_mode", mode.name).apply()
        _weatherMode.value = mode
        refreshWeather()
    }

    fun setWeatherZip(zip: String) {
        val trimmed = zip.trim()
        prefs.edit().putString("weather_zip", trimmed).apply()
        _weatherZip.value = trimmed
        if (_weatherMode.value == WeatherLocationMode.ZIP_CODE) refreshWeather()
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString("temperature_unit", unit.name).apply()
        _temperatureUnit.value = unit
    }

    /** Refresh the installed weather apps shown in Settings and used by the shortcut. */
    fun refreshWeatherApps() {
        viewModelScope.launch {
            _weatherApps.value = runCatching { installedAppsManager.getWeatherApps() }
                .getOrDefault(emptyList())
        }
    }

    fun setWeatherAppPackage(packageName: String) {
        prefs.edit().putString("weather_app_package", packageName).apply()
        _weatherAppPackage.value = packageName
        _weatherAppChooser.value = null
    }

    fun dismissWeatherAppChooser() {
        _weatherAppChooser.value = null
    }

    /**
     * Opens the weather app chosen in Settings. When more than one weather app is
     * installed and the user has not chosen one yet, the UI shows a picker.
     */
    fun openWeatherApp() {
        viewModelScope.launch {
            val apps = runCatching { installedAppsManager.getWeatherApps() }
                .getOrDefault(emptyList())
            _weatherApps.value = apps
            val selectedPackage = _weatherAppPackage.value
            val selectedApp = apps.firstOrNull { it.packageName == selectedPackage }
            when {
                selectedApp != null -> installedAppsManager.launchApp(selectedApp.packageName)
                apps.size == 1 -> installedAppsManager.launchApp(apps.first().packageName)
                apps.size > 1 -> _weatherAppChooser.value = apps
            }
        }
    }

    fun chooseWeatherApp(packageName: String) {
        setWeatherAppPackage(packageName)
        installedAppsManager.launchApp(packageName)
    }

    /** Fetch current conditions for the favorites date row. */
    fun refreshWeather() {
        if (!_weatherEnabled.value) return
        viewModelScope.launch {
            _weatherError.value = null
            weatherService.current(_weatherMode.value, _weatherZip.value)
                .onSuccess { _weatherInfo.value = it }
                .onFailure {
                    _weatherInfo.value = null
                    _weatherError.value = it.message ?: "Weather unavailable"
                }
        }
    }

    // ---- Library sync (Google Drive API or Local Folder) ---------------------------

    /** Uses a local/SAF folder as the exclusive sync target without losing existing library data. */
    fun chooseSyncFolder(uri: Uri) {
        isInteractiveDriveAuthActive = false
        runLibrarySync("Switching to folder sync…") {
            librarySyncService.configureFolder(uri).also {
                _librarySyncNeedsReauth.value = false
            }
        }
    }

    /** Starts Google Drive (`drive.file`) authorization & configures it as the exclusive sync target. */
    fun connectGoogleDrive() {
        if (_librarySyncBusy.value) return
        driveDevicePollJob?.cancel()
        driveDevicePollJob = null
        _pendingDriveDeviceSession.value = null
        viewModelScope.launch {
            _librarySyncBusy.value = true
            isInteractiveDriveAuthActive = true
            _librarySyncStatus.value = "Connecting to Google Drive…"
            try {
                when (val step = librarySyncService.beginGoogleDriveAuthorization()) {
                    is DriveAuthorizationStep.NeedsUserConsent -> {
                        _pendingDriveAuthIntent.value = step.pendingIntent
                        _librarySyncStatus.value = "Approve Google Drive access to continue"
                        _librarySyncBusy.value = false
                    }
                    is DriveAuthorizationStep.NeedsIntentResolution -> {
                        activeDriveFallbackContext = step.accountEmail to step.isAccountPicker
                        _pendingDriveFallbackIntent.value = PendingDriveFallbackIntent(
                            intent = step.intent,
                            accountEmail = step.accountEmail,
                            isAccountPicker = step.isAccountPicker
                        )
                        _librarySyncStatus.value = if (step.isAccountPicker) {
                            "Select your Google account for Drive sync"
                        } else {
                            "Approve Google Drive access to continue"
                        }
                        _librarySyncBusy.value = false
                    }
                    is DriveAuthorizationStep.NeedsDeviceCodeVerification -> {
                        _pendingDriveDeviceSession.value = step.session
                        _librarySyncStatus.value =
                            "Enter ${step.session.userCode} at ${step.session.verificationUrl}"
                        _librarySyncBusy.value = false
                        startDriveDevicePolling(step.session)
                    }
                    is DriveAuthorizationStep.Authorized -> {
                        isInteractiveDriveAuthActive = false
                        finishGoogleDriveConfiguration(step.account)
                    }
                }
            } catch (error: DriveOAuthConfigurationException) {
                isInteractiveDriveAuthActive = false
                if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE) {
                    _librarySyncNeedsReauth.value = true
                }
                _librarySyncStatus.value = error.message ?: "Google Drive OAuth configuration error"
                _librarySyncBusy.value = false
            } catch (_: kotlinx.coroutines.CancellationException) {
                isInteractiveDriveAuthActive = false
                _librarySyncStatus.value = "Google Drive sign-in cancelled"
                _librarySyncBusy.value = false
            } catch (error: Throwable) {
                isInteractiveDriveAuthActive = false
                _librarySyncStatus.value = error.message ?: "Google Drive sign-in failed"
                _librarySyncBusy.value = false
            }
        }
    }

    private fun startDriveDevicePolling(session: DriveDeviceCodeSession) {
        driveDevicePollJob?.cancel()
        driveDevicePollJob = viewModelScope.launch {
            var intervalMs = session.intervalSeconds.coerceAtLeast(5) * 1000L
            val deadlineMs = SystemClock.elapsedRealtime() + (session.expiresInSeconds * 1000L)
            try {
                while (SystemClock.elapsedRealtime() < deadlineMs) {
                    delay(intervalMs)
                    when (val poll = librarySyncService.pollGoogleDriveDeviceAuthorization(session.deviceCode)) {
                        is DriveDevicePollResult.Pending -> Unit
                        is DriveDevicePollResult.SlowDown -> intervalMs += 5_000L
                        is DriveDevicePollResult.Authorized -> {
                            _pendingDriveDeviceSession.value = null
                            isInteractiveDriveAuthActive = false
                            _librarySyncBusy.value = true
                            _librarySyncStatus.value = "Syncing with Google Drive…"
                            finishGoogleDriveConfiguration(poll.account)
                            return@launch
                        }
                        is DriveDevicePollResult.Denied -> {
                            _pendingDriveDeviceSession.value = null
                            isInteractiveDriveAuthActive = false
                            _librarySyncStatus.value = "Google Drive sign-in cancelled"
                            _librarySyncBusy.value = false
                            return@launch
                        }
                        is DriveDevicePollResult.Expired -> {
                            _pendingDriveDeviceSession.value = null
                            isInteractiveDriveAuthActive = false
                            _librarySyncStatus.value = "Google Drive sign-in code expired. Try again."
                            _librarySyncBusy.value = false
                            return@launch
                        }
                    }
                }
                _pendingDriveDeviceSession.value = null
                isInteractiveDriveAuthActive = false
                _librarySyncStatus.value = "Google Drive sign-in code expired. Try again."
                _librarySyncBusy.value = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _pendingDriveDeviceSession.value = null
                isInteractiveDriveAuthActive = false
                if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE) {
                    _librarySyncNeedsReauth.value = true
                }
                _librarySyncStatus.value = error.message ?: "Google Drive sign-in failed"
                _librarySyncBusy.value = false
            }
        }
    }

    fun cancelGoogleDriveDeviceAuthorization() {
        driveDevicePollJob?.cancel()
        driveDevicePollJob = null
        _pendingDriveDeviceSession.value = null
        isInteractiveDriveAuthActive = false
        _librarySyncStatus.value = "Google Drive sign-in cancelled"
        _librarySyncBusy.value = false
    }

    fun markDriveAuthLaunched() {
        lastDriveAuthLaunchElapsedMs = SystemClock.elapsedRealtime()
        isInteractiveDriveAuthActive = true
    }

    fun consumePendingDriveAuthIntent() {
        _pendingDriveAuthIntent.value = null
    }

    fun consumePendingDriveFallbackIntent() {
        _pendingDriveFallbackIntent.value = null
    }

    fun onGoogleDriveAuthorizationResult(resultCode: Int, data: Intent?) {
        val elapsedMs = if (lastDriveAuthLaunchElapsedMs > 0L) {
            SystemClock.elapsedRealtime() - lastDriveAuthLaunchElapsedMs
        } else {
            Long.MAX_VALUE
        }
        lastDriveAuthLaunchElapsedMs = 0L

        if (resultCode != Activity.RESULT_OK || data == null) {
            val inspectedError = librarySyncService.inspectFailedGoogleDriveIntent(data)
            val shouldTryFallback = elapsedMs < QUICK_AUTH_ABORT_THRESHOLD_MS ||
                (inspectedError != null && inspectedError !is kotlinx.coroutines.CancellationException)
            if (!shouldTryFallback) {
                isInteractiveDriveAuthActive = false
                _librarySyncStatus.value = "Google Drive sign-in cancelled"
                _librarySyncBusy.value = false
                return
            }

            viewModelScope.launch {
                _librarySyncBusy.value = true
                _librarySyncStatus.value = "Opening Google account sign-in…"
                try {
                    when (val step = librarySyncService.beginFallbackGoogleDriveAuthorization()) {
                        is DriveAuthorizationStep.NeedsIntentResolution -> {
                            activeDriveFallbackContext = step.accountEmail to step.isAccountPicker
                            _pendingDriveFallbackIntent.value = PendingDriveFallbackIntent(
                                intent = step.intent,
                                accountEmail = step.accountEmail,
                                isAccountPicker = step.isAccountPicker
                            )
                            _librarySyncStatus.value = if (step.isAccountPicker) {
                                "Select your Google account for Drive sync"
                            } else {
                                "Approve Google Drive access to continue"
                            }
                            _librarySyncBusy.value = false
                        }
                        is DriveAuthorizationStep.NeedsUserConsent -> {
                            _pendingDriveAuthIntent.value = step.pendingIntent
                            _librarySyncStatus.value = "Approve Google Drive access to continue"
                            _librarySyncBusy.value = false
                        }
                        is DriveAuthorizationStep.NeedsDeviceCodeVerification -> {
                            _pendingDriveDeviceSession.value = step.session
                            _librarySyncStatus.value =
                                "Enter ${step.session.userCode} at ${step.session.verificationUrl}"
                            _librarySyncBusy.value = false
                            startDriveDevicePolling(step.session)
                        }
                        is DriveAuthorizationStep.Authorized -> {
                            isInteractiveDriveAuthActive = false
                            finishGoogleDriveConfiguration(step.account)
                        }
                        null -> {
                            isInteractiveDriveAuthActive = false
                            if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE) {
                                _librarySyncNeedsReauth.value = true
                            }
                            _librarySyncStatus.value = (inspectedError as? DriveOAuthConfigurationException)?.message
                                ?: inspectedError?.message
                                ?: librarySyncService.driveDiagnosticConfigurationMessage(
                                    "Google Drive sign-in exited immediately before showing consent."
                                )
                            _librarySyncBusy.value = false
                        }
                    }
                } catch (error: Throwable) {
                    isInteractiveDriveAuthActive = false
                    if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE) {
                        _librarySyncNeedsReauth.value = true
                    }
                    _librarySyncStatus.value = when (error) {
                        is DriveOAuthConfigurationException -> error.message
                        is kotlinx.coroutines.CancellationException ->
                            (inspectedError as? DriveOAuthConfigurationException)?.message
                                ?: librarySyncService.driveDiagnosticConfigurationMessage(
                                    "Google Drive sign-in was aborted by Google Play Services."
                                )
                        else -> error.message ?: "Google Drive sign-in failed"
                    }
                    refreshLibrarySyncState()
                    _librarySyncBusy.value = false
                }
            }
            return
        }

        viewModelScope.launch {
            _librarySyncBusy.value = true
            _librarySyncStatus.value = "Syncing with Google Drive…"
            try {
                val authorized = librarySyncService.resolveGoogleDriveAuthorization(data)
                isInteractiveDriveAuthActive = false
                finishGoogleDriveConfiguration(authorized.account)
            } catch (_: kotlinx.coroutines.CancellationException) {
                isInteractiveDriveAuthActive = false
                _librarySyncStatus.value = "Google Drive sign-in cancelled"
                _librarySyncBusy.value = false
            } catch (error: Throwable) {
                isInteractiveDriveAuthActive = false
                if (error is DriveAuthorizationRequiredException ||
                    (error is DriveOAuthConfigurationException &&
                        librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE)
                ) {
                    _librarySyncNeedsReauth.value = true
                }
                _librarySyncStatus.value = error.message ?: "Google Drive sync failed"
                refreshLibrarySyncState()
                _librarySyncBusy.value = false
            }
        }
    }

    fun onGoogleDriveFallbackResult(
        resultCode: Int,
        data: Intent?,
        accountEmail: String? = null,
        isAccountPicker: Boolean = false
    ) {
        val elapsedMs = if (lastDriveAuthLaunchElapsedMs > 0L) {
            SystemClock.elapsedRealtime() - lastDriveAuthLaunchElapsedMs
        } else {
            Long.MAX_VALUE
        }
        lastDriveAuthLaunchElapsedMs = 0L

        val resolvedEmail = accountEmail ?: activeDriveFallbackContext?.first
        val resolvedIsPicker = if (accountEmail != null) {
            isAccountPicker
        } else {
            isAccountPicker || (activeDriveFallbackContext?.second == true)
        }
        activeDriveFallbackContext = null

        if (resultCode != Activity.RESULT_OK) {
            isInteractiveDriveAuthActive = false
            if (elapsedMs < QUICK_AUTH_ABORT_THRESHOLD_MS) {
                if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE) {
                    _librarySyncNeedsReauth.value = true
                }
                _librarySyncStatus.value = librarySyncService.driveDiagnosticConfigurationMessage(
                    "Google Drive consent exited immediately (resultCode=$resultCode)."
                )
            } else {
                _librarySyncStatus.value = "Google Drive sign-in cancelled"
            }
            _librarySyncBusy.value = false
            return
        }

        viewModelScope.launch {
            _librarySyncBusy.value = true
            _librarySyncStatus.value = "Authorizing Google Drive…"
            try {
                when (
                    val step = librarySyncService.resolveFallbackGoogleDriveAuthorization(
                        resultCode = resultCode,
                        data = data,
                        accountEmail = resolvedEmail,
                        isAccountPicker = resolvedIsPicker
                    )
                ) {
                    is DriveAuthorizationStep.NeedsIntentResolution -> {
                        if (!resolvedIsPicker && !step.isAccountPicker) {
                            isInteractiveDriveAuthActive = false
                            _librarySyncStatus.value =
                                "Google Drive consent was not completed. Please try again."
                            _librarySyncBusy.value = false
                        } else {
                            activeDriveFallbackContext = step.accountEmail to step.isAccountPicker
                            _pendingDriveFallbackIntent.value = PendingDriveFallbackIntent(
                                intent = step.intent,
                                accountEmail = step.accountEmail,
                                isAccountPicker = step.isAccountPicker
                            )
                            _librarySyncStatus.value = "Approve Google Drive access to continue"
                            _librarySyncBusy.value = false
                        }
                    }
                    is DriveAuthorizationStep.NeedsUserConsent -> {
                        _pendingDriveAuthIntent.value = step.pendingIntent
                        _librarySyncStatus.value = "Approve Google Drive access to continue"
                        _librarySyncBusy.value = false
                    }
                    is DriveAuthorizationStep.NeedsDeviceCodeVerification -> {
                        _pendingDriveDeviceSession.value = step.session
                        _librarySyncStatus.value =
                            "Enter ${step.session.userCode} at ${step.session.verificationUrl}"
                        _librarySyncBusy.value = false
                        startDriveDevicePolling(step.session)
                    }
                    is DriveAuthorizationStep.Authorized -> {
                        isInteractiveDriveAuthActive = false
                        finishGoogleDriveConfiguration(step.account)
                    }
                    null -> {
                        isInteractiveDriveAuthActive = false
                        _librarySyncStatus.value = "Could not read selected Google account."
                        _librarySyncBusy.value = false
                    }
                }
            } catch (error: Throwable) {
                isInteractiveDriveAuthActive = false
                if (error is DriveAuthorizationRequiredException ||
                    (error is DriveOAuthConfigurationException &&
                        librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE)
                ) {
                    _librarySyncNeedsReauth.value = true
                }
                _librarySyncStatus.value = error.message ?: "Google Drive sync failed"
                refreshLibrarySyncState()
                _librarySyncBusy.value = false
            }
        }
    }

    private suspend fun finishGoogleDriveConfiguration(account: DriveAuthAccount?) {
        runCatching {
            if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE && _librarySyncNeedsReauth.value) {
                librarySyncService.syncNow()
            } else {
                librarySyncService.configureGoogleDrive(account)
            }
        }.onSuccess { outcome ->
            _librarySyncNeedsReauth.value = false
            _librarySyncStatus.value = formatSyncOutcomeStatus(outcome, LibrarySyncMode.GOOGLE_DRIVE)
        }.onFailure { error ->
            if (error is DriveAuthorizationRequiredException) {
                _librarySyncNeedsReauth.value = true
                error.pendingIntent?.let { _pendingDriveAuthIntent.value = it }
                if (error.pendingIntent == null) {
                    error.recoveryIntent?.let { recovery ->
                        val email = account?.email ?: librarySyncService.driveAccountEmail()
                        activeDriveFallbackContext = email to false
                        _pendingDriveFallbackIntent.value = PendingDriveFallbackIntent(
                            intent = recovery,
                            accountEmail = email,
                            isAccountPicker = false
                        )
                    }
                }
            }
            _librarySyncStatus.value = error.message ?: "Google Drive sync failed"
        }
        refreshLibrarySyncState()
        _librarySyncBusy.value = false
    }

    fun syncLibraryNow() {
        if (!librarySyncService.isConfigured) {
            _librarySyncStatus.value = "Choose Google Drive or a sync folder first"
            return
        }
        if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE && _librarySyncNeedsReauth.value) {
            connectGoogleDrive()
            return
        }
        runLibrarySync(
            if (librarySyncService.syncMode == LibrarySyncMode.GOOGLE_DRIVE) {
                "Syncing with Google Drive…"
            } else {
                "Syncing library folder…"
            }
        ) {
            librarySyncService.syncNow()
        }
    }

    fun disconnectLibrarySync() {
        driveDevicePollJob?.cancel()
        driveDevicePollJob = null
        _pendingDriveDeviceSession.value = null
        isInteractiveDriveAuthActive = false
        activeDriveFallbackContext = null
        _pendingDriveAuthIntent.value = null
        _pendingDriveFallbackIntent.value = null
        viewModelScope.launch {
            librarySyncService.disconnect()
            _librarySyncNeedsReauth.value = false
            refreshLibrarySyncState()
            _librarySyncStatus.value = "Library sync disconnected (local library kept)"
        }
    }

    /** Reads the active sync target when the device is unlocked. */
    fun onDeviceUnlocked() {
        if (!librarySyncService.isConfigured || _librarySyncBusy.value || isInteractiveDriveAuthActive) return
        lastResumeLibrarySyncMs = SystemClock.elapsedRealtime()
        syncLibraryInBackground()
    }

    /** Pulls remote library and poster updates when returning to the launcher (throttled). */
    fun syncLibraryOnResume() {
        if (!librarySyncService.isConfigured || _librarySyncBusy.value || isInteractiveDriveAuthActive) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastResumeLibrarySyncMs < RESUME_SYNC_INTERVAL_MS) return
        lastResumeLibrarySyncMs = now
        syncLibraryInBackground()
    }

    private fun syncLibraryInBackground() {
        viewModelScope.launch {
            runCatching { librarySyncService.syncNow() }
                .onSuccess { outcome ->
                    _librarySyncNeedsReauth.value = false
                    if (outcome is LibrarySyncOutcome.Applied || outcome is LibrarySyncOutcome.Merged) {
                        _librarySyncStatus.value = formatSyncOutcomeStatus(
                            outcome,
                            librarySyncService.syncMode
                        )
                    }
                }
                .onFailure { error ->
                    if (error is DriveAuthorizationRequiredException) {
                        _librarySyncNeedsReauth.value = true
                    }
                    _librarySyncStatus.value = error.message ?: "Library sync failed"
                }
            refreshLibrarySyncState()
        }
    }

    private fun runLibrarySync(
        message: String,
        operation: suspend () -> LibrarySyncOutcome
    ) {
        if (_librarySyncBusy.value) return
        viewModelScope.launch {
            _librarySyncBusy.value = true
            _librarySyncStatus.value = message
            runCatching { operation() }
                .onSuccess { outcome ->
                    _librarySyncNeedsReauth.value = false
                    _librarySyncStatus.value = formatSyncOutcomeStatus(
                        outcome,
                        librarySyncService.syncMode
                    )
                }
                .onFailure { error ->
                    if (error is DriveAuthorizationRequiredException) {
                        _librarySyncNeedsReauth.value = true
                        error.pendingIntent?.let { _pendingDriveAuthIntent.value = it }
                        if (error.pendingIntent == null) {
                            error.recoveryIntent?.let { recovery ->
                                val email = librarySyncService.driveAccountEmail()
                                activeDriveFallbackContext = email to false
                                _pendingDriveFallbackIntent.value = PendingDriveFallbackIntent(
                                    intent = recovery,
                                    accountEmail = email,
                                    isAccountPicker = false
                                )
                            }
                        }
                    }
                    _librarySyncStatus.value = error.message ?: "Library sync failed"
                }
            refreshLibrarySyncState()
            _librarySyncBusy.value = false
        }
    }

    private fun formatSyncOutcomeStatus(
        outcome: LibrarySyncOutcome,
        mode: LibrarySyncMode
    ): String {
        val targetName = if (mode == LibrarySyncMode.GOOGLE_DRIVE) "Google Drive" else "sync folder"
        return when (outcome) {
            is LibrarySyncOutcome.Applied ->
                "Restored ${outcome.itemCount} games from $targetName"
            is LibrarySyncOutcome.WroteLocal ->
                "Saved ${outcome.itemCount} games to $targetName"
            is LibrarySyncOutcome.Merged ->
                if (outcome.addedFromRemote > 0) {
                    "Synced ${outcome.itemCount} games to $targetName (merged ${outcome.addedFromRemote} remote)"
                } else {
                    "Synced ${outcome.itemCount} existing games to $targetName"
                }
            LibrarySyncOutcome.UpToDate -> "Library is already up to date"
            LibrarySyncOutcome.NotConfigured -> "Choose Google Drive or a sync folder first"
        }
    }

    private fun refreshLibrarySyncState() {
        _librarySyncMode.value = librarySyncService.syncMode
        _librarySyncConfigured.value = librarySyncService.isConfigured
        _librarySyncTarget.value = librarySyncService.targetLabel()
        _librarySyncLastSyncedAt.value = librarySyncService.lastSyncedAt()
    }

    /**
     * Re-scan installed apps when the launcher regains focus (for example after the user
     * installs something from the Play Store). Debounced so quick resume cycles do not
     * repeat the package scan.
     */
    private var lastInstalledAppRefresh = 0L
    private var lastResumeLibrarySyncMs = 0L

    fun refreshInstalledApps() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInstalledAppRefresh < 1_500L) return
        lastInstalledAppRefresh = now
        viewModelScope.launch {
            repository.syncInstalledApps()
            repository.matchMissingGamePosters()
            val activePack = _selectedIconPack.value
            if (activePack != null) {
                loadIconPack(activePack)
            } else {
                loadDefaultThemeIcons()
            }
        }
    }

    /**
     * Pull-to-refresh action on the Games tab: re-scans installed apps, fills missing
     * game posters, and pulls/pushes the configured library sync target.
     */
    fun refreshGamesLibrary() {
        if (_gamesRefreshing.value) return
        viewModelScope.launch {
            _gamesRefreshing.value = true
            val startedAt = SystemClock.elapsedRealtime()
            try {
                repository.syncInstalledApps()
                repository.backfillGfnArtwork()
                repository.matchMissingGamePosters()
                if (librarySyncService.isConfigured) {
                    lastResumeLibrarySyncMs = SystemClock.elapsedRealtime()
                    val activeMode = librarySyncService.syncMode
                    runCatching { librarySyncService.syncNow() }
                        .onSuccess { outcome ->
                            _librarySyncNeedsReauth.value = false
                            if (outcome != LibrarySyncOutcome.NotConfigured) {
                                _librarySyncStatus.value = formatSyncOutcomeStatus(outcome, activeMode)
                            }
                            when (outcome) {
                                is LibrarySyncOutcome.Applied ->
                                    _gamesRefreshFeedback.value = "Synced ${outcome.itemCount} games"
                                is LibrarySyncOutcome.Merged ->
                                    _gamesRefreshFeedback.value = "Synced ${outcome.itemCount} games"
                                is LibrarySyncOutcome.WroteLocal ->
                                    _gamesRefreshFeedback.value = "Saved library updates"
                                LibrarySyncOutcome.UpToDate ->
                                    _gamesRefreshFeedback.value = "Library is up to date"
                                LibrarySyncOutcome.NotConfigured -> Unit
                            }
                        }
                        .onFailure { error ->
                            if (error is DriveAuthorizationRequiredException) {
                                _librarySyncNeedsReauth.value = true
                            }
                            val msg = error.message ?: "Library sync failed"
                            _librarySyncStatus.value = msg
                            _gamesRefreshFeedback.value = msg
                        }
                    refreshLibrarySyncState()
                } else {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    if (elapsed < 350L) {
                        delay(350L - elapsed)
                    }
                }
            } finally {
                _gamesRefreshing.value = false
            }
        }
    }

    fun clearGamesRefreshFeedback() {
        _gamesRefreshFeedback.value = null
    }

    fun saveSteamGridDbApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove("sgdb_api_key").apply()
        } else {
            prefs.edit().putString("sgdb_api_key", trimmed).apply()
        }
        _steamGridDbApiKey.value = trimmed
    }

    fun setSteamGridDbIncludeNsfw(include: Boolean) {
        prefs.edit().putBoolean("sgdb_include_nsfw", include).apply()
        _steamGridDbIncludeNsfw.value = include
    }

    fun openDefaultLauncherSettings() {
        installedAppsManager.openHomeSettings()
    }

    fun openAccessibilitySettings() {
        installedAppsManager.openAccessibilitySettings()
    }

    fun isDefaultLauncher(): Boolean = installedAppsManager.isDefaultLauncher()

    fun isHomeOverrideEnabled(): Boolean = installedAppsManager.isHomeOverrideEnabled()

    // Controller navigation and shortcuts. Panels and dialogs are never opened behind
    // an already-open sheet or dialog.
    fun onControllerAction(action: ControllerAction): Boolean {
        if (isAnyMenuOrModalOpen()) {
            return false
        }

        return when (action) {
            ControllerAction.PREV_TAB_LB -> {
                setTab(MainLibraryTab.APPS)
                true
            }
            ControllerAction.NEXT_TAB_RB -> {
                setTab(MainLibraryTab.GAMES)
                true
            }
            ControllerAction.OPEN_CONTEXT_MENU_X -> openContextMenuForFocusedItem()
            ControllerAction.OPEN_ADD_MODAL_BACK -> {
                openAddModal()
                true
            }
            ControllerAction.OPEN_SETTINGS_START -> {
                openSettingsModal()
                true
            }
        }
    }

    private fun copyTvWallpaperImage(context: Context, uri: Uri): String? =
        runCatching {
            val dir = File(context.filesDir, TV_WALLPAPER_DIR).apply { mkdirs() }
            val maxDimPx = 2560

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            val target = File(dir, "wallpaper-${System.currentTimeMillis()}.jpg")
            if (bounds.outWidth > maxDimPx || bounds.outHeight > maxDimPx) {
                var sampleSize = 1
                while (
                    (bounds.outWidth / (sampleSize * 2)) >= maxDimPx ||
                    (bounds.outHeight / (sampleSize * 2)) >= maxDimPx
                ) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val sampled = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, decodeOpts)
                }
                if (sampled != null) {
                    val longest = maxOf(sampled.width, sampled.height)
                    val finalBitmap = if (longest > maxDimPx) {
                        val scale = maxDimPx.toFloat() / longest.toFloat()
                        val w = (sampled.width * scale).toInt().coerceAtLeast(1)
                        val h = (sampled.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(sampled, w, h, true).also {
                            if (it !== sampled) sampled.recycle()
                        }
                    } else {
                        sampled
                    }
                    target.outputStream().use { out ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    finalBitmap.recycle()
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: return null
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
            }

            if (!target.exists() || target.length() == 0L) {
                target.delete()
                return null
            }
            dir.listFiles()?.forEach { old ->
                if (old.name != target.name) old.delete()
            }
            Uri.fromFile(target).toString()
        }.getOrNull()

    companion object {
        const val MAX_APP_GRID_COLUMNS = 8
        private const val DEFAULT_APPS_OPACITY = 0.6f
        private const val RESUME_SYNC_INTERVAL_MS = 15_000L
        private const val QUICK_AUTH_ABORT_THRESHOLD_MS = 1_500L
        private const val TV_WALLPAPER_DIR = "tv_wallpapers"

        /** One-time cleanup of the hardcoded library entries from early builds. */
        private const val KEY_LEGACY_CLEANUP_DONE = "legacy_prepopulated_cleanup_done"
        private const val KEY_STREAMING_CLIENTS_AS_APPS_DONE = "streaming_clients_as_apps_v1_done"
        private const val KEY_TV_WALLPAPER_URL = "tv_wallpaper_url"
        private const val KEY_APP_GRID_COLUMNS = "app_grid_columns"
    }
}
