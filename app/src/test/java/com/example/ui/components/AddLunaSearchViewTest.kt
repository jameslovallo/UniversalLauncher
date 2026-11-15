package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
}
