package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.min

internal class EvidenceRelayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = AppTypography.display(context, bold = true)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val markerRect = RectF()
    private val factorNames = Array(5) { "" }
    private val levelLabels = Array(5) { "" }
    private val states = Array(5) {
        EvidenceRelayFactorState.UNCONFIRMED
    }
    private val transitionRatios = FloatArray(5) { -1f }
    private var hasResult = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: EvidenceRelayResult) {
        val duration = (
            result.start.startAt -
                result.evaluatedAtMinute * MINUTE_MILLIS
            ).coerceAtLeast(1L)
        result.factors.forEachIndexed { index, factor ->
            factorNames[index] = factor.factor.shortTitle.uppercase(
                Locale.getDefault()
            )
            levelLabels[index] =
                "${levelShort(factor.currentLevel)}→" +
                levelShort(factor.startLevel)
            states[index] = factor.state
            transitionRatios[index] = factor.firstTransitionAt
                ?.let { transition ->
                    (
                        (transition -
                            result.evaluatedAtMinute *
                            MINUTE_MILLIS).toFloat() /
                            duration.toFloat()
                        ).coerceIn(0f, 1f)
                } ?: -1f
        }
        contentDescription = buildString {
            append("Эстафета фактов к старту. ")
            result.factors.forEachIndexed { index, factor ->
                if (index > 0) append(". ")
                append(factor.factor.title)
                append(": сейчас ")
                append(factor.currentLevel.title)
                append(", к старту ")
                append(factor.startLevel.title)
                append(", ")
                append(
                    when (factor.state) {
                        EvidenceRelayFactorState.SURVIVES ->
                            "доживает"
                        EvidenceRelayFactorState.RECHECK_REQUIRED ->
                            "нужна повторная проверка"
                        EvidenceRelayFactorState.UNCONFIRMED ->
                            "нет подтверждения"
                    }
                )
            }
        }
        hasResult = true
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(214f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasResult) return

        val left = dp(10f)
        val labelRight = dp(78f)
        val railLeft = labelRight + dp(11f)
        val valuesLeft = width - dp(49f)
        val railRight = valuesLeft - dp(12f)

        labelPaint.color = AppColors.muted
        labelPaint.textSize = cappedSp(8.5f)
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("СЕЙЧАС", railLeft, dp(19f), labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("СТАРТ", railRight, dp(19f), labelPaint)

        factorNames.indices.forEach { index ->
            val centerY = dp(48f + index * 33f)
            val state = states[index]
            val stateColor = stateColor(state)

            labelPaint.color = if (
                state == EvidenceRelayFactorState.UNCONFIRMED
            ) {
                AppColors.muted
            } else {
                AppColors.ink
            }
            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.textSize = fittedTextSize(
                factorNames[index],
                labelRight - left,
                9f,
                labelPaint
            )
            canvas.drawText(
                factorNames[index],
                left,
                textBaseline(labelPaint, centerY),
                labelPaint
            )

            linePaint.strokeWidth = dp(5f)
            linePaint.color = AppColors.line
            canvas.drawLine(
                railLeft,
                centerY,
                railRight,
                centerY,
                linePaint
            )
            linePaint.strokeWidth = dp(3f)
            linePaint.color = stateColor
            val ratio = transitionRatios[index]
            val activeRight = when {
                state == EvidenceRelayFactorState.UNCONFIRMED ->
                    railLeft
                ratio >= 0f ->
                    railLeft + (railRight - railLeft) * ratio
                else -> railRight
            }
            canvas.drawLine(
                railLeft,
                centerY,
                activeRight,
                centerY,
                linePaint
            )
            if (ratio >= 0f) {
                markerRect.set(
                    activeRight - dp(2f),
                    centerY - dp(9f),
                    activeRight + dp(2f),
                    centerY + dp(9f)
                )
                fillPaint.color = AppColors.warning
                canvas.drawRoundRect(
                    markerRect,
                    dp(2f),
                    dp(2f),
                    fillPaint
                )
            }

            fillPaint.color = AppColors.surface
            canvas.drawCircle(
                railLeft,
                centerY,
                dp(6f),
                fillPaint
            )
            linePaint.strokeWidth = dp(2.5f)
            linePaint.color = AppColors.signal
            canvas.drawCircle(
                railLeft,
                centerY,
                dp(5f),
                linePaint
            )
            fillPaint.color = stateColor
            canvas.drawCircle(
                railRight,
                centerY,
                dp(6f),
                fillPaint
            )

            valuePaint.color = stateColor
            valuePaint.textAlign = Paint.Align.RIGHT
            valuePaint.textSize = fittedTextSize(
                levelLabels[index],
                width - valuesLeft - dp(2f),
                9f,
                valuePaint
            )
            canvas.drawText(
                levelLabels[index],
                width - dp(3f),
                textBaseline(valuePaint, centerY),
                valuePaint
            )
        }
    }

    private fun stateColor(
        state: EvidenceRelayFactorState
    ): Int {
        return when (state) {
            EvidenceRelayFactorState.SURVIVES -> AppColors.accent
            EvidenceRelayFactorState.RECHECK_REQUIRED ->
                AppColors.warning
            EvidenceRelayFactorState.UNCONFIRMED -> AppColors.danger
        }
    }

    private fun levelShort(level: EvidenceLevel): String {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> "0"
            EvidenceLevel.SINGLE_SOURCE -> "1"
            EvidenceLevel.QUORUM -> "2+"
        }
    }

    private fun fittedTextSize(
        value: String,
        maxWidth: Float,
        maxSp: Float,
        paint: Paint
    ): Float {
        val preferred = cappedSp(maxSp)
        paint.textSize = preferred
        val measured = paint.measureText(value)
        return if (measured > maxWidth && measured > 0f) {
            preferred * maxWidth / measured
        } else {
            preferred
        }
    }

    private fun textBaseline(paint: Paint, centerY: Float): Float {
        return centerY - (paint.ascent() + paint.descent()) / 2f
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun cappedSp(value: Float): Float {
        val metrics = resources.displayMetrics
        return value * min(
            metrics.density * resources.configuration.fontScale,
            metrics.density * 1.25f
        )
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}
