package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.min

internal class PreflightProtocolView @JvmOverloads constructor(
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
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val factorNames = Array(5) { "" }
    private val statusLabels = Array(5) { "" }
    private val states = Array(5) { PreflightFactorState.HOLDS }
    private val scheduleRatios = FloatArray(5) { -1f }
    private var hasResult = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(protocol: PreflightProtocol) {
        val duration = (
            protocol.start.startAt - protocol.evaluatedAt
            ).coerceAtLeast(1L)
        protocol.checks.forEachIndexed { index, check ->
            factorNames[index] = check.factor.shortTitle.uppercase(
                Locale.getDefault()
            )
            states[index] = check.state
            statusLabels[index] = statusLabel(
                check = check,
                evaluatedAt = protocol.evaluatedAt
            )
            scheduleRatios[index] = check.scheduledAt?.let { at ->
                ((at - protocol.evaluatedAt).toFloat() /
                    duration.toFloat()).coerceIn(0f, 1f)
            } ?: -1f
        }
        contentDescription = buildString {
            append("Предстартовый протокол. ")
            protocol.checks.forEachIndexed { index, check ->
                if (index > 0) append(". ")
                append(check.factor.title)
                append(": ")
                append(
                    when (check.state) {
                        PreflightFactorState.HOLDS ->
                            "повторная проверка не нужна"
                        PreflightFactorState.CHECK_NOW ->
                            "проверить сейчас"
                        PreflightFactorState.SCHEDULED ->
                            "проверка запланирована через " +
                                offsetLabel(
                                    checkNotNull(check.scheduledAt) -
                                        protocol.evaluatedAt,
                                    compact = false
                                )
                        PreflightFactorState.MISSING ->
                            "нет подтверждения, проверить сейчас"
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
        val statusLeft = width - dp(74f)
        val railRight = statusLeft - dp(12f)

        labelPaint.color = AppColors.muted
        labelPaint.textSize = cappedSp(8.5f)
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("СЕЙЧАС", railLeft, dp(19f), labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("СТАРТ", railRight, dp(19f), labelPaint)

        for (index in factorNames.indices) {
            val centerY = dp(48f + index * 33f)
            val state = states[index]
            val stateColor = stateColor(state)

            labelPaint.color = if (
                state == PreflightFactorState.HOLDS
            ) {
                AppColors.ink
            } else {
                stateColor
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
            if (state == PreflightFactorState.HOLDS) {
                linePaint.strokeWidth = dp(3f)
                linePaint.color = AppColors.accent
                canvas.drawLine(
                    railLeft,
                    centerY,
                    railRight,
                    centerY,
                    linePaint
                )
            }

            linePaint.strokeWidth = dp(2f)
            linePaint.color = if (
                state == PreflightFactorState.HOLDS
            ) {
                AppColors.accent
            } else {
                AppColors.muted
            }
            canvas.drawCircle(
                railRight,
                centerY,
                dp(6f),
                linePaint
            )

            val ratio = scheduleRatios[index]
            if (ratio >= 0f) {
                val markerX = railLeft +
                    (railRight - railLeft) * ratio
                fillPaint.color = stateColor
                canvas.drawCircle(
                    markerX,
                    centerY,
                    dp(6f),
                    fillPaint
                )
                linePaint.strokeWidth = dp(2f)
                linePaint.color = AppColors.surface
                canvas.drawCircle(
                    markerX,
                    centerY,
                    dp(3f),
                    linePaint
                )
            } else {
                fillPaint.color = AppColors.accent
                canvas.drawCircle(
                    railRight,
                    centerY,
                    dp(5f),
                    fillPaint
                )
            }

            statusPaint.color = stateColor
            statusPaint.textAlign = Paint.Align.RIGHT
            statusPaint.textSize = fittedTextSize(
                statusLabels[index],
                width - statusLeft - dp(3f),
                8.5f,
                statusPaint
            )
            canvas.drawText(
                statusLabels[index],
                width - dp(3f),
                textBaseline(statusPaint, centerY),
                statusPaint
            )
        }
    }

    private fun statusLabel(
        check: PreflightFactorCheck,
        evaluatedAt: Long
    ): String {
        return when (check.state) {
            PreflightFactorState.HOLDS -> "ДЕРЖИТ"
            PreflightFactorState.CHECK_NOW -> "СЕЙЧАС"
            PreflightFactorState.SCHEDULED -> "+" +
                offsetLabel(
                    checkNotNull(check.scheduledAt) - evaluatedAt,
                    compact = true
                )
            PreflightFactorState.MISSING -> "НЕТ"
        }
    }

    private fun offsetLabel(
        durationMillis: Long,
        compact: Boolean
    ): String {
        val minutes = (durationMillis / MINUTE_MILLIS)
            .coerceAtLeast(0L)
        return when {
            minutes < 60L -> if (compact) {
                "$minutes МИН"
            } else {
                "$minutes минут"
            }
            minutes < 24L * 60L -> {
                val hours = minutes / 60L
                val remainder = minutes % 60L
                if (compact || remainder == 0L) {
                    "$hours Ч"
                } else {
                    "$hours ч $remainder мин"
                }
            }
            else -> {
                val days = minutes / (24L * 60L)
                val hours = minutes % (24L * 60L) / 60L
                if (compact || hours == 0L) {
                    "$days Д"
                } else {
                    "$days д $hours ч"
                }
            }
        }
    }

    private fun stateColor(state: PreflightFactorState): Int {
        return when (state) {
            PreflightFactorState.HOLDS -> AppColors.accent
            PreflightFactorState.SCHEDULED -> AppColors.warning
            PreflightFactorState.CHECK_NOW,
            PreflightFactorState.MISSING -> AppColors.danger
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
