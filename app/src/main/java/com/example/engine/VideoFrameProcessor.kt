package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Normalized spatial crop rectangle within [0f..1f].
 */
data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val widthFraction: Float get() = (right - left).coerceIn(0.15f, 1f)
    val heightFraction: Float get() = (bottom - top).coerceIn(0.15f, 1f)
}

enum class CropAspectPreset(val label: String, val aspectRatio: Float?) {
    FREE("自由裁剪", null),
    SQUARE("1:1 方形", 1.0f),
    PORTRAIT_9_16("9:16 竖屏", 9f / 16f),
    LANDSCAPE_16_9("16:9 横屏", 16f / 9f),
    CLASSIC_4_3("4:3 经典", 4f / 3f)
}

enum class ResolutionPreset(val label: String, val maxShortEdgePx: Int) {
    RES_240P("240p 轻量表情", 240),
    RES_360P("360p 社交极速", 360),
    RES_480P("480p 清晰均衡", 480),
    RES_720P("720p 超清画质", 720),
    RES_ORIGINAL("原始比例上限", 960)
}

data class VideoSourceMetadata(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val sourceType: String
)

data class GifExportResult(
    val gifFile: File,
    val thumbnailFile: File,
    val outputWidth: Int,
    val outputHeight: Int,
    val frameCount: Int,
    val durationMs: Long,
    val fileSizeBytes: Long
)

class VideoFrameProcessor(private val context: Context) {

