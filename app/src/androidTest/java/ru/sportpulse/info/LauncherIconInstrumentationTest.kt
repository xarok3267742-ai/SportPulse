package ru.sportpulse.info

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconInstrumentationTest {
    @Test
    fun launcherIconUsesBrandedAdaptiveLayers() {
        val context = InstrumentationRegistry.getInstrumentation()
            .targetContext
        val icon = context.packageManager.getApplicationIcon(
            context.packageName
        )
        assertTrue(icon is AdaptiveIconDrawable)

        val adaptive = icon as AdaptiveIconDrawable
        val foreground = adaptive.foreground as? BitmapDrawable
        assertNotNull(foreground)
        val bitmap = requireNotNull(foreground).bitmap
        assertEquals(1_254, bitmap.width)
        assertEquals(1_254, bitmap.height)
        assertTrue(bitmap.hasAlpha())
        assertTrue(bitmap.getPixel(0, 0) ushr 24 == 0)

        val background = adaptive.background as? android.graphics.drawable.ColorDrawable
        assertNotNull(background)
        assertEquals(0xFF071C1E.toInt(), requireNotNull(background).color)
    }
}
