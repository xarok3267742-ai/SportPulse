package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class StoryThreadMapView @JvmOverloads constructor(
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
    private val labels = arrayOf(
        "СВЯЗЬ",
        "СДВИГ",
        "ОТКРЫТО",
        "ЗАКРЫТО",
        "УПУЩЕНО"
    )
    private val counts = IntArray(labels.size)
    private var leadingIndex = -1
    private var hasResult = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: StoryThreadMapResult) {
        counts[0] = result.linkIssueCount
        counts[1] = result.movedCount
        counts[2] = result.openCount
        counts[3] = result.resolvedCount
        counts[4] = result.missedCount
        leadingIndex = when (result.leadingState) {
            StoryThreadMapState.TAMPERED,
            StoryThreadMapState.DETACHED -> 0
            StoryThreadMapState.MOVED -> 1
            StoryThreadMapState.OPEN -> 2
            StoryThreadMapState.RESOLVED -> 3
            StoryThreadMapState.MISSED -> 4
            StoryThreadMapState.EMPTY -> -1
        }
        contentDescription =
            "Карта нитей. Проблемы связи: ${counts[0]}. " +
                "Сдвинулось: ${counts[1]}. " +
                "Открыто: ${counts[2]}. " +
                "Закрыто: ${counts[3]}. " +
                "Упущено: ${counts[4]}."
        hasResult = true
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

        val left = dp(24f)
        val right = width - dp(24f)
        val railY = dp(42f)
        val labelY = dp(84f)
        val interval = (right - left) / (labels.size - 1)

        linePaint.strokeWidth = dp(6f)
        linePaint.color = AppColors.line
        canvas.drawLine(left, railY, right, railY, linePaint)

        labels.indices.forEach { index ->
            val x = left + interval * index
            val count = counts[index]
            val color = nodeColor(index)

            if (index == leadingIndex) {
                linePaint.strokeWidth = dp(2f)
                linePaint.color = color
                canvas.drawCircle(x, railY, dp(15f), linePaint)
            }

            fillPaint.color = if (count > 0) {
                color
            } else {
                AppColors.surface
            }
            canvas.drawCircle(x, railY, dp(11f), fillPaint)
            linePaint.strokeWidth = dp(2.5f)
            linePaint.color = if (count > 0) {
                color
            } else {
                AppColors.line
            }
            canvas.drawCircle(x, railY, dp(10f), linePaint)

            labelPaint.textSize = cappedSp(8.5f)
            labelPaint.color = if (count > 0) {
                AppColors.surface
            } else {
                AppColors.muted
            }
            canvas.drawText(count.toString(), x, railY + dp(3f), labelPaint)

            labelPaint.color = if (index == leadingIndex) {
                color
            } else {
                AppColors.muted
            }
            labelPaint.textSize = fittedTextSize(
                value = labels[index],
                maxWidth = interval - dp(5f),
                maxSp = 8f
            )
            canvas.drawText(labels[index], x, labelY, labelPaint)
        }
    }

    private fun nodeColor(index: Int): Int {
        return when (index) {
            0, 4 -> AppColors.danger
            1 -> AppColors.warning
            2 -> AppColors.signal
            else -> AppColors.accent
        }
    }

    private fun fittedTextSize(
        value: String,
        maxWidth: Float,
        maxSp: Float
    ): Float {
        val preferred = cappedSp(maxSp)
        labelPaint.textSize = preferred
        val measured = labelPaint.measureText(value)
        return if (measured > maxWidth && measured > 0f) {
            preferred * maxWidth / measured
        } else {
            preferred
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
