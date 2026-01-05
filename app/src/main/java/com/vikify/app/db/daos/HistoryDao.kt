package com.vikify.app.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vikify.app.db.entities.PlayEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PlayEvent)

    @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<PlayEvent>>
    
    @Query("SELECT * FROM play_history WHERE timestamp > :startTime ORDER BY timestamp DESC")
    fun getEventsSince(startTime: Long): List<PlayEvent>

    @Query("DELETE FROM play_history WHERE id IN (:ids)")
    suspend fun deleteEvents(ids: List<Long>)
    
    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}
