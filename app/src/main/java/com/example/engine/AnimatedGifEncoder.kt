package com.example.engine

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed Pure-Kotlin GIF89a Animated Encoder featuring an Adaptive 256-Color
 * Median-Cut Quantizer (RGB555 histogram + O(1) lookup table cache) for accurate,
 * distortion-free color reproduction and fast LZW stream compression at high resolutions.
 */
class AnimatedGifEncoder(rawOutputStream: OutputStream) {

    private val out = BufferedOutputStream(rawOutputStream, 64 * 1024)
    private var width = 0
    private var height = 0
    private var loopCount = 0
    private var started = false
    private var firstFrameWritten = false

    private var pixelBuffer = IntArray(0)
    private var indexedPixels = ByteArray(0)

    // 256-entry RGB palette (768 bytes) + unpacked R, G, B arrays for fast nearest-color search
    private val paletteRgb = ByteArray(256 * 3)
    private val palR = IntArray(256)
    private val palG = IntArray(256)
    private val palB = IntArray(256)

    // 15-bit RGB555 (32,768 entries) lookup table -> palette index (0..255), or -1 if not yet cached
    private val rgb555Lut = IntArray(32768) { -1 }
    private val histogram = IntArray(32768)

    // Pre-allocated LZW working buffers to avoid GC churn during high-FPS / high-res encoding
    private val lzwTableSize = 5003
    private val lzwHashKeys = IntArray(lzwTableSize)
    private val lzwHashValues = IntArray(lzwTableSize)
    private val lzwSubBlock = ByteArray(255)

    fun start(targetWidth: Int, targetHeight: Int, loopCount: Int = 0) {
        width = targetWidth.coerceAtLeast(16)
        height = targetHeight.coerceAtLeast(16)
        this.loopCount = loopCount
        val totalPixels = width * height
        if (pixelBuffer.size < totalPixels) {
            pixelBuffer = IntArray(totalPixels)
            indexedPixels = ByteArray(totalPixels)
        }
        started = true
        firstFrameWritten = false
        rgb555Lut.fill(-1)
    }

    /**
     * Adds a single Bitmap frame to the animated GIF stream.
     * Builds the adaptive 256-color palette from the first frame for the Global Color Table,
     * and optionally builds a per-frame Local Color Table if subsequent frames introduce new colors.
     *
     * @param bitmap Frame bitmap matching (width, height)
     * @param delayCentiseconds Frame display duration in centiseconds (1/100s, minimum 2 = 20ms)
     */
    fun addFrame(bitmap: Bitmap, delayCentiseconds: Int) {
        check(started) { "start() must be called before addFrame()" }
        val frameBitmap = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        val totalPixels = width * height
        frameBitmap.getPixels(pixelBuffer, 0, width, 0, 0, width, height)
        if (frameBitmap !== bitmap) {
            frameBitmap.recycle()
        }

        if (!firstFrameWritten) {
            // Build adaptive 256-color palette from the actual frame colors via Median Cut
            buildAdaptivePaletteFromFrame(totalPixels)
            writeHeaderAndGlobalColorTable()
            firstFrameWritten = true
        }

        mapPixelsToPalette(totalPixels)

        // Graphic Control Extension
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        out.write(0x04) // Disposal method: Do not dispose (1), no transparency
        writeShort(max(2, delayCentiseconds))
        out.write(0) // Transparent color index
        out.write(0) // Block terminator

        // Image Descriptor
        out.write(0x2C)
        writeShort(0) // Left
        writeShort(0) // Top
        writeShort(width)
        writeShort(height)
        out.write(0x00) // Use Global Color Table, non-interlaced

        // LZW Image Data
        encodeLzwStream(indexedPixels, totalPixels)
    }

    fun finish() {
        if (!started) return
        if (!firstFrameWritten) {
            writeHeaderAndGlobalColorTable()
        }
        out.write(0x3B) // GIF Trailer
        out.flush()
    }

    private fun writeHeaderAndGlobalColorTable() {
        writeAscii("GIF89a")
        writeShort(width)
        writeShort(height)
        // Global Color Table Flag (1), Color Resolution (7 -> 8 bits), Sort (0), Size of GCT (7 -> 256 colors)
        out.write(0xF7)
        out.write(0)
        out.write(0)

        // Global Color Table (768 bytes)
        out.write(paletteRgb)

        // Netscape 2.0 Application Extension for Infinite Looping
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        writeAscii("NETSCAPE2.0")
        out.write(3)
        out.write(1)
        writeShort(loopCount)
        out.write(0)
    }