    // Memory cache for timeline filmstrip thumbnails & scrub frames to keep UI at 60/120fps
    private val frameCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount / 1024
        }
    }

    suspend fun inspectVideo(
        uri: Uri,
        sourceType: String = "VIDEO_IMPORT",
        fallbackTitle: String = "屏幕动态捕捉"
    ): Result<VideoSourceMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                setRetrieverSource(retriever, uri)
                val durationMs = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()?.coerceAtLeast(500L) ?: 4000L

                val rawWidth = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 720
                val rawHeight = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 1280
                val rotation = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0

                val effectiveWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
                val effectiveHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight

                VideoSourceMetadata(
                    uri = uri,
                    displayName = fallbackTitle,
                    durationMs = durationMs,
                    width = effectiveWidth.coerceAtLeast(64),
                    height = effectiveHeight.coerceAtLeast(64),
                    rotationDegrees = rotation,
                    sourceType = sourceType
                )
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    /**
     * Extracts a list of downsampled timeline filmstrip thumbnails with LruCache backing.
     */
    suspend fun extractTimelineThumbnails(
        uri: Uri,
        durationMs: Long,
        count: Int = 8
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Bitmap>()
        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverSource(retriever, uri)
            val stepMs = max(100L, durationMs / max(1, count))
            for (i in 0 until count) {
                val timeMs = min(durationMs, i * stepMs)
                val cacheKey = "${uri}_${timeMs}_thumb"
                val cached = frameCache.get(cacheKey)
                if (cached != null && !cached.isRecycled) {
                    result.add(cached)
                    continue
                }
                val bmp = decodeScaledFrame(retriever, timeMs * 1000L, 160, 160)
                if (bmp != null) {
                    frameCache.put(cacheKey, bmp)
                    result.add(bmp)
                }
            }
        } catch (_: Exception) {
            // Return whatever frames succeeded
        } finally {
            runCatching { retriever.release() }
        }
        result
    }

    /**
     * Fetches a preview frame at [timeMs] for the interactive crop canvas.
     */
    suspend fun extractPreviewFrame(
        uri: Uri,
        timeMs: Long,
        maxDimension: Int = 640
    ): Bitmap? = withContext(Dispatchers.IO) {
        val bucketTimeMs = (timeMs / 80L) * 80L
        val cacheKey = "${uri}_${bucketTimeMs}_preview_$maxDimension"
        val cached = frameCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }
        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverSource(retriever, uri)
            val bmp = decodeScaledFrame(retriever, bucketTimeMs * 1000L, maxDimension, maxDimension)
            if (bmp != null) {
                frameCache.put(cacheKey, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Computes exact output dimensions after applying NormalizedCropRect and ResolutionPreset.
     */
    fun calculateOutputDimensions(
        sourceWidth: Int,
        sourceHeight: Int,
        cropRect: NormalizedCropRect,
        resolutionPreset: ResolutionPreset
    ): Pair<Int, Int> {
        val croppedW = max(32f, sourceWidth * cropRect.widthFraction)
        val croppedH = max(32f, sourceHeight * cropRect.heightFraction)
        val shortEdge = min(croppedW, croppedH)
        val targetShort = resolutionPreset.maxShortEdgePx.toFloat()

        val scale = if (shortEdge > targetShort) {
            targetShort / shortEdge
        } else {
            1f
        }
        // Align to even pixel numbers for clean rendering
        val outW = ((croppedW * scale).roundToInt() and 0xFFFE).coerceAtLeast(64)
        val outH = ((croppedH * scale).roundToInt() and 0xFFFE).coerceAtLeast(64)
        return outW to outH
    }

    /**
     * Executes full frame extraction, spatial cropping, scaling, and GIF89a LZW encoding
     * on Dispatchers.Default with real-time progress callbacks.
     */
    suspend fun exportVideoToGif(
        metadata: VideoSourceMetadata,
        trimStartMs: Long,
        trimEndMs: Long,
        cropRect: NormalizedCropRect,
        targetFps: Int,
        resolutionPreset: ResolutionPreset,
        playbackSpeed: Float,
        onProgress: suspend (currentFrame: Int, totalFrames: Int) -> Unit
    ): Result<GifExportResult> = withContext(Dispatchers.Default) {
        runCatching {
            val validStartMs = trimStartMs.coerceIn(0L, max(0L, metadata.durationMs - 200L))
            val validEndMs = trimEndMs.coerceIn(validStartMs + 200L, metadata.durationMs)
            val sourceDurationMs = (validEndMs - validStartMs).coerceAtLeast(200L)

            val effectiveDurationMs = (sourceDurationMs / playbackSpeed).roundToInt().coerceAtLeast(200)
            val totalFrames = ((effectiveDurationMs / 1000f) * targetFps).roundToInt().coerceIn(4, 150)
            val frameDelayMs = (1000f / targetFps).roundToInt().coerceAtLeast(30)

            val (outWidth, outHeight) = calculateOutputDimensions(
                sourceWidth = metadata.width,
                sourceHeight = metadata.height,
                cropRect = cropRect,
                resolutionPreset = resolutionPreset
            )

            val gifsDir = File(context.filesDir, "gifs").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val gifFile = File(gifsDir, "PixelLoop_${timestamp}.gif")
            val thumbFile = File(gifsDir, "PixelLoop_${timestamp}_thumb.png")

            val retriever = MediaMetadataRetriever()
            var firstCroppedFrame: Bitmap? = null

            try {
                setRetrieverSource(retriever, metadata.uri)

                FileOutputStream(gifFile).use { fos ->
                    val encoder = AnimatedGifEncoder(fos)
                    encoder.start(outWidth, outHeight, loopCount = 0)

                    for (frameIdx in 0 until totalFrames) {
                        coroutineContext.ensureActive()

                        val fraction = if (totalFrames <= 1) 0f else frameIdx.toFloat() / (totalFrames - 1)
                        val sampleTimeMs = validStartMs + (fraction * sourceDurationMs).toLong()

                        val rawFrame = decodeScaledFrame(
                            retriever = retriever,
                            timeUs = sampleTimeMs * 1000L,
                            maxW = max(outWidth * 2, 480),
                            maxH = max(outHeight * 2, 480)
                        ) ?: generateFallbackFrame(outWidth, outHeight, frameIdx, totalFrames)

                        val croppedAndScaled = cropAndScaleBitmap(
                            source = rawFrame,
                            cropRect = cropRect,
                            targetWidth = outWidth,
                            targetHeight = outHeight
                        )

                        if (firstCroppedFrame == null) {
                            firstCroppedFrame = croppedAndScaled.copy(Bitmap.Config.ARGB_8888, false)
                        }

                        encoder.addFrame(croppedAndScaled, frameDelayMs)

                        if (croppedAndScaled !== rawFrame) {
                            croppedAndScaled.recycle()
                        }
                        rawFrame.recycle()

                        onProgress(frameIdx + 1, totalFrames)
                    }

                    encoder.finish()
                }

                // Save crisp PNG thumbnail of the first cropped frame
                val thumbBitmap = firstCroppedFrame
                    ?: generateFallbackFrame(outWidth, outHeight, 0, 1)
                FileOutputStream(thumbFile).use { thumbOut ->
                    thumbBitmap.compress(Bitmap.CompressFormat.PNG, 90, thumbOut)
                }
                thumbBitmap.recycle()

                GifExportResult(
                    gifFile = gifFile,
                    thumbnailFile = thumbFile,
                    outputWidth = outWidth,
                    outputHeight = outHeight,
                    frameCount = totalFrames,
                    durationMs = effectiveDurationMs.toLong(),
                    fileSizeBytes = gifFile.length()
                )
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun setRetrieverSource(retriever: MediaMetadataRetriever, uri: Uri) {
        if (uri.scheme == "file") {
            retriever.setDataSource(uri.path)
        } else {
            retriever.setDataSource(context, uri)
        }
    }

    private fun decodeScaledFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        maxW: Int,
        maxH: Int
    ): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                maxW,
                maxH
            ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }

    private fun cropAndScaleBitmap(
        source: Bitmap,
        cropRect: NormalizedCropRect,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val srcW = source.width
        val srcH = source.height
        val cropX = (cropRect.left * srcW).roundToInt().coerceIn(0, max(0, srcW - 8))
        val cropY = (cropRect.top * srcH).roundToInt().coerceIn(0, max(0, srcH - 8))
        val cropW = (cropRect.widthFraction * srcW).roundToInt().coerceIn(8, srcW - cropX)
        val cropH = (cropRect.heightFraction * srcH).roundToInt().coerceIn(8, srcH - cropY)

        val cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        if (cropped.width == targetWidth && cropped.height == targetHeight) {
            return cropped
        }
        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        if (scaled !== cropped) {
            cropped.recycle()
        }
        return scaled
    }

    private fun generateFallbackFrame(w: Int, h: Int, index: Int, total: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(24, 20, 42))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val progress = index.toFloat() / max(1, total)
        paint.color = Color.rgb(255, 179, 0)
        val cx = w * (0.2f + 0.6f * progress)
        val cy = h * 0.5f
        canvas.drawCircle(cx, cy, min(w, h) * 0.18f, paint)
        return bmp
    }
}
