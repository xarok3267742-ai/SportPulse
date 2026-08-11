package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class AttentionBudgetView @JvmOverloads constructor(
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
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private val segmentRect = RectF()
    private var usedFraction = 0f
    private var status = AttentionBudgetStatus.OPEN
    private var hasResult = false

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: AttentionBudgetResult) {
        usedFraction = (
            result.usedMillis.toFloat() /
                result.limitMillis.toFloat()
            ).coerceIn(0f, 1f)
        status = result.status
        hasResult = true
        contentDescription = buildString {
            append("Бюджет внимания. Использовано ")
            append(result.progressPercent)
            append(" процентов времени. Лимит ")
            append(result.limitMinutes)
            append(" минут. Статус: ")
            append(
                when (result.status) {
                    AttentionBudgetStatus.OPEN -> "открыт."
                    AttentionBudgetStatus.WARNING ->
                        "близко к границе."
                    AttentionBudgetStatus.EXHAUSTED ->
                        "исчерпан."
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
            resolveSize(dp(112f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasResult) return
        val count = 8
        val left = dp(5f)
        val right = width - dp(5f)
        val gap = dp(4f)
        val segmentWidth =
            (right - left - gap * (count - 1)) / count
        val top = dp(29f)
        val bottom = dp(73f)
        val usedColor = when (status) {
            AttentionBudgetStatus.OPEN -> AppColors.signal
            AttentionBudgetStatus.WARNING -> AppColors.warning
            AttentionBudgetStatus.EXHAUSTED -> AppColors.danger
        }

        for (index in 0 until count) {
            val segmentLeft = left +
                index * (segmentWidth + gap)
            segmentRect.set(
                segmentLeft,
                top,
                segmentLeft + segmentWidth,
                bottom
            )
            fillPaint.color = AppColors.accentSoft
            canvas.drawRoundRect(
                segmentRect,
                dp(4f),
                dp(4f),
                fillPaint
            )
            val segmentStart = index.toFloat() / count
            val segmentEnd = (index + 1).toFloat() / count
            val fillFraction = when {
                usedFraction <= segmentStart -> 0f
                usedFraction >= segmentEnd -> 1f
                else ->
                    (usedFraction - segmentStart) * count
            }
            if (fillFraction > 0f) {
                segmentRect.right = segmentLeft +
                    segmentWidth * fillFraction
                fillPaint.color = usedColor
                canvas.drawRoundRect(
                    segmentRect,
                    dp(4f),
                    dp(4f),
                    fillPaint
                )
            }
        }

        val warningX = left + (right - left) * 0.75f
        linePaint.color = AppColors.warning
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(
            warningX,
            top - dp(8f),
            warningX,
            bottom + dp(8f),
            linePaint
        )
        val stopX = right
        linePaint.color = AppColors.danger
        linePaint.strokeWidth = dp(3f)
        canvas.drawLine(
            stopX,
            top - dp(9f),
            stopX,
            bottom + dp(9f),
            linePaint
        )

        labelPaint.textSize = cappedSp(8f)
        labelPaint.color = AppColors.muted
        canvas.drawText("СТАРТ", left + dp(15f), dp(98f), labelPaint)
        labelPaint.color = AppColors.warning
        canvas.drawText("75%", warningX, dp(18f), labelPaint)
        labelPaint.color = AppColors.danger
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("СТОП", right, dp(98f), labelPaint)
        labelPaint.textAlign = Paint.Align.CENTER
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun cappedSp(value: Float): Float {
        val metrics = resources.displayMetrics
        return value * min(
            metrics.density *
                resources.configuration.fontScale,
            metrics.density * 1.25f
        )
    }
}
