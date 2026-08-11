package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class StoryQuietWindowView @JvmOverloads constructor(
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
    private val gateBounds = RectF()
    private var result: StoryQuietWindowResult? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: StoryQuietWindowResult) {
        require(
            value.state == StoryQuietWindowState.AVAILABLE ||
                value.state == StoryQuietWindowState.UNSCHEDULED
        )
        result = value
        contentDescription = when (value.state) {
            StoryQuietWindowState.AVAILABLE ->
                "Тихое окно. Сейчас, режим тишины, затем ближайшая доказуемая точка возврата."
            StoryQuietWindowState.UNSCHEDULED ->
                "Тихое окно. У открытых нитей пока нет доказуемого времени возврата."
            StoryQuietWindowState.EMPTY,
            StoryQuietWindowState.NO_ACTIVE -> "Тихое окно недоступно."
        }
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(108f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = result ?: return
        val left = dp(30f)
        val right = width - dp(30f)
        val gateX = left + (right - left) * 0.44f
        val railY = dp(42f)
        val available = value.state ==
            StoryQuietWindowState.AVAILABLE

        linePaint.strokeWidth = dp(7f)
        linePaint.color = AppColors.line
        canvas.drawLine(left, railY, right, railY, linePaint)

        linePaint.strokeWidth = dp(3.5f)
        linePaint.color = AppColors.signal
        canvas.drawLine(left, railY, gateX, railY, linePaint)
        if (available) {
            linePaint.color = AppColors.accent
            canvas.drawLine(gateX, railY, right, railY, linePaint)
        }

        fillPaint.color = AppColors.surface
        canvas.drawCircle(left, railY, dp(10f), fillPaint)
        linePaint.strokeWidth = dp(3f)
        linePaint.color = AppColors.signal
        canvas.drawCircle(left, railY, dp(9f), linePaint)

        fillPaint.color = AppColors.ink
        gateBounds.set(
            gateX - dp(13f),
            railY - dp(16f),
            gateX + dp(13f),
            railY + dp(16f)
        )
        canvas.drawRoundRect(
            gateBounds,
            dp(4f),
            dp(4f),
            fillPaint
        )
        fillPaint.color = AppColors.warning
        canvas.drawRect(
            gateX - dp(4f),
            railY - dp(19f),
            gateX + dp(4f),
            railY - dp(14f),
            fillPaint
        )

        if (available) {
            fillPaint.color = AppColors.accent
            canvas.drawCircle(right, railY, dp(10f), fillPaint)
            fillPaint.color = AppColors.surface
            canvas.drawCircle(right, railY, dp(3f), fillPaint)
        } else {
            fillPaint.color = AppColors.surface
            canvas.drawCircle(right, railY, dp(10f), fillPaint)
            linePaint.strokeWidth = dp(3f)
            linePaint.color = AppColors.muted
            canvas.drawCircle(right, railY, dp(9f), linePaint)
        }

        drawLabel(canvas, "СЕЙЧАС", left, AppColors.signal)
        drawLabel(canvas, "ТИШИНА", gateX, AppColors.ink)
        drawLabel(
            canvas,
            if (available) "ВЕРНУТЬСЯ" else "НЕТ ВРЕМЕНИ",
            right,
            if (available) AppColors.accentDark else AppColors.muted
        )
    }

    private fun drawLabel(
        canvas: Canvas,
        value: String,
        x: Float,
        color: Int
    ) {
        labelPaint.color = color
        labelPaint.textSize = fittedTextSize(
            value = value,
            maxWidth = dp(86f),
            maxSp = 8.5f
        )
        canvas.drawText(value, x, dp(86f), labelPaint)
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
