package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Android 16 (API 36) Live Updates Notification Builder & Manager.
 *
 * Key optimizations:
 * 1. Unified Material 3 Cobalt Indigo / Dynamic System Accent palette (no jarring multi-color segments).
 * 2. Anti-jitter / anti-twitch architecture:
 *    - Preserves a fixed `setWhen(sessionStartTimeMs)` across all updates of a session so SystemUI
 *      never re-runs card entry/button layout animations.
 *    - Throttles frame-level `notify()` calls to at most ~3.5 updates/sec (every 280ms) and deduplicates
 *      identical percentages so the ProgressStyle bar interpolates smoothly without convulsing.
 *    - Single-segment ProgressStyle (`setStyledByProgress(false)`) and stable single-line copy so
 *      action buttons never shift vertically.
 * 3. Tapping any Live Update status bar chip/bubble or notification card opens MainActivity directly
 *    to the corresponding page (`HOME`, `EDITOR`, `QUEUE`, or `GALLERY`).
 */
object LiveUpdateNotificationHelper {

    const val CHANNEL_ID_LIVE_UPDATES = "pixelloop_android16_live_updates"
    const val NOTIFICATION_ID_RECORDING = 1601
    const val NOTIFICATION_ID_ENCODING = 1602
    const val NOTIFICATION_ID_QUEUE = 1603
    const val NOTIFICATION_ID_COMPLETE = 1604

    const val ACTION_OPEN_FROM_LIVE_NOTIFICATION = "com.example.action.OPEN_FROM_LIVE_NOTIFICATION"
    const val EXTRA_OPEN_TAB = "extra_open_tab"

    const val TAB_HOME = "HOME"
    const val TAB_EDITOR = "EDITOR"
    const val TAB_QUEUE = "QUEUE"
    const val TAB_GALLERY = "GALLERY"

    const val RECOMMENDED_MAX_SECONDS = 30
    private const val MIN_UPDATE_INTERVAL_MS = 280L

    // Stable timestamps & throttling state to prevent notification progress bar & button jitter
    @Volatile
    private var recordingSessionStartMs: Long = 0L

    @Volatile
    private var encodingSessionStartMs: Long = 0L
    @Volatile
    private var lastEncodingNotifyElapsedMs: Long = 0L
    @Volatile
    private var lastEncodingNotifiedPercent: Int = -1

    @Volatile
    private var queueSessionStartMs: Long = 0L
    @Volatile
    private var lastQueueNotifyElapsedMs: Long = 0L
    @Volatile
    private var lastQueueNotifiedPercent: Int = -1
    @Volatile
    private var lastQueueNotifiedItem: Int = -1

    private data class NotificationPalette(
        val builderAccentColor: Int,
        val progressTrackActiveColor: Int
    )

