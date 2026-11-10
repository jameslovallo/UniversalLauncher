package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.GameDao
import com.example.data.model.GameItem
import com.example.data.model.GameType
import com.example.data.service.GeForceNowService
import com.example.data.service.InstalledAppsManager
import kotlinx.coroutines.flow.Flow

class GameRepository(
    private val gameDao: GameDao,
    private val installedAppsManager: InstalledAppsManager
) {
    val allGames: Flow<List<GameItem>> = gameDao.getAllGames()
    val favoriteGames: Flow<List<GameItem>> = gameDao.getFavoriteGames()
    val recentGames: Flow<List<GameItem>> = gameDao.getRecentGames()

    fun getGamesByType(type: GameType): Flow<List<GameItem>> = gameDao.getGamesByType(type)

    suspend fun getGameById(id: String): GameItem? = gameDao.getGameById(id)

    suspend fun initializeLibraryIfEmpty() {
        val count = gameDao.getCount()
        if (count == 0) {
            // Seed with rich default GeForce NOW catalog
            gameDao.insertGames(GeForceNowService.DEFAULT_GFN_LIBRARY)
            // Add popular cloud shortcuts
            val defaultShortcuts = listOf(
                GameItem(
                    id = "shortcut_xbox_cloud",
                    title = "Xbox Cloud Gaming",
                    type = GameType.SHORTCUT,
                    launchUrl = "https://www.xbox.com/play",
                    posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5v43.jpg",
                    heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc73e7.jpg",
                    genre = "Cloud Gaming Service",
                    publisher = "Microsoft",
                    description = "Stream hundreds of console games on the cloud.",
                    tags = "Xbox,Cloud,Streaming"
                ),
                GameItem(
                    id = "shortcut_steam_link",
                    title = "Steam Link",
                    type = GameType.SHORTCUT,
                    launchUrl = "steam://open/bigpicture",
                    posterUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co2m37.jpg",
                    heroBannerUrl = "https://images.igdb.com/igdb/image/upload/t_1080p/sc6q6w.jpg",
                    genre = "Game Streaming",
                    publisher = "Valve",
                    description = "Stream games directly from your PC to your mobile device.",
                    tags = "Steam,Remote Play,PC"
                )
            )
            gameDao.insertGames(defaultShortcuts)
        }
        // Auto-scan installed apps and update
        syncInstalledApps()
    }

    suspend fun syncGfnCatalog() {
        val liveTitles = GeForceNowService.DEFAULT_GFN_LIBRARY
        for (gfnGame in liveTitles) {
            val existing = gameDao.getGameById(gfnGame.id)
            if (existing == null) {
                gameDao.insertGame(gfnGame)
            } else {
                // Keep favorite and custom artwork, update launchUrl and metadata
                val updated = existing.copy(
                    launchUrl = gfnGame.launchUrl,
                    gfnCmsId = gfnGame.gfnCmsId,
                    description = if (existing.description.isBlank()) gfnGame.description else existing.description,
                    posterUrl = existing.posterUrl ?: gfnGame.posterUrl,
                    heroBannerUrl = existing.heroBannerUrl ?: gfnGame.heroBannerUrl
                )
                gameDao.updateGame(updated)
            }
        }
    }

    suspend fun syncInstalledApps() {
        val installed = installedAppsManager.getInstalledApps()
        for (app in installed) {
            val existing = gameDao.getGameById(app.id)
            if (existing == null) {
                gameDao.insertGame(app)
            } else {
                val updated = existing.copy(
                    title = app.title,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    iconUrl = app.iconUrl
                )
                gameDao.updateGame(updated)
            }
        }
    }

    suspend fun addCustomShortcut(
        title: String,
        url: String,
        posterUrl: String?,
        type: GameType,
        genre: String = "Web Shortcut"
    ) {
        val id = "custom_${System.currentTimeMillis()}"
        val item = GameItem(
            id = id,
            title = title,
            type = type,
            launchUrl = url,
            posterUrl = posterUrl,
            genre = genre,
            publisher = "Custom Link",
            description = "Custom game or shortcut ($url)",
            tags = "Custom,Shortcut"
        )
        gameDao.insertGame(item)
    }

    suspend fun updateArtwork(gameId: String, posterUrl: String, heroUrl: String?, source: String) {
        gameDao.updateArtwork(gameId, posterUrl, heroUrl, source)
    }

    suspend fun toggleFavorite(gameId: String, currentStatus: Boolean) {
        gameDao.setFavorite(gameId, !currentStatus)
    }

    suspend fun recordPlay(gameId: String) {
        gameDao.recordPlay(gameId, System.currentTimeMillis())
    }

    suspend fun deleteGame(gameId: String) {
        gameDao.deleteGameById(gameId)
    }

    companion object {
        fun create(context: Context): GameRepository {
            val db = AppDatabase.getDatabase(context)
            val installedAppsManager = InstalledAppsManager(context)
            return GameRepository(db.gameDao(), installedAppsManager)
        }
    }
}
