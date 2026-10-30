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
    val sourceType: String, // "SCREEN_RECORD" or "VIDEO_IMPORT" or "DEMO_SYNTH"
    val cropRatioLabel: String,
    val playbackSpeed: Float,
    val createdAt: Long = System.currentTimeMillis()
)
