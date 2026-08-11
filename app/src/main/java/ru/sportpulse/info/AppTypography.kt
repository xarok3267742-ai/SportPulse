package ru.sportpulse.info

import android.content.Context
import android.graphics.Typeface

internal object AppTypography {
    @Volatile
    private var bodyTypeface: Typeface? = null

    @Volatile
    private var displaySemiboldTypeface: Typeface? = null

    @Volatile
    private var displayBoldTypeface: Typeface? = null

    fun body(context: Context): Typeface {
        return bodyTypeface ?: synchronized(this) {
            bodyTypeface ?: context.resources
                .getFont(R.font.golos_text)
                .also { bodyTypeface = it }
        }
    }

    fun display(
        context: Context,
        bold: Boolean = false
    ): Typeface {
        return if (bold) {
            displayBoldTypeface ?: synchronized(this) {
                displayBoldTypeface ?: context.resources
                    .getFont(R.font.fira_sans_condensed_bold)
                    .also { displayBoldTypeface = it }
            }
        } else {
            displaySemiboldTypeface ?: synchronized(this) {
                displaySemiboldTypeface ?: context.resources
                    .getFont(R.font.fira_sans_condensed_semibold)
                    .also { displaySemiboldTypeface = it }
            }
        }
    }

    fun forText(context: Context, style: Int): Typeface {
        return if (style and Typeface.BOLD != 0) {
            display(context, bold = true)
        } else {
            body(context)
        }
    }
}
