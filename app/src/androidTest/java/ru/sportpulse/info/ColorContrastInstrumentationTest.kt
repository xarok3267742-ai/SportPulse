package ru.sportpulse.info

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorContrastInstrumentationTest {
    @Test
    fun semanticTextPairsMeetNormalTextContrast() {
        val pairs = listOf(
            ContrastPair("ink / background", AppColors.ink, AppColors.background),
            ContrastPair("ink / surface", AppColors.ink, AppColors.surface),
            ContrastPair("muted / background", AppColors.muted, AppColors.background),
            ContrastPair("muted / surface", AppColors.muted, AppColors.surface),
            ContrastPair("accent / surface", AppColors.accent, AppColors.surface),
            ContrastPair("accent / soft", AppColors.accent, AppColors.accentSoft),
            ContrastPair("accent dark / soft", AppColors.accentDark, AppColors.accentSoft),
            ContrastPair("signal / surface", AppColors.signal, AppColors.surface),
            ContrastPair("signal / soft", AppColors.signal, AppColors.signalSoft),
            ContrastPair("danger / surface", AppColors.danger, AppColors.surface),
            ContrastPair("danger / soft", AppColors.danger, AppColors.dangerSoft),
            ContrastPair("warning / surface", AppColors.warning, AppColors.surface),
            ContrastPair("warning / soft", AppColors.warning, AppColors.warningSoft),
            ContrastPair("field muted / field", AppColors.fieldMuted, AppColors.field),
            ContrastPair("field signal / field", AppColors.fieldSignal, AppColors.field),
            ContrastPair("white / accent", Color.WHITE, AppColors.accent),
            ContrastPair("white / signal", Color.WHITE, AppColors.signal),
            ContrastPair("white / danger", Color.WHITE, AppColors.danger),
            ContrastPair("white / warning", Color.WHITE, AppColors.warning)
        )

        val failures = pairs.mapNotNull { pair ->
            val ratio = contrastRatio(pair.foreground, pair.background)
            if (ratio + TOLERANCE < MINIMUM_TEXT_CONTRAST) {
                "${pair.name}: ${"%.2f".format(ratio)}:1"
            } else {
                null
            }
        }
        assertTrue(
            "Contrast failures:\n${failures.joinToString("\n")}",
            failures.isEmpty()
        )
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private data class ContrastPair(
        val name: String,
        val foreground: Int,
        val background: Int
    )

    private companion object {
        const val MINIMUM_TEXT_CONTRAST = 4.5
        const val TOLERANCE = 0.001
    }
}
