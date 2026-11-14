package com.example.ui.components

import com.example.data.model.GameItem
import com.example.data.model.GameType
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconResolutionTest {

    private fun appItem(
        id: String = "app_com.spotify.music",
        packageName: String? = "com.spotify.music",
        iconUrl: String? = "file:///data/user/0/com.example/files/app_icons/com.spotify.music-1.png",
        isHidden: Boolean = false,
        isGame: Boolean = false
    ) = GameItem(
        id = id,
        title = "Spotify",
        type = GameType.INSTALLED_APP,
        isGame = isGame,
        packageName = packageName,
        iconUrl = iconUrl,
        isHidden = isHidden
    )

    @Test
    fun `applies themed icon to visible and hidden apps across surfaces`() {
        val visibleApp = appItem(isHidden = false)
        val hiddenApp = appItem(
            id = "app_com.hidden.app",
            packageName = "com.hidden.app",
            iconUrl = "file:///data/user/0/com.example/files/app_icons/com.hidden.app-1.png",
            isHidden = true
        )
        val themedIcons = mapOf(
            "com.spotify.music" to "file:///pack/spotify.png",
            "app_com.spotify.music" to "file:///pack/spotify_custom.png",
            "com.hidden.app" to "file:///pack/hidden.png"
        )

        assertEquals(
            "file:///pack/spotify_custom.png",
            resolveAppIconUrl(visibleApp, themedIcons)
        )
        assertEquals(
            "file:///pack/hidden.png",
            resolveAppIconUrl(hiddenApp, themedIcons)
        )
    }

    @Test
    fun `legacy custom_icons file is overridden when switching packs or resetting to default`() {
        val legacyCustomized = appItem(
            iconUrl = "file:///data/user/0/com.example/files/custom_icons/app_com.spotify.music-99.png"
        )
        val otherPackIcons = mapOf("com.spotify.music" to "file:///pack_b/spotify.png")

        assertEquals(
            "file:///pack_b/spotify.png",
            resolveAppIconUrl(legacyCustomized, otherPackIcons)
        )
    }

    @Test
    fun `switching back to default icons falls back to device icon even when pack map cleared`() {
        val defaultDeviceUrl = "file:///data/user/0/com.example/files/app_icons/com.spotify.music-1.png"
        val app = appItem(iconUrl = defaultDeviceUrl)

        assertEquals(defaultDeviceUrl, resolveAppIconUrl(app, emptyMap()))
    }

    @Test
    fun `explicit local photo wins over pack while active and never applies pack to games`() {
        val localPhotoUrl = "file:///data/user/0/com.example/files/custom_art/app_com.spotify.music_icon-1.img"
        val appWithLocalPhoto = appItem(iconUrl = localPhotoUrl)
        val gameItem = appItem(isGame = true)
        val themedIcons = mapOf(
            "app_com.spotify.music" to "file:///pack/spotify.png",
            "com.spotify.music" to "file:///pack/spotify.png"
        )

        assertEquals(localPhotoUrl, resolveAppIconUrl(appWithLocalPhoto, themedIcons))
        assertEquals(gameItem.iconUrl, resolveAppIconUrl(gameItem, themedIcons))
    }
}
