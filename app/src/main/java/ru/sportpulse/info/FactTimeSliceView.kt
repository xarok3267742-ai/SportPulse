package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class FactTimeSliceView @JvmOverloads constructor(
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
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private var result: FactTimeSlice? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: FactTimeSlice) {
        result = value
        contentDescription = accessibleDescription(value)
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(132f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = result ?: return
        val left = dp(24f)
        val right = width - dp(24f)
        val railY = height / 2f

        drawRail(canvas, left, right, railY)
        if (value.points.size < 2) {
            drawPoints(canvas, value, left, right, railY)
            return
        }

        val earliest = value.points.minOf { it.checkedAt }
        val latest = value.points.maxOf { it.checkedAt }
        val syncWindow = requireNotNull(value.syncWindowMillis)
        val thresholdAt = (latest - syncWindow).coerceAtLeast(earliest)
        val thresholdX = pointX(
            timestamp = thresholdAt,
            earliest = earliest,
            latest = latest,
            left = left,
            right = right
        )
        fillPaint.color = sliceSoftColor(value.status)
        canvas.drawRoundRect(
            thresholdX,
            railY - dp(13f),
            right,
            railY + dp(13f),
            dp(13f),
            dp(13f),
            fillPaint
        )
        linePaint.color = sliceColor(value.status)
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(
            thresholdX,
            railY - dp(21f),
            thresholdX,
            railY + dp(21f),
            linePaint
        )
        drawRail(canvas, left, right, railY)
        drawPoints(canvas, value, left, right, railY)
    }

    private fun drawRail(
        canvas: Canvas,
        left: Float,
        right: Float,
        railY: Float
    ) {
        linePaint.color = AppColors.line
        linePaint.strokeWidth = dp(6f)
        canvas.drawLine(left, railY, right, railY, linePaint)
        fillPaint.color = AppColors.muted
        canvas.drawCircle(left, railY, dp(3f), fillPaint)
        canvas.drawCircle(right, railY, dp(3f), fillPaint)
    }

    private fun drawPoints(
        canvas: Canvas,
        value: FactTimeSlice,
        left: Float,
        right: Float,
        railY: Float
    ) {
        if (value.points.isEmpty()) {
            linePaint.color = AppColors.muted
            linePaint.strokeWidth = dp(2f)
            canvas.drawCircle(
                (left + right) / 2f,
                railY,
                dp(9f),
                linePaint
            )
            return
        }
        val earliest = value.points.minOf { it.checkedAt }
        val latest = value.points.maxOf { it.checkedAt }
        value.points.forEach { point ->
            val x = pointX(
                timestamp = point.checkedAt,
                earliest = earliest,
                latest = latest,
                left = left,
                right = right
            )
            val y = railY + laneOffset(point.factor)
            val color = pointColor(point)
            linePaint.color = color
            linePaint.strokeWidth = dp(1.5f)
            canvas.drawLine(x, railY, x, y, linePaint)

            if (point.factor == value.suggestedFactor) {
                linePaint.color = sliceColor(value.status)
                linePaint.strokeWidth = dp(2f)
                canvas.drawCircle(x, y, dp(14f), linePaint)
            }
            fillPaint.color = AppColors.surface
            canvas.drawCircle(x, y, dp(11f), fillPaint)
            linePaint.color = color
            linePaint.strokeWidth = dp(3f)
            canvas.drawCircle(x, y, dp(10f), linePaint)

            numberPaint.color = color
            numberPaint.textSize = cappedSp(10f)
            val baseline = y -
                (numberPaint.ascent() + numberPaint.descent()) / 2f
            canvas.drawText(
                (point.factor.ordinal + 1).toString(),
                x,
                baseline,
                numberPaint
            )
        }
    }

    private fun pointX(
        timestamp: Long,
        earliest: Long,
        latest: Long,
        left: Float,
        right: Float
    ): Float {
        if (latest == earliest) return (left + right) / 2f
        val ratio = (timestamp - earliest).toDouble() /
            (latest - earliest).toDouble()
        return left + (right - left) * ratio.toFloat()
    }

    private fun laneOffset(factor: SignalFactor): Float {
        return dp((factor.ordinal - 2) * 17f)
    }

    private fun pointColor(point: FactTimeSlicePoint): Int {
        return when (point.freshnessStatus) {
            FreshnessStatus.FRESH -> AppColors.accentDark
            FreshnessStatus.EXPIRING,
            FreshnessStatus.DEGRADED -> AppColors.warning
            FreshnessStatus.EXPIRED,
            FreshnessStatus.UNCONFIRMED -> AppColors.danger
        }
    }

    private fun sliceColor(status: FactTimeSliceStatus): Int {
        return when (status) {
            FactTimeSliceStatus.INSUFFICIENT -> AppColors.signal
            FactTimeSliceStatus.ALIGNED -> AppColors.accentDark
            FactTimeSliceStatus.DRIFTING -> AppColors.warning
            FactTimeSliceStatus.SPLIT -> AppColors.danger
        }
    }

    private fun sliceSoftColor(status: FactTimeSliceStatus): Int {
        return when (status) {
            FactTimeSliceStatus.INSUFFICIENT -> AppColors.signalSoft
            FactTimeSliceStatus.ALIGNED -> AppColors.accentSoft
            FactTimeSliceStatus.DRIFTING -> AppColors.warningSoft
            FactTimeSliceStatus.SPLIT -> AppColors.dangerSoft
        }
    }

    private fun accessibleDescription(value: FactTimeSlice): String {
        return buildString {
            append("Единый срез. ")
            append(
                when (value.status) {
                    FactTimeSliceStatus.INSUFFICIENT ->
                        "Нужно минимум два активных факта"
                    FactTimeSliceStatus.ALIGNED ->
                        "Проверки относятся к одному моменту"
                    FactTimeSliceStatus.DRIFTING ->
                        "Есть сдвиг времени проверок"
                    FactTimeSliceStatus.SPLIT ->
                        "Факты относятся к разным моментам"
                }
            )
            value.spreadMillis?.let { spread ->
                append(". Разброс ")
                append(FreshnessFormatter.duration(spread))
            }
            value.suggestedFactor?.let { factor ->
                append(". Старейший фактор: ")
                append(factor.title)
            }
        }
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
