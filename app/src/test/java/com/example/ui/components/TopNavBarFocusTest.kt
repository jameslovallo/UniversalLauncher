package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.viewmodel.MainLibraryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dashboard uses this callback to hide the library views' "last focused" highlight while
 * the top navigation owns focus, so the signal has to cover the whole bar, not just one tab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi")
class TopNavBarFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `reports focus entering and leaving the navigation bar`() {
        val navFirst = FocusRequester()
        val contentRequester = FocusRequester()
        val focusStates = mutableListOf<Boolean>()

        composeRule.setContent {
            MaterialTheme {
                Column {
                    TopNavBar(
                        currentTab = MainLibraryTab.APPS,
                        showFocusEffects = true,
                        onTabSelected = {},
                        onOpenAddModal = {},
                        onOpenSettings = {},
                        firstItemFocusRequester = navFirst,
                        onNavFocusChanged = { focusStates += it }
                    )
                    Button(
                        onClick = {},
                        modifier = Modifier.focusRequester(contentRequester).testTag("content_button")
                    ) {
                        Text("Content")
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { navFirst.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("topbar_add_button").assertIsFocused()
        assertTrue("The bar must report focus when one of its controls is focused", focusStates.isNotEmpty())
        assertEquals(true, focusStates.last())

        composeRule.runOnIdle { contentRequester.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("content_button").assertIsFocused()
        assertEquals(false, focusStates.last())
    }
}
