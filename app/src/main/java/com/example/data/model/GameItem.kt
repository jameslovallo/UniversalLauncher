package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class GameType {
    GEFORCE_NOW,
    INSTALLED_APP,
    SHORTCUT,
    CUSTOM_CLOUD
}

@Entity(tableName = "games")
data class GameItem(
    @PrimaryKey
    val id: String,
    val title: String,
    val type: GameType,
    val isGame: Boolean = true, // true -> Games Tab (Steam poster grid), false -> Apps Tab
    val packageName: String? = null,
    val activityName: String? = null,
    val launchUrl: String? = null,
    val gfnCmsId: String? = null,
    val posterUrl: String? = null,
    val heroBannerUrl: String? = null,
    val iconUrl: String? = null,
    val genre: String = "Game",
    val publisher: String = "",
    val description: String = "",
    val isFavorite: Boolean = false,
    val lastPlayedTimestamp: Long = 0L,
    val playCount: Int = 0,
    val customArtworkSource: String? = null, // "GFN", "STEAMGRIDDB", "IGDB", "LOCAL", "CUSTOM_URL"
    val tags: String = "",
    val isPinned: Boolean = false,
    val isHidden: Boolean = false,
    // Position within the favorites view. Lower values appear first.
    val favoriteOrder: Int = 0
) {
    val displayCategory: String
        get() = when (type) {
            GameType.GEFORCE_NOW -> "GeForce NOW"
            GameType.INSTALLED_APP -> if (isGame) "Android Game" else "Installed App"
            GameType.SHORTCUT -> "Shortcut"
            GameType.CUSTOM_CLOUD -> "Cloud Game"
        }
}
