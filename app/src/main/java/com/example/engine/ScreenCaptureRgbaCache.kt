package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Paint
import android.media.Image
import android.os.Build
import android.util.LruCache
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Utility for lossless RGBA_8888 [Image] extraction (stripping `rowStride - pixelStride * width`
 * row padding) and wide-gamut (Display P3 / BT.709) to standard sRGB [Bitmap.Config.ARGB_8888]
 * calibration.
 */
object ScreenCaptureRgbaCache {

    private data class TimestampedFrame(
        val timeMs: Long,
        val cacheKey: String
    )

    // Bounded in-memory LRU cache (max ~16 MB) with safe eviction
    private val bitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    private val sessionFramesByPath = ConcurrentHashMap<String, MutableList<TimestampedFrame>>()
    private val srgbPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Converts an [Image] with format [android.graphics.PixelFormat.RGBA_8888] into a clean,
     * tightly-packed sRGB [Bitmap.Config.ARGB_8888] while stripping `rowStride - pixelStride * width`
     * row padding bytes.
     */
    fun extractLosslessArgbBitmap(
        image: Image,
        width: Int,
        height: Int,
        reusableTightBuffer: ByteBuffer? = null
    ): Bitmap? {
        return try {
            val planes = image.planes
            if (planes.isEmpty()) return null
            val plane = planes[0]
            val buffer = plane.buffer ?: return null
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            buffer.rewind()

            val rawBitmap: Bitmap = if (rowPadding == 0 && pixelStride == 4) {
                val bmp = createSrgbBitmap(width, height)
                bmp.copyPixelsFromBuffer(buffer)
                bmp
            } else if (rowPadding > 0 && pixelStride == 4) {
                val tightBytesNeeded = width * height * 4
                val tightBuf = if (reusableTightBuffer != null && reusableTightBuffer.capacity() >= tightBytesNeeded) {
                    reusableTightBuffer.clear()
                    reusableTightBuffer
                } else {
                    ByteBuffer.allocateDirect(tightBytesNeeded)
                }

                val rowBytes = ByteArray(width * 4)
                for (row in 0 until height) {
                    val rowStartOffset = row * rowStride
                    if (rowStartOffset + width * 4 > buffer.capacity()) break
                    buffer.position(rowStartOffset)
                    buffer.get(rowBytes, 0, width * 4)
                    tightBuf.put(rowBytes)
                }
                tightBuf.rewind()
                val bmp = createSrgbBitmap(width, height)
                bmp.copyPixelsFromBuffer(tightBuf)
                bmp
            } else if (rowPadding >= 0 && pixelStride > 0 && rowPadding % pixelStride == 0) {
                val paddedWidth = width + rowPadding / pixelStride
                val paddedBmp = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                paddedBmp.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(paddedBmp, 0, 0, width, height)
                if (cropped !== paddedBmp) {
                    paddedBmp.recycle()
                }
                calibrateBitmapToSrgb(cropped)
            } else {
                return null
            }

            calibrateBitmapToSrgb(rawBitmap)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Ensures that a [Bitmap] (potentially tagged with Display P3, BT.709, or RGB_565) is
     * converted and clamped into standard [ColorSpace.Named.SRGB] with [Bitmap.Config.ARGB_8888]
     * so high-saturation colors and neutral UI surfaces never appear washed out or shifted in GIFs.
     */
    fun calibrateBitmapToSrgb(source: Bitmap): Bitmap {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val srgb = ColorSpace.get(ColorSpace.Named.SRGB)
                val srcSpace = source.colorSpace
                val needsColorSpaceConversion = srcSpace != null && srcSpace != srgb
                val needsConfigConversion = source.config != Bitmap.Config.ARGB_8888

                if (!needsColorSpaceConversion && !needsConfigConversion) {
                    return source
                }

                val calibrated = Bitmap.createBitmap(
                    source.width,
                    source.height,
                    Bitmap.Config.ARGB_8888,
                    false,
                    srgb
                )
                val canvas = Canvas(calibrated)
                canvas.drawBitmap(source, 0f, 0f, srgbPaint)
                calibrated
            } else {
                if (source.config == Bitmap.Config.ARGB_8888) {
                    source
                } else {
                    source.copy(Bitmap.Config.ARGB_8888, false) ?: source
                }
            }
        } catch (_: Throwable) {
            source
        }
    }

    fun createSrgbBitmap(width: Int, height: Int): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888,
                false,
                ColorSpace.get(ColorSpace.Named.SRGB)
            )
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }

    @Synchronized
    fun getLosslessFrameAt(videoFilePath: String, timeMs: Long, maxToleranceMs: Long = 250L): Bitmap? {
        return try {
            val frames = sessionFramesByPath[videoFilePath] ?: return null
            if (frames.isEmpty()) return null

            val closest = frames.minByOrNull { abs(it.timeMs - timeMs) } ?: return null
            if (abs(closest.timeMs - timeMs) > maxToleranceMs && frames.size > 2) {
                return null
            }
            val cached = bitmapCache.get(closest.cacheKey)
            if (cached == null || cached.isRecycled) {
                return null
            }
            cached.copy(Bitmap.Config.ARGB_8888, false)?.let { calibrateBitmapToSrgb(it) }
        } catch (_: Throwable) {
            null
        }
    }

    @Synchronized
    fun clearFramesForFile(videoFile: File?) {
        val path = videoFile?.absolutePath ?: return
        val removed = sessionFramesByPath.remove(path) ?: return
        for (item in removed) {
            bitmapCache.remove(item.cacheKey)
        }
    }
}
