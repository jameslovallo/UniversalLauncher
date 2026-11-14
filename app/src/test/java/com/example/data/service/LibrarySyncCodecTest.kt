package com.example.data.service

import android.net.Uri
import com.example.data.model.GameItem
import com.example.data.model.GameType
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySyncCodecTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun gfnGame(
        id: String = "gfn_1",
        title: String = "Control",
        posterUrl: String? = "https://cdn.example/poster.jpg"
    ) = GameItem(
        id = id,
        title = title,
        type = GameType.GEFORCE_NOW,
        posterUrl = posterUrl
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
        assertNull(parsed.items.single().nsfwLogoUrl)
        assertEquals(false, parsed.items.single().overlayNsfwLogo)
    }

    @Test
    fun `nsfw logo overlay fields round trip and affect signature`() {
        val base = gfnGame().copy(nsfwPosterUrl = "https://cdn.example/nsfw.jpg")
        val withLogo = base.copy(
            nsfwLogoUrl = "https://cdn.example/logo.png",
            overlayNsfwLogo = true
        )

        val parsed = LibrarySyncCodec.parse(
            LibrarySyncCodec.buildDocument(listOf(withLogo), 99L).toString()
        )

        assertEquals("https://cdn.example/logo.png", parsed.items.single().nsfwLogoUrl)
        assertEquals(true, parsed.items.single().overlayNsfwLogo)
        assertNotEquals(
            LibrarySyncCodec.signature(listOf(base)),
            LibrarySyncCodec.signature(listOf(withLogo))
        )
    }

    @Test
    fun `safe logo overlay fields round trip and affect signature`() {
        val base = gfnGame()
        val withLogo = base.copy(
            logoUrl = "https://cdn.example/safe-logo.png",
            overlayLogo = true
        )

        val parsed = LibrarySyncCodec.parse(
            LibrarySyncCodec.buildDocument(listOf(withLogo), 99L).toString()
        )

        assertEquals("https://cdn.example/safe-logo.png", parsed.items.single().logoUrl)
        assertEquals(true, parsed.items.single().overlayLogo)
        assertNotEquals(
            LibrarySyncCodec.signature(listOf(base)),
            LibrarySyncCodec.signature(listOf(withLogo))
        )
    }

    @Test
    fun `chosen local file nsfw poster records content hash and changes signature`() {
        val deviceAFile = File(tempFolder.root, "picked_nsfw.img").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
        }
        val deviceBFile = File(tempFolder.root, "gfn_1-nsfw-synced-hash.png").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
        }

        val withoutNsfw = gfnGame()
        val withLocalNsfw = withoutNsfw.copy(nsfwPosterUrl = Uri.fromFile(deviceAFile).toString())

        val document = LibrarySyncCodec.buildDocument(listOf(withLocalNsfw), 77L).toString()
        val parsed = LibrarySyncCodec.parse(document)

        assertTrue(parsed.tracksLocalPosters)
        // Raw device file path is never written to JSON.
        assertNull(parsed.items.single().nsfwPosterUrl)
        assertNotNull(parsed.posterHashes["gfn_1"]?.nsfwPosterHash)

        // Assigning or updating a local file changes the signature so sync triggers.
        assertNotEquals(
            LibrarySyncCodec.signature(listOf(withoutNsfw)),
            LibrarySyncCodec.signature(listOf(withLocalNsfw))
        )

        // Device B importing the exact same image bytes matches the remote signature.
        val deviceBItem = withoutNsfw.copy(nsfwPosterUrl = Uri.fromFile(deviceBFile).toString())
        assertEquals(parsed.signature, LibrarySyncCodec.signature(listOf(deviceBItem)))

        // Modifying the local file on Device A produces a different signature.
        deviceAFile.writeBytes(byteArrayOf(9, 8, 7, 6))
        assertNotEquals(parsed.signature, LibrarySyncCodec.signature(listOf(withLocalNsfw)))
    }

    @Test
    fun `legacy version two documents do not claim local poster hash tracking`() {
        val legacyV2 = """{"format":"nexus_library","version":2,"updatedAt":10,"items":[]}"""
        assertFalse(LibrarySyncCodec.parse(legacyV2).tracksLocalPosters)
    }

    @Test
    fun `custom cloud games are syncable`() {
        val customCloud = GameItem(
            id = "custom_1",
            title = "Xbox Cloud",
            type = GameType.CUSTOM_CLOUD,
            isGame = true,
            launchUrl = "https://www.xbox.com/play"
        )
        assertTrue(LibrarySyncCodec.isSyncable(customCloud))
    }

    @Test
    fun `installed game with chosen nsfw poster file syncs in appCustomizations without polluting items`() {
        val posterFile = File(tempFolder.root, "game_nsfw.img").apply {
            writeBytes(byteArrayOf(11, 22, 33, 44, 55))
        }
        val installedGame = GameItem(
            id = "app_com.example.game",
            title = "Installed Game",
            type = GameType.INSTALLED_APP,
            isGame = true,
            packageName = "com.example.game",
            nsfwPosterUrl = Uri.fromFile(posterFile).toString(),
            isCustomized = true
        )
        val plainSystemApp = GameItem(
            id = "app_com.android.settings",
            title = "Settings",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.android.settings"
        )

        val rows = listOf(installedGame, plainSystemApp)
        val syncableApps = LibrarySyncCodec.syncableAppCustomizations(rows)
        assertEquals(listOf("app_com.example.game"), syncableApps.map { it.id })

        val doc = LibrarySyncCodec.buildDocument(
            items = LibrarySyncCodec.syncable(rows),
            updatedAt = 50L,
            appCustomizations = syncableApps
        ).toString()

        val parsed = LibrarySyncCodec.parse(doc)
        // Installed apps never enter `items` (so uninstalled apps never appear on another device).
        assertTrue(parsed.items.isEmpty())
        assertEquals(listOf("app_com.example.game"), parsed.appCustomizations.map { it.id })
        assertNotNull(parsed.appPosterHashes["app_com.example.game"]?.nsfwPosterHash)

        // Preserved unowned remote app customizations keep their hash override across rewrites.
        val rewritten = LibrarySyncCodec.buildDocument(
            items = emptyList(),
            updatedAt = 51L,
            appCustomizations = parsed.appCustomizations,
            appPosterHashOverrides = parsed.appPosterHashes
        ).toString()
        val reparsed = LibrarySyncCodec.parse(rewritten)
        assertEquals(
            parsed.appPosterHashes["app_com.example.game"]?.nsfwPosterHash,
            reparsed.appPosterHashes["app_com.example.game"]?.nsfwPosterHash
        )
    }

    @Test
    fun `favorites are never serialized parsed synced or included in signatures`() {
        val favoritedGame = gfnGame().copy(isFavorite = true, favoriteOrder = 3)
        val favoritedApp = GameItem(
            id = "app_com.example.mail",
            title = "Mail",
            type = GameType.INSTALLED_APP,
            isGame = false,
            packageName = "com.example.mail",
            isFavorite = true,
            favoriteOrder = 1
        )
        val customizedFavoritedApp = favoritedApp.copy(
            id = "app_com.example.music",
            title = "Music",
            packageName = "com.example.music",
            isCustomized = true
        )

        // Favoriting an uncustomized app does not make it syncable.
        val syncableApps = LibrarySyncCodec.syncableAppCustomizations(
            listOf(favoritedApp, customizedFavoritedApp)
        )
        assertEquals(listOf("app_com.example.music"), syncableApps.map { it.id })

        // Document never writes favorite fields and parses them as false/0 even from legacy JSON.
        val json = LibrarySyncCodec.buildDocument(
            items = listOf(favoritedGame),
            updatedAt = 200L,
            appCustomizations = syncableApps
        ).toString()
        assertFalse(json.contains("isFavorite"))
        assertFalse(json.contains("favoriteOrder"))

        val parsed = LibrarySyncCodec.parse(json)
        assertFalse(parsed.items.single().isFavorite)
        assertEquals(0, parsed.items.single().favoriteOrder)
        assertFalse(parsed.appCustomizations.single().isFavorite)
        assertEquals(0, parsed.appCustomizations.single().favoriteOrder)

        val legacyJsonWithFavorites = """
            {
              "format": "nexus_library",
              "version": 2,
              "updatedAt": 200,
              "items": [
                {"id":"gfn_1","title":"Control","type":"GEFORCE_NOW","isFavorite":true,"favoriteOrder":5}
              ],
              "appCustomizations": [
                {"id":"app_com.example.music","title":"Music","type":"INSTALLED_APP","isGame":false,"isCustomized":true,"isFavorite":true,"favoriteOrder":4}
              ]
            }
        """.trimIndent()
        val parsedLegacy = LibrarySyncCodec.parse(legacyJsonWithFavorites)
        assertFalse(parsedLegacy.items.single().isFavorite)
        assertEquals(0, parsedLegacy.items.single().favoriteOrder)
        assertFalse(parsedLegacy.appCustomizations.single().isFavorite)
        assertEquals(0, parsedLegacy.appCustomizations.single().favoriteOrder)

        // Toggling or reordering favorites does not affect the library signature.
        assertEquals(
            LibrarySyncCodec.librarySignature(listOf(gfnGame()), listOf(customizedFavoritedApp.copy(isFavorite = false, favoriteOrder = 0))),
            LibrarySyncCodec.librarySignature(listOf(favoritedGame), listOf(customizedFavoritedApp))
        )
    }
}
