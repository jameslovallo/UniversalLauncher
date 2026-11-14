package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.GameDao
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherViewModelIconThemeMemoryTest {

    private lateinit var app: Application
    private lateinit var dao: GameDao

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("nexus_launcher_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        // Register a mock icon pack package so refreshIconPacks keeps it selected.
        val shadowPm = shadowOf(app.packageManager)
        val packPkg = "com.example.iconpack"
        val activityInfo = ActivityInfo().apply {
            packageName = packPkg
            name = "$packPkg.MainActivity"
            applicationInfo = ApplicationInfo().apply {
                packageName = packPkg
            }
        }
        val resolveInfo = ResolveInfo().apply {
            this.activityInfo = activityInfo
            nonLocalizedLabel = "Example Icon Pack"
        }
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(Intent("org.adw.launcher.THEMES"), resolveInfo)

        val db = AppDatabase.getDatabase(app)
        dao = db.gameDao()
        runBlocking(Dispatchers.IO) {
            db.clearAllTables()
        }
    }

    private fun awaitCondition(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(25L)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `default theme remembers customized app icons independently across icon pack switches`(): Unit = runBlocking {
        val spotify = GameItem(
            id = "app_com.spotify.music",
            title = "Spotify",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.spotify.music",
            iconUrl = "file:///data/user/0/com.example/files/app_icons/com.spotify.music-1.png"
        )
        val chrome = GameItem(
            id = "app_com.android.chrome",
            title = "Chrome",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.android.chrome",
            iconUrl = "file:///data/user/0/com.example/files/app_icons/com.android.chrome-1.png"
        )
        withContext(Dispatchers.IO) {
            dao.insertGame(spotify)
            dao.insertGame(chrome)
        }

        val viewModel = LauncherViewModel(app)
        awaitCondition { viewModel.installedIconPacks.value.any { it.packageName == "com.example.iconpack" } }

        // 1. Customize Spotify in the Default theme.
        viewModel.openEditCustomizer(spotify)
        viewModel.applyItemPoster(
            id = spotify.id,
            newTitle = spotify.title,
            isNsfwSlot = false,
            artworkUrl = "https://example.com/default-spotify.png"
        )
        viewModel.closeEditCustomizer()
        awaitCondition { viewModel.themedAppIcons.value[spotify.id] == "https://example.com/default-spotify.png" }

        assertEquals(
            "https://example.com/default-spotify.png",
            viewModel.themedAppIcons.value[spotify.id]
        )
        assertEquals(
            "https://example.com/default-spotify.png",
            withContext(Dispatchers.IO) { dao.getGameById(spotify.id)?.iconUrl }
        )

        // 2. Switch to a custom icon theme and customize Chrome inside that theme.
        viewModel.setIconPack("com.example.iconpack")
        awaitCondition {
            viewModel.selectedIconPack.value == "com.example.iconpack" &&
                viewModel.themedAppIcons.value[spotify.id] == null
        }

        // Default theme's custom Spotify icon should NOT carry over into the custom pack.
        assertNull(viewModel.themedAppIcons.value[spotify.id])

        val latestChrome = withContext(Dispatchers.IO) { dao.getGameById(chrome.id)!! }
        viewModel.openEditCustomizer(latestChrome)
        viewModel.applyItemPoster(
            id = chrome.id,
            newTitle = chrome.title,
            isNsfwSlot = false,
            artworkUrl = "https://example.com/pack-chrome.png"
        )
        viewModel.closeEditCustomizer()
        awaitCondition { viewModel.themedAppIcons.value[chrome.id] == "https://example.com/pack-chrome.png" }

        assertEquals(
            "https://example.com/pack-chrome.png",
            viewModel.themedAppIcons.value[chrome.id]
        )

        // 3. Switch back to the Default theme: Spotify's Default customization is restored,
        // while Chrome (only customized in the icon pack) does not carry its pack customization.
        viewModel.setIconPack(null)
        awaitCondition {
            viewModel.selectedIconPack.value == null &&
                viewModel.themedAppIcons.value[spotify.id] == "https://example.com/default-spotify.png"
        }

        assertEquals(
            "https://example.com/default-spotify.png",
            viewModel.themedAppIcons.value[spotify.id]
        )
        assertEquals(
            "https://example.com/default-spotify.png",
            withContext(Dispatchers.IO) { dao.getGameById(spotify.id)?.iconUrl }
        )
        assertNull(viewModel.themedAppIcons.value[chrome.id])

        // 4. Switch back to the custom pack: Chrome's pack customization is still remembered.
        viewModel.setIconPack("com.example.iconpack")
        awaitCondition {
            viewModel.selectedIconPack.value == "com.example.iconpack" &&
                viewModel.themedAppIcons.value[chrome.id] == "https://example.com/pack-chrome.png"
        }
        assertEquals(
            "https://example.com/pack-chrome.png",
            viewModel.themedAppIcons.value[chrome.id]
        )

        // 5. Resetting Spotify in the Default theme clears Default memory only.
        viewModel.setIconPack(null)
        awaitCondition {
            viewModel.selectedIconPack.value == null &&
                viewModel.themedAppIcons.value[spotify.id] == "https://example.com/default-spotify.png"
        }
        viewModel.resetItemIcon(spotify.id)
        awaitCondition { viewModel.themedAppIcons.value[spotify.id] == null }

        assertNull(viewModel.themedAppIcons.value[spotify.id])
        viewModel.setIconPack("com.example.iconpack")
        awaitCondition { viewModel.themedAppIcons.value[chrome.id] == "https://example.com/pack-chrome.png" }
        assertEquals(
            "https://example.com/pack-chrome.png",
            viewModel.themedAppIcons.value[chrome.id]
        )
    }
}