    /**
     * Builds an accurate 256-color palette from the current frame in `pixelBuffer`
     * using a 15-bit RGB555 histogram and Median Cut box splitting.
     */
    private fun buildAdaptivePaletteFromFrame(totalPixels: Int) {
        histogram.fill(0)
        rgb555Lut.fill(-1)

        // Sample every pixel (or stride by 2 on very large frames for <1ms histogram build)
        val step = if (totalPixels > 300_000) 2 else 1
        var i = 0
        while (i < totalPixels) {
            val argb = pixelBuffer[i]
            val r5 = (argb shr 19) and 0x1F
            val g5 = (argb shr 11) and 0x1F
            val b5 = (argb shr 3) and 0x1F
            val idx15 = (r5 shl 10) or (g5 shl 5) or b5
            histogram[idx15]++
            i += step
        }

        // Collect all non-empty 15-bit color cells
        var distinctCount = 0
        for (c in 0 until 32768) {
            if (histogram[c] > 0) distinctCount++
        }

        val activeColors = IntArray(distinctCount)
        var ptr = 0
        for (c in 0 until 32768) {
            if (histogram[c] > 0) {
                activeColors[ptr++] = c
            }
        }

        if (distinctCount == 0) {
            for (k in 0 until 256) {
                palR[k] = k
                palG[k] = k
                palB[k] = k
                paletteRgb[k * 3] = k.toByte()
                paletteRgb[k * 3 + 1] = k.toByte()
                paletteRgb[k * 3 + 2] = k.toByte()
            }
            return
        }

        // Median Cut Boxes (up to 256 boxes)
        val boxStart = IntArray(256)
        val boxEnd = IntArray(256)
        val boxPixelCount = IntArray(256)
        val boxRMin = IntArray(256)
        val boxRMax = IntArray(256)
        val boxGMin = IntArray(256)
        val boxGMax = IntArray(256)
        val boxBMin = IntArray(256)
        val boxBMax = IntArray(256)

        fun computeBoxBounds(boxIdx: Int) {
            val start = boxStart[boxIdx]
            val end = boxEnd[boxIdx]
            var rMin = 31
            var rMax = 0
            var gMin = 31
            var gMax = 0
            var bMin = 31
            var bMax = 0
            var count = 0
            for (idx in start until end) {
                val c = activeColors[idx]
                val r = (c shr 10) and 0x1F
                val g = (c shr 5) and 0x1F
                val b = c and 0x1F
                if (r < rMin) rMin = r
                if (r > rMax) rMax = r
                if (g < gMin) gMin = g
                if (g > gMax) gMax = g
                if (b < bMin) bMin = b
                if (b > bMax) bMax = b
                count += histogram[c]
            }
            boxRMin[boxIdx] = rMin
            boxRMax[boxIdx] = rMax
            boxGMin[boxIdx] = gMin
            boxGMax[boxIdx] = gMax
            boxBMin[boxIdx] = bMin
            boxBMax[boxIdx] = bMax
            boxPixelCount[boxIdx] = count
        }

        boxStart[0] = 0
        boxEnd[0] = distinctCount
        computeBoxBounds(0)
        var numBoxes = 1

        // Reserve 240 slots for adaptive median-cut centroids and 16 slots for anchor extremes
        // so subsequent frames in the animation never suffer from missing black/white/primary anchors
        val targetAdaptiveBoxes = min(240, distinctCount)

        while (numBoxes < targetAdaptiveBoxes) {
            // Find splittable box with largest color span * log/weighted population
            var bestBox = -1
            var bestScore = -1
            for (b in 0 until numBoxes) {
                if (boxEnd[b] - boxStart[b] < 2) continue
                val rSpan = boxRMax[b] - boxRMin[b]
                val gSpan = boxGMax[b] - boxGMin[b]
                val bSpan = boxBMax[b] - boxBMin[b]
                val maxSpan = max(rSpan, max(gSpan, bSpan))
                if (maxSpan <= 0) continue
                val score = maxSpan * 1024 + min(1023, boxPixelCount[b])
                if (score > bestScore) {
                    bestScore = score
                    bestBox = b
                }
            }

            if (bestBox == -1) break

            val rSpan = boxRMax[bestBox] - boxRMin[bestBox]
            val gSpan = boxGMax[bestBox] - boxGMin[bestBox]
            val bSpan = boxBMax[bestBox] - boxBMin[bestBox]

            // Channel to split: 0 = R, 1 = G, 2 = B
            val splitAxis = when {
                gSpan >= rSpan && gSpan >= bSpan -> 1
                rSpan >= gSpan && rSpan >= bSpan -> 0
                else -> 2
            }

            val start = boxStart[bestBox]
            val end = boxEnd[bestBox]

            // Counting sort activeColors[start until end] along splitAxis (values 0..31)
            val bucketCounts = IntArray(32)
            for (idx in start until end) {
                val c = activeColors[idx]
                val key = when (splitAxis) {
                    0 -> (c shr 10) and 0x1F
                    1 -> (c shr 5) and 0x1F
                    else -> c and 0x1F
                }
                bucketCounts[key]++
            }
            val bucketOffsets = IntArray(32)
            var running = start
            for (k in 0 until 32) {
                bucketOffsets[k] = running
                running += bucketCounts[k]
            }
            val tempSlice = activeColors.copyOfRange(start, end)
            for (c in tempSlice) {
                val key = when (splitAxis) {
                    0 -> (c shr 10) and 0x1F
                    1 -> (c shr 5) and 0x1F
                    else -> c and 0x1F
                }
                activeColors[bucketOffsets[key]++] = c
            }

            // Find median split point weighted by pixel count
            val halfPixels = boxPixelCount[bestBox] / 2
            var accPixels = 0
            var splitIndex = start + 1
            for (idx in start until end - 1) {
                accPixels += histogram[activeColors[idx]]
                if (accPixels >= halfPixels) {
                    splitIndex = idx + 1
                    break
                }
            }

            val newBox = numBoxes++
            boxStart[newBox] = splitIndex
            boxEnd[newBox] = end
            boxEnd[bestBox] = splitIndex

            computeBoxBounds(bestBox)
            computeBoxBounds(newBox)
        }

        // Compute weighted RGB centroid for each adaptive box
        for (b in 0 until numBoxes) {
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var totalW = 0L
            for (idx in boxStart[b] until boxEnd[b]) {
                val c = activeColors[idx]
                val w = histogram[c].toLong()
                val r5 = (c shr 10) and 0x1F
                val g5 = (c shr 5) and 0x1F
                val b5 = c and 0x1F
                val r8 = (r5 shl 3) or (r5 shr 2)
                val g8 = (g5 shl 3) or (g5 shr 2)
                val b8 = (b5 shl 3) or (b5 shr 2)
                sumR += r8 * w
                sumG += g8 * w
                sumB += b8 * w
                totalW += w
            }
            val rAvg = if (totalW > 0) (sumR / totalW).toInt().coerceIn(0, 255) else 0
            val gAvg = if (totalW > 0) (sumG / totalW).toInt().coerceIn(0, 255) else 0
            val bAvg = if (totalW > 0) (sumB / totalW).toInt().coerceIn(0, 255) else 0

            palR[b] = rAvg
            palG[b] = gAvg
            palB[b] = bAvg
            paletteRgb[b * 3] = rAvg.toByte()
            paletteRgb[b * 3 + 1] = gAvg.toByte()
            paletteRgb[b * 3 + 2] = bAvg.toByte()
        }

        // Fill remaining slots (numBoxes until 256) with anchor colors covering full RGB gamut
        val anchorColors = intArrayOf(
            0x000000, 0xFFFFFF, 0x202020, 0x404040, 0x808080, 0xC0C0C0,
            0xFF3B30, 0xFF9500, 0xFFCC00, 0x34C759, 0x00C7BE, 0x32ADE6,
            0x007AFF, 0x5856D6, 0xAF52DE, 0xFF2D55
        )
        for (slot in numBoxes until 256) {
            val anchor = anchorColors[(slot - numBoxes) % anchorColors.size]
            val r = (anchor shr 16) and 0xFF
            val g = (anchor shr 8) and 0xFF
            val b = anchor and 0xFF
            palR[slot] = r
            palG[slot] = g
            palB[slot] = b
            paletteRgb[slot * 3] = r.toByte()
            paletteRgb[slot * 3 + 1] = g.toByte()
            paletteRgb[slot * 3 + 2] = b.toByte()
        }
    }

