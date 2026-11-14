package com.example.data.service

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IconPackOverridesStoreTest {

    private lateinit var context: Context
    private lateinit var store: IconPackOverridesStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_icon_pack_store", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        store = IconPackOverridesStore(prefs)
    }

    @Test
    fun `switching to default icons preserves per-pack custom icon IDs`() {
        store.selectedIconPack = "com.pack.alpha"
        store.setOverride("com.pack.alpha", "app_com.spotify.music", "spotify_alt")
        store.setOverride("com.pack.beta", "app_com.spotify.music", "music_circle")

        // User switches back to Default icons.
        store.selectedIconPack = null
        assertNull(store.selectedIconPack)

        // Switching back to Pack Alpha or Pack Beta still remembers their custom icon IDs.
        assertEquals(
            mapOf("app_com.spotify.music" to "spotify_alt"),
            store.getOverridesForPack("com.pack.alpha")
        )
        assertEquals(
            mapOf("app_com.spotify.music" to "music_circle"),
            store.getOverridesForPack("com.pack.beta")
        )
    }

    @Test
    fun `default theme maintains its own memory of customized app icons across theme switches`() {
        val defaultPhoto = File(context.filesDir, "custom_art_default_spotify.img").apply {
            writeBytes(byteArrayOf(10, 20, 30))
        }
        val packAlphaPhoto = File(context.filesDir, "custom_art_alpha_slack.img").apply {
            writeBytes(byteArrayOf(40, 50, 60))
        }
        val defaultPhotoUrl = Uri.fromFile(defaultPhoto).toString()
        val packAlphaPhotoUrl = Uri.fromFile(packAlphaPhoto).toString()

        // User customizes Spotify and Chrome in Default theme.
        store.selectedIconPack = null
        store.setCustomUrlOverride(null, "app_com.spotify.music", defaultPhotoUrl)
        store.setCustomUrlOverride(null, "app_com.android.chrome", "https://example.com/chrome.png")

        // User switches to Pack Alpha and customizes Spotify (pack drawable) and Slack (photo).
        store.selectedIconPack = "com.pack.alpha"
        store.setOverride("com.pack.alpha", "app_com.spotify.music", "spotify_dark")
        store.setCustomUrlOverride("com.pack.alpha", "app_com.Slack", packAlphaPhotoUrl)

        // Switching back to Default theme remembers Default theme's customized icons independently.
        store.selectedIconPack = null
        assertEquals(
            mapOf(
                "app_com.spotify.music" to defaultPhotoUrl,
                "app_com.android.chrome" to "https://example.com/chrome.png"
            ),
            store.getCustomUrlOverrides(null)
        )

        // Pack Alpha also keeps its own drawable + custom URL overrides intact.
        assertEquals(
            mapOf("app_com.spotify.music" to "spotify_dark"),
            store.getOverridesForPack("com.pack.alpha")
        )
        assertEquals(
            mapOf("app_com.Slack" to packAlphaPhotoUrl),
            store.getCustomUrlOverrides("com.pack.alpha")
        )
    }

    @Test
    fun `resetting an icon in one theme does not disturb another theme memory`() {
        store.setCustomUrlOverride(null, "app_com.spotify.music", "https://example.com/default.png")
        store.setCustomUrlOverride("com.pack.alpha", "app_com.spotify.music", "https://example.com/alpha.png")

        // Reset in Default theme.
        store.removeCustomUrlOverride(null, "app_com.spotify.music")

        assertNull(store.getCustomUrlOverride(null, "app_com.spotify.music"))
        assertEquals(
            "https://example.com/alpha.png",
            store.getCustomUrlOverride("com.pack.alpha", "app_com.spotify.music")
        )

        // Picking a pack drawable in Pack Alpha replaces Pack Alpha's custom URL only.
        store.setOverride("com.pack.alpha", "app_com.spotify.music", "spotify_round")
        assertNull(store.getCustomUrlOverride("com.pack.alpha", "app_com.spotify.music"))
        assertEquals("spotify_round", store.getOverride("com.pack.alpha", "app_com.spotify.music"))
    }

    @Test
    fun `removing an override clears only that item in that pack`() {
        store.setOverride("com.pack.alpha", "app_one", "icon_one")
        store.setOverride("com.pack.alpha", "app_two", "icon_two")
        store.setOverride("com.pack.beta", "app_one", "beta_one")

        store.removeOverride("com.pack.alpha", "app_one")

        assertNull(store.getOverride("com.pack.alpha", "app_one"))
        assertEquals("icon_two", store.getOverride("com.pack.alpha", "app_two"))
        assertEquals("beta_one", store.getOverride("com.pack.beta", "app_one"))
    }

    @Test
    fun `cached icon map hydrates existing files synchronously for active theme only`() {
        val cachedFile = File(context.cacheDir, "themed_spotify.png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val missingFile = File(context.cacheDir, "deleted_icon.png")
        val cachedUrl = Uri.fromFile(cachedFile).toString()
        val missingUrl = Uri.fromFile(missingFile).toString()

        store.saveCachedIconMap(
            "com.pack.alpha",
            mapOf(
                "com.spotify.music" to cachedUrl,
                "com.missing.app" to missingUrl
            )
        )

        assertEquals(
            mapOf("com.spotify.music" to cachedUrl),
            store.loadCachedIconMap("com.pack.alpha")
        )
        assertEquals(emptyMap<String, String>(), store.loadCachedIconMap("com.pack.beta"))
        assertEquals(emptyMap<String, String>(), store.loadCachedIconMap(null))

        // Caching for Default theme (null) hydrates Default theme synchronously.
        store.saveCachedIconMap(null, mapOf("com.spotify.music" to cachedUrl))
        assertEquals(
            mapOf("com.spotify.music" to cachedUrl),
            store.loadCachedIconMap(null)
        )
        assertEquals(emptyMap<String, String>(), store.loadCachedIconMap("com.pack.alpha"))
    }

    @Test
    fun `shortcut base icon backup preserves original shortcuts url`() {
        val shortcutFile = File(context.filesDir, "shortcuts/com.app_1.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(7, 8, 9))
        }
        val shortcutUrl = Uri.fromFile(shortcutFile).toString()

        store.saveBaseIconUrlIfUnset("pinned_com.app_1", shortcutUrl)
        // Subsequent saves do not overwrite the original shortcut icon URL.
        store.saveBaseIconUrlIfUnset("pinned_com.app_1", "file:///custom_art/override.img")

        assertEquals(shortcutUrl, store.getBaseIconUrl("pinned_com.app_1"))
    }
}
