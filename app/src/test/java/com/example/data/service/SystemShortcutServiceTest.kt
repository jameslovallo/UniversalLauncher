package com.example.data.service

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GameType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemShortcutServiceTest {

    private fun sampleCatalog(): SystemShortcutCatalog {
        val entries = listOf(
            SystemShortcutEntry(
                id = "launcher:com.android.chrome:incognito",
                title = "New Incognito Tab",
                subtitle = "Open a private tab",
                packageName = "com.android.chrome",
                appLabel = "Chrome",
                kind = SystemShortcutKind.LAUNCHER_SHORTCUT,
                shortcutId = "incognito",
                launchIntentUri = "intent:#Intent;package=com.android.chrome;end"
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
                id = "create:com.android.settings:SettingsShortcut",
                title = "Settings",
                subtitle = "Configure shortcut",
                packageName = "com.android.settings",
                appLabel = "Settings",
                kind = SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY,
                activityName = "SettingsShortcut"
            )
        )
        return SystemShortcutService.buildCatalogFromEntries(entries, hasShortcutHostPermission = true)
    }

    @Test
    fun `buildCatalogFromEntries groups and sorts shortcuts by app`() {
        val catalog = sampleCatalog()

        assertEquals(2, catalog.appGroups.size)
        assertEquals("Chrome", catalog.appGroups[0].appLabel)
        assertEquals(2, catalog.appGroups[0].shortcuts.size)
        assertEquals(listOf("New Incognito Tab", "New Tab"), catalog.appGroups[0].shortcuts.map { it.title })
        assertEquals("Settings", catalog.appGroups[1].appLabel)
        assertEquals(1, catalog.appGroups[1].shortcuts.size)
    }

    @Test
    fun `filterShortcuts matches shortcut title and app name and ranks title matches first`() {
        val catalog = sampleCatalog()

        val incognitoMatches = SystemShortcutService.filterShortcuts(catalog, "incognito")
        assertEquals(listOf("New Incognito Tab"), incognitoMatches.map { it.title })

        val chromeMatches = SystemShortcutService.filterShortcuts(catalog, "chrome")
        assertEquals(2, chromeMatches.size)
        assertTrue(chromeMatches.all { it.appLabel == "Chrome" })

        val settingsMatches = SystemShortcutService.filterShortcuts(catalog, "settings")
        assertEquals(listOf("Settings"), settingsMatches.map { it.title })
    }

    @Test
    fun `createGameItemFromLauncherShortcut preserves non-pinned sys_shortcut id and shortcut metadata`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val entry = SystemShortcutEntry(
            id = "launcher:com.android.chrome:incognito",
            title = "New Incognito Tab",
            packageName = "com.android.chrome",
            appLabel = "Chrome",
            appIconUrl = "file:///tmp/chrome.png",
            kind = SystemShortcutKind.LAUNCHER_SHORTCUT,
            shortcutId = "incognito",
            launchIntentUri = "intent:#Intent;package=com.android.chrome;end",
            isGameApp = false
        )

        val item = SystemShortcutService.createGameItemFromLauncherShortcut(app, entry)

        assertTrue(item.id.startsWith(SystemShortcutService.SYSTEM_SHORTCUT_ID_PREFIX))
        assertFalse(item.id.startsWith(PinnedShortcutImporter.ID_PREFIX))
        assertFalse(item.isPinned)
        assertFalse(item.isGame)
        assertEquals(GameType.SHORTCUT, item.type)
        assertEquals("com.android.chrome", item.packageName)
        assertEquals("incognito", item.activityName)
        assertEquals("New Incognito Tab", item.title)
        assertEquals("file:///tmp/chrome.png", item.iconUrl)
    }

    @Test
    fun `createGameItemFromCreateShortcutResult parses intent name and bitmap icon`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val provider = SystemShortcutEntry(
            id = "create:com.android.settings:SettingsShortcut",
            title = "Settings",
            packageName = "com.android.settings",
            appLabel = "Settings",
            kind = SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY,
            activityName = "SettingsShortcut"
        )
        val target = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            `package` = "com.android.settings"
        }
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        @Suppress("DEPRECATION")
        val resultData = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, target)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, "Wi-Fi")
            putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
        }

        val item = SystemShortcutService.createGameItemFromCreateShortcutResult(app, provider, resultData)

        assertNotNull(item)
        assertEquals("Wi-Fi", item!!.title)
        assertEquals(GameType.SHORTCUT, item.type)
        assertFalse(item.isGame)
        assertFalse(item.isPinned)
        assertEquals("com.android.settings", item.packageName)
        assertNull(item.activityName)
        assertTrue(item.id.startsWith(SystemShortcutService.CREATE_SHORTCUT_ID_PREFIX))
        assertTrue(item.launchUrl?.contains(Settings.ACTION_WIFI_SETTINGS) == true)
        assertTrue(item.iconUrl?.startsWith("file:") == true)
        assertNotNull(Uri.parse(item.iconUrl))
    }

    @Test
    fun `createGameItemFromCreateShortcutResult returns null when target intent is missing`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val provider = SystemShortcutEntry(
            id = "create:com.android.settings:SettingsShortcut",
            title = "Settings",
            packageName = "com.android.settings",
            appLabel = "Settings",
            kind = SystemShortcutKind.CREATE_SHORTCUT_ACTIVITY,
            activityName = "SettingsShortcut"
        )

        val item = SystemShortcutService.createGameItemFromCreateShortcutResult(app, provider, Intent())
        assertNull(item)
    }
}
