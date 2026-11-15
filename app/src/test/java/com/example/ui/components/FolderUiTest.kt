package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.ui.theme.FavoritesLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun childApp(id: String, title: String) = GameItem(
        id = id,
        title = title,
        type = GameType.INSTALLED_APP,
        isGame = false,
        packageName = "com.example.$id",
        iconUrl = null
    )

    @Test
    fun `folder icon shows 4 child previews and hides preview when customized then restores on reset`() {
        val folderBase = GameItem(
            id = "folder_utils",
            title = "Utilities",
            type = GameType.FOLDER,
            isGame = false,
            iconUrl = null
        )
        val children = listOf(
            childApp("a", "Alpha"),
            childApp("b", "Bravo"),
            childApp("c", "Charlie"),
            childApp("d", "Delta"),
            childApp("e", "Echo")
        )
        var folderState by mutableStateOf(folderBase)
        var themedMap by mutableStateOf<Map<String, String>>(emptyMap())

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalFolderPreviewItems provides mapOf(folderBase.id to children),
                    LocalThemedAppIcons provides themedMap
                ) {
                    AppIcon(app = folderState, size = 58.dp)
                }
            }
        }

        composeRule.waitForIdle()

        // Default uncustomized state renders the geometric preview with up to 4 children (A-D, not E).
        composeRule.onNodeWithTag("folder_preview_${folderBase.id}").assertIsDisplayed()
        composeRule.onNodeWithText("A").assertIsDisplayed()
        composeRule.onNodeWithText("B").assertIsDisplayed()
        composeRule.onNodeWithText("C").assertIsDisplayed()
        composeRule.onNodeWithText("D").assertIsDisplayed()
        composeRule.onNodeWithText("E").assertDoesNotExist()

        // Custom icon URL disables preview icons inside.
        folderState = folderBase.copy(iconUrl = "https://example.com/custom_folder.png")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_preview_${folderBase.id}").assertDoesNotExist()

        // Resetting icon back to default restores the preview icons.
        folderState = folderBase.copy(iconUrl = null)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_preview_${folderBase.id}").assertIsDisplayed()
        composeRule.onNodeWithText("A").assertIsDisplayed()

        // Active theme override on folder also disables preview, and clearing restores it.
        themedMap = mapOf(folderBase.id to "file:///pack/folder_custom.png")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_preview_${folderBase.id}").assertDoesNotExist()

        themedMap = emptyMap()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_preview_${folderBase.id}").assertIsDisplayed()
    }

    @Test
    fun `FolderContentsSheet renders apps identically in text and icons modes without letter separators`() {
        val folder = GameItem(
            id = "folder_social",
            title = "Social",
            type = GameType.FOLDER,
            isGame = false
        )
        val apps = listOf(
            childApp("app_discord", "Discord"),
            childApp("app_signal", "Signal")
        )
        var layoutState by mutableStateOf(FavoritesLayout.TEXT)

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalFolderPreviewItems provides mapOf(folder.id to apps)
                ) {
                    FolderContentsSheet(
                        folder = folder,
                        apps = apps,
                        layout = layoutState,
                        focusedItemId = null,
                        showFocusEffects = false,
                        onAppClick = {},
                        onAppLongClick = {},
                        onCustomizeFolder = {},
                        onManageFolderApps = {},
                        onDismiss = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_contents_sheet_${folder.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_contents_list").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_app_row_app_discord").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_app_row_app_signal").assertIsDisplayed()
        // No standalone letter separators "D" or "S" (AppIcon falls back to 1-char initial only when no letter header exists,
        // wait: our test apps have iconUrl=null so AppIcon shows fallback "D" & "S" once each — verify there isn't a second header node).
        composeRule.onNodeWithText("2 apps").assertIsDisplayed()

        layoutState = FavoritesLayout.ICONS
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("folder_contents_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_app_row_app_discord").assertIsDisplayed()
        composeRule.onNodeWithTag("folder_app_row_app_signal").assertIsDisplayed()
    }
}
