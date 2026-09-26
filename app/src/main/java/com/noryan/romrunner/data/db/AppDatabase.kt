package com.noryan.romrunner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.noryan.romrunner.data.dao.GameDao
import com.noryan.romrunner.data.dao.PlatformDao
import com.noryan.romrunner.data.model.Game
import com.noryan.romrunner.data.model.Platform

@Database(entities = [Platform::class, Game::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun platformDao(): PlatformDao
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "romrunner.db"
                )
                    // Pre-release: no real migrations yet, just wipe and reseed on schema changes.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
