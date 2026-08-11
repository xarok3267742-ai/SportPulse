package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class EventDispatchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = AppTypography.display(context, bold = true)
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = AppTypography.display(context, bold = true)
    }
    private val barRect = RectF()
    private val rowTitles = arrayOf(
        "СТОП",
        "ВНИМАНИЕ",
        "В РАБОТЕ",
        "СТАБИЛЬНО"
    )
    private val rowColors = intArrayOf(
        AppColors.danger,
        AppColors.warning,
        AppColors.signal,
        AppColors.accent
    )
    private val rowCounts = IntArray(4)
    private val rowCountLabels = Array(4) { "0" }
    private var total = 1
    private var hasResult = false

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: EventDispatchResult) {
        rowCounts[0] = value.stopCount
        rowCounts[1] = value.attentionCount
        rowCounts[2] = value.activeCount
        rowCounts[3] = value.stableCount
        rowCounts.forEachIndexed { index, count ->
            rowCountLabels[index] = count.toString()
        }
        total = value.entries.size.coerceAtLeast(1)
        hasResult = true
        contentDescription =
            "Распределение событий. Стоп ${value.stopCount}. " +
                "Внимание ${value.attentionCount}. " +
                "В работе ${value.activeCount}. " +
                "Стабильно ${value.stableCount}."
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
        if (!hasResult) return
        val labelRight = dp(79f)
        val countLeft = width - dp(25f)
        val barLeft = labelRight + dp(8f)
        val barRight = countLeft - dp(15f)
        val rowHeight = height.toFloat() / rowTitles.size

        rowTitles.indices.forEach { index ->
            val title = rowTitles[index]
            val count = rowCounts[index]
            val color = rowColors[index]
            val centerY = rowHeight * (index + 0.5f)
            labelPaint.color = if (count > 0) {
                color
            } else {
                AppColors.muted
            }
            labelPaint.textSize = fittedTextSize(
                value = title,
                maxWidth = labelRight - dp(6f),
                maxSp = 8.5f,
                paint = labelPaint
            )
            canvas.drawText(
                title,
                dp(5f),
                textBaseline(labelPaint, centerY),
                labelPaint
            )

            barRect.set(
                barLeft,
                centerY - dp(4f),
                barRight,
                centerY + dp(4f)
            )
            fillPaint.color = AppColors.line
            canvas.drawRoundRect(
                barRect,
                dp(4f),
                dp(4f),
                fillPaint
            )
            if (count > 0) {
                val ratio = count.toFloat() / total.toFloat()
                barRect.right = barLeft +
                    (barRight - barLeft) * ratio
                fillPaint.color = color
                canvas.drawRoundRect(
                    barRect,
                    dp(4f),
                    dp(4f),
                    fillPaint
                )
            }

            countPaint.color = if (count > 0) {
                color
            } else {
                AppColors.muted
            }
            countPaint.textSize = cappedSp(9.5f)
            canvas.drawText(
                rowCountLabels[index],
                width - dp(4f),
                textBaseline(countPaint, centerY),
                countPaint
            )
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

    private fun textBaseline(
        paint: Paint,
        centerY: Float
    ): Float {
        return centerY -
            (paint.ascent() + paint.descent()) / 2f
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
