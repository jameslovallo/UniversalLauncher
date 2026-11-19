package com.example.data.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GameItem
import com.example.data.model.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoritesSnapshotStoreTest {

    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("test_favorites_snapshot", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun `every field survives a round trip, including local artwork paths`() {
        val items = listOf(
            GameItem(
                id = "app_com.spotify.music",
                title = "Spotify",
                type = GameType.INSTALLED_APP,
                isGame = false,
                packageName = "com.spotify.music",
                activityName = "com.spotify.music.MainActivity",
                launchUrl = "spotify://home",
                iconUrl = "file:///data/user/0/com.example/files/app_icons/com.spotify.music-1.png",
                posterUrl = "https://cdn.example.com/spotify.png",
                nsfwPosterUrl = "https://cdn.example.com/spotify-nsfw.png",
                logoUrl = "https://cdn.example.com/spotify-logo.png",
                overlayLogo = true,
                nsfwLogoUrl = "https://cdn.example.com/spotify-nsfw-logo.png",
                overlayNsfwLogo = true,
                heroBannerUrl = "https://cdn.example.com/spotify-hero.png",
                genre = "Music",
                publisher = "Spotify AB",
                description = "Music for everyone",
                isFavorite = true,
                lastPlayedTimestamp = 1_700_000_000_123L,
                playCount = 42,
                customArtworkSource = "LOCAL",
                tags = "music,audio",
                isPinned = true,
                isCustomized = true,
                favoriteOrder = 3,
                folderId = "folder_media"
            ),
            GameItem(
                id = "gfn_1234",
                title = "Cyberpunk 2077",
                type = GameType.GEFORCE_NOW,
                isGame = true,
                gfnCmsId = "1234",
                posterUrl = "file:///data/user/0/com.example/files/posters/1234.jpg",
                isFavorite = true,
                favoriteOrder = 0
            )
        )

        FavoritesSnapshotStore.persist(prefs, items)

        assertEquals(items, FavoritesSnapshotStore.restore(prefs))
    }

    @Test
    fun `empty favorites clear the cache instead of storing an empty list`() {
        FavoritesSnapshotStore.persist(prefs, listOf(GameItem(id = "a", title = "A", type = GameType.INSTALLED_APP)))
        assertTrue(FavoritesSnapshotStore.restore(prefs).isNotEmpty())

        FavoritesSnapshotStore.persist(prefs, emptyList())

        assertTrue(FavoritesSnapshotStore.restore(prefs).isEmpty())
    }

    @Test
    fun `corrupt entries are skipped without discarding the rest of the cache`() {
        prefs.edit().putString(
            "favorites_snapshot_v1",
            """
            [
              {"id":"good","title":"Good","type":"INSTALLED_APP","isGame":false,"isFavorite":true},
              {"id":"bad-type","title":"Bad","type":"NOT_A_TYPE"},
              {"title":"Missing id","type":"INSTALLED_APP"},
              "not an object"
            ]
            """.trimIndent()
        ).commit()

        val restored = FavoritesSnapshotStore.restore(prefs)

        assertEquals(listOf("good"), restored.map { it.id })
    }

    @Test
    fun `unreadable snapshot falls back to an empty list`() {
        prefs.edit().putString("favorites_snapshot_v1", "{not json").commit()

        assertTrue(FavoritesSnapshotStore.restore(prefs).isEmpty())
    }
}
