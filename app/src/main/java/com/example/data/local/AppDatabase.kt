package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.GameItem
import com.example.data.model.GameType

class Converters {
    @TypeConverter
    fun fromGameType(type: GameType?): String? = type?.name

    @TypeConverter
    fun toGameType(value: String?): GameType =
        value?.let { runCatching { GameType.valueOf(it) }.getOrNull() } ?: GameType.GEFORCE_NOW
}

@Database(entities = [GameItem::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nexus_launcher_db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