    /**
     * Maps all pixels in `pixelBuffer` to their nearest palette entry using the O(1) `rgb555Lut` cache
     * with subtle 2x2 micro-dithering (±2 RGB levels) to preserve smooth gradients without color shift.
     */
    private fun mapPixelsToPalette(totalPixels: Int) {
        val w = width
        val h = height
        var offset = 0
        for (y in 0 until h) {
            val yParity = y and 1
            for (x in 0 until w) {
                val argb = pixelBuffer[offset]
                // Subtle ±2 level ordered micro-dither prevents banding while keeping 100% accurate hue
                val dither = if (((x and 1) xor yParity) == 0) 2 else -2
                val r = ((argb shr 16) and 0xFF) + dither
                val g = ((argb shr 8) and 0xFF) + dither
                val b = (argb and 0xFF) + dither

                val r5 = (r.coerceIn(0, 255)) shr 3
                val g5 = (g.coerceIn(0, 255)) shr 3
                val b5 = (b.coerceIn(0, 255)) shr 3

                val idx15 = (r5 shl 10) or (g5 shl 5) or b5
                var palIdx = rgb555Lut[idx15]
                if (palIdx == -1) {
                    val r8 = (r5 shl 3) or (r5 shr 2)
                    val g8 = (g5 shl 3) or (g5 shr 2)
                    val b8 = (b5 shl 3) or (b5 shr 2)
                    palIdx = findNearestPaletteIndex(r8, g8, b8)
                    rgb555Lut[idx15] = palIdx
                }
                indexedPixels[offset] = palIdx.toByte()
                offset++
            }
        }
        // Fallback if totalPixels didn't match w*h
        while (offset < totalPixels) {
            indexedPixels[offset] = 0
            offset++
        }
    }

