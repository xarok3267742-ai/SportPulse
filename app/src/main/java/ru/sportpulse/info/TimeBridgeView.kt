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

internal class TimeBridgeView @JvmOverloads constructor(
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
        typeface = AppTypography.display(context, bold = true)
    }
    private val pillRect = RectF()
    private var hasResult = false
    private var sourceCity = "МОСКВА"
    private var targetCity = "МОСКВА"
    private var sourceTime = "00:00"
    private var targetTime = "00:00"
    private var offsetLabel = "UTC+3"
    private var differenceLabel = "одно время с Москвой"
    private var dayLabel = "ТЕ ЖЕ СУТКИ"
    private var dayShift = 0

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: TimeBridgeResult) {
        sourceCity = "МОСКВА"
        targetCity = result.selectedZone.city.uppercase(
            Locale.getDefault()
        )
        sourceTime = clock(
            result.moscowDateTime.hour,
            result.moscowDateTime.minute
        )
        targetTime = clock(
            result.selectedDateTime.hour,
            result.selectedDateTime.minute
        )
        offsetLabel = result.selectedOffsetLabel
        differenceLabel = result.differenceLabel
        dayShift = result.dayShift
        dayLabel = when {
            dayShift > 0 -> "СЛЕДУЮЩИЕ СУТКИ"
            dayShift < 0 -> "ПРЕДЫДУЩИЕ СУТКИ"
            else -> "ТЕ ЖЕ СУТКИ"
        }
        contentDescription = buildString {
            append("Часовой мост. Москва ")
            append(sourceTime)
            append(". ")
            append(result.selectedZone.city)
            append(" ")
            append(targetTime)
            append(". ")
            append(differenceLabel)
            if (dayShift != 0) {
                append(". Переход на ")
                append(
                    if (dayShift > 0) {
                        "следующие сутки"
                    } else {
                        "предыдущие сутки"
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
            resolveSize(dp(138f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasResult) return

        val left = dp(12f)
        val right = width - dp(12f)
        val sourceX = left + dp(15f)
        val targetX = right - dp(15f)
        val railY = dp(74f)

        labelPaint.textSize = fittedTextSize(
            sourceCity,
            (width - dp(56f)) * 0.42f,
            10f,
            labelPaint
        )
        labelPaint.color = AppColors.muted
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(sourceCity, left, dp(18f), labelPaint)

        labelPaint.textSize = fittedTextSize(
            targetCity,
            (width - dp(56f)) * 0.42f,
            10f,
            labelPaint
        )
        labelPaint.color = AppColors.accentDark
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(targetCity, right, dp(18f), labelPaint)

        valuePaint.textSize = cappedSp(22f)
        valuePaint.color = AppColors.ink
        valuePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(sourceTime, left, dp(48f), valuePaint)
        valuePaint.color = AppColors.accentDark
        valuePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(targetTime, right, dp(48f), valuePaint)

        linePaint.strokeWidth = dp(5f)
        linePaint.color = AppColors.line
        canvas.drawLine(sourceX, railY, targetX, railY, linePaint)
        linePaint.strokeWidth = dp(3f)
        linePaint.color = AppColors.signal
        canvas.drawLine(sourceX, railY, targetX, railY, linePaint)

        fillPaint.color = AppColors.surface
        canvas.drawCircle(sourceX, railY, dp(9f), fillPaint)
        linePaint.strokeWidth = dp(3f)
        linePaint.color = AppColors.signal
        canvas.drawCircle(sourceX, railY, dp(8f), linePaint)
        fillPaint.color = AppColors.accent
        canvas.drawCircle(targetX, railY, dp(9f), fillPaint)
        fillPaint.color = AppColors.surface
        canvas.drawCircle(targetX, railY, dp(3f), fillPaint)

        val pillWidth = dp(67f)
        pillRect.set(
            width / 2f - pillWidth / 2f,
            railY - dp(14f),
            width / 2f + pillWidth / 2f,
            railY + dp(14f)
        )
        fillPaint.color = AppColors.signalSoft
        canvas.drawRoundRect(pillRect, dp(8f), dp(8f), fillPaint)
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.color = AppColors.signal
        labelPaint.textSize = fittedTextSize(
            offsetLabel,
            pillWidth - dp(10f),
            10.5f,
            labelPaint
        )
        canvas.drawText(
            offsetLabel,
            width / 2f,
            textBaseline(labelPaint, railY),
            labelPaint
        )

        val dayColor = if (dayShift == 0) {
            AppColors.muted
        } else {
            AppColors.danger
        }
        labelPaint.color = dayColor
        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.textSize = cappedSp(9f)
        canvas.drawText(dayLabel, left, dp(108f), labelPaint)

        labelPaint.color = AppColors.ink
        labelPaint.textAlign = Paint.Align.LEFT
        labelPaint.textSize = fittedTextSize(
            differenceLabel,
            width - left * 2f,
            11f,
            labelPaint
        )
        canvas.drawText(differenceLabel, left, dp(132f), labelPaint)
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

    private fun clock(hour: Int, minute: Int): String {
        return hour.toString().padStart(2, '0') +
            ":" + minute.toString().padStart(2, '0')
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
