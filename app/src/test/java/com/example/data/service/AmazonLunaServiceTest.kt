package com.example.data.service

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AmazonLunaServiceTest {

    @Test
    fun `createLunaLaunchIntentUri matches exact intent specification`() {
        val expected =
            "intent://luna.amazon.com/#Intent;scheme=https;package=com.amazon.spiderpork;S.browser_fallback_url=https://luna.amazon.com;end;"

        assertEquals(expected, AmazonLunaService.createLunaLaunchIntentUri(null))
        assertEquals(expected, AmazonLunaService.createLunaLaunchIntentUri("https://luna.amazon.com"))
        assertEquals(expected, AmazonLunaService.createLunaLaunchIntentUri("  https://luna.amazon.com/  "))
    }

    @Test
    fun `createLunaLaunchIntentUri preserves deep path and query`() {
        val uri = AmazonLunaService.createLunaLaunchIntentUri("https://luna.amazon.com/game/fortnite/B09G8?ref=nexus")
        assertEquals(
            "intent://luna.amazon.com/game/fortnite/B09G8?ref=nexus#Intent;scheme=https;package=com.amazon.spiderpork;S.browser_fallback_url=https://luna.amazon.com/game/fortnite/B09G8?ref=nexus;end;",
            uri
        )
    }

    @Test
    fun `extractWebUrl extracts fallback url and handles raw or empty inputs`() {
        val intentUri =
            "intent://luna.amazon.com/#Intent;scheme=https;package=com.amazon.spiderpork;S.browser_fallback_url=https://luna.amazon.com;end;"
        assertEquals("https://luna.amazon.com", AmazonLunaService.extractWebUrl(intentUri))
        assertEquals("https://luna.amazon.com/game/halo", AmazonLunaService.extractWebUrl("https://luna.amazon.com/game/halo"))
        assertEquals("https://luna.amazon.com/game/x", AmazonLunaService.extractWebUrl("intent://luna.amazon.com/game/x#Intent;end;"))
        assertEquals("https://luna.amazon.com", AmazonLunaService.extractWebUrl(null))
    }

    @Test
    fun `parseWpGamelistJson parses CloudDosage gamelist entries and deduplicates`() {
        val json = """
            [
              {
                "id": 101,
                "content": {
                  "rendered": "<div class=\"entry\"><a href=\"https://clouddosage.com/games/fortnite/\"><img src=\"https://cdn.example.com/fortnite.jpg\" />Fortnite</a><a href=\"https://clouddosage.com/games/fortnite/\">Fortnite</a><a href=\"https://luna.amazon.com/game/control/123\">Control Ultimate Edition</a><a href=\"https://clouddosage.com/games/play-now/\">Play Now</a></div>"
                }
              }
            ]
        """.trimIndent()

        val parsed = AmazonLunaService.parseWpGamelistJson(json, includedWithPrime = true)

        assertEquals(2, parsed.size)
        assertEquals("Fortnite", parsed[0].title)
        assertEquals("fortnite", parsed[0].slug)
        assertEquals("https://cdn.example.com/fortnite.jpg", parsed[0].imageUrl)
        assertTrue(parsed[0].includedWithPrime)
        assertEquals("Control Ultimate Edition", parsed[1].title)
        assertEquals("https://luna.amazon.com/game/control/123", parsed[1].webUrl)

        val item = parsed[0].toGameItem()
        assertEquals(GameType.AMAZON_LUNA, item.type)
        assertTrue(item.isGame)
        assertEquals(
            "intent://luna.amazon.com/#Intent;scheme=https;package=com.amazon.spiderpork;S.browser_fallback_url=https://luna.amazon.com;end;",
            item.launchUrl
        )
    }

    @Test
    fun `parseGamelistHtml supports table rows and list fallbacks`() {
        val tableHtml = """
            <table>
              <tr><th>Game</th><th>Genre</th></tr>
              <tr><td>Fallout New Vegas</td><td>RPG</td></tr>
              <tr><td>Lego Star Wars</td><td>Action</td></tr>
            </table>
        """.trimIndent()

        val tableEntries = AmazonLunaService.parseGamelistHtml(tableHtml, includedWithPrime = true)
        assertEquals(listOf("Fallout New Vegas", "Lego Star Wars"), tableEntries.map { it.title })

        val listHtml = """
            <ul>
              <li>Trackmania – Racing</li>
              <li>Dave the Diver - Adventure</li>
            </ul>
        """.trimIndent()
        val listEntries = AmazonLunaService.parseGamelistHtml(listHtml, includedWithPrime = false)
        assertEquals(listOf("Trackmania", "Dave the Diver"), listEntries.map { it.title })
        assertFalse(listEntries.first().includedWithPrime)
    }

    @Test
    fun `mergeCatalogEntries keeps prime flag and enriches image url from full catalog`() {
        val prime = listOf(
            AmazonLunaService.LunaCatalogEntry(
                id = "fortnite",
                title = "Fortnite",
                slug = "fortnite",
                imageUrl = null,
                includedWithPrime = true
            )
        )
        val full = listOf(
            AmazonLunaService.LunaCatalogEntry(
                id = "fortnite",
                title = "Fortnite",
                slug = "fortnite",
                imageUrl = "https://cdn.example.com/fn.jpg",
                includedWithPrime = false
            ),
            AmazonLunaService.LunaCatalogEntry(
                id = "star_wars",
                title = "Star Wars Outlaws",
                slug = "star_wars",
                imageUrl = "https://cdn.example.com/sw.jpg",
                includedWithPrime = false
            )
        )

        val merged = AmazonLunaService.mergeCatalogEntries(prime, full)
        assertEquals(2, merged.size)
        assertTrue(merged[0].includedWithPrime)
        assertEquals("https://cdn.example.com/fn.jpg", merged[0].imageUrl)
        assertFalse(merged[1].includedWithPrime)
    }

    @Test
    fun `parseTempoGetPageResponse extracts missing games like Star Wars Galactic Racer with portrait and landscape art`() {
        val presentationGalacticRacer = org.json.JSONObject().apply {
            put("title", "STAR WARS: Galactic Racer™")
            put("imagePortrait", "https://m.media-amazon.com/images/I/81wxZcCl3dL.png")
            put("imageLandscape", "https://m.media-amazon.com/images/I/91KCNdndiqL.png")
            put("gameId", "amzn1.luna.product.bef8572c-d814-4c91-b8e7-980d5c84ad16")
            put("releaseYear", "2026")
            put("genreTags", org.json.JSONArray(listOf("Racing")))
            put(
                "hoverDetails",
                org.json.JSONObject().apply {
                    put("publishersText", "Secret Mode")
                }
            )
        }.toString()

        val presentationHogwarts = org.json.JSONObject().apply {
            put("title", "Hogwarts Legacy")
            put("imagePortrait", "https://m.media-amazon.com/images/I/91example.jpg")
            put("imageLandscape", "https://m.media-amazon.com/images/I/92example.jpg")
            put("gameId", "amzn1.luna.product.1111")
            put("releaseYear", "2023")
            put("genreTags", org.json.JSONArray(listOf("Action", "RPG")))
        }.toString()

        val promoWidgetPresentation = org.json.JSONObject().apply {
            put("title", "Included with Prime")
        }.toString()

        val responseJson = org.json.JSONObject().apply {
            put(
                "pageMemberGroups",
                org.json.JSONObject().apply {
                    put(
                        "mainContent",
                        org.json.JSONObject().apply {
                            put(
                                "widgets",
                                org.json.JSONArray().apply {
                                    put(
                                        org.json.JSONObject().apply {
                                            put("presentationData", promoWidgetPresentation)
                                            put(
                                                "actions",
                                                org.json.JSONArray().apply {
                                                    put(
                                                        org.json.JSONObject().apply {
                                                            put("target", "/store/included-with-prime")
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                    put(
                                        org.json.JSONObject().apply {
                                            put("id", "collection_browse_included_with_prime")
                                            put(
                                                "widgets",
                                                org.json.JSONArray().apply {
                                                    put(
                                                        org.json.JSONObject().apply {
                                                            put("presentationData", presentationGalacticRacer)
                                                            put(
                                                                "actions",
                                                                org.json.JSONArray().apply {
                                                                    put(
                                                                        org.json.JSONObject().apply {
                                                                            put(
                                                                                "target",
                                                                                "/game/star-wars-galactic-racer/B0HLN4GG5K"
                                                                            )
                                                                        }
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    )
                                                    put(
                                                        org.json.JSONObject().apply {
                                                            put("presentationData", presentationHogwarts)
                                                            put(
                                                                "actions",
                                                                org.json.JSONArray().apply {
                                                                    put(
                                                                        org.json.JSONObject().apply {
                                                                            put("target", "/game/hogwarts-legacy/B0CL999999")
                                                                        }
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }.toString()

        val entries = AmazonLunaService.parseTempoGetPageResponse(
            jsonBody = responseJson,
            includedWithPrime = true
        )

        assertEquals(2, entries.size)
        val starWars = entries[0]
        assertEquals("B0HLN4GG5K", starWars.id)
        assertEquals("STAR WARS: Galactic Racer™", starWars.title)
        assertEquals("star-wars-galactic-racer", starWars.slug)
        assertEquals(
            "https://luna.amazon.com/game/star-wars-galactic-racer/B0HLN4GG5K",
            starWars.webUrl
        )
        assertEquals("https://m.media-amazon.com/images/I/81wxZcCl3dL.png", starWars.imageUrl)
        assertEquals("https://m.media-amazon.com/images/I/91KCNdndiqL.png", starWars.heroBannerUrl)
        assertEquals("Secret Mode", starWars.publisher)
        assertEquals(listOf("Racing"), starWars.genres)
        assertEquals("2026", starWars.releaseYear)
        assertTrue(starWars.includedWithPrime)

        val gameItem = starWars.toGameItem()
        assertEquals("luna_B0HLN4GG5K", gameItem.id)
        assertEquals(GameType.AMAZON_LUNA, gameItem.type)
        assertEquals("https://m.media-amazon.com/images/I/81wxZcCl3dL.png", gameItem.posterUrl)
        assertEquals("https://m.media-amazon.com/images/I/91KCNdndiqL.png", gameItem.heroBannerUrl)
        assertEquals("Secret Mode", gameItem.publisher)
        assertEquals("Racing", gameItem.genre)
        assertEquals("LUNA", gameItem.customArtworkSource)
        assertEquals(
            "intent://luna.amazon.com/game/star-wars-galactic-racer/B0HLN4GG5K#Intent;scheme=https;package=com.amazon.spiderpork;S.browser_fallback_url=https://luna.amazon.com/game/star-wars-galactic-racer/B0HLN4GG5K;end;",
            gameItem.launchUrl
        )
    }

    @Test
    fun `parseTempoGetPageResponse ignores malformed json and deduplicates ordered search results`() {
        assertTrue(AmazonLunaService.parseTempoGetPageResponse("not-json", includedWithPrime = true).isEmpty())
        assertTrue(AmazonLunaService.parseTempoGetPageResponse("{}", includedWithPrime = true).isEmpty())

        val searchEntry = AmazonLunaService.LunaCatalogEntry(
            id = "B0HLN4GG5K",
            title = "STAR WARS: Galactic Racer™",
            slug = "star-wars-galactic-racer",
            webUrl = "https://luna.amazon.com/game/star-wars-galactic-racer/B0HLN4GG5K",
            imageUrl = "https://m.media-amazon.com/images/I/81wxZcCl3dL.png",
            includedWithPrime = false
        )
        val primeMatch = AmazonLunaService.LunaCatalogEntry(
            id = "B0HLN4GG5K",
            title = "STAR WARS: Galactic Racer™",
            slug = "star-wars-galactic-racer",
            webUrl = "https://luna.amazon.com/game/star-wars-galactic-racer/B0HLN4GG5K",
            imageUrl = null,
            publisher = "Secret Mode",
            includedWithPrime = true
        )
        val extraLocal = AmazonLunaService.LunaCatalogEntry(
            id = "B0EXTRA",
            title = "Star Wars Outlaws",
            slug = "star-wars-outlaws",
            webUrl = "https://luna.amazon.com/game/star-wars-outlaws/B0EXTRA",
            imageUrl = "https://m.media-amazon.com/images/I/outlaws.png",
            includedWithPrime = false
        )

        val merged = AmazonLunaService.mergeOrderedCatalogEntries(
            primaryEntries = listOf(searchEntry),
            secondaryEntries = listOf(primeMatch, extraLocal)
        )
        assertEquals(2, merged.size)
        assertEquals("STAR WARS: Galactic Racer™", merged[0].title)
        assertTrue(merged[0].includedWithPrime)
        assertEquals("https://m.media-amazon.com/images/I/81wxZcCl3dL.png", merged[0].imageUrl)
        assertEquals("Secret Mode", merged[0].publisher)
        assertEquals("Star Wars Outlaws", merged[1].title)
    }

    @Test
    fun `selectLunaPwaPackage prefers webapk domain handler and excludes generic browsers`() {
        val selected = selectLunaPwaPackage(
            lunaHandlerPackages = setOf(
                "com.amazon.spiderpork",
                "com.android.chrome",
                "org.chromium.webapk.a12345"
            ),
            genericHandlerPackages = setOf("com.android.chrome"),
            lunaAppPackage = AmazonLunaService.LUNA_PACKAGE_NAME,
            selfPackage = "com.example"
        )
        assertEquals("org.chromium.webapk.a12345", selected)

        val noneSelected = selectLunaPwaPackage(
            lunaHandlerPackages = setOf("com.android.chrome", "org.mozilla.firefox"),
            genericHandlerPackages = setOf("com.android.chrome", "org.mozilla.firefox"),
            lunaAppPackage = AmazonLunaService.LUNA_PACKAGE_NAME,
            selfPackage = "com.example"
        )
        assertNull(noneSelected)
    }

    @Test
    fun `launchLunaGame prefers native Amazon Luna app when installed`() {
        val intentUri = AmazonLunaService.createLunaLaunchIntentUri(null)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(app.packageManager)
        val shadowApp = shadowOf(app)
        shadowPm.installPackage(PackageInfo().apply {
            packageName = AmazonLunaService.LUNA_PACKAGE_NAME
            applicationInfo = ApplicationInfo().apply { packageName = AmazonLunaService.LUNA_PACKAGE_NAME }
        })

        val launched = InstalledAppsManager(app).launchLunaGame(intentUri)
        assertTrue(launched)
        val started = shadowApp.nextStartedActivity
        assertNotNull(started)
        assertEquals(AmazonLunaService.LUNA_PACKAGE_NAME, started.`package`)
    }

    @Test
    fun `launchLunaGame falls back to Luna PWA when native app is absent`() {
        val intentUri = AmazonLunaService.createLunaLaunchIntentUri(null)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(app.packageManager)
        val shadowApp = shadowOf(app)
        val webApkPkg = "org.chromium.webapk.luna123"
        shadowPm.installPackage(PackageInfo().apply {
            packageName = webApkPkg
            applicationInfo = ApplicationInfo().apply { packageName = webApkPkg }
        })
        val pwaResolve = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = webApkPkg
                name = "$webApkPkg.WebApkActivity"
                applicationInfo = ApplicationInfo().apply { packageName = webApkPkg }
            }
        }
        val browserResolve = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.chrome"
                name = "com.google.android.apps.chrome.Main"
                applicationInfo = ApplicationInfo().apply { packageName = "com.android.chrome" }
            }
        }
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://luna.amazon.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE),
            pwaResolve
        )
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://luna.amazon.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE),
            browserResolve
        )
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://nonexistent.invalid/"))
                .addCategory(Intent.CATEGORY_BROWSABLE),
            browserResolve
        )

        val launched = InstalledAppsManager(app).launchLunaGame(intentUri)
        assertTrue(launched)
        val started = shadowApp.nextStartedActivity
        assertNotNull(started)
        assertEquals(webApkPkg, started.`package`)
        assertEquals("https://luna.amazon.com", started.dataString)
    }

    @Test
    fun `launchLunaGame falls back to browser website when neither native app nor PWA is installed`() {
        val intentUri = AmazonLunaService.createLunaLaunchIntentUri(null)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowPm = shadowOf(app.packageManager)
        val shadowApp = shadowOf(app)
        val chromeResolve = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.chrome"
                name = "com.google.android.apps.chrome.Main"
                applicationInfo = ApplicationInfo().apply { packageName = "com.android.chrome" }
            }
        }
        @Suppress("DEPRECATION")
        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://luna.amazon.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE),
            chromeResolve
        )

        val launched = InstalledAppsManager(app).launchLunaGame(intentUri)
        assertTrue(launched)
        val started = shadowApp.nextStartedActivity
        assertNotNull(started)
        assertEquals("com.android.chrome", started.`package`)
        assertEquals("https://luna.amazon.com", started.dataString)
    }
}
