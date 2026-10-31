package com.example.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

/**
 * Foreground Service dedicated to keeping single-video GIF encoding and multi-video batch queue
 * conversion alive when PixelLoop is placed in the background.
 *
 * Uses `FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` on Android 14+ (which requires no extra manifest
 * sub-permissions or Play Console video declarations) combined with a partial CPU WakeLock so
 * Android's Cached App Freezer never suspends frame extraction or LZW encoding threads.
 */
class GifProcessingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentNotificationId: Int = LiveUpdateNotificationHelper.NOTIFICATION_ID_ENCODING
    private var lastNotification: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ENCODING_FOREGROUND -> {
                val notifId = intent.getIntExtra(
                    EXTRA_NOTIFICATION_ID,
                    LiveUpdateNotificationHelper.NOTIFICATION_ID_ENCODING
                )
                val isQueue = intent.getBooleanExtra(EXTRA_IS_QUEUE_MODE, false)
                val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
                val currentItem = intent.getIntExtra(EXTRA_CURRENT_ITEM, 1)
                val totalItems = intent.getIntExtra(EXTRA_TOTAL_ITEMS, 1)
                val currentFrame = intent.getIntExtra(EXTRA_CURRENT_FRAME, 0)
                val totalFrames = intent.getIntExtra(EXTRA_TOTAL_FRAMES, 100)

                currentNotificationId = notifId
                acquireWakeLockIfNeeded()

                val notification = if (isQueue) {
                    LiveUpdateNotificationHelper.buildCurrentBatchQueueNotification(
                        context = this,
                        fallbackItemIndex = currentItem,
                        fallbackTotalItems = totalItems,
                        fallbackTitle = title,
                        fallbackTotalFrames = totalFrames
                    )
                } else {
                    LiveUpdateNotificationHelper.buildCurrentEncodingNotification(
                        context = this,
                        fallbackTotalFrames = totalFrames
                    )
                }
                lastNotification = notification
                promoteToForeground(notifId, notification)
            }

            ACTION_STOP_ENCODING_FOREGROUND -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground(notificationId: Int, notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                )
            } else {
                startForeground(notificationId, notification)
            }
        }
    }

    override fun onTimeout(startId: Int) {
        // If a large batch queue exceeds the ~3 minute shortService window,
        // re-promote or gracefully stop foreground while keeping WakeLock until the current frame loop finishes.
        val notif = lastNotification
        if (notif != null) {
            runCatching {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
        } else {
            stopSelf(startId)
        }
    }

    private fun acquireWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PixelLoop::GifEncodingWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10-minute safety ceiling
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_ENCODING_FOREGROUND = "com.example.action.START_ENCODING_FOREGROUND"
        const val ACTION_STOP_ENCODING_FOREGROUND = "com.example.action.STOP_ENCODING_FOREGROUND"

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_QUEUE_MODE = "extra_is_queue_mode"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_CURRENT_ITEM = "extra_current_item"
        const val EXTRA_TOTAL_ITEMS = "extra_total_items"
        const val EXTRA_CURRENT_FRAME = "extra_current_frame"
        const val EXTRA_TOTAL_FRAMES = "extra_total_frames"

        fun startEncodingForeground(
            context: Context,
            currentFrame: Int,
            totalFrames: Int
        ) {
            val intent = Intent(context, GifProcessingService::class.java).apply {
                action = ACTION_START_ENCODING_FOREGROUND
                putExtra(EXTRA_NOTIFICATION_ID, LiveUpdateNotificationHelper.NOTIFICATION_ID_ENCODING)
                putExtra(EXTRA_IS_QUEUE_MODE, false)
                putExtra(EXTRA_CURRENT_FRAME, currentFrame)
                putExtra(EXTRA_TOTAL_FRAMES, totalFrames)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun startQueueForeground(
            context: Context,
            currentItemIndex: Int,
            totalItems: Int,
            currentTaskTitle: String,
            currentFrame: Int,
            totalFrames: Int
        ) {
            val intent = Intent(context, GifProcessingService::class.java).apply {
                action = ACTION_START_ENCODING_FOREGROUND
                putExtra(EXTRA_NOTIFICATION_ID, LiveUpdateNotificationHelper.NOTIFICATION_ID_QUEUE)
                putExtra(EXTRA_IS_QUEUE_MODE, true)
                putExtra(EXTRA_CURRENT_ITEM, currentItemIndex)
                putExtra(EXTRA_TOTAL_ITEMS, totalItems)
                putExtra(EXTRA_TASK_TITLE, currentTaskTitle)
                putExtra(EXTRA_CURRENT_FRAME, currentFrame)
                putExtra(EXTRA_TOTAL_FRAMES, totalFrames)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stopEncodingForeground(context: Context) {
            val intent = Intent(context, GifProcessingService::class.java).apply {
                action = ACTION_STOP_ENCODING_FOREGROUND
            }
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
