package com.example.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.ui.components.FavoritesHome
import com.example.ui.theme.FavoritesLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
class DashboardPhoneTouchStartupTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedDatabase() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("nexus_launcher_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val db = AppDatabase.getDatabase(app)
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
            val dao = db.gameDao()
            for (i in 1..6) {
                dao.insertGame(
                    GameItem(
                        id = "app_test_$i",
                        title = "App $i",
                        type = GameType.INSTALLED_APP,
                        isGame = false,
                        isFavorite = i <= 4,
                        favoriteOrder = if (i <= 4) i - 1 else 0,
                        packageName = "com.example.app$i"
                    )
                )
            }
            for (i in 1..4) {
                dao.insertGame(
                    GameItem(
                        id = "game_test_$i",
                        title = "Game $i",
                        type = GameType.GEFORCE_NOW,
                        isGame = true,
                        gfnCmsId = "cms_$i"
                    )
                )
            }
        }
    }

    @Test
    fun `phone touch startup with seeded favorites renders without crashing`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("favorites_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("favorites_text_list").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_app_app_test_1").assertIsDisplayed()

        // Switch to Games tab in touch mode as well.
        composeRule.onNodeWithTag("tab_games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_poster_game_test_1").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
class FavoritesHomeTouchModeTest {

    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

    private val sampleFavorites = listOf(
        GameItem(
            id = "fav_1",
            title = "Alpha",
            type = GameType.INSTALLED_APP,
            isGame = false,
            isFavorite = true,
            packageName = "com.example.alpha"
        ),
        GameItem(
            id = "fav_2",
            title = "Beta",
            type = GameType.INSTALLED_APP,
            isGame = false,
            isFavorite = true,
            packageName = "com.example.beta"
        )
    )

    @Test
    fun `text layout renders safely when showFocusEffects is false`() {
        composeRule.setContent {
            MaterialTheme {
                FavoritesHome(
                    favorites = sampleFavorites,
                    focusedItemId = null,
                    layout = FavoritesLayout.TEXT,
                    showFocusEffects = false,
                    onLaunchFavorite = {},
                    onLongPressFavorite = {},
                    onOpenClock = {},
                    onOpenCalendar = {},
                    onPullDown = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorites_text_list").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_app_fav_1").assertIsDisplayed()
    }

    @Test
    fun `icons layout renders safely when showFocusEffects is false`() {
        composeRule.setContent {
            MaterialTheme {
                FavoritesHome(
                    favorites = sampleFavorites,
                    focusedItemId = null,
                    layout = FavoritesLayout.ICONS,
                    showFocusEffects = false,
                    onLaunchFavorite = {},
                    onLongPressFavorite = {},
                    onOpenClock = {},
                    onOpenCalendar = {},
                    onPullDown = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorites_icons_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_app_fav_1").assertIsDisplayed()
    }

    @Test
    fun `tv cards layout renders safely when showFocusEffects is false`() {
        composeRule.setContent {
            MaterialTheme {
                FavoritesHome(
                    favorites = sampleFavorites,
                    focusedItemId = null,
                    layout = FavoritesLayout.TV_CARDS,
                    showFocusEffects = false,
                    onLaunchFavorite = {},
                    onLongPressFavorite = {},
                    onOpenClock = {},
                    onOpenCalendar = {},
                    onPullDown = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorites_tv_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_app_fav_1").assertIsDisplayed()
    }
}
