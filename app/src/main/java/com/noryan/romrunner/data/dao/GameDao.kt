package com.noryan.romrunner.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.noryan.romrunner.data.model.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Game>>

    @Query("SELECT fileUri FROM games")
    suspend fun getAllFileUris(): List<String>

    @Insert
    suspend fun insert(game: Game): Long

    @Insert
    suspend fun insertAll(games: List<Game>)

    @Update
    suspend fun update(game: Game)

    @Delete
    suspend fun delete(game: Game)

    @Query("DELETE FROM games WHERE fileUri IN (:fileUris)")
    suspend fun deleteByFileUris(fileUris: List<String>)

    @Query("DELETE FROM games WHERE platformId = :platformId")
    suspend fun deleteAllForPlatform(platformId: Long)

    /** Moves already-scanned games with a given extension from one platform to another, for fixing past mis-seeds. */
    @Query("UPDATE games SET platformId = :toPlatformId WHERE platformId = :fromPlatformId AND fileName LIKE '%.' || :extension")
    suspend fun reassignByExtension(fromPlatformId: Long, toPlatformId: Long, extension: String)
}
