package com.example.temiguide.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface InteractionLogDao {
    @Insert
    suspend fun insert(log: InteractionLog)

    @Query("SELECT * FROM interaction_logs ORDER BY timestamp DESC LIMIT 100")
    suspend fun getRecent(): List<InteractionLog>

    @Query("SELECT COUNT(*) FROM interaction_logs WHERE timestamp > :since")
    suspend fun countSince(since: Long): Int

    @Query("DELETE FROM interaction_logs WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long)
}
