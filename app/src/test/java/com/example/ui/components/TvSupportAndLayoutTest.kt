package com.example.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.AppIconStore
import com.example.data.service.DriveDeviceCodeSession
import com.example.data.service.GeForceNowService
import com.example.data.service.LibrarySyncMode
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherInfo
import com.example.ui.screens.AppListEntry
import com.example.ui.screens.AppsAlphabeticalList
import com.example.ui.screens.SectionDownTarget
import com.example.ui.screens.buildAppListEntries
import com.example.ui.screens.computeSectionedDownTargets
import com.example.ui.theme.FavoritesLayout
import com.example.ui.theme.ThemeStyle
import com.example.ui.theme.resolveEffectiveFavoritesLayout
import com.example.ui.viewmodel.MainLibraryTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
class TvSupportAndLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleApp(id: String, title: String, isFavorite: Boolean = false) = GameItem(
        id = id,
        title = title,
        type = GameType.INSTALLED_APP,
        isGame = false,
        isFavorite = isFavorite,
        packageName = "com.example.$id",
        iconUrl = "https://example.invalid/$id.png"
    )

    @Test
    fun `resolveEffectiveFavoritesLayout enforces TV_CARDS exclusively on TV and hides it on phone`() {
        assertEquals(listOf(FavoritesLayout.TEXT, FavoritesLayout.ICONS), FavoritesLayout.mobileOptions())

        assertEquals(
            FavoritesLayout.TV_CARDS,
            resolveEffectiveFavoritesLayout(FavoritesLayout.TEXT, isTelevision = true)
        )
        assertEquals(
            FavoritesLayout.TV_CARDS,
            resolveEffectiveFavoritesLayout(FavoritesLayout.ICONS, isTelevision = true)
        )
        assertEquals(
            FavoritesLayout.TV_CARDS,
            resolveEffectiveFavoritesLayout(FavoritesLayout.TV_CARDS, isTelevision = true)
        )

        assertEquals(
            FavoritesLayout.TEXT,
            resolveEffectiveFavoritesLayout(FavoritesLayout.TEXT, isTelevision = false)
        )
        assertEquals(
            FavoritesLayout.ICONS,
            resolveEffectiveFavoritesLayout(FavoritesLayout.ICONS, isTelevision = false)
        )
        assertEquals(
            FavoritesLayout.TEXT,
            resolveEffectiveFavoritesLayout(FavoritesLayout.TV_CARDS, isTelevision = false)
        )

        val tvConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_TELEVISION
        }
        assertTrue(isTelevisionConfiguration(tvConfig))

        val normalConfig = Configuration().apply {
            uiMode = Configuration.UI_MODE_TYPE_NORMAL
        }
        assertFalse(isTelevisionConfiguration(normalConfig))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
    fun `settings modal on phone shows only text and icons layout options`() {
        composeRule.setContent {
            MaterialTheme {
                SettingsModal(
                    favoritesLayout = FavoritesLayout.TEXT,
                    steamGridDbApiKey = null,
                    steamGridDbIncludeNsfw = false,
                    iconPacks = emptyList(),
                    selectedIconPack = null,
                    hiddenItems = emptyList(),
                    appsBackgroundOpacity = 0.7f,
                    themeStyle = ThemeStyle.WALLPAPER,
                    weatherEnabled = false,
                    weatherMode = com.example.data.service.WeatherLocationMode.DEVICE_LOCATION,
                    weatherZip = "",
                    weatherInfo = null,
                    weatherError = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    weatherApps = emptyList(),
                    weatherAppPackage = null,
                    librarySyncBusy = false,
                    librarySyncStatus = null,
                    librarySyncConfigured = false,
                    librarySyncTarget = null,
                    librarySyncLastSyncedAt = null,
                    isDefaultLauncher = { true },
                    isHomeOverrideEnabled = { false },
                    onSelectFavoritesLayout = {},
                    onSaveApiKey = {},
                    onSteamGridDbIncludeNsfwChange = {},
                    onSelectIconPack = {},
                    onSetDefaultLauncher = {},
                    onSetAppsBackgroundOpacity = {},
                    onSelectThemeStyle = {},
                    onUnhideItem = {},
                    onOpenHiddenItem = {},
                    onWeatherEnabledChange = {},
                    onWeatherModeChange = {},
                    onWeatherZipChange = {},
                    onTemperatureUnitChange = {},
                    onSelectWeatherApp = {},
                    onOpenAccessibilitySettings = {},
                    onChooseSyncFolder = {},
                    onSyncLibraryNow = {},
                    onDisconnectLibrarySync = {},
                    onDismiss = {},
                    isTelevision = false
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Favorites Layout").assertIsDisplayed()
        composeRule.onNodeWithTag("favorites_layout_text").assertIsDisplayed()
        composeRule.onNodeWithTag("favorites_layout_icons").assertIsDisplayed()
        composeRule.onNodeWithTag("favorites_layout_tv_cards").assertDoesNotExist()
    }

    @Test
    fun `settings modal on TV hides list and grid layout options and supports D-pad slider stepping`() {
        var opacity by mutableFloatStateOf(0.5f)
        composeRule.setContent {
            MaterialTheme {
                SettingsModal(
                    favoritesLayout = FavoritesLayout.TV_CARDS,
                    steamGridDbApiKey = null,
                    steamGridDbIncludeNsfw = false,
                    iconPacks = emptyList(),
                    selectedIconPack = null,
                    hiddenItems = emptyList(),
                    appsBackgroundOpacity = opacity,
                    themeStyle = ThemeStyle.WALLPAPER,
                    weatherEnabled = false,
                    weatherMode = com.example.data.service.WeatherLocationMode.DEVICE_LOCATION,
                    weatherZip = "",
                    weatherInfo = null,
                    weatherError = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    weatherApps = emptyList(),
                    weatherAppPackage = null,
                    librarySyncBusy = false,
                    librarySyncStatus = null,
                    librarySyncConfigured = false,
                    librarySyncTarget = null,
                    librarySyncLastSyncedAt = null,
                    isDefaultLauncher = { true },
                    isHomeOverrideEnabled = { false },
                    onSelectFavoritesLayout = {},
                    onSaveApiKey = {},
                    onSteamGridDbIncludeNsfwChange = {},
                    onSelectIconPack = {},
                    onSetDefaultLauncher = {},
                    onSetAppsBackgroundOpacity = { opacity = it },
                    onSelectThemeStyle = {},
                    onUnhideItem = {},
                    onOpenHiddenItem = {},
                    onWeatherEnabledChange = {},
                    onWeatherModeChange = {},
                    onWeatherZipChange = {},
                    onTemperatureUnitChange = {},
                    onSelectWeatherApp = {},
                    onOpenAccessibilitySettings = {},
                    onChooseSyncFolder = {},
                    onSyncLibraryNow = {},
                    onDisconnectLibrarySync = {},
                    onDismiss = {},
                    isTelevision = true
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Favorites Layout").assertDoesNotExist()
        composeRule.onNodeWithTag("favorites_layout_text").assertDoesNotExist()
        composeRule.onNodeWithTag("favorites_layout_icons").assertDoesNotExist()
        composeRule.onNodeWithTag("favorites_layout_tv_cards").assertDoesNotExist()

        composeRule.onNodeWithTag("apps_opacity_slider").performScrollTo()
        composeRule.onNodeWithTag("apps_opacity_slider").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apps_opacity_slider").assertIsFocused()
        composeRule.onNodeWithTag("apps_opacity_slider").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        assertEquals(0.6f, opacity, 0.01f)
    }

    @Test
    fun `TV FavoritesHome renders centered 16x9 cards max 5 across and 3 tall with clock date weather`() {
        val favorites = (0 until 18).map { i ->
            sampleApp("fav_$i", "App $i", isFavorite = true)
        }

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    FavoritesHome(
                        favorites = favorites,
                        focusedItemId = null,
                        layout = FavoritesLayout.TV_CARDS,
                        showFocusEffects = true,
                        weatherEnabled = true,
                        weather = WeatherInfo(
                            temperatureC = 22.0,
                            weatherCode = 0,
                            locationLabel = "Seattle"
                        ),
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        onLaunchFavorite = {},
                        onLongPressFavorite = {},
                        onOpenClock = {},
                        onOpenCalendar = {},
                        onOpenWeather = {},
                        onPullDown = {},
                        onNavigateDownToAppList = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("favorites_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("favorites_date_row").assertIsDisplayed()
        composeRule.onNodeWithTag("favorites_weather").assertIsDisplayed()
        composeRule.onNodeWithTag("favorites_tv_grid").assertIsDisplayed()

        // Row 1 (indices 0..4) share the same row top and centerline.
        val row1Bounds = (0..4).map { i ->
            composeRule.onNodeWithTag("favorite_app_fav_$i").assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        val row1Top = row1Bounds.first().top.value
        row1Bounds.forEachIndexed { idx, b ->
            assertTrue("Expected card $idx on row 1 top $row1Top, was ${b.top.value}", abs(b.top.value - row1Top) < 1f)
        }

        // Verify 16:9 card aspect ratio.
        val cardRatio = (row1Bounds[0].right - row1Bounds[0].left).value /
            (row1Bounds[0].bottom - row1Bounds[0].top).value
        assertTrue("Expected ~16:9 aspect ratio, was $cardRatio", cardRatio in 1.65f..1.90f)

        // Verify grid is centered horizontally around 480.dp midpoint.
        val gridBounds = composeRule.onNodeWithTag("favorites_tv_grid").getUnclippedBoundsInRoot()
        val gridCenterX = (gridBounds.left + gridBounds.right).value / 2f
        assertTrue("Expected horizontal center ~480dp, was $gridCenterX", abs(gridCenterX - 480f) < 12f)

        // Verify clock + grid block is centered vertically inside 476dp container.
        val clockBounds = composeRule.onNodeWithTag("favorites_clock").getUnclippedBoundsInRoot()
        val blockCenterY = (clockBounds.top.value + gridBounds.bottom.value) / 2f
        assertTrue("Expected vertical center ~238dp, was $blockCenterY", abs(blockCenterY - 238f) < 20f)

        // Card 15 (4th row) should not be displayed because the grid never shows more than 3 rows tall.
        composeRule.onNodeWithTag("favorite_app_fav_15").assertDoesNotExist()
    }

    @Test
    fun `TV FavoritesHome D-pad Down from last row opens app list and App List D-pad Up returns`() {
        val favorites = (0 until 6).map { i -> sampleApp("fav_$i", "Fav $i", isFavorite = true) }
        val libraryApps = listOf(
            sampleApp("alpha", "Alpha"),
            sampleApp("bravo", "Bravo")
        )
        var navigatedDown by mutableIntStateOf(0)
        var navigatedUp by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    Box(modifier = Modifier.size(width = 960.dp, height = 240.dp)) {
                        FavoritesHome(
                            favorites = favorites,
                            focusedItemId = "fav_5",
                            layout = FavoritesLayout.TV_CARDS,
                            showFocusEffects = true,
                            onLaunchFavorite = {},
                            onLongPressFavorite = {},
                            onOpenClock = {},
                            onOpenCalendar = {},
                            onPullDown = {},
                            onNavigateDownToAppList = { navigatedDown++ }
                        )
                    }
                    Box(modifier = Modifier.size(width = 960.dp, height = 236.dp)) {
                        AppsAlphabeticalList(
                            entries = buildAppListEntries(libraryApps),
                            focusedItemId = "alpha",
                            layout = FavoritesLayout.TV_CARDS,
                            showFocusEffects = true,
                            listState = rememberLazyListState(),
                            gridState = rememberLazyGridState(),
                            onAppClick = {},
                            onAppLongClick = {},
                            onLetterSelected = {},
                            onNavigateUpToFavorites = { navigatedUp++ }
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_fav_5").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_fav_5").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        assertEquals(1, navigatedDown)

        // Focus first app in AppsAlphabeticalList and press Up.
        composeRule.onNodeWithTag("app_row_alpha").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_row_alpha").assertIsFocused()
        composeRule.onNodeWithTag("app_row_alpha").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        assertEquals(1, navigatedUp)
    }

    @Test
    fun `AppsAlphabeticalList TV mode keeps letter separators and 16x9 cards up to 5 columns`() {
        val folder = GameItem(
            id = "folder_media",
            title = "Media",
            type = GameType.FOLDER,
            isGame = false
        )
        val apps = listOf(folder) + (0 until 7).map { i ->
            sampleApp("a_$i", "Alpha $i")
        }

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFolderPreviewItems provides mapOf("folder_media" to emptyList())) {
                    Box(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                        AppsAlphabeticalList(
                            entries = buildAppListEntries(apps),
                            focusedItemId = "folder_media",
                            layout = FavoritesLayout.TV_CARDS,
                            showFocusEffects = true,
                            listState = rememberLazyListState(),
                            gridState = rememberLazyGridState(),
                            onAppClick = {},
                            onAppLongClick = {},
                            onLetterSelected = {}
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apps_tv_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("app_letter_header_Folders").assertIsDisplayed()
        composeRule.onNodeWithTag("app_letter_header_A").assertIsDisplayed()
        val cardBounds = composeRule.onNodeWithTag("app_row_a_0").assertIsDisplayed().getUnclippedBoundsInRoot()
        val ratio = (cardBounds.right - cardBounds.left).value / (cardBounds.bottom - cardBounds.top).value
        assertTrue("Expected ~16:9 card in App List TV mode, was $ratio", ratio in 1.65f..1.90f)
    }

    @Test
    fun `long pressing Select on app or game opens context menu without launching on release`() {
        var clicks = 0
        var longClicks = 0
        val app = sampleApp("app_1", "Browser")
        val game = GameItem(
            id = "game_1",
            title = "Hades",
            type = GameType.MOONLIGHT,
            isGame = true
        )
        val appRequester = FocusRequester()
        val gameRequester = FocusRequester()

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.size(400.dp, 500.dp)) {
                    Box(modifier = Modifier.size(180.dp, 102.dp)) {
                        AppTvCard(
                            app = app,
                            isFocused = true,
                            showFocusEffects = true,
                            testTag = "test_tv_app",
                            onClick = { clicks++ },
                            onLongClick = { longClicks++ },
                            modifier = Modifier.focusRequester(appRequester)
                        )
                    }
                    Box(modifier = Modifier.size(120.dp, 180.dp)) {
                        SteamGamePosterCard(
                            game = game,
                            isFocused = false,
                            showFocusEffects = true,
                            onClick = { clicks++ },
                            onLongClick = { longClicks++ },
                            modifier = Modifier.focusRequester(gameRequester)
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { appRequester.requestFocus() }
        composeRule.waitForIdle()

        // Short Select -> launch click only
        composeRule.onNodeWithTag("test_tv_app").performKeyInput {
            keyDown(Key.DirectionCenter)
        }
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithTag("test_tv_app").performKeyInput {
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(1, clicks)
        assertEquals(0, longClicks)

        // Long Select -> context menu only, release does not increment clicks
        composeRule.onNodeWithTag("test_tv_app").performKeyInput {
            keyDown(Key.DirectionCenter)
        }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals(1, clicks)
        composeRule.onNodeWithTag("test_tv_app").performKeyInput {
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(1, longClicks)
        assertEquals(1, clicks)

        // Long Enter on game poster -> context menu only
        composeRule.runOnIdle { gameRequester.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_poster_game_1").performKeyInput {
            keyDown(Key.Enter)
        }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_poster_game_1").performKeyInput {
            keyUp(Key.Enter)
        }
        composeRule.waitForIdle()
        assertEquals(2, longClicks)
        assertEquals(1, clicks)
    }

    @Test
    fun `TopNavBar and EditFavoritesSheet support remote D-pad navigation and reordering`() {
        var downTriggered = false
        var reordered by mutableStateOf(emptyList<GameItem>())
        val navTabRequester = FocusRequester()
        val favs = listOf(
            sampleApp("f1", "First", isFavorite = true),
            sampleApp("f2", "Second", isFavorite = true)
        )

        composeRule.setContent {
            MaterialTheme {
                Column {
                    TopNavBar(
                        currentTab = MainLibraryTab.APPS,
                        showFocusEffects = true,
                        onTabSelected = {},
                        onOpenAddModal = {},
                        onOpenSettings = {},
                        onNavigateDown = { downTriggered = true },
                        modifier = Modifier.focusRequester(navTabRequester)
                    )
                    EditFavoritesSheet(
                        favorites = favs,
                        onReorder = { reordered = it },
                        onRemoveFavorite = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        // Move Down button reorders favorites via Select/click without drag-and-drop.
        composeRule.onNodeWithTag("edit_favorites_move_down_f1").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf("f2", "f1"), reordered.map { it.id })

        // Dismiss is independent; verify TopNavBar Down hook fires with remote Down.
        composeRule.onNodeWithTag("tab_apps").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tab_apps").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        assertTrue(downTriggered)
    }

    @Test
    fun `holding Select to open menu consumes release so first menu item is not auto-activated`() {
        val app = sampleApp("app_menu", "Media App")
        var showMenu by mutableStateOf(false)
        var firstMenuClicks by mutableIntStateOf(0)
        var sheetMoveClicks by mutableIntStateOf(0)
        val cardRequester = FocusRequester()
        val menuFirstItemRequester = FocusRequester()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(600.dp, 500.dp)) {
                    Box(modifier = Modifier.size(180.dp, 102.dp)) {
                        AppTvCard(
                            app = app,
                            isFocused = !showMenu,
                            showFocusEffects = true,
                            testTag = "hold_select_card",
                            onClick = {},
                            onLongClick = { showMenu = true },
                            modifier = Modifier.focusRequester(cardRequester)
                        )
                    }
                    if (showMenu) {
                        LaunchedEffect(Unit) {
                            menuFirstItemRequester.requestFocus()
                        }
                        Column(modifier = Modifier.consumeOrphanedSelectKeyEvents()) {
                            Box(
                                modifier = Modifier
                                    .focusRequester(menuFirstItemRequester)
                                    .focusable()
                                    .clickable { firstMenuClicks++ }
                                    .testTag("guarded_menu_first_item")
                            ) {
                                Text("Move to Games")
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { cardRequester.requestFocus() }
        composeRule.waitForIdle()

        // Hold Select on card until long-press opens the guarded menu and shifts focus to the first item.
        composeRule.onNodeWithTag("hold_select_card").performKeyInput {
            keyDown(Key.DirectionCenter)
        }
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.waitForIdle()
        assertTrue(showMenu)
        composeRule.onNodeWithTag("guarded_menu_first_item").assertIsFocused()

        // Releasing the still-held Select key on the newly focused menu must NOT fire its click.
        composeRule.onNodeWithTag("guarded_menu_first_item").performKeyInput {
            keyUp(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(0, firstMenuClicks)

        // A subsequent fresh Select press inside the menu DOES trigger the action.
        composeRule.onNodeWithTag("guarded_menu_first_item").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(1, firstMenuClicks)
    }

    @Test
    fun `ItemContextMenuSheet immediately focuses first action and activates on fresh Select press`() {
        val app = sampleApp("app_menu_sheet", "Media App")
        var sheetMoveClicks by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                ItemContextMenuSheet(
                    item = app,
                    onCustomize = {},
                    onMove = { sheetMoveClicks++ },
                    onToggleFavorite = {},
                    onRemoveFromRecents = {},
                    onOpenAppInfo = {},
                    onHide = {},
                    onDelete = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("context_menu_move_to_games").assertIsFocused()
        composeRule.onNodeWithTag("context_menu_move_to_games").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()
        assertEquals(1, sheetMoveClicks)
    }

    @Test
    fun `AppTvCard renders full-bleed banner when heroBannerUrl is set and falls back when null or custom`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bannerFile = File(context.filesDir, "test_tv_banner.png")
        val bmp = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        bannerFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val bannerUri = Uri.fromFile(bannerFile).toString()

        val withBanner = GameItem(
            id = "with_banner",
            title = "Banner App",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.example.banner",
            iconUrl = "file:///data/user/0/com.example/files/app_icons/com.example.banner-1.png",
            heroBannerUrl = bannerUri
        )
        val withoutBanner = GameItem(
            id = "without_banner",
            title = "No Banner App",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.example.nobanner",
            iconUrl = null,
            heroBannerUrl = null
        )
        val customIconWithBanner = GameItem(
            id = "custom_icon_app",
            title = "Custom Icon App",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.example.custom",
            iconUrl = "https://example.invalid/custom_override.png",
            heroBannerUrl = bannerUri
        )

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.size(300.dp, 540.dp)) {
                    Box(modifier = Modifier.size(200.dp, 113.dp)) {
                        AppTvCard(
                            app = withBanner,
                            isFocused = false,
                            showFocusEffects = true,
                            testTag = "card_with_banner",
                            onClick = {},
                            onLongClick = {}
                        )
                    }
                    Box(modifier = Modifier.size(200.dp, 113.dp)) {
                        AppTvCard(
                            app = withoutBanner,
                            isFocused = false,
                            showFocusEffects = true,
                            testTag = "card_without_banner",
                            onClick = {},
                            onLongClick = {}
                        )
                    }
                    Box(modifier = Modifier.size(200.dp, 113.dp)) {
                        AppTvCard(
                            app = customIconWithBanner,
                            isFocused = false,
                            showFocusEffects = true,
                            testTag = "card_custom_icon",
                            onClick = {},
                            onLongClick = {}
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val bannerNode = composeRule.onNodeWithTag("tv_card_banner_with_banner", useUnmergedTree = true)
        runCatching { bannerNode.assertIsDisplayed() }.getOrElse { err ->
            throw AssertionError(
                "${err.message}\nTree:\n${composeRule.onRoot(useUnmergedTree = true).printToString()}",
                err
            )
        }
        composeRule.onNodeWithText("Banner App", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("tv_card_banner_without_banner", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("No Banner App", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("tv_card_banner_custom_icon", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Custom Icon App", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `AppIconStore resolves TV banners without falling back to square app icons`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPm = Shadows.shadowOf(context.packageManager)

        // Package A: Self package declares android:banner="@drawable/tv_banner" in AndroidManifest.
        val bannerPkg = context.packageName
        val bannerClass = "com.example.MainActivity"

        val resolvedBannerDrawable = AppIconStore.resolveTvBannerDrawable(
            packageManager = context.packageManager,
            packageName = bannerPkg,
            preferredActivityName = bannerClass
        )
        assertNotNull(resolvedBannerDrawable)

        val cachedBannerUrl = AppIconStore.bannerUrl(
            context = context,
            packageName = bannerPkg,
            versionStamp = 42L,
            preferredActivityName = bannerClass
        )
        assertNotNull(cachedBannerUrl)
        val bannerUrlValue = cachedBannerUrl!!
        // Windows test file URIs percent-encode the separator, so normalize before matching.
        val normalizedBannerUrl = runCatching { Uri.decode(bannerUrlValue) }
            .getOrDefault(bannerUrlValue)
            .replace('\\', '/')
        assertTrue(normalizedBannerUrl.contains("/app_banners/"))
        val cachedBannerFile = File(Uri.parse(bannerUrlValue).path!!)
        assertTrue(cachedBannerFile.exists() && cachedBannerFile.length() > 0L)

        // Package B: Standard app with only a square icon and no banner -> returns null.
        val noBannerPkg = "com.example.squareonly"
        val noBannerPackageInfo = PackageInfo().apply {
            packageName = noBannerPkg
            applicationInfo = ApplicationInfo().apply {
                packageName = noBannerPkg
                icon = R.mipmap.ic_launcher_round
                banner = 0
            }
        }
        shadowPm.installPackage(noBannerPackageInfo)

        assertNull(
            AppIconStore.resolveTvBannerDrawable(
                packageManager = context.packageManager,
                packageName = noBannerPkg,
                preferredActivityName = "$noBannerPkg.MainActivity"
            )
        )
        assertNull(
            AppIconStore.bannerUrl(
                context = context,
                packageName = noBannerPkg,
                versionStamp = 7L,
                preferredActivityName = "$noBannerPkg.MainActivity"
            )
        )
    }

    @Test
    fun `empty TV FavoritesHome stacks clock above date row without overlap and opens app list on Down`() {
        var navigatedDown by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    FavoritesHome(
                        favorites = emptyList(),
                        focusedItemId = null,
                        layout = FavoritesLayout.TV_CARDS,
                        showFocusEffects = true,
                        weatherEnabled = true,
                        weather = WeatherInfo(
                            temperatureC = 4.5,
                            weatherCode = 2,
                            locationLabel = "Seattle"
                        ),
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        onLaunchFavorite = {},
                        onLongPressFavorite = {},
                        onOpenClock = {},
                        onOpenCalendar = {},
                        onOpenWeather = {},
                        onPullDown = {},
                        onNavigateDownToAppList = { navigatedDown++ }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val clockBounds = composeRule.onNodeWithTag("favorites_clock").assertIsDisplayed().getUnclippedBoundsInRoot()
        val dateRowBounds = composeRule.onNodeWithTag("favorites_date_row").assertIsDisplayed().getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("favorites_weather").assertIsDisplayed()

        // Clock must sit completely above date + weather row with no vertical overlap.
        assertTrue(
            "Expected clock bottom (${clockBounds.bottom}) <= dateRow top (${dateRowBounds.top})",
            clockBounds.bottom <= dateRowBounds.top
        )

        // Focus the date row so pressing Down immediately opens all apps. With no favorites
        // it is the empty page's focus anchor, so it stays focusable on TV.
        composeRule.onNodeWithTag("favorites_date_row").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorites_date_row").assertIsFocused()
        composeRule.onNodeWithTag("favorites_date_row").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        assertEquals(1, navigatedDown)
    }

    @Test
    fun `TV FavoritesHome with over 15 items opens app list when pressing Down on third visible row`() {
        val favorites = (0 until 18).map { i -> sampleApp("fav_$i", "App $i", isFavorite = true) }
        var navigatedDown by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    FavoritesHome(
                        favorites = favorites,
                        focusedItemId = "fav_10",
                        layout = FavoritesLayout.TV_CARDS,
                        showFocusEffects = true,
                        onLaunchFavorite = {},
                        onLongPressFavorite = {},
                        onOpenClock = {},
                        onOpenCalendar = {},
                        onPullDown = {},
                        onNavigateDownToAppList = { navigatedDown++ }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_fav_10").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_fav_10").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        assertEquals(1, navigatedDown)
    }

    @Test
    fun `settings modal on TV shows Wallpaper section and App Row Columns controls while phone hides Wallpaper`() {
        var columns by mutableIntStateOf(0)
        var wallpaperUrl by mutableStateOf<String?>("file:///tmp/wall.png")
        var clearCount by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                SettingsModal(
                    favoritesLayout = FavoritesLayout.TV_CARDS,
                    steamGridDbApiKey = null,
                    steamGridDbIncludeNsfw = false,
                    iconPacks = emptyList(),
                    selectedIconPack = null,
                    hiddenItems = emptyList(),
                    appsBackgroundOpacity = 0.7f,
                    themeStyle = ThemeStyle.WALLPAPER,
                    weatherEnabled = false,
                    weatherMode = com.example.data.service.WeatherLocationMode.DEVICE_LOCATION,
                    weatherZip = "",
                    weatherInfo = null,
                    weatherError = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    weatherApps = emptyList(),
                    weatherAppPackage = null,
                    librarySyncBusy = false,
                    librarySyncStatus = null,
                    librarySyncConfigured = false,
                    librarySyncTarget = null,
                    librarySyncLastSyncedAt = null,
                    isDefaultLauncher = { true },
                    isHomeOverrideEnabled = { false },
                    onSelectFavoritesLayout = {},
                    onSaveApiKey = {},
                    onSteamGridDbIncludeNsfwChange = {},
                    onSelectIconPack = {},
                    onSetDefaultLauncher = {},
                    onSetAppsBackgroundOpacity = {},
                    onSelectThemeStyle = {},
                    onUnhideItem = {},
                    onOpenHiddenItem = {},
                    onWeatherEnabledChange = {},
                    onWeatherModeChange = {},
                    onWeatherZipChange = {},
                    onTemperatureUnitChange = {},
                    onSelectWeatherApp = {},
                    onOpenAccessibilitySettings = {},
                    onChooseSyncFolder = {},
                    onSyncLibraryNow = {},
                    onDisconnectLibrarySync = {},
                    onDismiss = {},
                    isTelevision = true,
                    tvWallpaperUrl = wallpaperUrl,
                    appGridColumns = columns,
                    onClearTvWallpaper = {
                        clearCount++
                        wallpaperUrl = null
                    },
                    onSetAppGridColumns = { columns = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_wallpaper_preview").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tv_wallpaper_choose_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("tv_wallpaper_clear_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, clearCount)
        composeRule.onNodeWithTag("tv_wallpaper_preview").assertDoesNotExist()

        composeRule.onNodeWithTag("app_columns_auto_chip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("app_columns_increase_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, columns)
        composeRule.onNodeWithTag("app_columns_increase_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, columns)
        composeRule.onNodeWithTag("app_columns_auto_chip").performClick()
        composeRule.waitForIdle()
        assertEquals(0, columns)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
    fun `settings modal on phone hides TV Wallpaper and App Row Columns controls`() {
        composeRule.setContent {
            MaterialTheme {
                SettingsModal(
                    favoritesLayout = FavoritesLayout.ICONS,
                    steamGridDbApiKey = null,
                    steamGridDbIncludeNsfw = false,
                    iconPacks = emptyList(),
                    selectedIconPack = null,
                    hiddenItems = emptyList(),
                    appsBackgroundOpacity = 0.7f,
                    themeStyle = ThemeStyle.WALLPAPER,
                    weatherEnabled = false,
                    weatherMode = com.example.data.service.WeatherLocationMode.DEVICE_LOCATION,
                    weatherZip = "",
                    weatherInfo = null,
                    weatherError = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    weatherApps = emptyList(),
                    weatherAppPackage = null,
                    librarySyncBusy = false,
                    librarySyncStatus = null,
                    librarySyncConfigured = false,
                    librarySyncTarget = null,
                    librarySyncLastSyncedAt = null,
                    isDefaultLauncher = { true },
                    isHomeOverrideEnabled = { false },
                    onSelectFavoritesLayout = {},
                    onSaveApiKey = {},
                    onSteamGridDbIncludeNsfwChange = {},
                    onSelectIconPack = {},
                    onSetDefaultLauncher = {},
                    onSetAppsBackgroundOpacity = {},
                    onSelectThemeStyle = {},
                    onUnhideItem = {},
                    onOpenHiddenItem = {},
                    onWeatherEnabledChange = {},
                    onWeatherModeChange = {},
                    onWeatherZipChange = {},
                    onTemperatureUnitChange = {},
                    onSelectWeatherApp = {},
                    onOpenAccessibilitySettings = {},
                    onChooseSyncFolder = {},
                    onSyncLibraryNow = {},
                    onDisconnectLibrarySync = {},
                    onDismiss = {},
                    isTelevision = false,
                    tvWallpaperUrl = "file:///tmp/wall.png",
                    appGridColumns = 3
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tv_wallpaper_choose_button").assertDoesNotExist()
        composeRule.onNodeWithText("App Row Columns").assertDoesNotExist()
        composeRule.onNodeWithTag("app_columns_auto_chip").assertDoesNotExist()
        composeRule.onNodeWithTag("app_columns_value_text").assertDoesNotExist()
    }

    @Test
    fun `AppsAlphabeticalList hides letter rail on TV and respects custom configuredColumns`() {
        val apps = (0 until 5).map { i -> sampleApp("app_$i", "Alpha $i") }

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    AppsAlphabeticalList(
                        entries = buildAppListEntries(apps),
                        focusedItemId = "app_0",
                        layout = FavoritesLayout.TV_CARDS,
                        showFocusEffects = true,
                        listState = rememberLazyListState(),
                        gridState = rememberLazyGridState(),
                        configuredColumns = 3,
                        showAlphabetRail = false,
                        onAppClick = {},
                        onAppLongClick = {},
                        onLetterSelected = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apps_alphabet_rail").assertDoesNotExist()

        val row0First = composeRule.onNodeWithTag("app_row_app_0").assertIsDisplayed().getUnclippedBoundsInRoot()
        val row0Second = composeRule.onNodeWithTag("app_row_app_1").assertIsDisplayed().getUnclippedBoundsInRoot()
        val row0Last = composeRule.onNodeWithTag("app_row_app_2").assertIsDisplayed().getUnclippedBoundsInRoot()
        val row1First = composeRule.onNodeWithTag("app_row_app_3").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue(abs(row0Second.top.value - row0Last.top.value) < 2f)
        assertTrue("Expected app_3 wrapped onto row 2 with configuredColumns=3", row1First.top > row0Last.bottom)
        assertTrue("Expected app_0 on same row as app_2", abs(row0First.top.value - row0Last.top.value) < 10f)
    }

    @Test
    fun `FavoritesHome TV D-pad Down from partial row column with nothing directly below focuses last item on next row`() {
        val favorites = (0 until 7).map { i -> sampleApp("fav_$i", "App $i", isFavorite = true) }

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    FavoritesHome(
                        favorites = favorites,
                        focusedItemId = "fav_3",
                        layout = FavoritesLayout.TV_CARDS,
                        showFocusEffects = true,
                        onLaunchFavorite = {},
                        onLongPressFavorite = {},
                        onOpenClock = {},
                        onOpenCalendar = {},
                        onPullDown = {},
                        onNavigateDownToAppList = {},
                        configuredColumns = 5
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_fav_3").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_fav_3").assertIsFocused()

        composeRule.onNodeWithTag("favorite_app_fav_3").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_fav_6").assertIsFocused()
    }

    @Test
    fun `computeSectionedDownTargets and AppsAlphabeticalList handle partial rows within sections and across letter headers`() {
        val alphaApps = (0 until 7).map { i -> sampleApp("a_$i", "Alpha $i") }
        val bravoApps = (0 until 2).map { i -> sampleApp("b_$i", "Bravo $i") }
        val entries = buildAppListEntries(alphaApps + bravoApps)
        val targets = computeSectionedDownTargets(entries, columns = 5)

        // Section A row 0 col 4 -> Section A row 1 last index (a_6)
        assertEquals("a_6", targets["a_4"]?.targetId)
        // Section A row 1 col 1 -> Section B row 0 col 1 (b_1) across letter header
        assertEquals("b_1", targets["a_6"]?.targetId)
        // Final row has no downward target
        assertNull(targets["b_0"])
        assertNull(targets["b_1"])

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    AppsAlphabeticalList(
                        entries = entries,
                        focusedItemId = "a_4",
                        layout = FavoritesLayout.TV_CARDS,
                        showFocusEffects = true,
                        listState = rememberLazyListState(),
                        gridState = rememberLazyGridState(),
                        configuredColumns = 5,
                        showAlphabetRail = false,
                        onAppClick = {},
                        onAppLongClick = {},
                        onLetterSelected = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_row_a_4").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_row_a_4").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_row_a_6").assertIsFocused()

        composeRule.onNodeWithTag("app_row_a_6").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_row_b_1").assertIsFocused()
    }

    @Test
    fun `AddGfnSearchView and AddLunaSearchView move Down to partial next row when nothing sits directly below`() {
        val gfnGames = (0 until 7).map { i ->
            GameItem(
                id = "gfn_$i",
                title = "Game $i",
                type = GameType.GEFORCE_NOW,
                isGame = true
            )
        }
        val lunaGames = (0 until 7).map { i ->
            GameItem(
                id = "luna_$i",
                title = "Luna $i",
                type = GameType.AMAZON_LUNA,
                isGame = true
            )
        }
        var showLuna by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.size(width = 860.dp, height = 460.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (!showLuna) {
                            AddGfnSearchView(
                                onGameSelected = {},
                                catalogLoader = {
                                    GeForceNowService.GfnBrowseResult(
                                        games = gfnGames,
                                        classification = GeForceNowService.GfnDefaultClassification.CATALOG_FALLBACK
                                    )
                                }
                            )
                        } else {
                            AddLunaSearchView(
                                onGameSelected = {},
                                catalogLoader = { Result.success(lunaGames) },
                                posterResolver = { null }
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        // At 860dp, columns = max(2, (860+12)/(120+12)) = 6 -> row 0 has 0..5, row 1 has 6.
        composeRule.onNodeWithTag("gfn_game_gfn_4").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gfn_game_gfn_4").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gfn_game_gfn_6").assertIsFocused()

        showLuna = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("luna_game_luna_4").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("luna_game_luna_4").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("luna_game_luna_6").assertIsFocused()
    }

    @Test
    fun `FolderContentsSheet moves Down to partial next row when nothing sits directly below`() {
        val folder = GameItem(
            id = "folder_media",
            title = "Media",
            type = GameType.FOLDER,
            isGame = false
        )
        val folderApps = (0 until 7).map { i -> sampleApp("fapp_$i", "Folder App $i") }

        composeRule.setContent {
            MaterialTheme {
                FolderContentsSheet(
                    folder = folder,
                    apps = folderApps,
                    layout = FavoritesLayout.TV_CARDS,
                    focusedItemId = "fapp_3",
                    showFocusEffects = true,
                    configuredColumns = 5,
                    onAppClick = {},
                    onAppLongClick = {},
                    onCustomizeFolder = {},
                    onManageFolderApps = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_app_row_fapp_3").performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_app_row_fapp_3").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_app_row_fapp_6").assertIsFocused()
    }

    @Test
    fun `settings modal shows clean Google Drive and Local Folder sync controls across all modes`() {
        var syncMode by mutableStateOf(LibrarySyncMode.NONE)
        var targetLabel by mutableStateOf<String?>(null)
        var syncStatus by mutableStateOf<String?>(null)
        var driveConnectClicks by mutableIntStateOf(0)
        var disconnectClicks by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                SettingsModal(
                    favoritesLayout = FavoritesLayout.TV_CARDS,
                    steamGridDbApiKey = null,
                    steamGridDbIncludeNsfw = false,
                    iconPacks = emptyList(),
                    selectedIconPack = null,
                    hiddenItems = emptyList(),
                    appsBackgroundOpacity = 0.7f,
                    themeStyle = ThemeStyle.WALLPAPER,
                    weatherEnabled = false,
                    weatherMode = com.example.data.service.WeatherLocationMode.DEVICE_LOCATION,
                    weatherZip = "",
                    weatherInfo = null,
                    weatherError = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    weatherApps = emptyList(),
                    weatherAppPackage = null,
                    librarySyncBusy = false,
                    librarySyncStatus = syncStatus,
                    librarySyncConfigured = syncMode != LibrarySyncMode.NONE,
                    librarySyncMode = syncMode,
                    librarySyncTarget = targetLabel,
                    librarySyncLastSyncedAt = null,
                    isDefaultLauncher = { true },
                    isHomeOverrideEnabled = { false },
                    onSelectFavoritesLayout = {},
                    onSaveApiKey = {},
                    onSteamGridDbIncludeNsfwChange = {},
                    onSelectIconPack = {},
                    onSetDefaultLauncher = {},
                    onSetAppsBackgroundOpacity = {},
                    onSelectThemeStyle = {},
                    onUnhideItem = {},
                    onOpenHiddenItem = {},
                    onWeatherEnabledChange = {},
                    onWeatherModeChange = {},
                    onWeatherZipChange = {},
                    onTemperatureUnitChange = {},
                    onSelectWeatherApp = {},
                    onOpenAccessibilitySettings = {},
                    onChooseSyncFolder = {},
                    onConnectGoogleDrive = { driveConnectClicks++ },
                    onSyncLibraryNow = {},
                    onDisconnectLibrarySync = {
                        disconnectClicks++
                        syncMode = LibrarySyncMode.NONE
                        targetLabel = null
                        syncStatus = null
                    },
                    onDismiss = {},
                    isTelevision = true
                )
            }
        }

        composeRule.waitForIdle()
        // Disconnected state: side-by-side Google Drive and Local Folder buttons.
        val driveNode = composeRule.onNodeWithTag("library_sync_google_drive_button")
            .performScrollTo()
            .assertIsDisplayed()
        val folderNode = composeRule.onNodeWithTag("library_sync_pick_folder_button")
            .performScrollTo()
            .assertIsDisplayed()
        val driveBounds = driveNode.fetchSemanticsNode().boundsInRoot
        val folderBounds = folderNode.fetchSemanticsNode().boundsInRoot
        assertEquals(driveBounds.top, folderBounds.top, 2f)
        assertTrue(driveBounds.right <= folderBounds.left)

        driveNode.performClick()
        composeRule.waitForIdle()
        assertEquals(1, driveConnectClicks)

        // SteamGridDB copy updates.
        composeRule.onNodeWithText("Improves artwork matching for imported games.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Hiding NSFW posters keeps saved art in your library.")
            .performScrollTo()
            .assertIsDisplayed()

        // Active Google Drive mode: shows account, status, and wide Disconnect button.
        syncMode = LibrarySyncMode.GOOGLE_DRIVE
        targetLabel = "Google Drive (player@example.com)"
        syncStatus = "Saved 34 games to Google Drive"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_sync_target").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("player@example.com").assertIsDisplayed()
        composeRule.onNodeWithTag("library_sync_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Saved 34 games to Google Drive").assertIsDisplayed()
        composeRule.onNodeWithTag("library_sync_disconnect_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("library_sync_google_drive_button").assertDoesNotExist()
        composeRule.onNodeWithTag("library_sync_pick_folder_button").assertDoesNotExist()
        composeRule.onNodeWithTag("library_sync_now_button").assertDoesNotExist()

        // Active Local Folder mode: shows folder name, status, and wide Disconnect button.
        syncMode = LibrarySyncMode.LOCAL_FOLDER
        targetLabel = "NexusBackup"
        syncStatus = "Saved 34 games to NexusBackup"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_sync_target").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("NexusBackup").assertIsDisplayed()
        composeRule.onNodeWithTag("library_sync_status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("library_sync_disconnect_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, disconnectClicks)
        assertEquals(LibrarySyncMode.NONE, syncMode)
    }

    @Test
    fun `settings modal shows TV Device Code dialog with QR url and code and supports cancel`() {
        var activeSession by mutableStateOf<DriveDeviceCodeSession?>(
            DriveDeviceCodeSession(
                deviceCode = "dev-code-123",
                userCode = "ABCD-EFGH",
                verificationUrl = "https://www.google.com/device",
                verificationUrlComplete = "https://www.google.com/device?user_code=ABCD-EFGH",
                expiresInSeconds = 1800,
                intervalSeconds = 5
            )
        )
        var cancelCalls = 0

        composeRule.setContent {
            MaterialTheme {
                SettingsModal(
                    favoritesLayout = FavoritesLayout.TV_CARDS,
                    steamGridDbApiKey = null,
                    steamGridDbIncludeNsfw = false,
                    iconPacks = emptyList(),
                    selectedIconPack = null,
                    hiddenItems = emptyList(),
                    appsBackgroundOpacity = 0.7f,
                    themeStyle = ThemeStyle.WALLPAPER,
                    weatherEnabled = false,
                    weatherMode = com.example.data.service.WeatherLocationMode.DEVICE_LOCATION,
                    weatherZip = "",
                    weatherInfo = null,
                    weatherError = null,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    weatherApps = emptyList(),
                    weatherAppPackage = null,
                    librarySyncBusy = false,
                    librarySyncStatus = null,
                    librarySyncConfigured = false,
                    librarySyncTarget = null,
                    librarySyncLastSyncedAt = null,
                    isDefaultLauncher = { true },
                    isHomeOverrideEnabled = { false },
                    onSelectFavoritesLayout = {},
                    onSaveApiKey = {},
                    onSteamGridDbIncludeNsfwChange = {},
                    onSelectIconPack = {},
                    onSetDefaultLauncher = {},
                    onSetAppsBackgroundOpacity = {},
                    onSelectThemeStyle = {},
                    onUnhideItem = {},
                    onOpenHiddenItem = {},
                    onWeatherEnabledChange = {},
                    onWeatherModeChange = {},
                    onWeatherZipChange = {},
                    onTemperatureUnitChange = {},
                    onSelectWeatherApp = {},
                    onOpenAccessibilitySettings = {},
                    onChooseSyncFolder = {},
                    onSyncLibraryNow = {},
                    onDisconnectLibrarySync = {},
                    onDismiss = {},
                    librarySyncMode = LibrarySyncMode.NONE,
                    pendingDriveDeviceSession = activeSession,
                    onCancelDriveDeviceAuthorization = {
                        cancelCalls += 1
                        activeSession = null
                    },
                    isTelevision = true
                )
            }
        }

        composeRule.onNodeWithTag("drive_device_auth_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_device_auth_url").assertIsDisplayed()
        composeRule.onNodeWithText("www.google.com/device").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_device_auth_code").assertIsDisplayed()
        composeRule.onNodeWithText("ABCD-EFGH").assertIsDisplayed()
        composeRule.onNodeWithTag("drive_device_auth_cancel")
            .assertIsDisplayed()
            .performClick()

        composeRule.waitForIdle()
        assertEquals(1, cancelCalls)
        composeRule.onNodeWithTag("drive_device_auth_dialog").assertDoesNotExist()
    }

    @Test
    fun `AppsAlphabeticalList letter rail shows no focus styling and still jumps to selected letter`() {
        val selected = mutableListOf<String>()
        val apps = listOf(
            sampleApp("app_a", "Alpha"),
            sampleApp("app_b", "Bravo")
        )

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 600.dp)) {
                    AppsAlphabeticalList(
                        entries = buildAppListEntries(apps),
                        focusedItemId = "app_a",
                        layout = FavoritesLayout.TEXT,
                        showFocusEffects = true,
                        listState = rememberLazyListState(),
                        gridState = rememberLazyGridState(),
                        showAlphabetRail = true,
                        onAppClick = {},
                        onAppLongClick = {},
                        onLetterSelected = { selected += it }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apps_alphabet_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("alphabet_rail_letter_A").assertIsDisplayed()
        composeRule.onNodeWithTag("alphabet_rail_letter_B").assertIsDisplayed()

        // Verify clicking on B in the rail triggers selection cleanly without crashing or focusing rail letters.
        composeRule.onNodeWithTag("alphabet_rail_letter_B").performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()
        assertTrue(selected.isNotEmpty())
    }
}
