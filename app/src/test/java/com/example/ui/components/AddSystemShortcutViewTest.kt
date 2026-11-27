package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.example.data.service.SystemShortcutCatalog
import com.example.data.service.SystemShortcutEntry
import com.example.data.service.SystemShortcutKind
import com.example.data.service.SystemShortcutService
import com.example.ui.viewmodel.AddModalSubView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
class AddSystemShortcutViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun testCatalog(): SystemShortcutCatalog {
        val entries = listOf(
            SystemShortcutEntry(
                id = "launcher:com.android.chrome:incognito",
                title = "New Incognito Tab",
                subtitle = "Open a private tab",
                packageName = "com.android.chrome",
                appLabel = "Chrome",
                kind = SystemShortcutKind.LAUNCHER_SHORTCUT,
                shortcutId = "incognito"
            ),
            SystemShortcutEntry(
                id = "launcher:com.android.chrome:new_tab",
                title = "New Tab",
                packageName = "com.android.chrome",
                appLabel = "Chrome",
                kind = SystemShortcutKind.LAUNCHER_SHORTCUT,
                shortcutId = "new_tab"
            ),
            SystemShortcutEntry(
                id = "launcher:com.android.settings:wifi",
                title = "Wi-Fi",
                packageName = "com.android.settings",
                appLabel = "Settings",
                kind = SystemShortcutKind.LAUNCHER_SHORTCUT,
                shortcutId = "wifi"
            )
        )
        return SystemShortcutService.buildCatalogFromEntries(entries, hasShortcutHostPermission = true)
    }

    @Test
    fun `browse by app drills into app shortcuts and returns via All Apps`() {
        var selectedAppPkg by mutableStateOf<String?>(null)
        var addedShortcut: SystemShortcutEntry? = null
        val catalog = testCatalog()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 700.dp)) {
                    AddSystemShortcutView(
                        selectedAppPackage = selectedAppPkg,
                        onSelectAppPackage = { selectedAppPkg = it },
                        onAddLauncherShortcut = { addedShortcut = it },
                        onCreateShortcutResult = { _, _, _ -> },
                        catalogLoader = { catalog },
                        iconResolver = { _, _ -> null }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("system_shortcut_apps_list").assertIsDisplayed()
        composeRule.onNodeWithTag("shortcut_app_com.android.chrome").assertIsDisplayed()
        composeRule.onNodeWithTag("shortcut_app_com.android.settings").assertIsDisplayed()

        composeRule.onNodeWithTag("shortcut_app_com.android.chrome").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("system_shortcut_all_apps_button").assertIsDisplayed()
        composeRule.onNodeWithTag("system_shortcut_app_items_list").assertIsDisplayed()
        composeRule.onNodeWithText("New Incognito Tab").assertIsDisplayed()
        composeRule.onNodeWithText("New Tab").assertIsDisplayed()

        composeRule.onNodeWithTag("system_shortcut_all_apps_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("system_shortcut_apps_list").assertIsDisplayed()

        composeRule.onNodeWithTag("shortcut_app_com.android.settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("system_shortcut_item_launcher:com.android.settings:wifi").performClick()
        composeRule.waitForIdle()

        assertNotNull(addedShortcut)
        assertEquals("Wi-Fi", addedShortcut?.title)
    }

    @Test
    fun `search bar filters shortcuts across apps and allows selection`() {
        var addedShortcut: SystemShortcutEntry? = null
        val catalog = testCatalog()

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 700.dp)) {
                    AddSystemShortcutView(
                        selectedAppPackage = null,
                        onSelectAppPackage = {},
                        onAddLauncherShortcut = { addedShortcut = it },
                        onCreateShortcutResult = { _, _, _ -> },
                        catalogLoader = { catalog },
                        iconResolver = { _, _ -> null }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_system_shortcut_search").performTextInput("Incognito")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("system_shortcut_results_list").assertIsDisplayed()
        composeRule.onNodeWithTag("system_shortcut_result_launcher:com.android.chrome:incognito")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertNotNull(addedShortcut)
        assertEquals("New Incognito Tab", addedShortcut?.title)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
    fun `clicking System Shortcut option in Discover opens AddSystemShortcutView and focuses search`() {
        var currentSubView by mutableStateOf(AddModalSubView.OPTIONS)

        composeRule.setContent {
            MaterialTheme {
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

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("option_add_system_shortcut").performClick()
        composeRule.waitForIdle()

        assertEquals(AddModalSubView.ADD_SYSTEM_SHORTCUT, currentSubView)
        composeRule.onNodeWithText("Add System Shortcut").assertIsDisplayed()
        composeRule.onNodeWithTag("add_system_shortcut_search").assertIsDisplayed()
        composeRule.onNodeWithTag("add_system_shortcut_search").assertIsFocused()

        composeRule.onNodeWithTag("add_system_shortcut_search").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_modal_close_button").assertIsFocused()
    }
}
