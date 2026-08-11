package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class FactExpressView @JvmOverloads constructor(
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private val moduleBounds = RectF()
    private val positions = FloatArray(FactExpressPolicy.MAX_EVENTS)
    private var result: FactExpressResult? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: FactExpressResult) {
        result = value
        contentDescription = buildString {
            append("Маршрут фактов. Сохранено событий ")
            append(value.entries.size)
            append(". ")
            append(
                when (value.state) {
                    FactExpressState.EMPTY ->
                        "Нужно выбрать от двух до четырех событий."
                    FactExpressState.NEED_MORE ->
                        "Нужно добавить еще одно событие."
                    FactExpressState.READY ->
                        "Маршрут готов. Действий сейчас ${value.actionNowCount}."
                    FactExpressState.TOO_MANY ->
                        "Событий больше четырех, автоматический отбор отключен."
                }
            )
        }
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(116f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = result ?: return
        val railLeft = dp(35f)
        val gateCenter = width - dp(37f)
        val railRight = gateCenter - dp(44f)
        val railY = dp(48f)
        val span = railRight - railLeft
        positions.indices.forEach { index ->
            positions[index] = railLeft + span * index /
                (FactExpressPolicy.MAX_EVENTS - 1)
        }

        linePaint.strokeWidth = dp(8f)
        linePaint.color = AppColors.line
        canvas.drawLine(
            railLeft,
            railY,
            gateCenter,
            railY,
            linePaint
        )
        val connectedCount = min(
            value.entries.size,
            FactExpressPolicy.MAX_EVENTS
        )
        if (connectedCount > 0) {
            val connectedRight = if (
                value.state == FactExpressState.READY
            ) {
                gateCenter
            } else {
                positions[connectedCount - 1]
            }
            linePaint.strokeWidth = dp(4f)
            linePaint.color = if (value.isReady) {
                AppColors.accent
            } else {
                AppColors.signal
            }
            canvas.drawLine(
                railLeft,
                railY,
                connectedRight,
                railY,
                linePaint
            )
        }

        positions.forEachIndexed { index, x ->
            val entry = value.entries.getOrNull(index)
            val color = entry?.let(::entryColor) ?: AppColors.muted
            moduleBounds.set(
                x - dp(21f),
                railY - dp(16f),
                x + dp(21f),
                railY + dp(16f)
            )
            fillPaint.color = AppColors.surface
            canvas.drawRoundRect(
                moduleBounds,
                dp(8f),
                dp(8f),
                fillPaint
            )
            linePaint.strokeWidth = dp(3f)
            linePaint.color = color
            canvas.drawRoundRect(
                moduleBounds,
                dp(8f),
                dp(8f),
                linePaint
            )
            if (entry != null) {
                fillPaint.color = color
                canvas.drawCircle(x, railY, dp(6f), fillPaint)
                if (entry.nextMoment != null) {
                    fillPaint.color = AppColors.warning
                    canvas.drawCircle(
                        x + dp(15f),
                        railY - dp(15f),
                        dp(4f),
                        fillPaint
                    )
                }
            }
            drawLabel(
                canvas,
                (index + 1).toString(),
                x,
                dp(82f),
                color
            )
        }

        val gateColor = when (value.state) {
            FactExpressState.READY -> AppColors.accentDark
            FactExpressState.TOO_MANY -> AppColors.danger
            FactExpressState.EMPTY,
            FactExpressState.NEED_MORE -> AppColors.muted
        }
        fillPaint.color = AppColors.surface
        canvas.drawCircle(gateCenter, railY, dp(20f), fillPaint)
        linePaint.strokeWidth = dp(4f)
        linePaint.color = gateColor
        canvas.drawCircle(gateCenter, railY, dp(18f), linePaint)
        if (value.isReady) {
            fillPaint.color = gateColor
            canvas.drawCircle(gateCenter, railY, dp(8f), fillPaint)
        }
        drawLabel(
            canvas,
            "ФАКТ",
            gateCenter,
            dp(82f),
            gateColor
        )
    }

    private fun entryColor(entry: FactExpressEntry): Int {
        return when (entry.state) {
            FactExpressEntryState.ACTION_NOW -> AppColors.signal
            FactExpressEntryState.WAITING -> AppColors.warning
            FactExpressEntryState.UNSCHEDULED -> AppColors.danger
            FactExpressEntryState.COMPLETE -> AppColors.accentDark
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        color: Int
    ) {
        textPaint.color = color
        textPaint.textSize = cappedSp(10f)
        canvas.drawText(value, x, baseline, textPaint)
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
}
