package com.example.engine

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.MediaStore
import android.system.ErrnoException
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
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

    // Maps original content:// picker URIs to local cached MP4 files in context.cacheDir
    private val resolvedUriFileCache = ConcurrentHashMap<String, File>()

    private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Eagerly persists URI read permission if supported by the provider, and logs
     * initial URI grant diagnostics (PID/UID permission status, authority, MIME type).
     * Call on the main thread right when the picker returns a URI.
     */
    fun takeUriPermissionSafely(uri: Uri) {
        if (uri.scheme == "content") {
            val pid = Process.myPid()
            val uid = Process.myUid()
            val preCheck = runCatching {
                context.checkUriPermission(uri, pid, uid, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.getOrDefault(PackageManager.PERMISSION_DENIED)
            val preCheckLabel = if (preCheck == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED($preCheck)"

            val persistResult = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            persistResult.onSuccess {
                Log.d(
                    TAG,
                    "[URI_PERM] takePersistableUriPermission SUCCEEDED | uri=$uri | authority=${uri.authority} | initialCheck=$preCheckLabel"
                )
            }.onFailure { err ->
                Log.d(
                    TAG,
                    "[URI_PERM] takePersistableUriPermission not supported/denied (expected for transient PhotoPicker URIs) | " +
                        "uri=$uri | authority=${uri.authority} | initialCheck=$preCheckLabel | " +
                        "reason=${err.javaClass.simpleName}: ${err.message}"
                )
            }
        }
    }

    suspend fun inspectVideo(
        uri: Uri,
        sourceType: String = "VIDEO_IMPORT",
        fallbackTitle: String = "屏幕动态捕捉"
    ): Result<VideoSourceMetadata> = withContext(Dispatchers.IO) {
        Log.d(
            TAG,
            "[INSPECT_START] sourceType=$sourceType | uri=$uri | scheme=${uri.scheme} | " +
                "authority=${uri.authority} | segments=${uri.pathSegments}"
        )
        runCatching {
            withConfiguredRetriever(uri) { retriever, effectiveUri ->
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

                Log.d(
                    TAG,
                    "[INSPECT_SUCCESS] originalUri=$uri -> effectiveUri=$effectiveUri | " +
                        "durationMs=$durationMs | size=${effectiveWidth}x${effectiveHeight} (rot=$rotation)"
                )

                VideoSourceMetadata(
                    uri = effectiveUri,
                    displayName = fallbackTitle,
                    durationMs = durationMs,
                    width = effectiveWidth.coerceAtLeast(64),
                    height = effectiveHeight.coerceAtLeast(64),
                    rotationDegrees = rotation,
                    sourceType = sourceType
                )
            }
        }.onFailure { err ->
            Log.e(
                TAG,
                "[INSPECT_FAILED] sourceType=$sourceType | uri=$uri | " +
                    "diagnosis=${classifyAccessFailureCause(err, uri)} | error=${err.javaClass.name}: ${err.message}",
                err
            )
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
        try {
            withConfiguredRetriever(uri) { retriever, effectiveUri ->
                val localFilePath = effectiveUri.path?.takeIf { effectiveUri.scheme == "file" }
                val stepMs = max(100L, durationMs / max(1, count))
                for (i in 0 until count) {
                    val timeMs = min(durationMs, i * stepMs)
                    val cacheKey = "${effectiveUri}_${timeMs}_thumb"
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
            }
        } catch (_: Exception) {
            // Return whatever frames succeeded
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
        val effectiveCacheUri = resolvedUriFileCache[uri.toString()]?.let { Uri.fromFile(it) } ?: uri
        val cacheKey = "${effectiveCacheUri}_${bucketTimeMs}_preview_$maxDimension"
        val cached = frameCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        val localFilePath = effectiveCacheUri.path?.takeIf { effectiveCacheUri.scheme == "file" }
        val directRgba = localFilePath?.let { path ->
            ScreenCaptureRgbaCache.getLosslessFrameAt(path, bucketTimeMs, maxToleranceMs = 250L)
        }
        if (directRgba != null) {
            val scaled = scaleBitmapSrgb(directRgba, maxDimension, maxDimension)
            if (scaled !== directRgba) directRgba.recycle()
            frameCache.put(cacheKey, scaled)
            return@withContext scaled
        }

        try {
            withConfiguredRetriever(uri) { retriever, resolvedUri ->
                val bmp = decodeScaledFrame(
                    retriever = retriever,
                    timeUs = bucketTimeMs * 1000L,
                    maxW = maxDimension,
                    maxH = maxDimension,
                    preciseOption = true
                )
                if (bmp != null) {
                    val resolvedKey = "${resolvedUri}_${bucketTimeMs}_preview_$maxDimension"
                    frameCache.put(resolvedKey, bmp)
                    frameCache.put(cacheKey, bmp)
                }
                bmp
            }
        } catch (_: Exception) {
            null
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

            var firstCroppedFrame: Bitmap? = null

            withConfiguredRetriever(metadata.uri) { retriever, effectiveUri ->
                val localFilePath = effectiveUri.path?.takeIf { effectiveUri.scheme == "file" }

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
            }
        }
    }

    /**
     * Opens a [MediaMetadataRetriever] safely for [uri]:
     * 1. Materializes `content://` picker URIs (including ColorOS `com.colors.gallery3d.photopicker`)
     *    into a local app cache file inside the app process so `media.extractor` never hits SELinux
     *    or `fuse.onedirect.enable` `openTypedAssetFile` failures.
     * 2. Keeps any underlying file descriptor open for the entire duration of [block], closing it
     *    only after `retriever.release()`.
     */
    private inline fun <T> withConfiguredRetriever(
        uri: Uri,
        block: (retriever: MediaMetadataRetriever, effectiveUri: Uri) -> T
    ): T {
        val retriever = MediaMetadataRetriever()
        val closeables = mutableListOf<Closeable>()
        try {
            val effectiveUri = configureRetrieverDataSource(retriever, uri, closeables)
            return block(retriever, effectiveUri)
        } finally {
            runCatching { retriever.release() }
            closeables.forEach { c -> runCatching { c.close() } }
        }
    }

    private fun configureRetrieverDataSource(
        retriever: MediaMetadataRetriever,
        uri: Uri,
        closeables: MutableList<Closeable>
    ): Uri {
        // 1. Direct local file or materialized local cache copy
        val localFile = resolveToLocalVideoFile(uri)
        if (localFile != null && localFile.exists() && localFile.length() > 0L) {
            val localUri = Uri.fromFile(localFile)
            val boundViaFis = runCatching {
                val fis = FileInputStream(localFile)
                closeables.add(fis)
                retriever.setDataSource(fis.fd)
                true
            }.onFailure { err ->
                Log.w(
                    TAG,
                    "[RETRIEVER_SOURCE] FileInputStream.fd setDataSource failed for ${localFile.absolutePath}, falling back to path: ${err.javaClass.simpleName}: ${err.message}"
                )
            }.getOrDefault(false)

            if (!boundViaFis) {
                retriever.setDataSource(localFile.absolutePath)
            }
            return localUri
        }

        // 2. Fallback: try keeping AssetFileDescriptor / ParcelFileDescriptor open across candidate URIs
        val candidates = buildCandidateUris(uri)
        var lastError: Throwable? = null
        val attemptDiagnostics = mutableListOf<String>()

        Log.w(
            TAG,
            "[RETRIEVER_FALLBACK] Local file materialization unavailable for uri=$uri; attempting direct FD binding across ${candidates.size} candidate URIs"
        )

        for ((index, candidate) in candidates.withIndex()) {
            // 2a. Try openAssetFileDescriptor(candidate, "r")
            val openedAfd = runCatching {
                val afd: AssetFileDescriptor? = context.contentResolver.openAssetFileDescriptor(candidate, "r")
                if (afd != null) {
                    closeables.add(afd)
                    if (afd.declaredLength < 0L) {
                        retriever.setDataSource(afd.fileDescriptor)
                    } else {
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                    }
                    Log.d(TAG, "[RETRIEVER_FALLBACK] Candidate[$index] openAssetFileDescriptor SUCCEEDED: $candidate")
                    true
                } else {
                    attemptDiagnostics.add("cand[$index]($candidate)#openAFD=null")
                    false
                }
            }.onFailure { err ->
                lastError = err
                val reason = "${err.javaClass.simpleName}: ${err.message} (${classifyAccessFailureCause(err, candidate)})"
                attemptDiagnostics.add("cand[$index]($candidate)#openAFD=$reason")
                Log.w(TAG, "[RETRIEVER_FALLBACK] Candidate[$index] openAssetFileDescriptor FAILED for $candidate -> $reason")
            }.getOrDefault(false)
            if (openedAfd) return candidate

            // 2b. Try openTypedAssetFileDescriptor(candidate, "video/*", null) for CloudMediaProviders
            val openedTypedAfd = runCatching {
                val afd: AssetFileDescriptor? =
                    context.contentResolver.openTypedAssetFileDescriptor(candidate, "video/*", null)
                if (afd != null) {
                    closeables.add(afd)
                    if (afd.declaredLength < 0L) {
                        retriever.setDataSource(afd.fileDescriptor)
                    } else {
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                    }
                    Log.d(TAG, "[RETRIEVER_FALLBACK] Candidate[$index] openTypedAssetFileDescriptor(video/*) SUCCEEDED: $candidate")
                    true
                } else {
                    attemptDiagnostics.add("cand[$index]($candidate)#openTypedAFD=null")
                    false
                }
            }.onFailure { err ->
                lastError = err
                val reason = "${err.javaClass.simpleName}: ${err.message} (${classifyAccessFailureCause(err, candidate)})"
                attemptDiagnostics.add("cand[$index]($candidate)#openTypedAFD=$reason")
                Log.w(TAG, "[RETRIEVER_FALLBACK] Candidate[$index] openTypedAssetFileDescriptor(video/*) FAILED for $candidate -> $reason")
            }.getOrDefault(false)
            if (openedTypedAfd) return candidate

            // 2c. Try openFileDescriptor(candidate, "r")
            val openedPfd = runCatching {
                val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(candidate, "r")
                if (pfd != null) {
                    closeables.add(pfd)
                    retriever.setDataSource(pfd.fileDescriptor)
                    Log.d(TAG, "[RETRIEVER_FALLBACK] Candidate[$index] openFileDescriptor SUCCEEDED: $candidate")
                    true
                } else {
                    attemptDiagnostics.add("cand[$index]($candidate)#openPFD=null")
                    false
                }
            }.onFailure { err ->
                lastError = err
                val reason = "${err.javaClass.simpleName}: ${err.message} (${classifyAccessFailureCause(err, candidate)})"
                attemptDiagnostics.add("cand[$index]($candidate)#openPFD=$reason")
                Log.w(TAG, "[RETRIEVER_FALLBACK] Candidate[$index] openFileDescriptor FAILED for $candidate -> $reason")
            }.getOrDefault(false)
            if (openedPfd) return candidate
        }

        val rootCauseSummary = lastError?.let { classifyAccessFailureCause(it, uri) } ?: "UNKNOWN_ACCESS_DENIAL"
        Log.e(
            TAG,
            "[RETRIEVER_EXHAUSTED] All local-copy and direct-FD strategies failed for uri=$uri | " +
                "rootCause=$rootCauseSummary | attempts=${attemptDiagnostics.joinToString(" ; ")}",
            lastError
        )

        throw IllegalArgumentException(
            lastError?.localizedMessage ?: "无法访问所选视频 ($uri)",
            lastError
        )
    }

    /**
     * Resolves a `file://` or `content://` URI into a readable local [File] inside `context.cacheDir`.
     * Copying happens inside the app process (which holds the Photo Picker URI grant), avoiding
     * cross-process `media.extractor` SELinux denials and OEM `fuse.onedirect.enable` bugs.
     */
    private fun resolveToLocalVideoFile(uri: Uri): File? {
        if (uri.scheme == "file") {
            val file = uri.path?.let(::File)
            if (file != null && file.exists() && file.length() > 0L) {
                return file
            }
            Log.w(TAG, "[LOCAL_RESOLVE] file:// URI does not exist or is empty: path=${uri.path}")
        }

        val key = uri.toString()
        resolvedUriFileCache[key]?.let { cached ->
            if (cached.exists() && cached.length() > 0L) {
                Log.d(TAG, "[LOCAL_RESOLVE] Hit in-memory URI cache for $uri -> ${cached.absolutePath} (${cached.length()} bytes)")
                return cached
            }
        }

        takeUriPermissionSafely(uri)

        val importDir = File(context.cacheDir, "imported_videos").apply { mkdirs() }
        pruneOldImportedCacheFiles(importDir)

        val hashHex = key.hashCode().toUInt().toString(16)
        val targetFile = File(importDir, "picker_video_${hashHex}.mp4")
        if (targetFile.exists() && targetFile.length() > 0L) {
            resolvedUriFileCache[key] = targetFile
            Log.d(TAG, "[LOCAL_RESOLVE] Hit disk cache for $uri -> ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            return targetFile
        }

        val tempFile = File(importDir, "picker_video_${hashHex}_${System.nanoTime()}.tmp")
        val candidates = buildCandidateUris(uri)
        Log.d(
            TAG,
            "[LOCAL_RESOLVE] Materializing content URI to local cache | originalUri=$uri | candidates=${candidates.size}: $candidates"
        )

        for ((candIdx, candidate) in candidates.withIndex()) {
            if (tryCopyCandidateUriToFile(candidate, tempFile, candIdx)) {
                if (tempFile.exists() && tempFile.length() > 0L) {
                    val copiedBytes = tempFile.length()
                    runCatching {
                        if (targetFile.exists()) targetFile.delete()
                        if (!tempFile.renameTo(targetFile)) {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                        }
                    }
                    val finalFile = if (targetFile.exists() && targetFile.length() > 0L) targetFile else tempFile
                    resolvedUriFileCache[key] = finalFile
                    Log.d(
                        TAG,
                        "[LOCAL_RESOLVE_SUCCESS] Materialized candidate[$candIdx]($candidate) -> ${finalFile.absolutePath} ($copiedBytes bytes)"
                    )
                    return finalFile
                }
            }
        }

        Log.e(
            TAG,
            "[LOCAL_RESOLVE_FAILED] Exhausted all ${candidates.size} candidate URIs and 6 stream strategies for uri=$uri"
        )
        runCatching { if (tempFile.exists()) tempFile.delete() }
        return null
    }

    /**
     * Builds fallback URIs for OEM Photo Picker URIs such as
     * `content://media/picker/0/com.colors.gallery3d.photopicker/media/28616`.
     */
    private fun buildCandidateUris(uri: Uri): List<Uri> {
        val result = linkedSetOf<Uri>()
        result.add(uri)

        val uriStr = uri.toString()
        // Fix ColorOS typo authority `com.colors.gallery3d` <-> `com.coloros.gallery3d`
        if (uriStr.contains("com.colors.gallery3d")) {
            runCatching {
                result.add(Uri.parse(uriStr.replace("com.colors.gallery3d", "com.coloros.gallery3d")))
            }
        }

        val segments = uri.pathSegments
        if (segments.size >= 2) {
            val lastSegment = segments.last()
            val decodedLast = Uri.decode(lastSegment)
            val numericId = decodedLast.substringAfterLast(':').toLongOrNull()

            // Pattern: content://media/picker/<userId>/<authority>/media/<id>
            if ((segments[0] == "picker" || segments[0] == "picker_get_content") && segments.size >= 4) {
                val userId = segments[1]
                val authority = segments[2]
                // 1. Standard Android PhotoPicker local provider authority
                runCatching {
                    result.add(
                        Uri.parse("content://media/picker/$userId/com.android.providers.media.photopicker/media/$lastSegment")
                    )
                }
                if (numericId != null) {
                    runCatching {
                        result.add(
                            Uri.parse("content://media/picker/$userId/com.android.providers.media.photopicker/media/$numericId")
                        )
                    }
                }
                // 2. Direct CloudMediaProvider URI
                runCatching {
                    result.add(Uri.parse("content://$authority/media/$lastSegment"))
                }
                if (authority.contains("com.colors.gallery3d")) {
                    val fixedAuth = authority.replace("com.colors.gallery3d", "com.coloros.gallery3d")
                    runCatching {
                        result.add(Uri.parse("content://$fixedAuth/media/$lastSegment"))
                    }
                }
            }

            // 3. Standard MediaStore Video & Files fallback URIs when numeric media ID is present
            if (numericId != null && numericId > 0L) {
                runCatching {
                    result.add(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, numericId))
                    result.add(ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), numericId))
                }
            }
        }

        return result.toList()
    }

    private fun tryCopyCandidateUriToFile(candidateUri: Uri, destFile: File, candidateIndex: Int): Boolean {
        val resolver = context.contentResolver
        val strategyErrors = mutableListOf<String>()

        // Strategy 1: Standard ContentResolver.openInputStream
        val copiedViaStream = runCatching {
            val stream = resolver.openInputStream(candidateUri)
            if (stream == null) {
                strategyErrors.add("S1(openInputStream)=returned null")
                false
            } else {
                stream.use { input -> writeStreamToFile(input, destFile) }
            }
        }.onFailure { err ->
            strategyErrors.add("S1(openInputStream)=${formatThrowableDiag(err, candidateUri)}")
        }.getOrDefault(false)
        if (copiedViaStream) {
            Log.d(TAG, "[COPY_STRATEGY] Candidate[$candidateIndex] S1(openInputStream) SUCCEEDED for $candidateUri")
            return true
        }

        // Strategy 2: ContentResolver.openTypedAssetFileDescriptor with "video/*"
        // (Avoids ColorOS fuse.onedirect.enable "*/*" + ACCEPT_ORIGINAL_MEDIA_FORMAT crash)
        val copiedViaTypedVideo = runCatching {
            val afd = resolver.openTypedAssetFileDescriptor(candidateUri, "video/*", null)
            if (afd == null) {
                strategyErrors.add("S2(openTypedAFD:video/*)=returned null")
                false
            } else {
                afd.use { desc ->
                    desc.createInputStream().use { input ->
                        writeStreamToFile(input, destFile)
                    }
                }
            }
        }.onFailure { err ->
            strategyErrors.add("S2(openTypedAFD:video/*)=${formatThrowableDiag(err, candidateUri)}")
        }.getOrDefault(false)
        if (copiedViaTypedVideo) {
            Log.d(TAG, "[COPY_STRATEGY] Candidate[$candidateIndex] S2(openTypedAFD:video/*) SUCCEEDED for $candidateUri")
            return true
        }

        // Strategy 3: ContentResolver.openAssetFileDescriptor
        val copiedViaAfd = runCatching {
            val afd = resolver.openAssetFileDescriptor(candidateUri, "r")
            if (afd == null) {
                strategyErrors.add("S3(openAssetFD)=returned null")
                false
            } else {
                afd.use { desc ->
                    desc.createInputStream().use { input ->
                        writeStreamToFile(input, destFile)
                    }
                }
            }
        }.onFailure { err ->
            strategyErrors.add("S3(openAssetFD)=${formatThrowableDiag(err, candidateUri)}")
        }.getOrDefault(false)
        if (copiedViaAfd) {
            Log.d(TAG, "[COPY_STRATEGY] Candidate[$candidateIndex] S3(openAssetFD) SUCCEEDED for $candidateUri")
            return true
        }

        // Strategy 4: ContentResolver.openFileDescriptor with AutoCloseInputStream
        val copiedViaPfd = runCatching {
            val pfd = resolver.openFileDescriptor(candidateUri, "r")
            if (pfd == null) {
                strategyErrors.add("S4(openFD)=returned null")
                false
            } else {
                pfd.use { desc ->
                    ParcelFileDescriptor.AutoCloseInputStream(desc).use { input ->
                        writeStreamToFile(input, destFile)
                    }
                }
            }
        }.onFailure { err ->
            strategyErrors.add("S4(openFD)=${formatThrowableDiag(err, candidateUri)}")
        }.getOrDefault(false)
        if (copiedViaPfd) {
            Log.d(TAG, "[COPY_STRATEGY] Candidate[$candidateIndex] S4(openFD) SUCCEEDED for $candidateUri")
            return true
        }

        // Strategy 5: ContentResolver.openTypedAssetFileDescriptor with "*/*" (null Bundle)
        val copiedViaTypedWildcard = runCatching {
            val afd = resolver.openTypedAssetFileDescriptor(candidateUri, "*/*", null)
            if (afd == null) {
                strategyErrors.add("S5(openTypedAFD:*/*)=returned null")
                false
            } else {
                afd.use { desc ->
                    desc.createInputStream().use { input ->
                        writeStreamToFile(input, destFile)
                    }
                }
            }
        }.onFailure { err ->
            strategyErrors.add("S5(openTypedAFD:*/*)=${formatThrowableDiag(err, candidateUri)}")
        }.getOrDefault(false)
        if (copiedViaTypedWildcard) {
            Log.d(TAG, "[COPY_STRATEGY] Candidate[$candidateIndex] S5(openTypedAFD:*/*) SUCCEEDED for $candidateUri")
            return true
        }

        // Strategy 6: Query _data path column from ContentResolver if readable
        val copiedViaDataColumn = runCatching {
            resolver.query(
                candidateUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val path = if (colIdx >= 0) cursor.getString(colIdx) else null
                    if (!path.isNullOrBlank()) {
                        val srcFile = File(path)
                        if (srcFile.exists() && srcFile.canRead() && srcFile.length() > 0L) {
                            FileInputStream(srcFile).use { input ->
                                writeStreamToFile(input, destFile)
                            }
                        } else {
                            strategyErrors.add("S6(query _data)=file unreadable or empty ($path, exists=${srcFile.exists()}, canRead=${srcFile.canRead()})")
                            false
                        }
                    } else {
                        strategyErrors.add("S6(query _data)=_data column blank/null")
                        false
                    }
                } else {
                    strategyErrors.add("S6(query _data)=empty cursor")
                    false
                }
            } ?: run {
                strategyErrors.add("S6(query _data)=resolver.query returned null")
                false
            }
        }.onFailure { err ->
            strategyErrors.add("S6(query _data)=${formatThrowableDiag(err, candidateUri)}")
        }.getOrDefault(false)

        if (copiedViaDataColumn) {
            Log.d(TAG, "[COPY_STRATEGY] Candidate[$candidateIndex] S6(query _data) SUCCEEDED for $candidateUri")
            return true
        }

        Log.w(
            TAG,
            "[COPY_STRATEGY_FAILED] Candidate[$candidateIndex] ($candidateUri) failed all 6 strategies | " +
                strategyErrors.joinToString(" | ")
        )
        return false
    }

    private fun formatThrowableDiag(err: Throwable, uri: Uri): String {
        val causeTag = classifyAccessFailureCause(err, uri)
        return "${err.javaClass.simpleName}(${err.message}) [$causeTag]"
    }

    /**
     * Classifies the specific underlying cause of a file/URI access denial for diagnostic logging.
     */
    fun classifyAccessFailureCause(err: Throwable, uri: Uri): String {
        val uriStr = uri.toString()
        val msg = (err.message ?: "").lowercase()
        val cause = err.cause
        val errno = (err as? ErrnoException) ?: (cause as? ErrnoException)

        return when {
            err is SecurityException || cause is SecurityException -> {
                "URI_PERMISSION_DENIED (Caller UID=${Process.myUid()} lacks read grant for authority='${uri.authority}')"
            }
            errno != null -> {
                "POSIX_ERRNO_${errno.errno} (${errno.message})"
            }
            err is FileNotFoundException || cause is FileNotFoundException -> {
                if (uriStr.contains("photopicker") || uriStr.contains("/picker/")) {
                    "PICKER_PROVIDER_FILE_NOT_FOUND (CloudMediaProvider '${uri.authority}' failed to open picker item or rejected typed MIME request)"
                } else {
                    "FILE_NOT_FOUND (Target media item missing or unmounted)"
                }
            }
            msg.contains("could not access") -> {
                "MMR_COULD_NOT_ACCESS_URI (MediaMetadataRetriever failed to open ContentResolver FD or hit OEM fuse.onedirect.enable openTypedAssetFile bug)"
            }
            msg.contains("setdatasourcefd failed") || msg.contains("0x80000000") -> {
                "MEDIA_EXTRACTOR_FD_REJECTED (Cross-process media.extractor SELinux denial or non-seekable pipe FD from provider '${uri.authority}')"
            }
            else -> {
                "UNCLASSIFIED_IO_OR_CODEC_ERROR (${err.javaClass.simpleName})"
            }
        }
    }

    private fun writeStreamToFile(input: InputStream, destFile: File): Boolean {
        return try {
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var totalBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    totalBytes += read
                }
                output.flush()
                totalBytes > 0L
            }
        } catch (e: Exception) {
            Log.w(TAG, "[STREAM_WRITE_ERROR] Failed writing to ${destFile.absolutePath}: ${e.javaClass.simpleName}: ${e.message}")
            runCatching { if (destFile.exists()) destFile.delete() }
            false
        }
    }

    private fun pruneOldImportedCacheFiles(importDir: File) {
        runCatching {
            val files = importDir.listFiles() ?: return
            if (files.size <= 12) return
            val activePaths = resolvedUriFileCache.values.map { it.absolutePath }.toSet()
            files.filter { it.absolutePath !in activePaths }
                .sortedBy { it.lastModified() }
                .take((files.size - 8).coerceAtLeast(0))
                .forEach { it.delete() }
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

    companion object {
        const val TAG = "PixelLoopPickerDiag"
    }
}
