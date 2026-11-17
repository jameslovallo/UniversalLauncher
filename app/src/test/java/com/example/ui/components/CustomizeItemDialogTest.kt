package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.IconPackIcon
import com.example.data.service.IconPackInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class CustomizeItemDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `landscape wide sheet places app name reset button and tabs in left column and content in right column`() {
        val app = GameItem(
            id = "app_test",
            title = "Spotify",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.spotify.music"
        )
        val pack = IconPackInfo(
            packageName = "com.example.pack",
            label = "Minimal Pack"
        )

        composeRule.setContent {
            MaterialTheme {
                CustomizeItemDialog(
                    item = app,
                    steamGridDbApiKey = null,
                    iconPack = pack,
                    iconPackIcons = listOf(IconPackIcon("ic_spotify", "Spotify")),
                    canResetIcon = true,
                    onApplyTitle = { _, _ -> },
                    onApplyPoster = { _, _, _, _ -> },
                    onSetOverlayLogo = { _, _, _, _ -> },
                    onUpdateLogoUrl = { _, _, _ -> },
                    onSelectIconPackIcon = { _, _, _ -> },
                    onResetIcon = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("customize_two_column_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("customize_single_column_layout").assertDoesNotExist()

        val leftBounds = composeRule.onNodeWithTag("customize_left_column")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val rightBounds = composeRule.onNodeWithTag("customize_right_column")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected left column (${leftBounds.right}) to sit left of right column (${rightBounds.left})",
            leftBounds.right <= rightBounds.left
        )

        val nameBounds = composeRule.onNodeWithTag("customize_name_input")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val resetBounds = composeRule.onNodeWithTag("customize_reset_icon_button")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val localPhotoTabBounds = composeRule.onNodeWithTag("customize_tab_local_photo")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val customUrlTabBounds = composeRule.onNodeWithTag("customize_tab_custom_url")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue(nameBounds.right <= leftBounds.right + androidx.compose.ui.unit.Dp(1f))
        assertTrue(resetBounds.right <= leftBounds.right + androidx.compose.ui.unit.Dp(1f))
        assertTrue(localPhotoTabBounds.right <= leftBounds.right + androidx.compose.ui.unit.Dp(1f))
        assertTrue(customUrlTabBounds.right <= leftBounds.right + androidx.compose.ui.unit.Dp(1f))

        // Switch to Custom URL tab and verify its content renders in the right column.
        composeRule.onNodeWithTag("customize_tab_custom_url").performClick()
        composeRule.waitForIdle()
        val urlInputBounds = composeRule.onNodeWithTag("customize_custom_url_input")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("customize_apply_url_button").assertIsDisplayed()
        assertTrue(
            "Expected custom URL input (${urlInputBounds.left}) inside right column (${rightBounds.left})",
            urlInputBounds.left >= rightBounds.left - androidx.compose.ui.unit.Dp(1f)
        )
    }

    @Test
    fun `landscape wide sheet renders game overlay toggle and tabs in left column`() {
        val game = GameItem(
            id = "game_hades",
            title = "Hades",
            type = GameType.GEFORCE_NOW,
            isGame = true
        )

        composeRule.setContent {
            MaterialTheme {
                CustomizeItemDialog(
                    item = game,
                    steamGridDbApiKey = null,
                    onApplyTitle = { _, _ -> },
                    onApplyPoster = { _, _, _, _ -> },
                    onSetOverlayLogo = { _, _, _, _ -> },
                    onUpdateLogoUrl = { _, _, _ -> },
                    onSelectIconPackIcon = { _, _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("customize_two_column_layout").assertIsDisplayed()
        val leftBounds = composeRule.onNodeWithTag("customize_left_column")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val overlayBounds = composeRule.onNodeWithTag("customize_overlay_logo_row")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(overlayBounds.right <= leftBounds.right + androidx.compose.ui.unit.Dp(1f))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h915dp-port-xhdpi")
    fun `portrait narrow sheet keeps stacked single column layout`() {
        val app = GameItem(
            id = "app_test",
            title = "Spotify",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.spotify.music"
        )

        composeRule.setContent {
            MaterialTheme {
                CustomizeItemDialog(
                    item = app,
                    steamGridDbApiKey = null,
                    canResetIcon = false,
                    onApplyTitle = { _, _ -> },
                    onApplyPoster = { _, _, _, _ -> },
                    onSetOverlayLogo = { _, _, _, _ -> },
                    onUpdateLogoUrl = { _, _, _ -> },
                    onSelectIconPackIcon = { _, _, _ -> },
                    onDismiss = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("customize_single_column_layout").assertIsDisplayed()
        composeRule.onNodeWithTag("customize_two_column_layout").assertDoesNotExist()
    }
}
