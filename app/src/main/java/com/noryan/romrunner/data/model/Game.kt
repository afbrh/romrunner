package com.noryan.romrunner.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One scanned ROM file, tied to the platform that matched its extension. */
@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: Long,
    val title: String,
    val fileName: String,
    val fileUri: String,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
