package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
) {
    val aspectRatio: Float
        get() = (width.toFloat() / height.coerceAtLeast(1).toFloat()).coerceIn(0.3f, 3.0f)
}

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
    private val frameCache = object : LruCache<String, Bitmap>(28 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

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
        val localFilePath = uri.path?.takeIf { uri.scheme == "file" }
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

                // Prefer direct ImageReader lossless RGBA frame if available
                val directRgba = localFilePath?.let { path ->
                    ScreenCaptureRgbaCache.getLosslessFrameAt(path, timeMs, maxToleranceMs = 350L)
                }
                val bmp = if (directRgba != null) {
                    val thumb = scaleBitmapSrgb(directRgba, 160, 160)
                    if (thumb !== directRgba) directRgba.recycle()
                    thumb
                } else {
                    decodeScaledFrame(
                        retriever = retriever,
                        timeUs = timeMs * 1000L,
                        maxW = 160,
                        maxH = 160,
                        preciseOption = false
                    )
                }

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
        val bucketTimeMs = (timeMs / 60L) * 60L
        val cacheKey = "${uri}_${bucketTimeMs}_preview_$maxDimension"
        val cached = frameCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        val localFilePath = uri.path?.takeIf { uri.scheme == "file" }
        val directRgba = localFilePath?.let { path ->
            ScreenCaptureRgbaCache.getLosslessFrameAt(path, bucketTimeMs, maxToleranceMs = 250L)
        }
        if (directRgba != null) {
            val scaled = scaleBitmapSrgb(directRgba, maxDimension, maxDimension)
            if (scaled !== directRgba) directRgba.recycle()
            frameCache.put(cacheKey, scaled)
            return@withContext scaled
        }

        val retriever = MediaMetadataRetriever()
        try {
            setRetrieverSource(retriever, uri)
            val bmp = decodeScaledFrame(
                retriever = retriever,
                timeUs = bucketTimeMs * 1000L,
                maxW = maxDimension,
                maxH = maxDimension,
                preciseOption = true
            )
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
        val outW = ((croppedW * scale).roundToInt() and 0xFFFE).coerceAtLeast(64)
        val outH = ((croppedH * scale).roundToInt() and 0xFFFE).coerceAtLeast(64)
        return outW to outH
    }

    /**
     * Executes full frame extraction, sRGB calibration, spatial cropping, scaling, and GIF89a LZW encoding
     * on Dispatchers.Default with accurate centisecond frame pacing (supporting 5..60 FPS).
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
            val clampedFps = targetFps.coerceIn(5, 60)
            val effectiveGifFps = min(50, clampedFps)
            val totalFrames = ((effectiveDurationMs / 1000f) * effectiveGifFps).roundToInt().coerceIn(4, 240)

            val (outWidth, outHeight) = calculateOutputDimensions(
                sourceWidth = metadata.width,
                sourceHeight = metadata.height,
                cropRect = cropRect,
                resolutionPreset = resolutionPreset
            )

            // Compute exact decode target size so hardware decoder downsamples cleanly without OOM
            val decodeMaxW = (outWidth / cropRect.widthFraction).roundToInt().coerceIn(outWidth, metadata.width)
            val decodeMaxH = (outHeight / cropRect.heightFraction).roundToInt().coerceIn(outHeight, metadata.height)

            val gifsDir = File(context.filesDir, "gifs").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val gifFile = File(gifsDir, "PixelLoop_${timestamp}.gif")
            val thumbFile = File(gifsDir, "PixelLoop_${timestamp}_thumb.png")

            val localFilePath = metadata.uri.path?.takeIf { metadata.uri.scheme == "file" }
            val retriever = MediaMetadataRetriever()
            var firstCroppedFrame: Bitmap? = null

            try {
                setRetrieverSource(retriever, metadata.uri)

                FileOutputStream(gifFile).use { fos ->
                    val encoder = AnimatedGifEncoder(fos).apply {
                        // Force quality = 1 (full 1:1 pixel sampling) and disable flat-UI dithering noise
                        setQuality(1)
                        setDither(false)
                        start(outWidth, outHeight, loopCount = 0)
                    }

                    val totalCentiseconds = max(totalFrames * 2, (effectiveDurationMs + 5) / 10)
                    var accumulatedCs = 0

                    onProgress(0, totalFrames)

                    for (frameIdx in 0 until totalFrames) {
                        coroutineContext.ensureActive()

                        val fraction = if (totalFrames <= 1) 0f else frameIdx.toFloat() / (totalFrames - 1)
                        val sampleTimeMs = validStartMs + (fraction * sourceDurationMs).toLong()

                        val targetNextCs = (((frameIdx + 1) * totalCentiseconds).toFloat() / totalFrames).roundToInt()
                        val frameDelayCs = max(2, targetNextCs - accumulatedCs)
                        accumulatedCs += frameDelayCs

                        // 1. Prefer direct lossless RGBA frame captured by ImageReader if available
                        val rawFrame = localFilePath?.let { path ->
                            ScreenCaptureRgbaCache.getLosslessFrameAt(path, sampleTimeMs, maxToleranceMs = 180L)
                        } ?: decodeScaledFrame(
                            retriever = retriever,
                            timeUs = sampleTimeMs * 1000L,
                            maxW = decodeMaxW,
                            maxH = decodeMaxH,
                            preciseOption = true
                        ) ?: generateFallbackFrame(outWidth, outHeight, frameIdx, totalFrames)

                        // 2. Crop, scale, and calibrate into standard sRGB ARGB_8888
                        val croppedAndScaled = cropAndScaleBitmap(
                            source = rawFrame,
                            cropRect = cropRect,
                            targetWidth = outWidth,
                            targetHeight = outHeight
                        )

                        if (firstCroppedFrame == null) {
                            firstCroppedFrame = croppedAndScaled.copy(Bitmap.Config.ARGB_8888, false)
                        }

                        encoder.addFrame(croppedAndScaled, frameDelayCs)

                        if (croppedAndScaled !== rawFrame) {
                            croppedAndScaled.recycle()
                        }
                        rawFrame.recycle()

                        onProgress(frameIdx + 1, totalFrames)
                    }

                    encoder.finish()
                }

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
            return
        }
        val openedViaFd = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                true
            } ?: false
        }.getOrDefault(false)

        if (!openedViaFd) {
            retriever.setDataSource(context, uri)
        }
    }

    private fun decodeScaledFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        maxW: Int,
        maxH: Int,
        preciseOption: Boolean
    ): Bitmap? {
        val option = if (preciseOption) {
            MediaMetadataRetriever.OPTION_CLOSEST
        } else {
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        }
        val decoded = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                val preferredParams = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    MediaMetadataRetriever.BitmapParams().apply {
                        preferredConfig = Bitmap.Config.ARGB_8888
                    }
                } else null

                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && preferredParams != null) {
                    runCatching {
                        retriever.getScaledFrameAtTime(timeUs, option, maxW, maxH, preferredParams)
                    }.getOrNull()
                } else {
                    runCatching {
                        retriever.getScaledFrameAtTime(timeUs, option, maxW, maxH)
                    }.getOrNull()
                }
                bmp ?: retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    maxW,
                    maxH
                )
            } else {
                retriever.getFrameAtTime(timeUs, option)
            }
        }.getOrNull() ?: return null

        // Calibrate Display P3 / wide-gamut / RGB_565 frames to standard sRGB ARGB_8888
        val calibrated = ScreenCaptureRgbaCache.calibrateBitmapToSrgb(decoded)
        if (calibrated !== decoded) {
            decoded.recycle()
        }
        return calibrated
    }

    private fun scaleBitmapSrgb(source: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val calibrated = ScreenCaptureRgbaCache.calibrateBitmapToSrgb(source)
        val srcW = calibrated.width.coerceAtLeast(1)
        val srcH = calibrated.height.coerceAtLeast(1)
        val scale = min(1f, min(maxW.toFloat() / srcW, maxH.toFloat() / srcH))
        val dstW = (srcW * scale).roundToInt().coerceAtLeast(16)
        val dstH = (srcH * scale).roundToInt().coerceAtLeast(16)
        if (dstW == srcW && dstH == srcH) return calibrated

        val dst = ScreenCaptureRgbaCache.createSrgbBitmap(dstW, dstH)
        val canvas = Canvas(dst)
        canvas.drawBitmap(
            calibrated,
            Rect(0, 0, srcW, srcH),
            Rect(0, 0, dstW, dstH),
            scalePaint
        )
        if (calibrated !== source) {
            calibrated.recycle()
        }
        return dst
    }

    private fun cropAndScaleBitmap(
        source: Bitmap,
        cropRect: NormalizedCropRect,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val calibratedSource = ScreenCaptureRgbaCache.calibrateBitmapToSrgb(source)
        val srcW = calibratedSource.width
        val srcH = calibratedSource.height
        val cropX = (cropRect.left * srcW).roundToInt().coerceIn(0, max(0, srcW - 8))
        val cropY = (cropRect.top * srcH).roundToInt().coerceIn(0, max(0, srcH - 8))
        val cropW = (cropRect.widthFraction * srcW).roundToInt().coerceIn(8, srcW - cropX)
        val cropH = (cropRect.heightFraction * srcH).roundToInt().coerceIn(8, srcH - cropY)

        // Render cropped region directly into a standard sRGB ARGB_8888 target Bitmap
        val targetSrgb = ScreenCaptureRgbaCache.createSrgbBitmap(targetWidth, targetHeight)
        val canvas = Canvas(targetSrgb)
        val srcRect = Rect(cropX, cropY, cropX + cropW, cropY + cropH)
        val dstRect = Rect(0, 0, targetWidth, targetHeight)
        canvas.drawBitmap(calibratedSource, srcRect, dstRect, scalePaint)

        if (calibratedSource !== source) {
            calibratedSource.recycle()
        }
        return targetSrgb
    }

    private fun generateFallbackFrame(w: Int, h: Int, index: Int, total: Int): Bitmap {
        val bmp = ScreenCaptureRgbaCache.createSrgbBitmap(w, h)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(24, 28, 42))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val progress = index.toFloat() / max(1, total)
        paint.color = Color.rgb(174, 198, 255)
        val cx = w * (0.2f + 0.6f * progress)
        val cy = h * 0.5f
        canvas.drawCircle(cx, cy, min(w, h) * 0.18f, paint)
        return bmp
    }
}
