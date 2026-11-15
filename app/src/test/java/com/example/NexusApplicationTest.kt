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
import coil.size.Precision
import coil.transform.CircleCropTransformation
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.ui.components.AppIconTile
import com.example.ui.components.PosterLogoOverlay
import com.example.ui.components.SteamGamePosterCard
import com.example.ui.components.buildAppIconImageRequest
import com.example.ui.components.buildPosterImageRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    fun `image requests bake circular crop for icons and exact rgb565 sizing for posters`() {
        val context = ApplicationProvider.getApplicationContext<NexusApplication>()
        val iconReq = buildAppIconImageRequest(context, "https://example.invalid/icon.png", 120)
        assertTrue(iconReq.transformations.any { it is CircleCropTransformation })

        val posterReq = buildPosterImageRequest(context, "https://example.invalid/poster.jpg", 300, 450)
        assertEquals(Precision.EXACT, posterReq.precision)
        assertTrue(posterReq.allowRgb565)
    }

    @Test
    fun `SteamGamePosterCard and AppIconTile render cleanly in touch mode`() {
        val game = GameItem(
            id = "steam_1",
            title = "Hades",
            type = GameType.MOONLIGHT,
            isGame = true,
            posterUrl = "https://example.invalid/poster.jpg",
            logoUrl = "https://example.invalid/logo.png",
            overlayLogo = true
        )
        val appItem = GameItem(
            id = "app_demo",
            title = "Demo",
            type = GameType.INSTALLED_APP,
            isGame = false,
            iconUrl = "https://example.invalid/icon.png"
        )

        composeRule.setContent {
            Box(modifier = Modifier.size(180.dp, 270.dp)) {
                SteamGamePosterCard(
                    game = game,
                    isFocused = false,
                    showFocusEffects = false,
                    onClick = {},
                    onLongClick = {},
                    posterWidthPx = 360,
                    posterHeightPx = 540
                )
                AppIconTile(
                    app = appItem,
                    isFocused = false,
                    showFocusEffects = false,
                    testTag = "app_row_app_demo",
                    onClick = {},
                    onLongClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("game_poster_steam_1").assertIsDisplayed()
        composeRule.onNodeWithTag("app_row_app_demo").assertIsDisplayed()
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
