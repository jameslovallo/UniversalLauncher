package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.ui.components.PosterLogoOverlay
import com.example.ui.components.SteamGamePosterCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NexusApplicationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun `application builds shared ImageLoader with memory and disk caches`() {
        val context = ApplicationProvider.getApplicationContext<NexusApplication>()
        val loader = context.newImageLoader()

        assertNotNull(loader.memoryCache)
        val diskCache = loader.diskCache
        assertNotNull(diskCache)
        assertEquals(
            File(context.cacheDir, NexusApplication.DISK_CACHE_DIR).absolutePath,
            diskCache!!.directory.toFile().absolutePath
        )
        assertEquals(NexusApplication.DISK_CACHE_MAX_BYTES, diskCache.maxSize)
        assertNotNull(Coil.imageLoader(context))
    }

    @Test
    fun `SteamGamePosterCard and PosterLogoOverlay render cleanly with explicit dimensions`() {
        val game = GameItem(
            id = "steam_1",
            title = "Hades",
            type = GameType.MOONLIGHT,
            isGame = true,
            posterUrl = "https://example.invalid/poster.jpg",
            logoUrl = "https://example.invalid/logo.png",
            overlayLogo = true
        )

        composeRule.setContent {
            Box(modifier = Modifier.size(180.dp, 270.dp)) {
                SteamGamePosterCard(
                    game = game,
                    isFocused = false,
                    onClick = {},
                    onLongClick = {},
                    posterWidthPx = 360,
                    posterHeightPx = 540
                )
            }
        }

        composeRule.onNodeWithTag("game_poster_steam_1").assertIsDisplayed()
    }

    @Test
    fun `PosterLogoOverlay lays out without subcomposition`() {
        composeRule.setContent {
            Box(modifier = Modifier.size(200.dp, 300.dp)) {
                PosterLogoOverlay(
                    logoUrl = "https://example.invalid/logo.png",
                    posterWidthPx = 400,
                    posterHeightPx = 600
                )
            }
        }

        composeRule.waitForIdle()
    }
}
