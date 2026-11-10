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
    @Query("SELECT * FROM games ORDER BY isPinned DESC, lastPlayedTimestamp DESC, title ASC")
    fun getAllGames(): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedTimestamp DESC, title ASC")
    fun getFavoriteGames(): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 20")
    fun getRecentGames(): Flow<List<GameItem>>

    @Query("SELECT * FROM games WHERE type = :type ORDER BY title ASC")
    fun getGamesByType(type: GameType): Flow<List<GameItem>>

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

    @Query("UPDATE games SET lastPlayedTimestamp = :timestamp, playCount = playCount + 1 WHERE id = :id")
    suspend fun recordPlay(id: String, timestamp: Long)

    @Query("UPDATE games SET posterUrl = :posterUrl, heroBannerUrl = :heroUrl, customArtworkSource = :source WHERE id = :id")
    suspend fun updateArtwork(id: String, posterUrl: String, heroUrl: String?, source: String)

    @Query("UPDATE games SET isGame = :isGame WHERE id = :id")
    suspend fun setIsGame(id: String, isGame: Boolean)

    @Query("UPDATE games SET title = :title, posterUrl = :posterUrl, iconUrl = :iconUrl WHERE id = :id")
    suspend fun updateTitleAndArtwork(id: String, title: String, posterUrl: String?, iconUrl: String?)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteGameById(id: String)

    @Query("DELETE FROM games WHERE id LIKE 'gfn_%' OR id IN ('shortcut_xbox_cloud', 'shortcut_steam_link')")
    suspend fun deletePrePopulatedItems()

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getCount(): Int
}
