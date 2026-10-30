package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gif_creations")
data class GifCreationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val filePath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationMs: Long,
    val frameCount: Int,
    val fileSizeBytes: Long,
    val sourceType: String, // "SCREEN_RECORD" or "VIDEO_IMPORT" or "BATCH_QUEUE"
    val cropRatioLabel: String,
    val playbackSpeed: Float,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Represents an in-app screen recording MP4 saved in app storage so users can
 * re-open it in the Editor to create new GIFs or delete the source MP4 to reclaim storage space.
 */
@Entity(tableName = "screen_recordings")
data class ScreenRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val videoFilePath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis()
)
