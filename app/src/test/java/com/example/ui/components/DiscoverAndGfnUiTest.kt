package com.example.ui.components

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.GeForceNowService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

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
            "option_add_web_url"
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
    }
}
