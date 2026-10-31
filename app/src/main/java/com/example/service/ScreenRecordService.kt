package com.example.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class LiveRecordingState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val latestRecordedFile: File? = null,
    val statusMessage: String? = null
)

class ScreenRecordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    @Volatile
    private var isStopping: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val width = intent.getIntExtra(EXTRA_SCREEN_WIDTH, 720)
                val height = intent.getIntExtra(EXTRA_SCREEN_HEIGHT, 1280)
                val densityDpi = intent.getIntExtra(EXTRA_SCREEN_DENSITY, 320)

                if (resultData != null) {
                    startScreenRecording(resultCode, resultData, width, height, densityDpi)
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP_AND_EDIT -> {
                stopRecordingAndNotify(openEditor = true)
            }
            ACTION_CANCEL_RECORDING -> {
                stopRecordingAndNotify(openEditor = false, deleteFile = true)
            }
        }
        return START_NOT_STICKY
    }

    private fun startScreenRecording(
        resultCode: Int,
        resultData: Intent,
        rawWidth: Int,
        rawHeight: Int,
        densityDpi: Int
    ) {
        isStopping = false
        // 1. Start Foreground immediately with Android 16 Live Update notification
        LiveUpdateNotificationHelper.resetRecordingSessionClock()
        val initialNotification = LiveUpdateNotificationHelper.buildRecordingLiveNotification(
            context = this,
            elapsedSeconds = 0
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                LiveUpdateNotificationHelper.NOTIFICATION_ID_RECORDING,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(LiveUpdateNotificationHelper.NOTIFICATION_ID_RECORDING, initialNotification)
        }

        // Normalize dimensions to even numbers capped at 720p for smooth encoding
        val scale = if (rawWidth > 720) 720f / rawWidth else 1f
        val videoW = (((rawWidth * scale).toInt()) and 0xFFFE).coerceAtLeast(360)
        val videoH = (((rawHeight * scale).toInt()) and 0xFFFE).coerceAtLeast(640)

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val outFile = File(recordingsDir, "ScreenCapture_${System.currentTimeMillis()}.mp4")
        currentOutputFile = outFile

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                stopSelf()
                return
            }
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!isStopping && _recordingState.value.isRecording) {
                        stopRecordingAndNotify(openEditor = true)
                    }
                }
            }, null)
            mediaProjection = projection

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(videoW, videoH)
                setVideoFrameRate(24)
                setVideoEncodingBitRate(3_500_000)
                setOutputFile(outFile.absolutePath)
                prepare()
            }
            mediaRecorder = recorder

            virtualDisplay = projection.createVirtualDisplay(
                "PixelLoopScreenCapture",
                videoW,
                videoH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            recorder.start()

            _recordingState.value = LiveRecordingState(
                isRecording = true,
                elapsedSeconds = 0,
                latestRecordedFile = null,
                statusMessage = "正在通过 Android 16 实时通知捕获屏幕…"
            )
            QuickRecordTileService.requestTileSync(this)

            startLiveNotificationTicker()
        } catch (e: Exception) {
            _recordingState.value = LiveRecordingState(
                isRecording = false,
                elapsedSeconds = 0,
                statusMessage = "录屏启动受限 (${e.localizedMessage ?: "环境不支持"})"
            )
            QuickRecordTileService.requestTileSync(this)
            cleanupRecorderResources()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startLiveNotificationTicker() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var seconds = 0
            while (isActive && _recordingState.value.isRecording) {
                delay(1000L)
                seconds++
                _recordingState.value = _recordingState.value.copy(elapsedSeconds = seconds)

                runCatching {
                    val updatedNotif = LiveUpdateNotificationHelper.buildRecordingLiveNotification(
                        context = this@ScreenRecordService,
                        elapsedSeconds = seconds
                    )
                    NotificationManagerCompat.from(this@ScreenRecordService).notify(
                        LiveUpdateNotificationHelper.NOTIFICATION_ID_RECORDING,
                        updatedNotif
                    )
                }
            }
        }
    }

    private fun stopRecordingAndNotify(openEditor: Boolean, deleteFile: Boolean = false) {
        if (isStopping) return
        isStopping = true
        timerJob?.cancel()
        val outputFile = currentOutputFile
        currentOutputFile = null
        _recordingState.value = _recordingState.value.copy(isRecording = false)

        runCatching { mediaRecorder?.stop() }
        cleanupRecorderResources()

        if (deleteFile) {
            outputFile?.delete()
            _recordingState.value = LiveRecordingState(
                isRecording = false,
                elapsedSeconds = 0,
                latestRecordedFile = null,
                statusMessage = "已取消本次屏幕录制"
            )
        } else if (outputFile != null && outputFile.exists() && outputFile.length() > 1024L) {
            _recordingState.value = LiveRecordingState(
                isRecording = false,
                elapsedSeconds = 0,
                latestRecordedFile = outputFile,
                statusMessage = "录屏捕获完成，已自动载入剪辑室"
            )
            if (openEditor) {
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_RECORDED_FILE_PATH, outputFile.absolutePath)
                }
                runCatching { startActivity(launchIntent) }
            }
        } else {
            _recordingState.value = LiveRecordingState(
                isRecording = false,
                elapsedSeconds = 0,
                latestRecordedFile = null
            )
        }

        QuickRecordTileService.requestTileSync(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupRecorderResources() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { mediaRecorder?.reset() }
        runCatching { mediaRecorder?.release() }
        mediaRecorder = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    override fun onDestroy() {
        timerJob?.cancel()
        cleanupRecorderResources()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_STOP_AND_EDIT = "com.example.action.STOP_AND_EDIT"
        const val ACTION_CANCEL_RECORDING = "com.example.action.CANCEL_RECORDING"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_SCREEN_WIDTH = "extra_screen_width"
        const val EXTRA_SCREEN_HEIGHT = "extra_screen_height"
        const val EXTRA_SCREEN_DENSITY = "extra_screen_density"
        const val EXTRA_RECORDED_FILE_PATH = "extra_recorded_file_path"

        private val _recordingState = MutableStateFlow(LiveRecordingState())
        val recordingState: StateFlow<LiveRecordingState> = _recordingState.asStateFlow()

        fun clearConsumedRecording() {
            _recordingState.value = _recordingState.value.copy(
                latestRecordedFile = null,
                statusMessage = null
            )
        }
    }
}