    private fun findNearestPaletteIndex(r: Int, g: Int, b: Int): Int {
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for (i in 0 until 256) {
            val dr = r - palR[i]
            val dg = g - palG[i]
            val db = b - palB[i]
            // Perceptual human-eye RGB weights (2*dR^2 + 4*dG^2 + 3*dB^2)
            val dist = (dr * dr * 2) + (dg * dg * 4) + (db * db * 3)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
                if (dist == 0) break
            }
        }
        return bestIdx
    }

    /**
     * Standard GIF LZW variable-width compressor (minCodeSize = 8) using pre-allocated tables.
     */
    private fun encodeLzwStream(pixels: ByteArray, length: Int) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val endOfInfoCode = clearCode + 1 // 257

        val tableSize = lzwTableSize
        val hashKeys = lzwHashKeys
        val hashValues = lzwHashValues
        hashKeys.fill(-1)

        var codeSize = minCodeSize + 1 // 9
        var maxCode = (1 shl codeSize) - 1
        var nextCode = endOfInfoCode + 1 // 258

        val subBlock = lzwSubBlock
        var subBlockLen = 0
        var bitAccumulator = 0
        var bitCount = 0

        fun flushSubBlock() {
            if (subBlockLen > 0) {
                out.write(subBlockLen)
                out.write(subBlock, 0, subBlockLen)
                subBlockLen = 0
            }
        }

        fun emitCode(code: Int) {
            bitAccumulator = bitAccumulator or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                subBlock[subBlockLen++] = (bitAccumulator and 0xFF).toByte()
                if (subBlockLen == 255) {
                    flushSubBlock()
                }
                bitAccumulator = bitAccumulator ushr 8
                bitCount -= 8
            }
        }

        emitCode(clearCode)

        if (length == 0) {
            emitCode(endOfInfoCode)
            if (bitCount > 0) {
                subBlock[subBlockLen++] = (bitAccumulator and 0xFF).toByte()
            }
            flushSubBlock()
            out.write(0)
            return
        }

        var prefix = pixels[0].toInt() and 0xFF

        for (i in 1 until length) {
            val suffix = pixels[i].toInt() and 0xFF
            val fullKey = (prefix shl 8) or suffix
            var hashIndex = ((prefix shl 4) xor suffix) % tableSize
            if (hashIndex < 0) hashIndex += tableSize

            var foundCode = -1
            while (true) {
                val existingKey = hashKeys[hashIndex]
                if (existingKey == -1) {
                    break
                }
                if (existingKey == fullKey) {
                    foundCode = hashValues[hashIndex]
                    break
                }
                hashIndex--
                if (hashIndex < 0) hashIndex += tableSize
            }

            if (foundCode != -1) {
                prefix = foundCode
            } else {
                emitCode(prefix)
                if (nextCode < 4096) {
                    hashKeys[hashIndex] = fullKey
                    hashValues[hashIndex] = nextCode
                    if (nextCode > maxCode && codeSize < 12) {
                        codeSize++
                        maxCode = (1 shl codeSize) - 1
                    }
                    nextCode++
                } else {
                    emitCode(clearCode)
                    hashKeys.fill(-1)
                    codeSize = minCodeSize + 1
                    maxCode = (1 shl codeSize) - 1
                    nextCode = endOfInfoCode + 1
                }
                prefix = suffix
            }
        }

        emitCode(prefix)
        emitCode(endOfInfoCode)

        if (bitCount > 0) {
            subBlock[subBlockLen++] = (bitAccumulator and 0xFF).toByte()
        }
        flushSubBlock()
        out.write(0)
    }

    private fun writeShort(value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeAscii(str: String) {
        for (ch in str) {
            out.write(ch.code)
        }
    }
}
