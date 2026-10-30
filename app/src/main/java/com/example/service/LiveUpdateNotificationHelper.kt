package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Android 16 (API 36) Live Updates Notification Builder & Manager.
 * Uses NotificationCompat.ProgressStyle + setRequestPromotedOngoing(true) + setShortCriticalText()
 * to surface live screen recording timers and GIF encoding progress in the Android 16 status bar chip,
 * lock screen, and top of the notification drawer.
 */
object LiveUpdateNotificationHelper {

    const val CHANNEL_ID_LIVE_UPDATES = "pixelloop_android16_live_updates"
    const val NOTIFICATION_ID_RECORDING = 1601
    const val NOTIFICATION_ID_ENCODING = 1602

    const val RECOMMENDED_MAX_SECONDS = 30

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID_LIVE_UPDATES)
            if (existing == null) {
                // Must be at least IMPORTANCE_DEFAULT (never IMPORTANCE_MIN) for Android 16 Live Updates
                val channel = NotificationChannel(
                    CHANNEL_ID_LIVE_UPDATES,
                    context.getString(R.string.notif_channel_live_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_live_desc)
                    setShowBadge(false)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Checks whether the system currently allows Android 16 Promoted Ongoing Live Updates.
     */
    fun canPostPromotedLiveUpdates(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 36) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.canPostPromotedNotifications() }.getOrDefault(true)
        } else {
            false
        }
    }

    /**
     * Builds the Android 16 Live Update notification for active screen recording.
     * Strictly adheres to Android 16 promotion rules:
     * - setOngoing(true)
     * - setRequestPromotedOngoing(true)
     * - ProgressStyle with segments & points
     * - setShortCriticalText("REC 00:XX") for the Status Bar Chip
     * - No setColorized(true) and no custom RemoteViews
     */
    fun buildRecordingLiveNotification(
        context: Context,
        elapsedSeconds: Int
    ): Notification {
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAndEditIntent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP_AND_EDIT
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            101,
            stopAndEditIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_CANCEL_RECORDING
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            102,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        val formattedTimer = String.format("%02d:%02d", mins, secs)
        val shortChipText = "REC $formattedTimer"

        val clampedProgress = (elapsedSeconds.coerceAtMost(RECOMMENDED_MAX_SECONDS) * 100) / RECOMMENDED_MAX_SECONDS

        // Android 16 ProgressStyle with 3 expressive segments:
        // Segment 1 (0-10s): Golden social meme duration (Emerald)
        // Segment 2 (10-20s): Standard demo tutorial duration (Amber Gold)
        // Segment 3 (20-30s+): Extended capture duration (Signal Coral)
        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(clampedProgress)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(34).setColor(Color.rgb(0, 230, 118)),
                    NotificationCompat.ProgressStyle.Segment(33).setColor(Color.rgb(255, 179, 0)),
                    NotificationCompat.ProgressStyle.Segment(33).setColor(Color.rgb(255, 82, 82))
                )
            )
            .setProgressPoints(
                listOf(
                    NotificationCompat.ProgressStyle.Point(34).setColor(Color.rgb(0, 230, 118)),
                    NotificationCompat.ProgressStyle.Point(67).setColor(Color.rgb(255, 179, 0))
                )
            )

        val stageHint = when {
            elapsedSeconds < 10 -> "黄金动图区间 · 点击下方按钮立即合成 GIF"
            elapsedSeconds < 20 -> "标准演示时长 · 画面已实时捕获 ($formattedTimer)"
            else -> "长录屏阶段 · 建议在剪辑台截取精华片段"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_LIVE_UPDATES)
            .setSmallIcon(R.drawable.ic_live_record_notif)
            .setContentTitle("正在录制屏幕 GIF ($formattedTimer)")
            .setContentText(stageHint)
            .setShortCriticalText(shortChipText)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(progressStyle)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_live_record_notif,
                context.getString(R.string.notif_action_stop_edit),
                stopPendingIntent
            )
            .addAction(
                0,
                context.getString(R.string.notif_action_cancel),
                cancelPendingIntent
            )
            .build()
    }

    /**
     * Posts or updates an Android 16 Live Update notification for GIF encoding progress.
     */
    fun notifyEncodingProgress(
        context: Context,
        currentFrame: Int,
        totalFrames: Int,
        isComplete: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        ensureChannel(context)

        if (isComplete) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ENCODING)
            return
        }

        val percent = ((currentFrame.toFloat() / totalFrames.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(percent)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(100).setColor(Color.rgb(38, 198, 218))
                )
            )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LIVE_UPDATES)
            .setSmallIcon(R.drawable.ic_live_record_notif)
            .setContentTitle("正在合成动态 GIF ($percent%)")
            .setContentText("已编码 $currentFrame / $totalFrames 帧 · LZW 256色高速压缩中")
            .setShortCriticalText("GIF $percent%")
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(progressStyle)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ENCODING, notification)
    }

    /**
     * Posts or updates a background queue progress notification for sequential multi-video GIF conversion.
     */
    fun notifyBatchQueueProgress(
        context: Context,
        currentItemIndex: Int,
        totalItems: Int,
        currentTaskTitle: String,
        currentFrame: Int,
        totalFrames: Int,
        isComplete: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        ensureChannel(context)

        if (isComplete) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_QUEUE)
            return
        }

        val framePercent = ((currentFrame.toFloat() / totalFrames.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
        val overallPercent = (((currentItemIndex - 1).coerceAtLeast(0) * 100 + framePercent) / totalItems.coerceAtLeast(1))
            .coerceIn(0, 100)

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(overallPercent)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(100).setColor(Color.rgb(38, 198, 218))
                )
            )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LIVE_UPDATES)
            .setSmallIcon(R.drawable.ic_live_record_notif)
            .setContentTitle("队列后台处理中 ($currentItemIndex/$totalItems)")
            .setContentText("$currentTaskTitle · $framePercent% ($currentFrame/$totalFrames 帧)")
            .setShortCriticalText("队列 $currentItemIndex/$totalItems")
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(progressStyle)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_QUEUE, notification)
    }

    const val NOTIFICATION_ID_QUEUE = 1603
}
