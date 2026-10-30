package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GifDao {
    @Query("SELECT * FROM gif_creations ORDER BY createdAt DESC")
    fun observeAllCreations(): Flow<List<GifCreationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreation(entity: GifCreationEntity): Long

    @Query("SELECT * FROM gif_creations WHERE id = :id LIMIT 1")
    suspend fun getCreationById(id: Long): GifCreationEntity?

    @Query("DELETE FROM gif_creations WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Screen Recordings (Source MP4s) queries
    @Query("SELECT * FROM screen_recordings ORDER BY createdAt DESC")
    fun observeAllRecordings(): Flow<List<ScreenRecordingEntity>>

    @Query("SELECT * FROM screen_recordings WHERE videoFilePath = :filePath LIMIT 1")
    suspend fun getRecordingByPath(filePath: String): ScreenRecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(entity: ScreenRecordingEntity): Long

    @Query("DELETE FROM screen_recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)

    @Query("DELETE FROM screen_recordings")
    suspend fun deleteAllRecordings()
}
