package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

internal class SignalStressTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = AppColors.line
        pathEffect = DashPathEffect(
            floatArrayOf(dp(4f), dp(4f)),
            0f
        )
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.ink
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        typeface = AppTypography.display(context, bold = true)
        color = AppColors.muted
    }
    private val chartPath = Path()
    private val areaPath = Path()

    private var result: SignalStressResult? = null
    private var now: Long = 0L

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: SignalStressResult, evaluatedAt: Long) {
        result = value
        now = evaluatedAt
        contentDescription = accessibilityDescription(value, evaluatedAt)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(148f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = result ?: return
        val left = dp(37f)
        val right = width - dp(13f)
        val top = dp(14f)
        val bottom = height - dp(34f)
        val baseline = value.baselineResult.effectiveSignal.readiness
        val points = value.timeline
        val horizon = points.lastOrNull()
            ?.at
            ?.minus(now)
            ?.coerceAtLeast(1L)
            ?: 1L
        val tone = tone(value.status)

        drawThreshold(canvas, SignalThresholds.READY, left, right, top, bottom)
        drawThreshold(canvas, SignalThresholds.OBSERVE, left, right, top, bottom)

        chartPath.reset()
        areaPath.reset()
        val startY = yFor(baseline, top, bottom)
        chartPath.moveTo(left, startY)
        areaPath.moveTo(left, bottom)
        areaPath.lineTo(left, startY)
        points.forEach { point ->
            val x = xFor(point.at, horizon, left, right)
            val y = yFor(
                point.result.effectiveSignal.readiness,
                top,
                bottom
            )
            chartPath.lineTo(x, y)
            areaPath.lineTo(x, y)
        }
        if (points.isEmpty()) {
            chartPath.lineTo(right, startY)
            areaPath.lineTo(right, startY)
        }
        areaPath.lineTo(right, bottom)
        areaPath.close()
        fillPaint.color = Color.argb(28, tone.red, tone.green, tone.blue)
        canvas.drawPath(areaPath, fillPaint)
        linePaint.color = tone.color
        canvas.drawPath(chartPath, linePaint)

        canvas.drawCircle(left, startY, dp(5f), currentPaint)
        points.forEach { point ->
            val x = xFor(point.at, horizon, left, right)
            val y = yFor(
                point.result.effectiveSignal.readiness,
                top,
                bottom
            )
            pointPaint.color = if (
                point == value.firstVerdictChange
            ) {
                AppColors.danger
            } else {
                tone.color
            }
            canvas.drawCircle(x, y, dp(4.5f), pointPaint)
        }

        drawLabel(canvas, "сейчас", left, height - dp(10f), left, right)
        val markedPoint = value.firstVerdictChange ?: points.lastOrNull()
        markedPoint?.let { point ->
            val prefix = if (point == value.firstVerdictChange) {
                "смена"
            } else {
                "горизонт"
            }
            drawLabel(
                canvas,
                "$prefix ${FreshnessFormatter.duration(point.at - now)}",
                right,
                height - dp(10f),
                left,
                right
            )
        }
    }

    private fun drawThreshold(
        canvas: Canvas,
        threshold: Int,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float
    ) {
        val y = yFor(threshold, top, bottom)
        canvas.drawLine(left, y, right, y, thresholdPaint)
        labelPaint.color = AppColors.muted
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            threshold.toString(),
            left - dp(5f),
            y - (labelPaint.ascent() + labelPaint.descent()) / 2f,
            labelPaint
        )
    }

    private fun xFor(
        timestamp: Long,
        horizon: Long,
        left: Float,
        right: Float
    ): Float {
        val elapsed = (timestamp - now).coerceIn(0L, horizon)
        return left + (right - left) * elapsed.toFloat() / horizon.toFloat()
    }

    private fun yFor(
        readiness: Int,
        top: Float,
        bottom: Float
    ): Float {
        return bottom - (bottom - top) * readiness.coerceIn(0, 100) / 100f
    }

    private fun drawLabel(
        canvas: Canvas,
        value: String,
        centerX: Float,
        baseline: Float,
        left: Float,
        right: Float
    ) {
        labelPaint.textAlign = Paint.Align.LEFT
        val textWidth = labelPaint.measureText(value)
        val x = (centerX - textWidth / 2f)
            .coerceIn(left, right - textWidth)
        canvas.drawText(value, x, baseline, labelPaint)
    }

    private fun accessibilityDescription(
        value: SignalStressResult,
        evaluatedAt: Long
    ): String {
        val baseline = value.baselineResult.effectiveSignal.readiness
        val stress = value.criticalShock
            ?.result
            ?.effectiveSignal
            ?.readiness
        val deadline = value.firstVerdictChange?.let {
            FreshnessFormatter.duration(it.at - evaluatedAt)
        }
        return buildString {
            append("Стресс-тест сигнала. Сейчас ")
            append(baseline)
            append(" из 100")
            stress?.let {
                append(". После потери одного подтверждения ")
                append(it)
            }
            deadline?.let {
                append(". Статус изменится через ")
                append(it)
            }
        }
    }

    private fun tone(status: SignalStressStatus): ChartTone {
        val color = when (status) {
            SignalStressStatus.ROBUST -> AppColors.accent
            SignalStressStatus.FRAGILE -> AppColors.danger
            SignalStressStatus.NO_BUFFER -> AppColors.warning
        }
        return ChartTone(
            color = color,
            red = Color.red(color),
            green = Color.green(color),
            blue = Color.blue(color)
        )
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

    private data class ChartTone(
        val color: Int,
        val red: Int,
        val green: Int,
        val blue: Int
    )
}
