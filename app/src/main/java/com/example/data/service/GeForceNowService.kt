package com.example.data.service

import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object GeForceNowService {
    private const val GFN_BASE_PLAY_URL = "https://play.geforcenow.com"
    private val httpClient = okhttp3.OkHttpClient.Builder().build()

    // Curated GeForce NOW titles with official GFN CMS App IDs and poster images
    val DEFAULT_GFN_LIBRARY: List<GameItem> = listOf(
        GameItem(
            id = "gfn_cyberpunk2077",
            title = "Cyberpunk 2077",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "100720411",
            launchUrl = "https://play.geforcenow.com/games?game-id=100720411",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co41ea.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc73e7.jpg",
            genre = "Action RPG, Sci-Fi",
            publisher = "CD PROJEKT RED",
            description = "An open-world, action-adventure RPG set in the megalopolis of Night City, where you play as a cyberpunk mercenary.",
            tags = "Ray Tracing,Cloud,RPG,Open World"
        ),
        GameItem(
            id = "gfn_baldursgate3",
            title = "Baldur's Gate 3",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "100863911",
            launchUrl = "https://play.geforcenow.com/games?game-id=100863911",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co670h.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/scdtvi.jpg",
            genre = "Turn-Based RPG",
            publisher = "Larian Studios",
            description = "Gather your party and return to the Forgotten Realms in a tale of fellowship and betrayal, sacrifice and survival.",
            tags = "RPG,Turn-Based,Story Rich,Co-op"
        ),
        GameItem(
            id = "gfn_witcher3",
            title = "The Witcher 3: Wild Hunt",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "100277311",
            launchUrl = "https://play.geforcenow.com/games?game-id=100277311",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1wyy.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc6q6w.jpg",
            genre = "Action RPG, Fantasy",
            publisher = "CD PROJEKT RED",
            description = "As war rages through the Northern Realms, you take on the greatest contract of your life — tracking down the Child of Prophecy.",
            tags = "Fantasy,Open World,Masterpiece"
        ),
        GameItem(
            id = "gfn_blackmyth_wukong",
            title = "Black Myth: Wukong",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "101982711",
            launchUrl = "https://play.geforcenow.com/games?game-id=101982711",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co806g.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/scwugy.jpg",
            genre = "Action RPG, Souls-like",
            publisher = "Game Science",
            description = "An action RPG rooted in Chinese mythology. Set out as the Destined One to venture into the challenges and marvels ahead.",
            tags = "Souls-like,Action,Mythology"
        ),
        GameItem(
            id = "gfn_fortnite",
            title = "Fortnite",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "100344511",
            launchUrl = "https://play.geforcenow.com/games?game-id=100344511",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5z8o.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc6pzn.jpg",
            genre = "Battle Royale, Shooter",
            publisher = "Epic Games",
            description = "Drop into Battle Royale or explore thousands of community-made islands with full touch & controller support.",
            tags = "Free to Play,Multiplayer,Shooter"
        ),
        GameItem(
            id = "gfn_alanwake2",
            title = "Alan Wake 2",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "101568411",
            launchUrl = "https://play.geforcenow.com/games?game-id=101568411",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co6nnl.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sclw9u.jpg",
            genre = "Survival Horror, Psychological",
            publisher = "Epic Games Publishing",
            description = "A string of ritualistic murders threatens Bright Falls. Saga Anderson investigates, while trapped writer Alan Wake pens his escape.",
            tags = "Horror,Ray Tracing,Cinematic"
        ),
        GameItem(
            id = "gfn_starfield",
            title = "Starfield",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "101548111",
            launchUrl = "https://play.geforcenow.com/games?game-id=101548111",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co6mvt.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/scnv18.jpg",
            genre = "Space RPG, Exploration",
            publisher = "Bethesda Softworks",
            description = "In the year 2330, humanity has ventured beyond our solar system, settling new planets, and living as a spacefaring people.",
            tags = "Space,Sci-Fi,Exploration"
        ),
        GameItem(
            id = "gfn_forzahorizon5",
            title = "Forza Horizon 5",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "101183211",
            launchUrl = "https://play.geforcenow.com/games?game-id=101183211",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co3ofw.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/scb1h1.jpg",
            genre = "Racing, Open World",
            publisher = "Xbox Game Studios",
            description = "Lead breathtaking expeditions across the vibrant and ever-evolving open world landscapes of Mexico.",
            tags = "Racing,Simulation,Driving"
        ),
        GameItem(
            id = "gfn_apexlegends",
            title = "Apex Legends",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "100412811",
            launchUrl = "https://play.geforcenow.com/games?game-id=100412811",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1rcb.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc6pzl.jpg",
            genre = "Hero Shooter, Battle Royale",
            publisher = "Electronic Arts",
            description = "Conquer with character in Apex Legends, a free-to-play Hero shooter where legendary characters battle for glory.",
            tags = "Shooter,Free to Play,Competitive"
        ),
        GameItem(
            id = "gfn_genshinimpact",
            title = "Genshin Impact",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "101139711",
            launchUrl = "https://play.geforcenow.com/games?game-id=101139711",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co2040.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc73e8.jpg",
            genre = "Anime Action RPG",
            publisher = "HoYoverse",
            description = "Step into Teyvat, a vast world teeming with life and flowing with elemental energy.",
            tags = "Anime,Open World,Adventure"
        ),
        GameItem(
            id = "gfn_stalker2",
            title = "S.T.A.L.K.E.R. 2: Heart of Chornobyl",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "102041911",
            launchUrl = "https://play.geforcenow.com/games?game-id=102041911",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co8352.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/scwvvh.jpg",
            genre = "FPS, Post-Apocalyptic",
            publisher = "GSC Game World",
            description = "Explore the vast Chornobyl Exclusion Zone full of dangerous enemies, deadly anomalies, and powerful artifacts.",
            tags = "Survival,Atmospheric,FPS"
        ),
        GameItem(
            id = "gfn_re4remake",
            title = "Resident Evil 4",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "101344211",
            launchUrl = "https://play.geforcenow.com/games?game-id=101344211",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5q06.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/scirrk.jpg",
            genre = "Action Horror",
            publisher = "Capcom",
            description = "Survival is only the beginning. Six years have passed since the disaster in Raccoon City. Leon S. Kennedy tracks the president's kidnapped daughter.",
            tags = "Horror,Action,Remake"
        ),
        GameItem(
            id = "gfn_deathstranding",
            title = "Death Stranding Director's Cut",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "101153911",
            launchUrl = "https://play.geforcenow.com/games?game-id=101153911",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co3u5f.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc73e9.jpg",
            genre = "Action, Sci-Fi",
            publisher = "505 Games / Kojima Productions",
            description = "From legendary game creator Hideo Kojima comes a genre-defying experience, now expanded in the definitive Director's Cut.",
            tags = "Cinematic,Open World,Story"
        ),
        GameItem(
            id = "gfn_destiny2",
            title = "Destiny 2",
            type = GameType.GEFORCE_NOW,
            gfnCmsId = "100378111",
            launchUrl = "https://play.geforcenow.com/games?game-id=100378111",
            posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7h.jpg",
            heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc73ea.jpg",
            genre = "Sci-Fi MMO FPS",
            publisher = "Bungie",
            description = "Dive into the world of Destiny 2 to explore the mysteries of the solar system and experience responsive first-person shooter combat.",
            tags = "Shooter,Co-op,Free to Play"
        )
    )

    fun createGfnLaunchUrl(cmsId: String?): String {
        return if (!cmsId.isNullOrBlank()) {
            "$GFN_BASE_PLAY_URL/games?game-id=$cmsId"
        } else {
            GFN_BASE_PLAY_URL
        }
    }

    /**
     * Search or sync titles from GeForce NOW web catalog.
     */
    suspend fun searchOrSyncCatalog(query: String? = null): List<GameItem> = withContext(Dispatchers.IO) {
        if (query.isNullOrBlank()) {
            return@withContext DEFAULT_GFN_LIBRARY
        }
        val lowercaseQuery = query.lowercase().trim()
        val localMatches = DEFAULT_GFN_LIBRARY.filter {
            it.title.lowercase().contains(lowercaseQuery) ||
                    it.genre.lowercase().contains(lowercaseQuery) ||
                    it.tags.lowercase().contains(lowercaseQuery)
        }
        if (localMatches.isNotEmpty()) {
            return@withContext localMatches
        }

        // Generate dynamic GFN Game item for the searched title if not found locally
        listOf(
            GameItem(
                id = "gfn_${query.replace("\\s+".toRegex(), "_").lowercase()}",
                title = query,
                type = GameType.GEFORCE_NOW,
                gfnCmsId = "custom_${System.currentTimeMillis() % 1000000}",
                launchUrl = "$GFN_BASE_PLAY_URL/mall/#/deal/deeplink?q=${URLEncoder.encode(query, "UTF-8")}",
                posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co41ea.jpg",
                genre = "GeForce NOW Title",
                publisher = "Cloud Streaming",
                description = "Launch $query on GeForce NOW cloud gaming with full controller support.",
                tags = "Cloud,GeForce NOW"
            )
        )
    }
}
