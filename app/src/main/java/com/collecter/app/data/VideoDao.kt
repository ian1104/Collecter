package com.collecter.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM video_entries WHERE title LIKE '%' || :query || '%' ORDER BY page ASC, id ASC")
    fun search(query: String): Flow<List<VideoEntry>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<VideoEntry>)

    @Query("DELETE FROM video_entries")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM video_entries")
    fun count(): Flow<Int>
}
