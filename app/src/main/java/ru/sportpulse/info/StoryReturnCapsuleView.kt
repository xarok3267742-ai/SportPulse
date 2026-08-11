package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class StoryReturnCapsuleView @JvmOverloads constructor(
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
    private val capsuleBounds = RectF()
    private val sealBounds = RectF()
    private var result: StoryReturnCapsuleResult? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: StoryReturnCapsuleResult) {
        result = value
        contentDescription = description(value.state)
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
        val left = dp(52f)
        val center = width * 0.53f
        val right = width - dp(52f)
        val railY = dp(42f)
        val outcomeColor = outcomeColor(value.state)

        linePaint.strokeWidth = dp(7f)
        linePaint.color = AppColors.line
        canvas.drawLine(left, railY, right, railY, linePaint)
        linePaint.strokeWidth = dp(3.5f)
        linePaint.color = AppColors.signal
        canvas.drawLine(left, railY, center, railY, linePaint)
        if (value.state != StoryReturnCapsuleState.SEALED) {
            linePaint.color = outcomeColor
            canvas.drawLine(center, railY, right, railY, linePaint)
        }

        capsuleBounds.set(
            left - dp(28f),
            railY - dp(14f),
            left + dp(28f),
            railY + dp(14f)
        )
        fillPaint.color = AppColors.surface
        canvas.drawRoundRect(
            capsuleBounds,
            dp(12f),
            dp(12f),
            fillPaint
        )
        linePaint.strokeWidth = dp(3f)
        linePaint.color = AppColors.ink
        canvas.drawRoundRect(
            capsuleBounds,
            dp(12f),
            dp(12f),
            linePaint
        )
        sealBounds.set(
            left - dp(4f),
            railY - dp(18f),
            left + dp(4f),
            railY + dp(18f)
        )
        fillPaint.color = AppColors.accent
        canvas.drawRoundRect(
            sealBounds,
            dp(3f),
            dp(3f),
            fillPaint
        )

        fillPaint.color = AppColors.ink
        canvas.drawCircle(center, railY, dp(7f), fillPaint)
        fillPaint.color = AppColors.warning
        canvas.drawCircle(center, railY - dp(10f), dp(3f), fillPaint)

        fillPaint.color = AppColors.surface
        canvas.drawCircle(right - dp(10f), railY, dp(10f), fillPaint)
        linePaint.strokeWidth = dp(3f)
        linePaint.color = AppColors.signal
        canvas.drawCircle(right - dp(10f), railY, dp(9f), linePaint)

        fillPaint.color = if (
            value.state == StoryReturnCapsuleState.SEALED
        ) {
            AppColors.surface
        } else {
            outcomeColor
        }
        canvas.drawCircle(right + dp(10f), railY, dp(10f), fillPaint)
        if (value.state == StoryReturnCapsuleState.SEALED) {
            linePaint.color = AppColors.muted
            canvas.drawCircle(
                right + dp(10f),
                railY,
                dp(9f),
                linePaint
            )
        } else {
            fillPaint.color = AppColors.surface
            canvas.drawCircle(
                right + dp(10f),
                railY,
                dp(3f),
                fillPaint
            )
        }

        drawLabel(canvas, "ТОГДА", left, AppColors.signal)
        drawLabel(canvas, "ПЛОМБА", center, AppColors.ink)
        drawLabel(
            canvas,
            if (value.state == StoryReturnCapsuleState.SEALED) {
                "ЗАКРЫТО"
            } else {
                "СЕЙЧАС"
            },
            right,
            if (value.state == StoryReturnCapsuleState.SEALED) {
                AppColors.muted
            } else {
                outcomeColor
            }
        )
    }

    private fun outcomeColor(state: StoryReturnCapsuleState): Int {
        return when (state) {
            StoryReturnCapsuleState.SEALED -> AppColors.muted
            StoryReturnCapsuleState.LIMIT_REACHED,
            StoryReturnCapsuleState.POINT_MOVED,
            StoryReturnCapsuleState.CHANGED,
            StoryReturnCapsuleState.DETACHED -> AppColors.warning
            StoryReturnCapsuleState.UNCHANGED -> AppColors.signal
            StoryReturnCapsuleState.RESOLVED -> AppColors.accentDark
            StoryReturnCapsuleState.MISSED,
            StoryReturnCapsuleState.MISSING,
            StoryReturnCapsuleState.CURRENT_TAMPERED -> AppColors.danger
        }
    }

    private fun description(state: StoryReturnCapsuleState): String {
        return when (state) {
            StoryReturnCapsuleState.SEALED ->
                "Капсула возврата запечатана. Результат пока закрыт."
            StoryReturnCapsuleState.LIMIT_REACHED ->
                "Капсула возврата. Предел паузы достигнут, исходная точка еще впереди."
            StoryReturnCapsuleState.UNCHANGED ->
                "Капсула возврата. Точка достигнута, локальная версия вопроса не изменилась."
            StoryReturnCapsuleState.POINT_MOVED ->
                "Капсула возврата. Связанная точка перенесена вперед."
            StoryReturnCapsuleState.CHANGED ->
                "Капсула возврата. Состояние вопроса изменилось."
            StoryReturnCapsuleState.RESOLVED ->
                "Капсула возврата. Вопрос закрыт."
            StoryReturnCapsuleState.MISSED ->
                "Капсула возврата. Момент упущен."
            StoryReturnCapsuleState.DETACHED ->
                "Капсула возврата. Событие больше не входит в каталог."
            StoryReturnCapsuleState.MISSING ->
                "Капсула возврата. Исходная нить удалена."
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                "Капсула возврата. Текущая связь не прошла проверку целостности."
        }
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
            maxWidth = dp(76f),
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
