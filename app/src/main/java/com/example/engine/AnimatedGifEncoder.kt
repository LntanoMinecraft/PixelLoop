package com.example.engine

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision Pure-Kotlin GIF89a Animated Encoder featuring:
 * - Full 1:1 pixel sampling (`setQuality(1)` by default, zero pixel skipping)
 * - 18-bit RGB666 histogram + Exact 24-bit RGB888 centroid accumulation (eliminates RGB555 gray shift & banding)
 * - Per-frame 256-color Local Color Table (LCT) so every frame preserves 100% of its UI/video palette
 * - Noise-free flat UI & text mapping (`setDither(false)` by default to eliminate snow speckles & flicker)
 */
class AnimatedGifEncoder(rawOutputStream: OutputStream) {

    private val out = BufferedOutputStream(rawOutputStream, 64 * 1024)
    private var width = 0
    private var height = 0
    private var loopCount = 0
    private var started = false
    private var firstFrameWritten = false

    // Quality = 1 means every single pixel is sampled (step = 1) with maximum color accuracy
    private var sampleQuality: Int = 1

    // Disabled by default for screen recordings & flat UI/text to prevent snow noise and frame flicker
    private var ditheringEnabled: Boolean = false

    private var pixelBuffer = IntArray(0)
    private var indexedPixels = ByteArray(0)

    // 256-entry RGB palette (768 bytes) + unpacked 8-bit R, G, B arrays for nearest-color search
    private val paletteRgb = ByteArray(256 * 3)
    private val palR = IntArray(256)
    private val palG = IntArray(256)
    private val palB = IntArray(256)

    // 18-bit RGB666 (64 x 64 x 64 = 262,144 entries) lookup table & histogram
    // Compact memory footprint (~2 MB total reused across all frames) to prevent GC churn & OOM
    private val rgb666Lut = IntArray(262_144) { -1 }
    private val histogram = IntArray(262_144)

    // Pre-allocated LZW working buffers to avoid GC churn during high-FPS / high-res encoding
    private val lzwTableSize = 5003
    private val lzwHashKeys = IntArray(lzwTableSize)
    private val lzwHashValues = IntArray(lzwTableSize)
    private val lzwSubBlock = ByteArray(255)

    /**
     * Sets color quantization sampling factor.
     * `1` = highest precision (every pixel sampled, zero skipping).
     */
    fun setQuality(quality: Int) {
        sampleQuality = quality.coerceAtLeast(1)
    }

