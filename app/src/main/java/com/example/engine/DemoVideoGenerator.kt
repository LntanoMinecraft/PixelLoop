package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthesizes a real, vibrant 4-second 30fps MP4 clip on device so users can immediately
 * test timeline trimming, spatial aspect ratio cropping, FPS/resolution/speed tuning, and GIF sharing
 * even before recording or picking an external video.
 */
object DemoVideoGenerator {

    suspend fun generateExpressiveDemoMp4(context: Context): Result<File> =
        withContext(Dispatchers.Default) {
            runCatching {
                val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
                val outputFile = File(recordingsDir, "expressive_demo_showcase.mp4")
                if (outputFile.exists() && outputFile.length() > 10_000L) {
                    return@runCatching outputFile
                }

                val width = 480
                val height = 640
                val fps = 24
                val durationSeconds = 4
                val totalFrames = fps * durationSeconds
                val bitRate = 1_800_000

                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                    setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                    setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                }

                val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                try {
                    encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                } catch (_: Exception) {
                    encoder.reset()
                    val fallbackFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                        setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                        )
                        setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    }
                    encoder.configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                }
                val inputSurface = encoder.createInputSurface()
                encoder.start()

                val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val bufferInfo = MediaCodec.BufferInfo()
                var trackIndex = -1
                var muxerStarted = false

                try {
                    for (frameIdx in 0 until totalFrames) {
                        val canvas = inputSurface.lockCanvas(null)
                        try {
                            drawExpressiveShowcaseFrame(canvas, width, height, frameIdx, totalFrames)
                        } finally {
                            inputSurface.unlockCanvasAndPost(canvas)
                        }
                        drainEncoder(encoder, muxer, bufferInfo, endOfStream = false, trackIndex) { newTrack ->
                            trackIndex = newTrack
                            muxerStarted = true
                        }
                    }
                    encoder.signalEndOfInputStream()
                    drainEncoder(encoder, muxer, bufferInfo, endOfStream = true, trackIndex) { newTrack ->
                        trackIndex = newTrack
                        muxerStarted = true
                    }
                } finally {
                    runCatching { encoder.stop() }
                    runCatching { encoder.release() }
                    runCatching { inputSurface.release() }
                    if (muxerStarted) {
                        runCatching { muxer.stop() }
                    }
                    runCatching { muxer.release() }
                }

                outputFile
            }
        }

    fun drawExpressiveShowcaseFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        frameIdx: Int,
        totalFrames: Int
    ) {
        val progress = frameIdx.toFloat() / totalFrames.toFloat()
        val angleRad = (progress * Math.PI * 2.0).toFloat()

        // Crisp Opaque Expressive Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.rgb(20, 16, 38),
                Color.rgb(38, 22, 62),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Orbiting Cyan Expressive Pill
        val cx = width * 0.5f
        val cy = height * 0.46f
        val orbitX = cx + cos(angleRad) * (width * 0.25f)
        val orbitY = cy + sin(angleRad) * (width * 0.22f)
        shapePaint.color = Color.rgb(38, 198, 218)
        canvas.drawCircle(orbitX, orbitY, width * 0.13f, shapePaint)

        // 2. Counter-orbiting Coral Expressive Squircle
        val coralX = cx - cos(angleRad * 1.5f) * (width * 0.24f)
        val coralY = cy + sin(angleRad * 1.5f) * (width * 0.18f)
        shapePaint.color = Color.rgb(255, 82, 82)
        val rectSize = width * 0.14f
        canvas.drawRoundRect(
            RectF(coralX - rectSize, coralY - rectSize, coralX + rectSize, coralY + rectSize),
            32f, 32f, shapePaint
        )

        // 3. Central Pulsing Amber Ring & Badge
        val pulseRadius = width * (0.17f + 0.04f * sin(angleRad * 2f))
        shapePaint.color = Color.rgb(255, 179, 0)
        canvas.drawCircle(cx, cy, pulseRadius, shapePaint)

        shapePaint.color = Color.rgb(22, 18, 36)
        canvas.drawCircle(cx, cy, pulseRadius * 0.58f, shapePaint)

        // 4. Crisp Timecode & Frame Readout Header/Footer for easy trim verification
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(44, 38, 68)
        }
        canvas.drawRoundRect(
            RectF(width * 0.1f, height * 0.78f, width * 0.9f, height * 0.91f),
            28f, 28f, badgePaint
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val seconds = (frameIdx / 24f)
        val timeText = String.format("PIXEL LOOP  %04.1fs  F:%02d", seconds, frameIdx + 1)
        canvas.drawText(timeText, cx, height * 0.86f, textPaint)
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        currentTrackIndex: Int,
        onTrackAdded: (Int) -> Unit
    ) {
        var trackIdx = currentTrackIndex
        while (true) {
            val outputStatus = encoder.dequeueOutputBuffer(bufferInfo, 4_000L)
            if (outputStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (outputStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = encoder.outputFormat
                trackIdx = muxer.addTrack(newFormat)
                muxer.start()
                onTrackAdded(trackIdx)
            } else if (outputStatus >= 0) {
                val encodedData = encoder.getOutputBuffer(outputStatus)
                if (encodedData != null && bufferInfo.size > 0 && trackIdx >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIdx, encodedData, bufferInfo)
                    }
                }
                encoder.releaseOutputBuffer(outputStatus, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }
}
