package com.example.data.service

import com.example.data.model.GameItem
import com.example.data.model.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySyncCodecTest {

    private fun gfnGame(
        id: String = "gfn_1",
        title: String = "Control",
        posterUrl: String? = "https://cdn.example/poster.jpg"
    ) = GameItem(
        id = id,
        title = title,
        type = GameType.GEFORCE_NOW,
        posterUrl = posterUrl,
        isFavorite = true,
        favoriteOrder = 2
    )

    private fun moonlightGame(
        isPinned: Boolean = false,
        playCount: Int = 0,
        iconUrl: String? = null
    ) = GameItem(
        id = "shortcut_com.limelight_1",
        title = "Portal 2",
        type = GameType.SHORTCUT,
        isGame = true,
        packageName = "com.limelight",
        activityName = "207d8f6d-5176-43fd-9ce5-990a254e7e76" + "5517696",
        launchUrl = "intent:#Intent;component=com.limelight/.ShortcutTrampoline;end",
        isPinned = isPinned,
        playCount = playCount,
        iconUrl = iconUrl,
        lastPlayedTimestamp = playCount.toLong()
    )

    @Test
    fun `document round trips the synced fields`() {
        val items = listOf(gfnGame(), moonlightGame())
        val document = LibrarySyncCodec.buildDocument(items, 1234L).toString()

        val parsed = LibrarySyncCodec.parse(document)

        assertEquals(1234L, parsed.updatedAt)
        assertEquals(items.sortedBy { it.id }, parsed.items)
        assertTrue(parsed.authoritative)
    }

    @Test
    fun `imported shortcuts are never marked pinned`() {
        val parsed = LibrarySyncCodec.parse(
            LibrarySyncCodec.buildDocument(listOf(moonlightGame(isPinned = true)), 1L).toString()
        )

        assertEquals(false, parsed.items.single().isPinned)
    }

    @Test
    fun `signature ignores device local state`() {
        val local = moonlightGame(isPinned = false, playCount = 0, iconUrl = "file:/icons/a.png")
        val otherDevice = moonlightGame(isPinned = true, playCount = 9, iconUrl = null)

        assertEquals(
            LibrarySyncCodec.signature(listOf(local)),
            LibrarySyncCodec.signature(listOf(otherDevice))
        )
    }

    @Test
    fun `signature changes when shareable content changes`() {
        val original = gfnGame()
        val renamed = original.copy(title = "Control Ultimate Edition")

        assertNotEquals(
            LibrarySyncCodec.signature(listOf(original)),
            LibrarySyncCodec.signature(listOf(renamed))
        )
    }

    @Test
    fun `local artwork paths are not written to the file`() {
        val document = LibrarySyncCodec.buildDocument(
            listOf(gfnGame(posterUrl = "file:/art/poster.png")),
            1L
        ).toString()

        val parsed = LibrarySyncCodec.parse(document)
        assertNull(parsed.items.single().posterUrl)
    }

    @Test
    fun `tombstones travel but installed apps do not`() {
        val tombstone = gfnGame().copy(isDeleted = true)
        val installedApp = GameItem(
            id = "app_1",
            title = "Camera",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.android.camera"
        )
        val appShortcut = GameItem(
            id = "shortcut_app_1",
            title = "Notes",
            type = GameType.SHORTCUT,
            isGame = false,
            packageName = "com.example.notes"
        )

        val parsed = LibrarySyncCodec.parse(
            LibrarySyncCodec.buildDocument(
                listOf(tombstone, installedApp, appShortcut),
                1L
            ).toString()
        )

        assertEquals(listOf(tombstone.id), parsed.items.map { it.id })
    }

    @Test
    fun `older bare array documents still parse`() {
        val legacy = """[{"id":"gfn_1","title":"Control","type":"GEFORCE_NOW"}]"""

        val parsed = LibrarySyncCodec.parse(legacy)

        assertEquals(0L, parsed.updatedAt)
        assertEquals("Control", parsed.items.single().title)
        // Old exports omit tombstones, so they must not delete local games.
        assertEquals(false, parsed.authoritative)
    }

    @Test
    fun `version one exports are not authoritative`() {
        val v1 = """{"format":"nexus_library","version":1,"exportedAt":5,"items":[]}"""

        assertEquals(false, LibrarySyncCodec.parse(v1).authoritative)
    }

    @Test
    fun `unrelated json is rejected`() {
        val error = runCatching { LibrarySyncCodec.parse("""{"hello":"world"}""") }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `a different format is rejected`() {
        val error = runCatching {
            LibrarySyncCodec.parse("""{"format":"something_else","items":[]}""")
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }

    @Test
    fun `nsfw poster url round trips and affects signature`() {
        val safeOnly = gfnGame()
        val withNsfw = safeOnly.copy(nsfwPosterUrl = "https://cdn.example/nsfw-poster.jpg")

        val document = LibrarySyncCodec.buildDocument(listOf(withNsfw), 42L).toString()
        val parsed = LibrarySyncCodec.parse(document)

        assertEquals("https://cdn.example/nsfw-poster.jpg", parsed.items.single().nsfwPosterUrl)
        assertEquals(safeOnly.posterUrl, parsed.items.single().posterUrl)
        assertNotEquals(
            LibrarySyncCodec.signature(listOf(safeOnly)),
            LibrarySyncCodec.signature(listOf(withNsfw))
        )
    }

    @Test
    fun `local nsfw artwork paths are omitted from sync document`() {
        val localNsfw = gfnGame().copy(nsfwPosterUrl = "file:/art/nsfw.png")
        val document = LibrarySyncCodec.buildDocument(listOf(localNsfw), 42L).toString()
        val parsed = LibrarySyncCodec.parse(document)

        assertNull(parsed.items.single().nsfwPosterUrl)
        assertEquals(
            LibrarySyncCodec.signature(listOf(gfnGame())),
            LibrarySyncCodec.signature(listOf(localNsfw))
        )
    }

    @Test
    fun `version two documents without nsfwPosterUrl remain compatible and authoritative`() {
        val legacyV2 = """
            {
              "format": "nexus_library",
              "version": 2,
              "updatedAt": 100,
              "items": [
                {
                  "id": "gfn_1",
                  "title": "Control",
                  "type": "GEFORCE_NOW",
                  "posterUrl": "https://cdn.example/poster.jpg"
                }
              ]
            }
        """.trimIndent()

        val parsed = LibrarySyncCodec.parse(legacyV2)

        assertTrue(parsed.authoritative)
        assertEquals("https://cdn.example/poster.jpg", parsed.items.single().posterUrl)
        assertNull(parsed.items.single().nsfwPosterUrl)
    }
}
