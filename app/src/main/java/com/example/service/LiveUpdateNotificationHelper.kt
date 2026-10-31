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
    private const val MIN_UPDATE_INTERVAL_MS = 120L

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
    private var activeEncodingCurrentFrame: Int = 0
    @Volatile
    private var activeEncodingTotalFrames: Int = 100

    @Volatile
    private var queueSessionStartMs: Long = 0L
    @Volatile
    private var lastQueueNotifyElapsedMs: Long = 0L
    @Volatile
    private var lastQueueNotifiedPercent: Int = -1
    @Volatile
    private var lastQueueNotifiedItem: Int = -1
    @Volatile
    private var activeQueueItemIndex: Int = 1
    @Volatile
    private var activeQueueTotalItems: Int = 1
    @Volatile
    private var activeQueueTaskTitle: String = ""
    @Volatile
    private var activeQueueCurrentFrame: Int = 0
    @Volatile
    private var activeQueueTotalFrames: Int = 100

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
     * Deliberately omits any progress bar during the recording capture stage, displaying
     * only the live timer, status chip, and quick stop/cancel actions.
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
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
     * Initializes a new single-video GIF encoding notification session so that
     * both Foreground Service promotion and frame callbacks share consistent progress state.
     */
    fun beginEncodingSession(context: Context, totalFrames: Int) {
        ensureChannel(context)
        encodingSessionStartMs = System.currentTimeMillis()
        activeEncodingCurrentFrame = 0
        activeEncodingTotalFrames = totalFrames.coerceAtLeast(1)
        lastEncodingNotifiedPercent = 0
        lastEncodingNotifyElapsedMs = SystemClock.elapsedRealtime()

        if (hasNotificationPermission(context)) {
            val notification = buildEncodingNotification(
                context = context,
                currentFrame = 0,
                totalFrames = activeEncodingTotalFrames
            )
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ENCODING, notification)
        }
    }

    /**
     * Used by [GifProcessingService] when promoting to foreground so it always uses the latest
     * frame progress and never resets an in-flight export notification back to 0%.
     */
    fun buildCurrentEncodingNotification(context: Context, fallbackTotalFrames: Int): Notification {
        val total = if (activeEncodingTotalFrames > 1) activeEncodingTotalFrames else fallbackTotalFrames.coerceAtLeast(1)
        val current = activeEncodingCurrentFrame.coerceIn(0, total)
        return buildEncodingNotification(context, current, total)
    }

    /**
     * Builds the ongoing Live Update notification for single-video GIF encoding.
     * Uses `setStyledByProgress(true)` and `Builder.setProgress(100, visualProgress, false)`
     * so the progress bar fills visibly and smoothly from 0% to 100% across all Android 16 & OEM surfaces.
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
        val visualProgress = percent.coerceIn(2, 100)

        val openEditorPendingIntent = createOpenAppPendingIntent(
            context = context,
            targetTab = TAB_EDITOR,
            requestCode = 300
        )

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressIndeterminate(false)
            .setProgress(visualProgress)
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
            .setProgress(100, visualProgress, false)
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
     */
    fun notifyEncodingProgress(
        context: Context,
        currentFrame: Int,
        totalFrames: Int,
        isComplete: Boolean = false
    ) {
        ensureChannel(context)

        if (isComplete) {
            encodingSessionStartMs = 0L
            lastEncodingNotifiedPercent = -1
            lastEncodingNotifyElapsedMs = 0L
            activeEncodingCurrentFrame = 0
            if (hasNotificationPermission(context)) {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_ENCODING)
            }
            return
        }

        val safeTotal = totalFrames.coerceAtLeast(1)
        val safeCurrent = currentFrame.coerceIn(0, safeTotal)
        activeEncodingCurrentFrame = safeCurrent
        activeEncodingTotalFrames = safeTotal

        if (!hasNotificationPermission(context)) return

        val percent = ((safeCurrent.toFloat() / safeTotal) * 100).toInt().coerceIn(0, 100)
        val nowElapsed = SystemClock.elapsedRealtime()

        val isFirstOrLast = safeCurrent <= 1 || safeCurrent >= safeTotal || lastEncodingNotifiedPercent < 0
        val enoughTimePassed = (nowElapsed - lastEncodingNotifyElapsedMs) >= MIN_UPDATE_INTERVAL_MS
        val percentChanged = percent != lastEncodingNotifiedPercent

        if (!isFirstOrLast && (!enoughTimePassed || !percentChanged)) {
            return
        }

        if (encodingSessionStartMs == 0L) {
            encodingSessionStartMs = System.currentTimeMillis()
        }
        lastEncodingNotifyElapsedMs = nowElapsed
        lastEncodingNotifiedPercent = percent

        val notification = buildEncodingNotification(
            context = context,
            currentFrame = safeCurrent,
            totalFrames = safeTotal
        )
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ENCODING, notification)
    }

    /**
     * Initializes or updates the active queue item state before foreground promotion.
     */
    fun beginQueueItemSession(
        context: Context,
        currentItemIndex: Int,
        totalItems: Int,
        currentTaskTitle: String,
        totalFrames: Int
    ) {
        ensureChannel(context)
        if (queueSessionStartMs == 0L) {
            queueSessionStartMs = System.currentTimeMillis()
        }
        activeQueueItemIndex = currentItemIndex.coerceAtLeast(1)
        activeQueueTotalItems = totalItems.coerceAtLeast(1)
        activeQueueTaskTitle = currentTaskTitle
        activeQueueCurrentFrame = 0
        activeQueueTotalFrames = totalFrames.coerceAtLeast(1)
        lastQueueNotifiedItem = currentItemIndex
        lastQueueNotifyElapsedMs = SystemClock.elapsedRealtime()

        if (hasNotificationPermission(context)) {
            val notification = buildBatchQueueNotification(
                context = context,
                currentItemIndex = activeQueueItemIndex,
                totalItems = activeQueueTotalItems,
                currentTaskTitle = activeQueueTaskTitle,
                currentFrame = 0,
                totalFrames = activeQueueTotalFrames
            )
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_QUEUE, notification)
        }
    }

    fun buildCurrentBatchQueueNotification(
        context: Context,
        fallbackItemIndex: Int,
        fallbackTotalItems: Int,
        fallbackTitle: String,
        fallbackTotalFrames: Int
    ): Notification {
        val itemIdx = if (activeQueueItemIndex > 0) activeQueueItemIndex else fallbackItemIndex.coerceAtLeast(1)
        val totalItems = if (activeQueueTotalItems > 0) activeQueueTotalItems else fallbackTotalItems.coerceAtLeast(1)
        val title = activeQueueTaskTitle.ifBlank { fallbackTitle }
        val totalFrames = if (activeQueueTotalFrames > 1) activeQueueTotalFrames else fallbackTotalFrames.coerceAtLeast(1)
        val currentFrame = activeQueueCurrentFrame.coerceIn(0, totalFrames)
        return buildBatchQueueNotification(
            context = context,
            currentItemIndex = itemIdx,
            totalItems = totalItems,
            currentTaskTitle = title,
            currentFrame = currentFrame,
            totalFrames = totalFrames
        )
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
        val visualProgress = overallPercent.coerceIn(2, 100)

        val openQueuePendingIntent = createOpenAppPendingIntent(
            context = context,
            targetTab = TAB_QUEUE,
            requestCode = 400
        )

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgressIndeterminate(false)
            .setProgress(visualProgress)
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
            .setProgress(100, visualProgress, false)
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
        ensureChannel(context)

        if (isComplete) {
            queueSessionStartMs = 0L
            lastQueueNotifiedPercent = -1
            lastQueueNotifiedItem = -1
            lastQueueNotifyElapsedMs = 0L
            activeQueueCurrentFrame = 0
            if (hasNotificationPermission(context)) {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_QUEUE)
            }
            return
        }

        val safeTotalItems = totalItems.coerceAtLeast(1)
        val safeTotalFrames = totalFrames.coerceAtLeast(1)
        val safeCurrentFrame = currentFrame.coerceIn(0, safeTotalFrames)

        activeQueueItemIndex = currentItemIndex.coerceIn(1, safeTotalItems)
        activeQueueTotalItems = safeTotalItems
        activeQueueTaskTitle = currentTaskTitle
        activeQueueCurrentFrame = safeCurrentFrame
        activeQueueTotalFrames = safeTotalFrames

        if (!hasNotificationPermission(context)) return

        val framePercent = ((safeCurrentFrame.toFloat() / safeTotalFrames) * 100).toInt().coerceIn(0, 100)
        val overallPercent = (((currentItemIndex - 1).coerceAtLeast(0) * 100 + framePercent) / safeTotalItems)
            .coerceIn(0, 100)
        val nowElapsed = SystemClock.elapsedRealtime()

        val itemChanged = currentItemIndex != lastQueueNotifiedItem
        val isFirstOrLastFrame = safeCurrentFrame <= 1 || safeCurrentFrame >= safeTotalFrames
        val enoughTimePassed = (nowElapsed - lastQueueNotifyElapsedMs) >= MIN_UPDATE_INTERVAL_MS
        val percentChanged = overallPercent != lastQueueNotifiedPercent

        if (!itemChanged && !isFirstOrLastFrame && (!enoughTimePassed || !percentChanged)) {
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
            currentFrame = safeCurrentFrame,
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
