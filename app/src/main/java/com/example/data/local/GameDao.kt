package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.GameItem
import com.example.data.model.GameType
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE isHidden = 0 AND isDeleted = 0 ORDER BY lastPlayedTimestamp DESC, title ASC")
    fun getAllGames(): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isHidden = 0 AND isDeleted = 0 ORDER BY lastPlayedTimestamp DESC, title ASC")
    fun getFavoriteGames(): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isGame = 0 AND isHidden = 0 AND isDeleted = 0 ORDER BY favoriteOrder ASC, title ASC")
    fun getFavoriteApps(): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isGame = 0 AND isHidden = 0 AND isDeleted = 0 ORDER BY favoriteOrder ASC, title ASC")
    suspend fun getFavoriteAppsNow(): List<GameItem>

    @Query("SELECT * FROM games WHERE lastPlayedTimestamp > 0 AND isHidden = 0 AND isDeleted = 0 ORDER BY lastPlayedTimestamp DESC LIMIT 20")
    fun getRecentGames(): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE type = :type AND isHidden = 0 AND isDeleted = 0 ORDER BY title ASC")
    fun getGamesByType(type: GameType): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE isHidden = 1 AND isDeleted = 0 ORDER BY title ASC")
    fun getHiddenGames(): Flow<List<GameItem>>

    // Intentionally unfiltered: maintenance code needs to see tombstones (deleted rows).
    @Query("SELECT * FROM games WHERE type = :type")
    suspend fun getGamesByTypeNow(type: GameType): List<GameItem>

    @Query("SELECT * FROM games WHERE id = :id LIMIT 1")
    suspend fun getGameById(id: String): GameItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<GameItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: GameItem)

    @Update
    suspend fun updateGame(game: GameItem)

    @Query("UPDATE games SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE games SET favoriteOrder = :order WHERE id = :id")
    suspend fun setFavoriteOrder(id: String, order: Int)

    @Query("UPDATE games SET lastPlayedTimestamp = :timestamp, playCount = playCount + 1 WHERE id = :id")
    suspend fun recordPlay(id: String, timestamp: Long)

    @Query("UPDATE games SET posterUrl = :posterUrl, heroBannerUrl = :heroUrl, customArtworkSource = :source WHERE id = :id")
    suspend fun updateArtwork(id: String, posterUrl: String, heroUrl: String?, source: String)

    @Query("SELECT * FROM games WHERE type IN ('INSTALLED_APP', 'SHORTCUT') AND isGame = 1 AND isHidden = 0 AND isDeleted = 0 AND (posterUrl IS NULL OR posterUrl = '') AND (customArtworkSource IS NULL OR customArtworkSource = '')")
    suspend fun getInstalledGamesMissingPoster(): List<GameItem>

    @Query("UPDATE games SET isHidden = :isHidden WHERE id = :id")
    suspend fun setHidden(id: String, isHidden: Boolean)

    @Query("UPDATE games SET isGame = :isGame WHERE id = :id")
    suspend fun setIsGame(id: String, isGame: Boolean)

    @Query("UPDATE games SET title = :title, posterUrl = :posterUrl, iconUrl = :iconUrl, isCustomized = 1 WHERE id = :id")
    suspend fun updateTitleAndArtwork(id: String, title: String, posterUrl: String?, iconUrl: String?)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGameById(id: String)

    /** Explicit user deletion. Kept as a tombstone so syncs cannot re-add the item. */
    @Query("UPDATE games SET isDeleted = 1, isFavorite = 0, favoriteOrder = 0 WHERE id = :id")
    suspend fun markDeleted(id: String)

    // Never touch tombstones: a deleted catalog game must stay deleted across launches.
    @Query("DELETE FROM games WHERE (id LIKE 'gfn_%' OR id IN ('shortcut_xbox_cloud', 'shortcut_steam_link')) AND isDeleted = 0")
    suspend fun deletePrePopulatedItems()

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getCount(): Int
}
