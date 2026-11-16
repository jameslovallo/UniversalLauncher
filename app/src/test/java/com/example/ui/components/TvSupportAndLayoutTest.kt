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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.AppIconStore
import com.example.data.service.TemperatureUnit
import com.example.data.service.WeatherInfo
import com.example.ui.screens.AppsAlphabeticalList
import com.example.ui.screens.buildAppListEntries
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
        val firstDrawerRequester = FocusRequester()
        val bottomFavRequester = FocusRequester()

        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.size(width = 960.dp, height = 476.dp)) {
                    Box(modifier = Modifier.size(width = 960.dp, height = 240.dp)) {
                        FavoritesHome(
                            favorites = favorites,
                            focusedItemId = "fav_5",
                            layout = FavoritesLayout.TV_CARDS,
                            showFocusEffects = true,
                            firstItemFocusRequester = bottomFavRequester,
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
                            firstItemFocusRequester = firstDrawerRequester,
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
        composeRule.runOnIdle { firstDrawerRequester.requestFocus() }
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
        assertTrue(cachedBannerUrl!!.contains("/app_banners/"))
        val cachedBannerFile = File(Uri.parse(cachedBannerUrl).path!!)
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
}
