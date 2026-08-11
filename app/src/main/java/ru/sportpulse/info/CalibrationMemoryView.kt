package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

internal class CalibrationMemoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = AppColors.signal
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9f)
        typeface = AppTypography.display(context, bold = true)
        color = AppColors.ink
        textAlign = Paint.Align.CENTER
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9f)
        typeface = AppTypography.display(context, bold = true)
        color = AppColors.muted
        textAlign = Paint.Align.CENTER
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9.5f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(9f)
        typeface = AppTypography.display(context, bold = true)
        color = AppColors.muted
        textAlign = Paint.Align.CENTER
    }
    private val cellRect = RectF()

    private var memory: CalibrationMemory? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setMemory(value: CalibrationMemory) {
        memory = value
        contentDescription = accessibilityDescription(value)
        requestLayout()
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val rowCount = visibleResults().size.coerceAtLeast(1)
        val desiredHeight = (
            HEADER_HEIGHT_DP +
                rowCount * ROW_HEIGHT_DP +
                BOTTOM_PADDING_DP
            )
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(desiredHeight).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = memory ?: return
        val results = visibleResults()
        val left = dp(31f)
        val right = width - dp(2f)
        val gap = dp(5f)
        val columnWidth = (
            right - left -
                gap * (SignalFactor.values().size - 1)
            ) / SignalFactor.values().size
        val headers = listOf(
            "Форма",
            "Состав",
            "Нагруз.",
            "Конт.",
            "Источ."
        )

        SignalFactor.values().forEachIndexed { index, _ ->
            val center = left +
                index * (columnWidth + gap) +
                columnWidth / 2f
            canvas.drawText(
                headers[index],
                center,
                dp(12f) - headerPaint.ascent() / 2f,
                headerPaint
            )
            val score = value.factorProfiles[index]
                .score
                ?.toString()
                ?: "—"
            canvas.drawText(
                "$score · ${value.factorProfiles[index].verifiedCount}",
                center,
                dp(33f),
                scorePaint
            )
        }

        if (results.isEmpty()) {
            drawEmptyRow(
                canvas,
                left,
                right,
                dp(HEADER_HEIGHT_DP)
            )
            return
        }
        results.forEachIndexed { rowIndex, result ->
            val top = dp(HEADER_HEIGHT_DP) +
                rowIndex * dp(ROW_HEIGHT_DP)
            val rowNumber = value.reviewCount - rowIndex
            canvas.drawText(
                "#$rowNumber",
                dp(14f),
                top + dp(24f),
                rowPaint
            )
            result.factorResults.forEachIndexed {
                    columnIndex,
                    factorResult ->
                val cellLeft = left +
                    columnIndex * (columnWidth + gap)
                cellRect.set(
                    cellLeft,
                    top + dp(3f),
                    cellLeft + columnWidth,
                    top + dp(35f)
                )
                val tone = tone(factorResult.outcome)
                fillPaint.color = tone.background
                canvas.drawRoundRect(
                    cellRect,
                    dp(6f),
                    dp(6f),
                    fillPaint
                )
                if (
                    value.focusProfile?.factor ==
                    factorResult.factor
                ) {
                    borderPaint.color = if (
                        value.status ==
                        CalibrationMemoryStatus.BLIND_SPOT
                    ) {
                        AppColors.danger
                    } else {
                        AppColors.signal
                    }
                    canvas.drawRoundRect(
                        cellRect,
                        dp(6f),
                        dp(6f),
                        borderPaint
                    )
                }
                cellPaint.color = tone.foreground
                canvas.drawText(
                    cellTitle(factorResult.outcome),
                    cellRect.centerX(),
                    cellRect.centerY() -
                        (
                            cellPaint.ascent() +
                                cellPaint.descent()
                            ) / 2f,
                    cellPaint
                )
            }
        }
    }

    private fun drawEmptyRow(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float
    ) {
        cellRect.set(
            left,
            top + dp(3f),
            right,
            top + dp(35f)
        )
        fillPaint.color = AppColors.background
        canvas.drawRoundRect(
            cellRect,
            dp(6f),
            dp(6f),
            fillPaint
        )
        cellPaint.color = AppColors.muted
        canvas.drawText(
            "Нет завершенных разборов",
            cellRect.centerX(),
            cellRect.centerY() -
                (cellPaint.ascent() + cellPaint.descent()) / 2f,
            cellPaint
        )
    }

    private fun visibleResults(): List<PostEventReviewResult> {
        return memory
            ?.reviewResults
            ?.takeLast(MAX_VISIBLE_REVIEWS)
            ?.asReversed()
            .orEmpty()
    }

    private fun tone(outcome: PostEventOutcome): Tone {
        return when (outcome) {
            PostEventOutcome.CONFIRMED ->
                Tone(AppColors.accentSoft, AppColors.accentDark)
            PostEventOutcome.PARTIAL ->
                Tone(AppColors.warningSoft, AppColors.warning)
            PostEventOutcome.DISPROVED ->
                Tone(AppColors.dangerSoft, AppColors.danger)
            PostEventOutcome.UNKNOWN,
            PostEventOutcome.UNREVIEWED ->
                Tone(AppColors.background, AppColors.muted)
        }
    }

    private fun cellTitle(
        outcome: PostEventOutcome
    ): String {
        return when (outcome) {
            PostEventOutcome.CONFIRMED -> "ДА"
            PostEventOutcome.PARTIAL -> "1/2"
            PostEventOutcome.DISPROVED -> "НЕТ"
            PostEventOutcome.UNKNOWN -> "?"
            PostEventOutcome.UNREVIEWED -> "—"
        }
    }

    private fun accessibilityDescription(
        value: CalibrationMemory
    ): String {
        return buildString {
            append("Карта слепых зон. Завершено разборов: ")
            append(value.reviewCount)
            append(". Проверяемых факторов: ")
            append(value.verifiedFactorCount)
            append(". ")
            value.factorProfiles.forEach { profile ->
                append(profile.factor.title)
                append(": ")
                append(profile.score ?: "нет данных")
                append(" из 100, наблюдений ")
                append(profile.verifiedCount)
                append(". ")
            }
            value.focusProfile?.let {
                append("Фокус: ")
                append(it.factor.title)
                append(".")
            }
        }
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            resources.displayMetrics
        )
    }

    private data class Tone(
        val background: Int,
        val foreground: Int
    )

    private companion object {
        const val MAX_VISIBLE_REVIEWS = 6
        const val HEADER_HEIGHT_DP = 43f
        const val ROW_HEIGHT_DP = 40f
        const val BOTTOM_PADDING_DP = 2f
    }
}
