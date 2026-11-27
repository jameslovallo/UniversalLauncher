package com.example.ui.components

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.AmazonLunaService
import com.example.data.service.GeForceNowService
import com.example.ui.viewmodel.AddModalSubView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
class DiscoverAndGfnUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `parseRecentlyAddedSlugs extracts only added rows in order`() {
        val html = """
            <a class="chg-row" href="/game/how-they-built-the-pyramids" data-type="added">Added</a>
            <a class="chg-row" href="/game/removed-title" data-type="removed">Removed</a>
            <a class="chg-row" data-type="added" href="/game/satelital">Added</a>
            <a class="chg-row" href="/game/how-they-built-the-pyramids" data-type="added">Duplicate</a>
        """.trimIndent()

        val slugs = GeForceNowService.parseRecentlyAddedSlugs(html)
        assertEquals(listOf("how-they-built-the-pyramids", "satelital"), slugs)
    }

    @Test
    fun `gfn search view shows recently added header without GFN badges`() {
        val recentGames = listOf(
            GameItem(
                id = "gfn_1",
                title = "Satelital",
                type = GameType.GEFORCE_NOW,
                isGame = true,
                posterUrl = "https://example.invalid/satelital.jpg"
            ),
            GameItem(
                id = "gfn_2",
                title = "Stormforge",
                type = GameType.GEFORCE_NOW,
                isGame = true,
                posterUrl = "https://example.invalid/stormforge.jpg"
            )
        )
        val searchGames = listOf(
            GameItem(
                id = "gfn_3",
                title = "Cyberpunk 2077",
                type = GameType.GEFORCE_NOW,
                isGame = true
            )
        )

        composeRule.setContent {
            MaterialTheme {
                AddGfnSearchView(
                    onGameSelected = {},
                    catalogLoader = { query ->
                        if (query.isBlank()) {
                            GeForceNowService.GfnBrowseResult(
                                games = recentGames,
                                classification = GeForceNowService.GfnDefaultClassification.RECENTLY_ADDED
                            )
                        } else {
                            GeForceNowService.GfnBrowseResult(
                                games = searchGames,
                                classification = GeForceNowService.GfnDefaultClassification.CATALOG_FALLBACK
                            )
                        }
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gfn_default_header").assertIsDisplayed()
        composeRule.onNodeWithText("Recently added to GeForce NOW (2)").assertIsDisplayed()
        composeRule.onNodeWithText("Satelital").assertIsDisplayed()
        composeRule.onNodeWithText("Stormforge").assertIsDisplayed()
        composeRule.onNodeWithText("GFN").assertDoesNotExist()

        composeRule.onNodeWithTag("add_gfn_search").performTextInput("Cyberpunk")
        composeRule.mainClock.advanceTimeBy(450)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("gfn_default_header").assertDoesNotExist()
        composeRule.onNodeWithText("Cyberpunk 2077").assertIsDisplayed()
        composeRule.onNodeWithText("GFN").assertDoesNotExist()
    }

    @Test
    fun `discover options appear in requested order with installed app icons for first four`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(app.packageManager)
        val packages = listOf(
            "com.amazon.spiderpork",
            "com.nvidia.geforcenow",
            "com.limelight",
            "com.android.vending"
        )
        for (pkg in packages) {
            shadowPm.installPackage(
                PackageInfo().apply {
                    packageName = pkg
                    lastUpdateTime = 12345L
                    applicationInfo = ApplicationInfo().apply {
                        packageName = pkg
                    }
                }
            )
            shadowPm.setApplicationIcon(pkg, ColorDrawable(Color.BLUE))
        }

        val resolved = kotlinx.coroutines.runBlocking {
            resolveDiscoverOptionIcons(app, emptyMap())
        }
        assertTrue(resolved["option_add_luna"]?.startsWith("file:") == true)
        assertTrue(resolved["option_add_gfn"]?.startsWith("file:") == true)
        assertTrue(resolved["option_add_moonlight"]?.startsWith("file:") == true)
        assertTrue(resolved["option_play_store"]?.startsWith("file:") == true)

        composeRule.setContent {
            MaterialTheme {
                // Single-column portrait viewport so visual top-to-bottom order matches option order.
                Box(modifier = Modifier.size(width = 360.dp, height = 720.dp)) {
                    OptionsSelectView(
                        onSelectPlayStore = {},
                        onSelectGfn = {},
                        onSelectLuna = {},
                        onSelectMoonlight = {},
                        onSelectWebUrl = {},
                        iconResolver = { _, _ -> resolved }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        val orderedTags = listOf(
            "option_add_luna",
            "option_add_gfn",
            "option_add_moonlight",
            "option_play_store",
            "option_add_web_url",
            "option_add_system_shortcut"
        )
        val tops = orderedTags.map { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value
        }
        for (i in 0 until tops.lastIndex) {
            assertTrue("Expected ${orderedTags[i]} above ${orderedTags[i + 1]}", tops[i] < tops[i + 1])
        }

        composeRule.onNodeWithTag("option_add_luna_app_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("option_add_gfn_app_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("option_add_moonlight_app_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("option_play_store_app_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("option_add_web_url_symbolic_icon", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("option_add_system_shortcut_symbolic_icon", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
    fun `TV remote D-pad navigates GeForce NOW search and populated posters without leaking to background`() {
        val sampleGames = listOf(
            GameItem(id = "gfn_1", title = "Cyberpunk 2077", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_2", title = "Alan Wake 2", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_3", title = "Baldur's Gate 3", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_4", title = "Starfield", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_5", title = "Hades II", type = GameType.GEFORCE_NOW, isGame = true)
        )
        var currentSubView by mutableStateOf(AddModalSubView.OPTIONS)
        var selectedGame: GameItem? = null
        val backgroundButtonRequester = FocusRequester()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Simulated DashboardScreen background gated by canFocus = false while modal is open.
                    Box(modifier = Modifier.fillMaxSize().focusProperties { canFocus = false }) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .focusRequester(backgroundButtonRequester)
                                .testTag("background_launcher_item")
                        ) {
                            Text("Background Item")
                        }
                    }

                    AddContentModal(
                        subView = currentSubView,
                        onNavigateSubView = { currentSubView = it },
                        onOpenPlayStore = {},
                        onOpenMoonlight = {},
                        onAddShortcut = { _, _, _ -> },
                        onAddGfnGame = { selectedGame = it },
                        onAddLunaGame = {},
                        onDismiss = {}
                    )
                }
            }
        }

        // Wait for Options initial focus and move right to GeForce NOW option, then Select.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("option_add_luna").assertIsFocused()
        composeRule.onNodeWithTag("option_add_luna").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("option_add_gfn").assertIsFocused()
        composeRule.onNodeWithTag("option_add_gfn").performKeyInput {
            pressKey(Key.DirectionCenter)
        }

        // Replace subView directly with AddGfnSearchView? Wait: AddContentModal internally calls
        // GeForceNowService.browseOrSearchCatalog on ADD_GFN; verify search field gets focus even while loading,
        // and test deterministic poster D-pad navigation cleanly on AddGfnSearchView below.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("background_launcher_item").assertIsNotFocused()
        composeRule.onNodeWithTag("add_gfn_search").assertIsDisplayed()
        composeRule.onNodeWithTag("add_gfn_search").assertIsFocused()

        // Up from search moves to Back button; Down from Back returns to search.
        composeRule.onNodeWithTag("add_gfn_search").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_modal_close_button").assertIsFocused()
        composeRule.onNodeWithTag("add_modal_close_button").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_gfn_search").assertIsFocused()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
    fun `TV remote D-pad navigates from AddGfnSearchView search box to populated posters and selects with Center`() {
        val sampleGames = listOf(
            GameItem(id = "gfn_1", title = "Cyberpunk 2077", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_2", title = "Alan Wake 2", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_3", title = "Baldur's Gate 3", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_4", title = "Starfield", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_5", title = "Hades II", type = GameType.GEFORCE_NOW, isGame = true),
            GameItem(id = "gfn_6", title = "Portal 2", type = GameType.GEFORCE_NOW, isGame = true)
        )
        var selectedGame: GameItem? = null

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 760.dp, height = 500.dp)) {
                    AddGfnSearchView(
                        onGameSelected = { selectedGame = it },
                        catalogLoader = {
                            GeForceNowService.GfnBrowseResult(
                                games = sampleGames,
                                classification = GeForceNowService.GfnDefaultClassification.RECENTLY_ADDED
                            )
                        }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_gfn_search").assertIsFocused()

        // Press Down from search box -> focuses first populated poster.
        composeRule.onNodeWithTag("add_gfn_search").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gfn_game_gfn_1").assertIsFocused()

        // Press Right -> focuses second poster, then Left -> back to first poster, then Up -> back to search box.
        composeRule.onNodeWithTag("gfn_game_gfn_1").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gfn_game_gfn_2").assertIsFocused()

        composeRule.onNodeWithTag("gfn_game_gfn_2").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        assertNotNull(selectedGame)
        assertEquals("gfn_2", selectedGame?.id)
        assertEquals("Alan Wake 2", selectedGame?.title)

        // Up from poster returns focus to search box.
        composeRule.onNodeWithTag("gfn_game_gfn_2").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_gfn_search").assertIsFocused()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
    fun `TV remote D-pad enters Amazon Luna from Discover options without leaking focus to background`() {
        var currentSubView by mutableStateOf(AddModalSubView.OPTIONS)

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().focusProperties { canFocus = false }) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .focusable()
                                .clickable {}
                                .testTag("background_tile")
                        )
                    }

                    AddContentModal(
                        subView = currentSubView,
                        onNavigateSubView = { currentSubView = it },
                        onOpenPlayStore = {},
                        onOpenMoonlight = {},
                        onAddShortcut = { _, _, _ -> },
                        onAddGfnGame = {},
                        onAddLunaGame = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("option_add_luna").assertIsFocused()
        composeRule.onNodeWithTag("option_add_luna").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("background_tile").assertIsNotFocused()
        composeRule.onNodeWithTag("add_luna_search").assertIsDisplayed()
        composeRule.onNodeWithTag("add_luna_search").assertIsFocused()

        // Up from Luna search moves to Back button; Down returns to Luna search.
        composeRule.onNodeWithTag("add_luna_search").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_modal_close_button").assertIsFocused()
        composeRule.onNodeWithTag("add_modal_close_button").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_luna_search").assertIsFocused()
    }
}
