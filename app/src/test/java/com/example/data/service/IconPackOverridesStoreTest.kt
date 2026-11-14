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
    fun `cached icon map hydrates existing files synchronously for active pack only`() {
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
    }
}
