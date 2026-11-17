package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.TvTestApplication
import com.example.data.local.AppDatabase
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * D-pad / TV remote navigation across the Apps tab.
 *
 * Remote and controller D-pad both arrive as Compose key events, so these tests exercise the
 * shared focus wiring: favorites rows move explicitly, Up escapes to the top bar (the clock,
 * date and weather are display-only on TV), only the last row hands off to the app list, and
 * the top bar is only reachable from a top row and never steals focus back from the library.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    qualifiers = "w960dp-h540dp-television-xhdpi",
    application = TvTestApplication::class
)
class DashboardDpadNavigationTest {

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
            // Six favorites with five columns gives a full first row plus one second-row item.
            for (i in 1..15) {
                dao.insertGame(
                    GameItem(
                        id = "app_test_$i",
                        title = "App ${('A'.code + (i % 26)).toChar()} $i",
                        type = GameType.INSTALLED_APP,
                        isGame = false,
                        isFavorite = i <= 6,
                        favoriteOrder = if (i <= 6) i - 1 else 0,
                        packageName = "com.example.app$i"
                    )
                )
            }
            for (i in 1..8) {
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
    fun `up from favorites skips the clock and reaches the top navigation bar`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_app_test_1").assertIsFocused()

        // On TV the clock, date and weather are display-only, so Up from the grid goes
        // straight to the top navigation bar instead of stepping through them.
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()

        assertTrue(
            "Up from the grid should reach the top navigation bar, skipping the clock",
            topBarTags().any { tag -> isFocused(tag) }
        )
        assertFalse("The clock must not be focusable on TV", isFocused("favorites_clock"))
        assertFalse("The date row must not be focusable on TV", isFocused("favorites_date_row"))

        // The bar stays reachable: Down returns to the favorites grid.
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_app_test_1").assertIsFocused()
    }

    @Test
    fun `down from the top favorites row moves within the grid and keeps the app list closed`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_app_test_1").assertIsFocused()

        composeRule.onNodeWithTag("favorite_app_app_test_1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        // app_test_6 is the only item on the second row.
        composeRule.onNodeWithTag("favorite_app_app_test_6").assertIsFocused()

        // The favorites page must still be the settled page: the app drawer stays below it.
        val drawerTop = composeRule.onNodeWithTag("app_row_app_test_7")
            .getUnclippedBoundsInRoot().top.value
        assertTrue(
            "Down from the top row opened the app list: drawer top=$drawerTop",
            drawerTop > 400f
        )
    }

    @Test
    fun `down from the last favorites row opens the app list`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_app_test_1").assertIsFocused()

        // Move to the only second-row favorite first.
        composeRule.onNodeWithTag("favorite_app_app_test_1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_app_test_6").assertIsFocused()

        composeRule.onNodeWithTag("favorite_app_app_test_6").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()

        // The drawer page settles and focus moves to the first app in the alphabetical list.
        composeRule.onNodeWithTag("app_row_app_test_7").assertIsFocused()
        val drawerTop = composeRule.onNodeWithTag("app_row_app_test_7")
            .getUnclippedBoundsInRoot().top.value
        assertTrue("App list did not open: drawer top=$drawerTop", drawerTop < 400f)
    }

    @Test
    fun `up from the games top row reaches the top navigation bar`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("favorite_app_app_test_1").assertIsFocused()

        composeRule.onNodeWithTag("tab_games").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("game_poster_game_test_1").assertIsFocused()

        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()

        assertTrue(
            "Up from the first games row should focus the top navigation bar",
            topBarTags().any { tag -> isFocused(tag) }
        )
    }

    @Test
    fun `closing the app list never hands focus to the top navigation bar`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeRule.mainClock.autoAdvance = false
        settleFrames()

        composeRule.onNodeWithTag("favorite_app_app_test_1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        settleFrames(5)
        composeRule.onNodeWithTag("favorite_app_app_test_6").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        settleFrames()
        composeRule.onNodeWithTag("app_row_app_test_7").assertIsFocused()

        // Close the app list again; the drawer slides away while the favorites page settles.
        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
        }
        var stoleFocus: String? = null
        repeat(45) {
            composeRule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
            if (stoleFocus == null) {
                stoleFocus = topBarTags().firstOrNull { tag -> isFocused(tag) }
            }
        }

        composeRule.mainClock.autoAdvance = true
        assertTrue("The top bar stole focus while closing the app list: $stoleFocus", stoleFocus == null)
        composeRule.onNodeWithTag("favorite_app_app_test_1").assertIsFocused()
    }

    @Test
    fun `add to favorites from the context menu returns focus to the app list`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeRule.mainClock.autoAdvance = false
        settleFrames()

        composeRule.onNodeWithTag("favorite_app_app_test_1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        settleFrames(5)
        composeRule.onNodeWithTag("favorite_app_app_test_6").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        settleFrames()
        composeRule.onNodeWithTag("app_row_app_test_7").assertIsFocused()

        composeRule.onNodeWithTag("app_row_app_test_7")
            .performSemanticsAction(SemanticsActions.OnLongClick)
        settleFrames()
        composeRule.onNodeWithTag("context_menu_favorite").performClick()

        // Adding the item to favorites removes it from this list, so focus must land on the
        // item that shifted into its slot on the very frame the sheet disappears.
        var topBar: String? = null
        repeat(60) {
            composeRule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
            if (topBar == null) topBar = topBarTags().firstOrNull { tag -> isFocused(tag) }
        }

        composeRule.mainClock.autoAdvance = true
        assertTrue("The top bar stole focus after adding a favorite: $topBar", topBar == null)
        composeRule.onNodeWithTag("app_row_app_test_8").assertIsFocused()
    }

    private fun settleFrames(frames: Int = 40) {
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun topBarTags() = listOf(
        "topbar_add_button",
        "tab_apps",
        "tab_games",
        "topbar_settings_button"
    )

    private fun isFocused(tag: String): Boolean =
        runCatching { composeRule.onNodeWithTag(tag).assertIsFocused() }.isSuccess
}
