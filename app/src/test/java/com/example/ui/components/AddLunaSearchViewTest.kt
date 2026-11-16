package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.AmazonLunaService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddLunaSearchViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun primeGame(id: String, title: String) = GameItem(
        id = "luna_$id",
        title = title,
        type = GameType.AMAZON_LUNA,
        isGame = true,
        launchUrl = AmazonLunaService.createLunaLaunchIntentUri(null),
        genre = "Included with Prime"
    )

    @Test
    fun `pre-search lists Prime games below search bar and search selects catalog game`() {
        val primeGames = listOf(
            primeGame("fortnite", "Fortnite"),
            primeGame("fallout_nv", "Fallout New Vegas")
        )
        val fullCatalog = primeGames + listOf(
            primeGame("control", "Control Ultimate Edition").copy(genre = "Amazon Luna")
        )
        var selectedGame: GameItem? = null

        composeRule.setContent {
            MaterialTheme {
                AddLunaSearchView(
                    onGameSelected = { selectedGame = it },
                    catalogLoader = { query ->
                        val trimmed = query.trim()
                        if (trimmed.isBlank()) {
                            Result.success(primeGames)
                        } else {
                            Result.success(
                                fullCatalog.filter {
                                    it.title.contains(trimmed, ignoreCase = true)
                                }
                            )
                        }
                    },
                    posterResolver = { null }
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("add_luna_search").assertIsDisplayed()
        composeRule.onNodeWithTag("luna_prime_header").assertIsDisplayed()
        composeRule.onNodeWithText("Free with Amazon Prime (2)").assertIsDisplayed()
        composeRule.onNodeWithText("Fortnite").assertIsDisplayed()
        composeRule.onNodeWithText("Fallout New Vegas").assertIsDisplayed()
        composeRule.onNodeWithText("LUNA").assertDoesNotExist()

        // Search filters from full catalog
        composeRule.onNodeWithTag("add_luna_search").performTextInput("Control")
        composeRule.mainClock.advanceTimeBy(450)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("luna_prime_header").assertDoesNotExist()
        composeRule.onNodeWithText("Control Ultimate Edition").assertIsDisplayed()
        composeRule.onNodeWithText("LUNA").assertDoesNotExist()
        composeRule.onNodeWithTag("luna_game_luna_control").performClick()
        composeRule.waitForIdle()

        assertNotNull(selectedGame)
        assertEquals("Control Ultimate Edition", selectedGame?.title)
        assertEquals(GameType.AMAZON_LUNA, selectedGame?.type)
        assertEquals(
            "intent://luna.amazon.com/#Intent;scheme=https;package=com.amazon.spiderpork;S.browser_fallback_url=https://luna.amazon.com;end;",
            selectedGame?.launchUrl
        )
    }

    @Test
    fun `shows retry button when catalog loading fails and recovers on click`() {
        var attempts = 0
        val recoveredGames = listOf(primeGame("fortnite", "Fortnite"))

        composeRule.setContent {
            MaterialTheme {
                AddLunaSearchView(
                    onGameSelected = {},
                    catalogLoader = {
                        attempts += 1
                        if (attempts == 1) {
                            Result.failure(IllegalStateException("Network unavailable"))
                        } else {
                            Result.success(recoveredGames)
                        }
                    },
                    posterResolver = { null }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("luna_retry_button").assertIsDisplayed()
        composeRule.onNodeWithTag("luna_retry_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Free with Amazon Prime (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Fortnite").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
    fun `TV remote D-pad navigates Luna search to retry button on failure then to populated posters`() {
        var attempts = 0
        val recoveredGames = listOf(
            primeGame("fortnite", "Fortnite"),
            primeGame("galactic_racer", "Star Wars: Galactic Racer"),
            primeGame("control", "Control Ultimate Edition")
        )
        var selectedGame: GameItem? = null

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 760.dp, height = 500.dp)) {
                    AddLunaSearchView(
                        onGameSelected = { selectedGame = it },
                        catalogLoader = {
                            attempts += 1
                            if (attempts == 1) {
                                Result.failure(IllegalStateException("Network unavailable"))
                            } else {
                                Result.success(recoveredGames)
                            }
                        },
                        posterResolver = { null }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_luna_search").assertIsFocused()

        // Press Down on error state -> focuses Retry button -> Select reloads catalog.
        composeRule.onNodeWithTag("add_luna_search").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("luna_retry_button").assertIsFocused()
        composeRule.onNodeWithTag("luna_retry_button").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        // Focus search box again and press Down -> moves into populated Luna poster grid.
        composeRule.onNodeWithTag("luna_retry_button").assertDoesNotExist()
        composeRule.onNodeWithTag("add_luna_search").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("luna_game_luna_fortnite").assertIsFocused()

        // Press Right -> focuses second poster and Select picks it.
        composeRule.onNodeWithTag("luna_game_luna_fortnite").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("luna_game_luna_galactic_racer").assertIsFocused()

        composeRule.onNodeWithTag("luna_game_luna_galactic_racer").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.waitForIdle()

        assertNotNull(selectedGame)
        assertEquals("luna_galactic_racer", selectedGame?.id)
        assertEquals("Star Wars: Galactic Racer", selectedGame?.title)

        // Pressing Up returns focus from poster row back to the Luna search field.
        composeRule.onNodeWithTag("luna_game_luna_galactic_racer").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_luna_search").assertIsFocused()
    }
}
