package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.repository.GameRepository
import com.example.data.service.GeForceNowService
import com.example.data.service.InstalledAppsManager
import com.example.data.service.LibrarySyncOutcome
import com.example.data.service.LibrarySyncService
import com.example.data.service.MOONLIGHT_PACKAGES
import com.example.data.service.SteamGridDbService
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherApp
import com.example.data.service.WeatherInfo
import com.example.data.service.WeatherLocationMode
import com.example.data.service.WeatherService
import com.example.input.ControllerAction
import com.example.input.ControllerManager
import com.example.ui.theme.FavoritesLayout
import com.example.ui.theme.ThemeStyle
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

    private val weatherService = WeatherService(application)
    private val librarySyncService = LibrarySyncService(application, repository, prefs)

    // Opacity of the wallpaper scrim on the Apps screen. 0 = wallpaper only, 1 = solid.
    private val _appsBackgroundOpacity = MutableStateFlow(
        prefs.getFloat("apps_bg_opacity", DEFAULT_APPS_OPACITY).coerceIn(0f, 1f)
    )
    val appsBackgroundOpacity: StateFlow<Float> = _appsBackgroundOpacity.asStateFlow()

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

    private val _librarySyncTarget = MutableStateFlow(librarySyncService.targetLabel())
    val librarySyncTarget: StateFlow<String?> = _librarySyncTarget.asStateFlow()

    private val _librarySyncLastSyncedAt = MutableStateFlow(librarySyncService.lastSyncedAt())
    val librarySyncLastSyncedAt: StateFlow<Long?> = _librarySyncLastSyncedAt.asStateFlow()

    // All library items from Room
    val allItems: StateFlow<List<GameItem>> = repository.allGames
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Games Tab: poster grid (all cloud games, game apps, shortcuts)
    val gamesList: StateFlow<List<GameItem>> = allItems.map { list ->
        list.filter { it.isGame }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Apps Tab: all non-game applications, alphabetised for the app list
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
            val pendingLegacyCleanup = !prefs.getBoolean(KEY_LEGACY_CLEANUP_DONE, false)
            repository.initializeLibraryIfEmpty(purgeLegacyCatalog = pendingLegacyCleanup)
            if (pendingLegacyCleanup) {
                prefs.edit().putBoolean(KEY_LEGACY_CLEANUP_DONE, true).apply()
            }

            // Restore the library file before anything else can write over it.
            if (librarySyncService.isConfigured) {
                runCatching { librarySyncService.syncNow() }
                    .onSuccess { outcome ->
                        if (outcome is LibrarySyncOutcome.Applied) {
                            _librarySyncStatus.value =
                                "Restored ${outcome.itemCount} games from the sync file"
                        }
                    }
                    .onFailure {
                        _librarySyncStatus.value = it.message ?: "Library sync failed"
                    }
            }
            refreshLibrarySyncState()
            librarySyncService.observeLibraryChanges(viewModelScope) { message ->
                _librarySyncStatus.value = message
            }

            repository.backfillGfnArtwork()
            repository.matchMissingGamePosters()
        }
        if (_weatherEnabled.value) refreshWeather()
        refreshWeatherApps()
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
        _showFavoritesEditor.value

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
        if (!canDeleteLibraryItem(item)) return
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
            val updatedIcon = if (!current.isGame) sanitizedUrl else current.iconUrl
            _editingItem.value = current.copy(
                title = resolvedTitle,
                posterUrl = updatedPoster,
                iconUrl = updatedIcon,
                isCustomized = true
            )
            viewModelScope.launch {
                repository.updateTitleAndArtwork(id, resolvedTitle, updatedPoster, updatedIcon)
            }
        }
    }

    fun setItemNsfwLogoOverlay(id: String, newTitle: String, enabled: Boolean) {
        val current = _editingItem.value?.takeIf { it.id == id } ?: return
        val resolvedTitle = newTitle.trim().ifBlank { current.title }
        _editingItem.value = current.copy(
            title = resolvedTitle,
            overlayNsfwLogo = enabled,
            isCustomized = true
        )
        viewModelScope.launch {
            val logoUrl = if (enabled) {
                SteamGridDbService.findLogoForGame(resolvedTitle, _steamGridDbApiKey.value)
                    ?: current.nsfwLogoUrl
            } else {
                current.nsfwLogoUrl
            }
            val latest = _editingItem.value
            if (latest != null && latest.id == id) {
                _editingItem.value = latest.copy(nsfwLogoUrl = logoUrl)
            }
            repository.updateTitleAndNsfwOverlay(id, resolvedTitle, enabled, logoUrl)
        }
    }

    fun updateItemNsfwLogoUrl(id: String, logoUrl: String?) {
        val current = _editingItem.value?.takeIf { it.id == id }
        if (current != null && current.nsfwLogoUrl == logoUrl) return
        if (current != null) {
            _editingItem.value = current.copy(nsfwLogoUrl = logoUrl)
        }
        viewModelScope.launch {
            repository.updateNsfwLogoUrl(id, logoUrl)
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
        refreshLibrarySyncState()
        refreshWeatherApps()
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

    // ---- Library folder sync -------------------------------------------------------

    /** Uses a folder as the sync target; Nexus stores nexus-library.json and posters inside it. */
    fun chooseSyncFolder(uri: Uri) {
        runLibrarySync("Reading library folder…") { librarySyncService.configureFolder(uri) }
    }

    fun syncLibraryNow() {
        if (!librarySyncService.isConfigured) {
            _librarySyncStatus.value = "Choose a sync folder first"
            return
        }
        runLibrarySync("Syncing library folder…") { librarySyncService.syncNow() }
    }

    fun disconnectLibrarySync() {
        librarySyncService.disconnect()
        refreshLibrarySyncState()
        _librarySyncStatus.value = "Library sync disconnected"
    }

    /** Reads the sync folder when the device is unlocked. */
    fun onDeviceUnlocked() {
        if (!librarySyncService.isConfigured || _librarySyncBusy.value) return
        viewModelScope.launch {
            runCatching { librarySyncService.syncNow() }
                .onSuccess { outcome ->
                    if (outcome is LibrarySyncOutcome.Applied) {
                        _librarySyncStatus.value =
                            "Restored ${outcome.itemCount} games and posters from sync folder"
                    }
                }
                .onFailure {
                    _librarySyncStatus.value = it.message ?: "Library folder sync failed"
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
                    _librarySyncStatus.value = when (outcome) {
                        is LibrarySyncOutcome.Applied ->
                            "Restored ${outcome.itemCount} games from the sync file"
                        is LibrarySyncOutcome.WroteLocal ->
                            "Saved ${outcome.itemCount} games to the sync file"
                        LibrarySyncOutcome.UpToDate -> "Library is already up to date"
                        LibrarySyncOutcome.NotConfigured -> "Choose a sync file or folder first"
                    }
                }
                .onFailure {
                    _librarySyncStatus.value = it.message ?: "Library sync failed"
                }
            refreshLibrarySyncState()
            _librarySyncBusy.value = false
        }
    }

    private fun refreshLibrarySyncState() {
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

    fun refreshInstalledApps() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastInstalledAppRefresh < 1_500L) return
        lastInstalledAppRefresh = now
        viewModelScope.launch {
            repository.syncInstalledApps()
            repository.matchMissingGamePosters()
        }
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

    private companion object {
        const val DEFAULT_APPS_OPACITY = 0.6f

        /** One-time cleanup of the hardcoded library entries from early builds. */
        const val KEY_LEGACY_CLEANUP_DONE = "legacy_prepopulated_cleanup_done"
    }
}
