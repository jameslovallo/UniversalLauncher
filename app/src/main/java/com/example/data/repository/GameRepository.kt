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
        // Remove any old pre-populated items so only actual installed Android games remain
        gameDao.deletePrePopulatedItems()
        // Auto-scan installed apps and games
        syncInstalledApps()
    }

    suspend fun insertUserGame(game: GameItem) {
        gameDao.insertGame(game)
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

    suspend fun setIsGame(id: String, isGame: Boolean) {
        gameDao.setIsGame(id, isGame)
    }

    suspend fun updateTitleAndArtwork(id: String, title: String, posterUrl: String?, iconUrl: String?) {
        gameDao.updateTitleAndArtwork(id, title, posterUrl, iconUrl)
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
