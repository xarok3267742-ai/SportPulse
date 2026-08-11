package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

internal class EvidenceRailView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val summaries = mutableListOf<PlainAnalyticsFactorSummary>()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun submit(items: List<PlainAnalyticsFactorSummary>) {
        require(items.map { it.factor } == SignalFactor.values().toList())
        summaries.clear()
        summaries.addAll(items)
        contentDescription = items.joinToString(
            prefix = "Схема доказательств. ",
            separator = ". "
        ) { item ->
            val level = when (item.effectiveLevel) {
                EvidenceLevel.UNCONFIRMED -> "нет источника"
                EvidenceLevel.SINGLE_SOURCE -> "один источник"
                EvidenceLevel.QUORUM -> "два источника"
            }
            val action = if (item.isNextAction) {
                ", следующий шаг"
            } else {
                ""
            }
            "${item.factor.title}: $level$action"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (summaries.isEmpty()) return

        val density = resources.displayMetrics.density
        val left = 24f * density
        val right = width - 24f * density
        val railY = 42f * density
        val cellWidth = (right - left) / (summaries.size - 1)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = AppColors.fieldLine
        canvas.drawLine(left, railY, right, railY, paint)

        summaries.forEachIndexed { index, summary ->
            val x = left + cellWidth * index
            val color = nodeColor(summary)
            val radius = 14f * density

            if (summary.isNextAction) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = AppColors.fieldSignal
                canvas.drawCircle(x, railY, radius + 7f * density, paint)
                paint.style = Paint.Style.FILL
                val marker = Path().apply {
                    moveTo(x, 8f * density)
                    lineTo(x - 6f * density, 18f * density)
                    lineTo(x + 6f * density, 18f * density)
                    close()
                }
                canvas.drawPath(marker, paint)
            }

            paint.style = Paint.Style.FILL
            paint.color = AppColors.field
            canvas.drawCircle(x, railY, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * density
            paint.color = color
            canvas.drawCircle(x, railY, radius, paint)

            paint.style = Paint.Style.FILL
            paint.color = color
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = AppTypography.display(context, bold = true)
            paint.textSize = 12f * density
            val count = when (summary.effectiveLevel) {
                EvidenceLevel.UNCONFIRMED -> "0"
                EvidenceLevel.SINGLE_SOURCE -> "1"
                EvidenceLevel.QUORUM -> "2"
            }
            canvas.drawText(
                count,
                x,
                railY - (paint.ascent() + paint.descent()) / 2f,
                paint
            )

            paint.color = if (summary.isNextAction) {
                AppColors.fieldSignal
            } else {
                AppColors.fieldMuted
            }
            paint.textSize = 9f * density
            val label = when (summary.factor) {
                SignalFactor.FORM -> "ФОРМА"
                SignalFactor.LINEUP -> "СОСТАВ"
                SignalFactor.LOAD -> "НАГР."
                SignalFactor.CONTEXT -> "КОНТ."
                SignalFactor.SOURCES -> "ИСТ."
            }
            canvas.drawText(label, x, 78f * density, paint)
        }
    }

    private fun nodeColor(summary: PlainAnalyticsFactorSummary): Int {
        return when {
            summary.freshnessStatus == FreshnessStatus.EXPIRED ||
                summary.effectiveLevel == EvidenceLevel.UNCONFIRMED ->
                AppColors.danger
            summary.freshnessStatus == FreshnessStatus.EXPIRING ||
                summary.freshnessStatus == FreshnessStatus.DEGRADED ||
                summary.effectiveLevel == EvidenceLevel.SINGLE_SOURCE ->
                Color.rgb(241, 172, 65)
            else -> Color.rgb(83, 207, 170)
        }
    }
}