    /**
     * Resolves a clean, unified Material 3 Expressive color pair that adapts to Dynamic Color (Android 12+)
     * and light/dark system themes, avoiding harsh neon rainbow colors.
     */
    private fun resolveUnifiedPalette(context: Context): NotificationPalette {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val accent500 = ContextCompat.getColor(context, android.R.color.system_accent1_500)
                val accent300 = ContextCompat.getColor(context, android.R.color.system_accent1_300)
                val accent600 = ContextCompat.getColor(context, android.R.color.system_accent1_600)
                return NotificationPalette(
                    builderAccentColor = if (isNight) accent300 else accent600,
                    progressTrackActiveColor = if (isNight) accent300 else accent500
                )
            }
        }

        // Unified Cobalt Indigo palette matching Color.kt
        return if (isNight) {
            NotificationPalette(
                builderAccentColor = Color.rgb(174, 198, 255),      // ExpressivePrimaryDark (#AEC6FF)
                progressTrackActiveColor = Color.rgb(138, 176, 255) // Harmonious luminous cobalt
            )
        } else {
            NotificationPalette(
                builderAccentColor = Color.rgb(41, 86, 155),        // ExpressivePrimaryLight (#29569B)
                progressTrackActiveColor = Color.rgb(59, 108, 183)  // Clean saturated cobalt
            )
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID_LIVE_UPDATES)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_LIVE_UPDATES,
                    context.getString(R.string.notif_channel_live_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_live_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Creates a PendingIntent that opens MainActivity and navigates directly to [targetTab]
     * when the user taps the Live Update status bar chip/bubble or notification card.
     */
    fun createOpenAppPendingIntent(
        context: Context,
        targetTab: String,
        requestCode: Int
    ): PendingIntent {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_FROM_LIVE_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, targetTab)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun resetRecordingSessionClock() {
        recordingSessionStartMs = System.currentTimeMillis()
    }

    /**
     * Builds the Android 16 Live Update notification for active screen recording.
     * Uses a unified Cobalt/Dynamic progress bar and stable `when` timestamp so neither
     * the progress bar nor the action buttons twitch on 1-second timer ticks.
     */
    fun buildRecordingLiveNotification(
        context: Context,
        elapsedSeconds: Int
    ): Notification {
        ensureChannel(context)
        if (elapsedSeconds == 0 || recordingSessionStartMs == 0L) {
            recordingSessionStartMs = System.currentTimeMillis()
        }

        val palette = resolveUnifiedPalette(context)
        val contentPendingIntent = createOpenAppPendingIntent(
            context = context,
            targetTab = TAB_HOME,
            requestCode = 200
        )

        val stopAndEditIntent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP_AND_EDIT
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            201,
            stopAndEditIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_CANCEL_RECORDING
        }
        val cancelPendingIntent = PendingIntent.getService(
            context,
            202,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        val formattedTimer = String.format("%02d:%02d", mins, secs)
        val shortChipText = "REC $formattedTimer"

        val clampedProgress = ((elapsedSeconds.coerceAtMost(RECOMMENDED_MAX_SECONDS) * 100) / RECOMMENDED_MAX_SECONDS)
            .coerceIn(2, 100)

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(false)
            .setProgress(clampedProgress)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(100)
                        .setColor(palette.progressTrackActiveColor)
                )
            )

        return NotificationCompat.Builder(context, CHANNEL_ID_LIVE_UPDATES)
            .setSmallIcon(R.drawable.ic_live_record_notif)
            .setColor(palette.builderAccentColor)
            .setContentTitle("正在录制屏幕 GIF · $formattedTimer")
            .setContentText("点击通知返回应用，或直接停止并载入剪辑室")
            .setShortCriticalText(shortChipText)
            .setWhen(recordingSessionStartMs)
            .setShowWhen(true)
            .setUsesChronometer(false)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(progressStyle)
            .setContentIntent(contentPendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    context.getString(R.string.notif_action_stop_edit),
                    stopPendingIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    context.getString(R.string.notif_action_cancel),
                    cancelPendingIntent
                ).build()
            )
            .build()
    }

    /**
     * Builds the ongoing Live Update notification for single-video GIF encoding.
     */
    fun buildEncodingNotification(
        context: Context,
        currentFrame: Int,
        totalFrames: Int
    ): Notification {
        ensureChannel(context)
        if (encodingSessionStartMs == 0L) {
            encodingSessionStartMs = System.currentTimeMillis()
        }

        val palette = resolveUnifiedPalette(context)
        val safeTotal = totalFrames.coerceAtLeast(1)
        val safeCurrent = currentFrame.coerceIn(0, safeTotal)
        val percent = ((safeCurrent.toFloat() / safeTotal) * 100).toInt().coerceIn(0, 100)

        val openEditorPendingIntent = createOpenAppPendingIntent(
            context = context,
            targetTab = TAB_EDITOR,
            requestCode = 300
        )

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(false)
            .setProgress(percent.coerceAtLeast(2))
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(100)
                        .setColor(palette.progressTrackActiveColor)
                )
            )

        return NotificationCompat.Builder(context, CHANNEL_ID_LIVE_UPDATES)
            .setSmallIcon(R.drawable.ic_live_record_notif)
            .setColor(palette.builderAccentColor)
            .setContentTitle("正在导出动态 GIF ($percent%)")
            .setContentText("已处理 $safeCurrent / $safeTotal 帧 · 点击返回剪辑室")
            .setShortCriticalText("GIF $percent%")
            .setWhen(encodingSessionStartMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(progressStyle)
            .setContentIntent(openEditorPendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "进入剪辑室",
                    openEditorPendingIntent
                ).build()
            )
            .build()
    }

    /**
     * Posts or updates an Android 16 Live Update notification for single GIF encoding progress.
     * Throttled so SystemUI progress bar & action buttons never jitter.
     */
    fun notifyEncodingProgress(
        context: Context,
        currentFrame: Int,
        totalFrames: Int,
        isComplete: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        if (isComplete) {
            encodingSessionStartMs = 0L
            lastEncodingNotifiedPercent = -1
            lastEncodingNotifyElapsedMs = 0L
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ENCODING)
            return
        }

        val safeTotal = totalFrames.coerceAtLeast(1)
        val percent = ((currentFrame.toFloat() / safeTotal) * 100).toInt().coerceIn(0, 100)
        val nowElapsed = SystemClock.elapsedRealtime()

        val isFirstOrLast = currentFrame <= 1 || currentFrame >= safeTotal || lastEncodingNotifiedPercent < 0
        val enoughTimePassed = (nowElapsed - lastEncodingNotifyElapsedMs) >= MIN_UPDATE_INTERVAL_MS
        val percentChangedEnough = kotlin.math.abs(percent - lastEncodingNotifiedPercent) >= 2

        if (!isFirstOrLast && (!enoughTimePassed || !percentChangedEnough)) {
            return
        }

        if (encodingSessionStartMs == 0L) {
            encodingSessionStartMs = System.currentTimeMillis()
        }
        lastEncodingNotifyElapsedMs = nowElapsed
        lastEncodingNotifiedPercent = percent

        val notification = buildEncodingNotification(
            context = context,
            currentFrame = currentFrame,
            totalFrames = safeTotal
        )
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ENCODING, notification)
    }

    /**
     * Builds the ongoing Live Update notification for sequential multi-video batch queue processing.
     */
    fun buildBatchQueueNotification(
        context: Context,
        currentItemIndex: Int,
        totalItems: Int,
        currentTaskTitle: String,
        currentFrame: Int,
        totalFrames: Int
    ): Notification {
        ensureChannel(context)
        if (queueSessionStartMs == 0L) {
            queueSessionStartMs = System.currentTimeMillis()
        }

        val palette = resolveUnifiedPalette(context)
        val safeTotalItems = totalItems.coerceAtLeast(1)
        val safeItemIdx = currentItemIndex.coerceIn(1, safeTotalItems)
        val safeTotalFrames = totalFrames.coerceAtLeast(1)
        val safeCurrentFrame = currentFrame.coerceIn(0, safeTotalFrames)

        val framePercent = ((safeCurrentFrame.toFloat() / safeTotalFrames) * 100).toInt().coerceIn(0, 100)
        val overallPercent = (((safeItemIdx - 1) * 100 + framePercent) / safeTotalItems).coerceIn(0, 100)

        val openQueuePendingIntent = createOpenAppPendingIntent(
            context = context,
            targetTab = TAB_QUEUE,
            requestCode = 400
        )

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(false)
            .setProgress(overallPercent.coerceAtLeast(2))
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(100)
                        .setColor(palette.progressTrackActiveColor)
                )
            )

        val cleanTitle = currentTaskTitle.ifBlank { "视频 #$safeItemIdx" }

        return NotificationCompat.Builder(context, CHANNEL_ID_LIVE_UPDATES)
            .setSmallIcon(R.drawable.ic_live_record_notif)
            .setColor(palette.builderAccentColor)
            .setContentTitle("队列后台处理中 ($safeItemIdx/$safeTotalItems · $overallPercent%)")
            .setContentText("$cleanTitle · 第 $safeCurrentFrame/$safeTotalFrames 帧 · 点击查看队列")
            .setShortCriticalText("队列 $safeItemIdx/$safeTotalItems")
            .setWhen(queueSessionStartMs)
            .setShowWhen(true)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(progressStyle)
            .setContentIntent(openQueuePendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "查看队列",
                    openQueuePendingIntent
                ).build()
            )
            .build()
    }

    /**
     * Posts or updates a background queue progress notification for sequential multi-video GIF conversion.
     * Throttled to prevent SystemUI progress bar and button jitter.
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
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        if (isComplete) {
            queueSessionStartMs = 0L
            lastQueueNotifiedPercent = -1
            lastQueueNotifiedItem = -1
            lastQueueNotifyElapsedMs = 0L
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_QUEUE)
            return
        }

        val safeTotalItems = totalItems.coerceAtLeast(1)
        val safeTotalFrames = totalFrames.coerceAtLeast(1)
        val framePercent = ((currentFrame.toFloat() / safeTotalFrames) * 100).toInt().coerceIn(0, 100)
        val overallPercent = (((currentItemIndex - 1).coerceAtLeast(0) * 100 + framePercent) / safeTotalItems)
            .coerceIn(0, 100)
        val nowElapsed = SystemClock.elapsedRealtime()

        val itemChanged = currentItemIndex != lastQueueNotifiedItem
        val isFirstOrLastFrame = currentFrame <= 1 || currentFrame >= safeTotalFrames
        val enoughTimePassed = (nowElapsed - lastQueueNotifyElapsedMs) >= MIN_UPDATE_INTERVAL_MS
        val percentChangedEnough = kotlin.math.abs(overallPercent - lastQueueNotifiedPercent) >= 2

        if (!itemChanged && !isFirstOrLastFrame && (!enoughTimePassed || !percentChangedEnough)) {
            return
        }

        if (queueSessionStartMs == 0L) {
            queueSessionStartMs = System.currentTimeMillis()
        }
        lastQueueNotifyElapsedMs = nowElapsed
        lastQueueNotifiedPercent = overallPercent
        lastQueueNotifiedItem = currentItemIndex

        val notification = buildBatchQueueNotification(
            context = context,
            currentItemIndex = currentItemIndex,
            totalItems = safeTotalItems,
            currentTaskTitle = currentTaskTitle,
            currentFrame = currentFrame,
            totalFrames = safeTotalFrames
        )

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_QUEUE, notification)
    }

    /**
     * Posts a tappable completion notification when background GIF export or queue processing finishes,
     * allowing the user to tap the notification bubble/card to jump straight into the app.
     */
    fun notifyProcessingCompleted(
        context: Context,
        title: String,
        subtitle: String,
        targetTab: String = TAB_GALLERY
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val palette = resolveUnifiedPalette(context)
        val openPendingIntent = createOpenAppPendingIntent(
            context = context,
            targetTab = targetTab,
            requestCode = 500
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_LIVE_UPDATES)
            .setSmallIcon(R.drawable.ic_live_record_notif)
            .setColor(palette.builderAccentColor)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
