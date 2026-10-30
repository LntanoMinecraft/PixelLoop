package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.engine.AnimatedGifEncoder
import com.example.service.LiveUpdateNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and verify Android 16 Live Update notification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("PixelLoop", appName)

        val notification = LiveUpdateNotificationHelper.buildRecordingLiveNotification(
            context = context,
            elapsedSeconds = 12
        )
        assertEquals("REC 00:12", NotificationCompat.getShortCriticalText(notification))
        assertTrue(notification.extras.getBoolean(NotificationCompat.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `verify AnimatedGifEncoder outputs valid GIF89a binary stream`() {
        val baos = ByteArrayOutputStream()
        val encoder = AnimatedGifEncoder(baos)
        encoder.start(64, 64, loopCount = 0)

        val frame1 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(255, 179, 0))
        }
        val frame2 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(38, 198, 218))
        }

        encoder.addFrame(frame1, 80)
        encoder.addFrame(frame2, 80)
        encoder.finish()

        val bytes = baos.toByteArray()
        assertTrue(bytes.size > 800)
        val header = String(bytes, 0, 6, Charsets.US_ASCII)
        assertEquals("GIF89a", header)
        assertEquals(0x3B.toByte(), bytes.last())
    }
}
