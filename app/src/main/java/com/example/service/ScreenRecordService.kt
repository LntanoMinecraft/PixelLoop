package com.example.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Surface
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.engine.ScreenCaptureRgbaCache
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class LiveRecordingState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val latestRecordedFile: File? = null,
    val statusMessage: String? = null
)

class ScreenRecordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerJob: Job? = null
    private var codecDrainJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    // Primary pipeline: Zero-copy hardware Surface -> Full-Range BT.709 MediaCodec + MediaMuxer
    private var videoEncoder: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var mediaMuxer: MediaMuxer? = null
    private var muxerTrackIndex: Int = -1
    private var muxerStarted: Boolean = false

    // Fallback pipeline if a specific device rejects MediaCodec surface configuration
    private var fallbackRecorder: MediaRecorder? = null

    private var currentOutputFile: File? = null

    @Volatile
    private var isStopping: Boolean = false

    @Volatile
    private var shouldDrainEndOfStream: Boolean = false

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
        shouldDrainEndOfStream = false
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

        // Normalize dimensions to multiples of 16 capped at 720p for universal hardware H.264 compatibility
        val scale = if (rawWidth > 720) 720f / rawWidth else 1f
        val videoW = (((rawWidth * scale).toInt()) and 0xFFF0).coerceAtLeast(368)
        val videoH = (((rawHeight * scale).toInt()) and 0xFFF0).coerceAtLeast(640)

        val recordingsDir = File(filesDir, "recordings").apply { mkdirs() }
        val outFile = File(recordingsDir, "ScreenCapture_${System.currentTimeMillis()}.mp4")
        currentOutputFile = outFile

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!isStopping && _recordingState.value.isRecording) {
                        stopRecordingAndNotify(openEditor = true)
                    }
                }
            }, mainHandler)
            mediaProjection = projection

            val targetFps = 24
            val targetBitRate = 8_000_000 // 8 Mbps for crisp text & zero compression washout

            val startedCodecPipeline = tryStartFullRangeCodecPipeline(
                projection = projection,
                videoW = videoW,
                videoH = videoH,
                densityDpi = densityDpi,
                targetFps = targetFps,
                targetBitRate = targetBitRate,
                outFile = outFile
            )

            if (!startedCodecPipeline) {
                startFallbackRecorderPipeline(
                    projection = projection,
                    videoW = videoW,
                    videoH = videoH,
                    densityDpi = densityDpi,
                    targetFps = targetFps,
                    targetBitRate = targetBitRate,
                    outFile = outFile
                )
            }

            _recordingState.value = LiveRecordingState(
                isRecording = true,
                elapsedSeconds = 0,
                latestRecordedFile = null,
                statusMessage = "正在通过全色域硬件通道捕获屏幕…"
            )
            QuickRecordTileService.requestTileSync(this)

            startLiveNotificationTicker()
        } catch (t: Throwable) {
            _recordingState.value = LiveRecordingState(
                isRecording = false,
                elapsedSeconds = 0,
                statusMessage = "录屏启动受限 (${t.localizedMessage ?: "环境不支持"})"
            )
            QuickRecordTileService.requestTileSync(this)
            cleanupAllResourcesSync()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * Connects [VirtualDisplay] directly to a Full-Range BT.709 [MediaCodec] input [Surface]
     * with zero JVM Bitmap allocations per frame, preventing any OOM or GC pauses regardless
     * of recording duration.
     */
    private fun tryStartFullRangeCodecPipeline(
        projection: MediaProjection,
        videoW: Int,
        videoH: Int,
        densityDpi: Int,
        targetFps: Int,
        targetBitRate: Int,
        outFile: File
    ): Boolean {
        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        return try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val fullRangeFormat = createVideoEncoderFormat(
                width = videoW,
                height = videoH,
                fps = targetFps,
                bitRate = targetBitRate,
                includeFullRangeMetadata = true
            )
            try {
                encoder.configure(fullRangeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (_: Throwable) {
                encoder.reset()
                val fallbackFormat = createVideoEncoderFormat(
                    width = videoW,
                    height = videoH,
                    fps = targetFps,
                    bitRate = targetBitRate,
                    includeFullRangeMetadata = false
                )
                encoder.configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            videoEncoder = encoder
            encoderInputSurface = inputSurface
            mediaMuxer = muxer
            muxerTrackIndex = -1
            muxerStarted = false

            virtualDisplay = projection.createVirtualDisplay(
                "PixelLoopScreenCapture",
                videoW,
                videoH,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                mainHandler
            )

            startCodecDrainLoop(encoder, muxer)
            true
        } catch (_: Throwable) {
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { inputSurface?.release() }
            runCatching { muxer?.release() }
            videoEncoder = null
            encoderInputSurface = null
            mediaMuxer = null
            false
        }
    }

    private fun startFallbackRecorderPipeline(
        projection: MediaProjection,
        videoW: Int,
        videoH: Int,
        densityDpi: Int,
        targetFps: Int,
        targetBitRate: Int,
        outFile: File
    ) {
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
            setVideoFrameRate(targetFps)
            setVideoEncodingBitRate(targetBitRate)
            setOutputFile(outFile.absolutePath)
            prepare()
        }
        fallbackRecorder = recorder

        virtualDisplay = projection.createVirtualDisplay(
            "PixelLoopScreenCapture",
            videoW,
            videoH,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            mainHandler
        )
        recorder.start()
    }

    private fun createVideoEncoderFormat(
        width: Int,
        height: Int,
        fps: Int,
        bitRate: Int,
        includeFullRangeMetadata: Boolean
    ): MediaFormat {
        return MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            // Repeat previous frame in hardware every 100ms when screen is static
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
            if (includeFullRangeMetadata) {
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
        }
    }

    /**
     * Single-owner coroutine on Dispatchers.IO that exclusively drains [encoder] into [muxer].
     * Keeping all MediaCodec and MediaMuxer operations on this single coroutine prevents any
     * cross-thread race conditions or native crashes during recording or stop.
     */
    private fun startCodecDrainLoop(encoder: MediaCodec, muxer: MediaMuxer) {
        codecDrainJob?.cancel()
        codecDrainJob = serviceScope.launch(Dispatchers.IO) {
            val bufferInfo = MediaCodec.BufferInfo()
            var eosSignaled = false
            var eosTries = 0

            try {
                while (isActive) {
                    if (shouldDrainEndOfStream && !eosSignaled) {
                        eosSignaled = true
                        runCatching { encoder.signalEndOfInputStream() }
                    }

                    val timeoutUs = if (eosSignaled) 10_000L else 25_000L
                    val status = try {
                        encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    } catch (_: Throwable) {
                        break
                    }

                    if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (eosSignaled) {
                            eosTries++
                            if (eosTries >= 8) break
                        }
                    } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            runCatching {
                                muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                    } else if (status >= 0) {
                        val encodedData = runCatching { encoder.getOutputBuffer(status) }.getOrNull()
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted && muxerTrackIndex >= 0) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                runCatching {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(muxerTrackIndex, encodedData, bufferInfo)
                                }
                            }
                        }
                        runCatching { encoder.releaseOutputBuffer(status, false) }

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    } else {
                        if (eosSignaled) break
                    }
                }
            } catch (_: Throwable) {
                // Prevent any codec exception from crashing the process
            } finally {
                runCatching { encoder.stop() }
                runCatching { encoder.release() }
                runCatching { encoderInputSurface?.release() }
                if (muxerStarted) {
                    runCatching { muxer.stop() }
                }
                runCatching { muxer.release() }
            }
        }
    }

    private fun startLiveNotificationTicker() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var seconds = 0
            while (isActive && _recordingState.value.isRecording && !isStopping) {
                delay(1000L)
                if (isStopping || !_recordingState.value.isRecording) break
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

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Stop VirtualDisplay so no new frames enter the surface
                runCatching { virtualDisplay?.release() }
                virtualDisplay = null

                // 2. Signal EOS to MediaCodec drain loop and wait for clean muxer flush
                shouldDrainEndOfStream = true
                withTimeoutOrNull(1800L) {
                    codecDrainJob?.join()
                }
                codecDrainJob?.cancel()
                codecDrainJob = null
                videoEncoder = null
                encoderInputSurface = null
                mediaMuxer = null
                muxerStarted = false
                muxerTrackIndex = -1

                // 3. Stop fallback MediaRecorder if used
                runCatching { fallbackRecorder?.stop() }
                runCatching { fallbackRecorder?.reset() }
                runCatching { fallbackRecorder?.release() }
                fallbackRecorder = null
            }

            runCatching { mediaProjection?.stop() }
            mediaProjection = null

            if (deleteFile) {
                ScreenCaptureRgbaCache.clearFramesForFile(outputFile)
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
                    val launchIntent = Intent(this@ScreenRecordService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
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

            QuickRecordTileService.requestTileSync(this@ScreenRecordService)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun cleanupAllResourcesSync() {
        shouldDrainEndOfStream = true
        codecDrainJob?.cancel()
        codecDrainJob = null

        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        videoEncoder = null

        runCatching { encoderInputSurface?.release() }
        encoderInputSurface = null

        if (muxerStarted) {
            runCatching { mediaMuxer?.stop() }
        }
        runCatching { mediaMuxer?.release() }
        mediaMuxer = null
        muxerStarted = false
        muxerTrackIndex = -1

        runCatching { fallbackRecorder?.reset() }
        runCatching { fallbackRecorder?.release() }
        fallbackRecorder = null

        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    override fun onDestroy() {
        timerJob?.cancel()
        cleanupAllResourcesSync()
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
