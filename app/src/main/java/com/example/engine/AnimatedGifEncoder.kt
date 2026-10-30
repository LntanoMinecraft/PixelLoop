package com.example.engine

import android.graphics.Bitmap
import java.io.BufferedOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed Pure-Kotlin GIF89a Animated Encoder with a 256-color perceptual palette,
 * ordered 4x4 Bayer dithering, Netscape 2.0 infinite looping, and LZW stream compression.
 */
class AnimatedGifEncoder(rawOutputStream: OutputStream) {

    private val out = BufferedOutputStream(rawOutputStream, 32 * 1024)
    private var width = 0
    private var height = 0
    private var headerWritten = false
    private var pixelBuffer = IntArray(0)
    private var indexedPixels = ByteArray(0)

    // 6 Red x 7 Green x 6 Blue = 252 colors + 4 extra neutral grays = 256 palette entries
    private val paletteRgb = ByteArray(256 * 3)

    // 4x4 Bayer matrix normalized to [-7..+8] for crisp, artifact-free color dithering
    private val bayer4x4 = intArrayOf(
        -7,  1, -5,  3,
         5, -3,  7, -1,
        -4,  4, -6,  2,
         8,  0,  6, -2
    )

    init {
        buildFixedPerceptualPalette()
    }

    private fun buildFixedPerceptualPalette() {
        var idx = 0
        for (r in 0 until 6) {
            val rv = (r * 255) / 5
            for (g in 0 until 7) {
                val gv = (g * 255) / 6
                for (b in 0 until 6) {
                    val bv = (b * 255) / 5
                    paletteRgb[idx++] = rv.toByte()
                    paletteRgb[idx++] = gv.toByte()
                    paletteRgb[idx++] = bv.toByte()
                }
            }
        }
        // Remaining 4 slots (indices 252..255) filled with intermediate dark/mid grays
        val extraGrays = intArrayOf(18, 36, 72, 128)
        for (gray in extraGrays) {
            paletteRgb[idx++] = gray.toByte()
            paletteRgb[idx++] = gray.toByte()
            paletteRgb[idx++] = gray.toByte()
        }
    }

    fun start(targetWidth: Int, targetHeight: Int, loopCount: Int = 0) {
        width = targetWidth.coerceAtLeast(16)
        height = targetHeight.coerceAtLeast(16)
        val totalPixels = width * height
        if (pixelBuffer.size < totalPixels) {
            pixelBuffer = IntArray(totalPixels)
            indexedPixels = ByteArray(totalPixels)
        }

        // GIF89a Header
        writeAscii("GIF89a")

        // Logical Screen Descriptor
        writeShort(width)
        writeShort(height)
        // Global Color Table Flag (1), Color Resolution (7 -> 8 bits), Sort (0), Size of GCT (7 -> 256 colors)
        out.write(0xF7)
        out.write(0) // Background color index
        out.write(0) // Pixel aspect ratio

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

        headerWritten = true
    }

    /**
     * Adds a single Bitmap frame to the animated GIF stream.
     * @param bitmap Frame bitmap matching (width, height)
     * @param delayMs Frame display duration in milliseconds
     */
    fun addFrame(bitmap: Bitmap, delayMs: Int) {
        check(headerWritten) { "start() must be called before addFrame()" }
        val frameBitmap = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        frameBitmap.getPixels(pixelBuffer, 0, width, 0, 0, width, height)
        if (frameBitmap !== bitmap) {
            frameBitmap.recycle()
        }

        quantizeFramePixels(width, height)

        // Graphic Control Extension
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        out.write(0x04) // Disposal method: Do not dispose (1), no transparency
        val delayCentiseconds = max(2, (delayMs + 5) / 10)
        writeShort(delayCentiseconds)
        out.write(0) // Transparent color index (unused)
        out.write(0) // Block terminator

        // Image Descriptor
        out.write(0x2C)
        writeShort(0) // Left
        writeShort(0) // Top
        writeShort(width)
        writeShort(height)
        out.write(0x00) // Use Global Color Table, non-interlaced

        // LZW Image Data
        encodeLzwStream(indexedPixels, width * height)
    }

    fun finish() {
        if (!headerWritten) return
        out.write(0x3B) // GIF Trailer
        out.flush()
    }

    private fun quantizeFramePixels(w: Int, h: Int) {
        var offset = 0
        for (y in 0 until h) {
            val rowBayerOffset = (y and 3) shl 2
            for (x in 0 until w) {
                val argb = pixelBuffer[offset]
                val dither = bayer4x4[rowBayerOffset + (x and 3)]

                val r = min(255, max(0, ((argb shr 16) and 0xFF) + dither))
                val g = min(255, max(0, ((argb shr 8) and 0xFF) + dither))
                val b = min(255, max(0, (argb and 0xFF) + dither))

                val rIdx = (r * 5 + 127) / 255
                val gIdx = (g * 6 + 127) / 255
                val bIdx = (b * 5 + 127) / 255

                val paletteIndex = (rIdx * 42) + (gIdx * 6) + bIdx
                indexedPixels[offset] = paletteIndex.toByte()
                offset++
            }
        }
    }

    /**
     * Standard GIF LZW variable-width compressor (minCodeSize = 8).
     */
    private fun encodeLzwStream(pixels: ByteArray, length: Int) {
        val minCodeSize = 8
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val endOfInfoCode = clearCode + 1 // 257

        // Hash table of size 5003 (prime) for (prefix, suffix) -> code
        val tableSize = 5003
        val hashKeys = IntArray(tableSize) { -1 }
        val hashValues = IntArray(tableSize)

        var codeSize = minCodeSize + 1 // 9
        var maxCode = (1 shl codeSize) - 1
        var nextCode = endOfInfoCode + 1 // 258

        val subBlock = ByteArray(255)
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

        // Emit initial Clear Code
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
                    // Dictionary full: emit Clear Code and reset table
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
        out.write(0) // Block terminator
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
