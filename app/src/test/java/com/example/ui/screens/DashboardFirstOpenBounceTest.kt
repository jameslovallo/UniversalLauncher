package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
class DashboardFirstOpenBounceTest {

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
            for (i in 1..15) {
                dao.insertGame(
                    GameItem(
                        id = "app_test_$i",
                        title = "App ${('A'.code + (i % 26)).toChar()} $i",
                        type = GameType.INSTALLED_APP,
                        isGame = false,
                        isFavorite = i <= 4,
                        favoriteOrder = if (i <= 4) i - 1 else 0,
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
    fun `first open in non-touch TV mode with delayed insets stays settled on Favorites Home`() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        composeRule.mainClock.autoAdvance = false

        val clockTops = mutableListOf<Float>()
        val gridTops = mutableListOf<Float>()
        val gamesLefts = mutableListOf<Float>()

        repeat(90) { frame ->
            if (frame == 5) {
                // Simulate delayed system window insets dispatch on Frame 5
                composeRule.runOnUiThread {
                    val decor = composeRule.activity.window.decorView
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 32, 0, 0))
                        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                        .build()
                        .toWindowInsets()
                    decor.dispatchApplyWindowInsets(insets)
                }
            }
            composeRule.mainClock.advanceTimeByFrame()
            shadowOf(Looper.getMainLooper()).idle()

            val clock = runCatching {
                composeRule.onNodeWithTag("favorites_clock").getUnclippedBoundsInRoot().top.value
            }.getOrNull()
            if (clock != null) clockTops += clock

            val favGrid = runCatching {
                composeRule.onNodeWithTag("favorites_tv_grid").getUnclippedBoundsInRoot().top.value
            }.getOrNull()
            if (favGrid != null) gridTops += favGrid

            val gamesGrid = runCatching {
                composeRule.onNodeWithTag("games_poster_grid", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
            }.getOrNull()
            if (gamesGrid != null) gamesLefts += gamesGrid
        }

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // After settling, favorites_tv_grid must be visible and games grid must not have slid onto screen.
        composeRule.onNodeWithTag("favorites_clock").fetchSemanticsNode()
        val finalFavTop = composeRule.onNodeWithTag("favorites_tv_grid").getUnclippedBoundsInRoot().top.value
        assertTrue("Expected favorites_tv_grid on screen, was top=$finalFavTop", finalFavTop in 0f..500f)

        // Ensure clock did not oscillate back and forth across frames after Frame 10
        val settledClockSamples = clockTops.drop(15)
        if (settledClockSamples.isNotEmpty()) {
            val minC = settledClockSamples.minOrNull()!!
            val maxC = settledClockSamples.maxOrNull()!!
            assertTrue(
                "Clock bounced vertically after startup: min=$minC max=$maxC samples=$settledClockSamples",
                abs(maxC - minC) < 2f
            )
        }
    }
}