    /**
     * Controls ordered micro-dithering.
     * Keep `false` (default) for screen recordings, flat UI, and sharp text to avoid speckle noise.
     */
    fun setDither(enable: Boolean) {
        ditheringEnabled = enable
    }

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
        rgb666Lut.fill(-1)
    }

    /**
     * Adds a single sRGB Bitmap frame to the animated GIF stream.
     * Builds an adaptive 256-color Global Color Table on the first frame and a dedicated
     * 256-color Local Color Table on every subsequent frame so color transitions never wash out.
     *
     * @param bitmap Frame bitmap matching (width, height)
     * @param delayCentiseconds Frame display duration in centiseconds (1/100s, minimum 2 = 20ms)
     */
    fun addFrame(bitmap: Bitmap, delayCentiseconds: Int) {
        check(started) { "start() must be called before addFrame()" }
        val calibratedSource = ScreenCaptureRgbaCache.calibrateBitmapToSrgb(bitmap)
        val frameBitmap = if (calibratedSource.width != width || calibratedSource.height != height) {
            Bitmap.createScaledBitmap(calibratedSource, width, height, true)
        } else {
            calibratedSource
        }

        val totalPixels = width * height
        frameBitmap.getPixels(pixelBuffer, 0, width, 0, 0, width, height)
        if (frameBitmap !== calibratedSource && frameBitmap !== bitmap) {
            frameBitmap.recycle()
        }
        if (calibratedSource !== bitmap) {
            calibratedSource.recycle()
        }

        // Build exact 256-color adaptive palette for THIS frame at full sampling quality
        buildAdaptivePaletteFromFrame(totalPixels)

        val isFirstFrame = !firstFrameWritten
        if (isFirstFrame) {
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
        if (isFirstFrame) {
            // Frame 0 uses the Global Color Table written immediately above
            out.write(0x00)
        } else {
            // Subsequent frames emit a 256-entry Local Color Table (0x80 LCT flag | 0x07 size=256)
            out.write(0x87)
            out.write(paletteRgb)
        }

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
     * using an 18-bit RGB666 histogram + Exact 24-bit RGB888 centroid accumulation.
     * When `sampleQuality == 1`, every single pixel (`step = 1`) is sampled.
     */
    private fun buildAdaptivePaletteFromFrame(totalPixels: Int) {
        histogram.fill(0)
        rgb666Lut.fill(-1)

        // Strict full-pixel sampling when sampleQuality == 1
        val step = sampleQuality.coerceAtLeast(1)
        var i = 0
        while (i < totalPixels) {
            val argb = pixelBuffer[i]
            val r6 = (argb ushr 18) and 0x3F
            val g6 = (argb ushr 10) and 0x3F
            val b6 = (argb ushr 2) and 0x3F
            val idx18 = (r6 shl 12) or (g6 shl 6) or b6
            histogram[idx18]++
            i += step
        }

        // Count active 18-bit color cells
        var distinctCount = 0
        for (c in 0 until 262_144) {
            if (histogram[c] > 0) distinctCount++
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

        // Map each active 18-bit cell to a compact index (0 until distinctCount)
        // and accumulate exact 24-bit (8-bit per channel) R, G, B sums so flat UI colors
        // never suffer from bit-truncation gray shift!
        val activeColors = IntArray(distinctCount)
        val activeSumR = LongArray(distinctCount)
        val activeSumG = LongArray(distinctCount)
        val activeSumB = LongArray(distinctCount)

        var ptr = 0
        for (c in 0 until 262_144) {
            if (histogram[c] > 0) {
                activeColors[ptr] = c
                // Temporarily store compact index + 1 inrgb666Lut for O(1) exact 24-bit sum accumulation
                rgb666Lut[c] = ptr
                ptr++
            }
        }

        i = 0
        while (i < totalPixels) {
            val argb = pixelBuffer[i]
            val r8 = (argb ushr 16) and 0xFF
            val g8 = (argb ushr 8) and 0xFF
            val b8 = argb and 0xFF
            val idx18 = ((r8 ushr 2) shl 12) or ((g8 ushr 2) shl 6) or (b8 ushr 2)
            val compactIdx = rgb666Lut[idx18]
            if (compactIdx >= 0) {
                activeSumR[compactIdx] += r8.toLong()
                activeSumG[compactIdx] += g8.toLong()
                activeSumB[compactIdx] += b8.toLong()
            }
            i += step
        }

        // Reset LUT so it can cache palette indices (0..255) during pixel mapping
        for (idx in 0 until distinctCount) {
            rgb666Lut[activeColors[idx]] = -1
        }

        // Fast path: if the frame has <= 256 distinct RGB666 cells, assign each cell its exact
        // 24-bit average color directly with zero quantization loss!
        if (distinctCount <= 256) {
            for (b in 0 until distinctCount) {
                val c = activeColors[b]
                val count = histogram[c].toLong().coerceAtLeast(1L)
                val rAvg = (activeSumR[b] / count).toInt().coerceIn(0, 255)
                val gAvg = (activeSumG[b] / count).toInt().coerceIn(0, 255)
                val bAvg = (activeSumB[b] / count).toInt().coerceIn(0, 255)

                palR[b] = rAvg
                palG[b] = gAvg
                palB[b] = bAvg
                paletteRgb[b * 3] = rAvg.toByte()
                paletteRgb[b * 3 + 1] = gAvg.toByte()
                paletteRgb[b * 3 + 2] = bAvg.toByte()
                rgb666Lut[c] = b
            }
            for (slot in distinctCount until 256) {
                val ref = slot % distinctCount
                palR[slot] = palR[ref]
                palG[slot] = palG[ref]
                palB[slot] = palB[ref]
                paletteRgb[slot * 3] = paletteRgb[ref * 3]
                paletteRgb[slot * 3 + 1] = paletteRgb[ref * 3 + 1]
                paletteRgb[slot * 3 + 2] = paletteRgb[ref * 3 + 2]
            }
            return
        }

        // Permutation array so activeColors and activeSumR/G/B stay aligned during Median Cut
        val order = IntArray(distinctCount) { it }

        // Median Cut Boxes (all 256 slots used for true frame colors)
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
            var rMin = 63
            var rMax = 0
            var gMin = 63
            var gMax = 0
            var bMin = 63
            var bMax = 0
            var count = 0
            for (pos in start until end) {
                val c = activeColors[order[pos]]
                val r = (c ushr 12) and 0x3F
                val g = (c ushr 6) and 0x3F
                val b = c and 0x3F
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

        // Use all 256 slots for adaptive median-cut centroids:
        // - First 160 boxes prioritize population-weighted color span (keeps large UI surfaces & gradients ultra-smooth)
        // - Remaining 96 boxes prioritize pure color span (preserves small colorful badges, icons & text accents)
        val targetAdaptiveBoxes = min(256, distinctCount)

        while (numBoxes < targetAdaptiveBoxes) {
            val prioritizePopulation = numBoxes < 160
            var bestBox = -1
            var bestScore = -1L

            for (b in 0 until numBoxes) {
                if (boxEnd[b] - boxStart[b] < 2) continue
                val rSpan = boxRMax[b] - boxRMin[b]
                val gSpan = boxGMax[b] - boxGMin[b]
                val bSpan = boxBMax[b] - boxBMin[b]
                // Perceptual channel span (green/red weighted slightly higher than blue)
                val weightedSpan = max(rSpan * 3, max(gSpan * 4, bSpan * 2)).toLong()
                if (weightedSpan <= 0L) continue

                val score = if (prioritizePopulation) {
                    val popWeight = 32L - Integer.numberOfLeadingZeros(boxPixelCount[b].coerceAtLeast(1))
                    weightedSpan * popWeight * popWeight
                } else {
                    (weightedSpan shl 20) + boxPixelCount[b].toLong()
                }

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

            // Counting sort order[start until end] along splitAxis (values 0..63)
            val bucketCounts = IntArray(64)
            for (pos in start until end) {
                val c = activeColors[order[pos]]
                val key = when (splitAxis) {
                    0 -> (c ushr 12) and 0x3F
                    1 -> (c ushr 6) and 0x3F
                    else -> c and 0x3F
                }
                bucketCounts[key]++
            }
            val bucketOffsets = IntArray(64)
            var running = start
            for (k in 0 until 64) {
                bucketOffsets[k] = running
                running += bucketCounts[k]
            }
            val tempOrder = order.copyOfRange(start, end)
            for (itemIdx in tempOrder) {
                val c = activeColors[itemIdx]
                val key = when (splitAxis) {
                    0 -> (c ushr 12) and 0x3F
                    1 -> (c ushr 6) and 0x3F
                    else -> c and 0x3F
                }
                order[bucketOffsets[key]++] = itemIdx
            }

            // Find median split point weighted by pixel count
            val halfPixels = boxPixelCount[bestBox] / 2
            var accPixels = 0
            var splitIndex = start + 1
            for (pos in start until end - 1) {
                accPixels += histogram[activeColors[order[pos]]]
                if (accPixels >= halfPixels) {
                    splitIndex = pos + 1
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

        // Compute EXACT 24-bit RGB centroid for each adaptive box using the accumulated 8-bit sums
        for (b in 0 until numBoxes) {
            var sumR8 = 0L
            var sumG8 = 0L
            var sumB8 = 0L
            var totalW = 0L
            for (pos in boxStart[b] until boxEnd[b]) {
                val itemIdx = order[pos]
                val c = activeColors[itemIdx]
                sumR8 += activeSumR[itemIdx]
                sumG8 += activeSumG[itemIdx]
                sumB8 += activeSumB[itemIdx]
                totalW += histogram[c].toLong()
            }
            val rAvg = if (totalW > 0L) ((sumR8 + totalW / 2) / totalW).toInt().coerceIn(0, 255) else 0
            val gAvg = if (totalW > 0L) ((sumG8 + totalW / 2) / totalW).toInt().coerceIn(0, 255) else 0
            val bAvg = if (totalW > 0L) ((sumB8 + totalW / 2) / totalW).toInt().coerceIn(0, 255) else 0

            palR[b] = rAvg
            palG[b] = gAvg
            palB[b] = bAvg
            paletteRgb[b * 3] = rAvg.toByte()
            paletteRgb[b * 3 + 1] = gAvg.toByte()
            paletteRgb[b * 3 + 2] = bAvg.toByte()
        }

        for (slot in numBoxes until 256) {
            val ref = slot % numBoxes
            palR[slot] = palR[ref]
            palG[slot] = palG[ref]
            palB[slot] = palB[ref]
            paletteRgb[slot * 3] = paletteRgb[ref * 3]
            paletteRgb[slot * 3 + 1] = paletteRgb[ref * 3 + 1]
            paletteRgb[slot * 3 + 2] = paletteRgb[ref * 3 + 2]
        }
    }

    /**
     * Maps all pixels in `pixelBuffer` to their nearest palette entry using the O(1) `rgb666Lut` cache.
     * By default (`ditheringEnabled == false`), maps flat UI and text deterministically without noise.
     */
    private fun mapPixelsToPalette(totalPixels: Int) {
        val w = width
        val h = height
        var offset = 0

        if (!ditheringEnabled) {
            while (offset < totalPixels) {
                val argb = pixelBuffer[offset]
                val r8 = (argb ushr 16) and 0xFF
                val g8 = (argb ushr 8) and 0xFF
                val b8 = argb and 0xFF

                val idx18 = ((r8 ushr 2) shl 12) or ((g8 ushr 2) shl 6) or (b8 ushr 2)
                var palIdx = rgb666Lut[idx18]
                if (palIdx == -1) {
                    palIdx = findNearestPaletteIndex(r8, g8, b8)
                    rgb666Lut[idx18] = palIdx
                }
                indexedPixels[offset] = palIdx.toByte()
                offset++
            }
            return
        }

        // Optional low-amplitude dither (only when explicitly requested, skipping flat identical neighbors)
        for (y in 0 until h) {
            val yParity = y and 1
            for (x in 0 until w) {
                val argb = pixelBuffer[offset]
                val prevArgb = if (x > 0) pixelBuffer[offset - 1] else argb
                val isFlatUi = (argb and 0xFFFFFF) == (prevArgb and 0xFFFFFF)
                val dither = if (isFlatUi) 0 else if (((x and 1) xor yParity) == 0) 1 else -1

                val r8 = (((argb ushr 16) and 0xFF) + dither).coerceIn(0, 255)
                val g8 = (((argb ushr 8) and 0xFF) + dither).coerceIn(0, 255)
                val b8 = ((argb and 0xFF) + dither).coerceIn(0, 255)

                val idx18 = ((r8 ushr 2) shl 12) or ((g8 ushr 2) shl 6) or (b8 ushr 2)
                var palIdx = rgb666Lut[idx18]
                if (palIdx == -1) {
                    palIdx = findNearestPaletteIndex(r8, g8, b8)
                    rgb666Lut[idx18] = palIdx
                }
                indexedPixels[offset] = palIdx.toByte()
                offset++
            }
        }
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
            // Perceptual redmean-inspired sRGB distance weights (2*dR^2 + 4*dG^2 + 3*dB^2)
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
