package com.noryan.romrunner.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.noryan.romrunner.data.model.Platform
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {
    @Query("SELECT * FROM platforms ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Platform>>

    @Query("SELECT * FROM platforms ORDER BY sortOrder, name")
    suspend fun getAllOnce(): List<Platform>

    @Query("SELECT * FROM platforms WHERE id = :id")
    suspend fun getById(id: Long): Platform?

    @Insert
    suspend fun insert(platform: Platform): Long

    @Update
    suspend fun update(platform: Platform)

    @Delete
    suspend fun delete(platform: Platform)
}
