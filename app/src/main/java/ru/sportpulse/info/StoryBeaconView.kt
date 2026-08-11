package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class StoryBeaconView @JvmOverloads constructor(
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
    private var moments: List<StoryBeaconMoment> = emptyList()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: StoryBeaconResult) {
        moments = result.moments
        contentDescription = if (moments.isEmpty()) {
            "Маяк события. Будущих опорных моментов нет."
        } else {
            buildString {
                append("Маяк события. ")
                moments.forEachIndexed { index, moment ->
                    if (index > 0) append(". ")
                    append(accessibleTitle(moment.kind))
                }
            }
        }
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(104f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (moments.isEmpty()) {
            drawEmpty(canvas)
            return
        }

        val left = dp(22f)
        val right = width - dp(22f)
        val railY = dp(37f)
        val labelY = dp(82f)
        val interval = if (moments.size == 1) {
            0f
        } else {
            (right - left) / (moments.size - 1).toFloat()
        }
        val firstX = if (moments.size == 1) width / 2f else left

        if (moments.size > 1) {
            linePaint.strokeWidth = dp(6f)
            linePaint.color = AppColors.line
            canvas.drawLine(left, railY, right, railY, linePaint)
            linePaint.strokeWidth = dp(3f)
            linePaint.color = momentColor(moments.first().kind)
            canvas.drawLine(
                left,
                railY,
                left + interval,
                railY,
                linePaint
            )
        }

        moments.forEachIndexed { index, moment ->
            val x = firstX + interval * index
            val color = momentColor(moment.kind)
            if (index == 0) {
                linePaint.strokeWidth = dp(2f)
                linePaint.color = color
                canvas.drawCircle(x, railY, dp(13f), linePaint)
                fillPaint.color = color
                canvas.drawCircle(x, railY, dp(8f), fillPaint)
            } else {
                fillPaint.color = AppColors.surface
                canvas.drawCircle(x, railY, dp(8f), fillPaint)
                linePaint.strokeWidth = dp(3f)
                linePaint.color = color
                canvas.drawCircle(x, railY, dp(8f), linePaint)
            }

            val title = shortTitle(moment.kind)
            labelPaint.color = if (index == 0) {
                color
            } else {
                AppColors.muted
            }
            labelPaint.textSize = fittedTextSize(
                value = title,
                maxWidth = if (moments.size == 1) {
                    width - dp(32f)
                } else {
                    interval - dp(5f)
                },
                maxSp = 8.5f
            )
            canvas.drawText(title, x, labelY, labelPaint)
        }
    }

    private fun drawEmpty(canvas: Canvas) {
        val centerX = width / 2f
        val railY = dp(37f)
        linePaint.strokeWidth = dp(6f)
        linePaint.color = AppColors.line
        canvas.drawLine(
            dp(24f),
            railY,
            width - dp(24f),
            railY,
            linePaint
        )
        fillPaint.color = AppColors.surface
        canvas.drawCircle(centerX, railY, dp(9f), fillPaint)
        linePaint.strokeWidth = dp(3f)
        linePaint.color = AppColors.muted
        canvas.drawCircle(centerX, railY, dp(9f), linePaint)
        labelPaint.color = AppColors.muted
        labelPaint.textSize = cappedSp(8.5f)
        canvas.drawText("НЕТ ТОЧЕК", centerX, dp(82f), labelPaint)
    }

    private fun momentColor(kind: StoryBeaconMomentKind): Int {
        return when (kind) {
            StoryBeaconMomentKind.ACTION_NOW,
            StoryBeaconMomentKind.CHECK_WINDOW -> AppColors.signal
            StoryBeaconMomentKind.FACT_EXPIRY -> AppColors.warning
            StoryBeaconMomentKind.START,
            StoryBeaconMomentKind.COMPLETE -> AppColors.accent
            StoryBeaconMomentKind.REVIEW_OPEN -> AppColors.danger
        }
    }

    private fun shortTitle(kind: StoryBeaconMomentKind): String {
        return when (kind) {
            StoryBeaconMomentKind.ACTION_NOW -> "СЕЙЧАС"
            StoryBeaconMomentKind.CHECK_WINDOW -> "ПРОВЕРКА"
            StoryBeaconMomentKind.FACT_EXPIRY -> "СРОК"
            StoryBeaconMomentKind.START -> "СТАРТ"
            StoryBeaconMomentKind.REVIEW_OPEN -> "РАЗБОР"
            StoryBeaconMomentKind.COMPLETE -> "ГОТОВО"
        }
    }

    private fun accessibleTitle(
        kind: StoryBeaconMomentKind
    ): String {
        return when (kind) {
            StoryBeaconMomentKind.ACTION_NOW -> "действие доступно сейчас"
            StoryBeaconMomentKind.CHECK_WINDOW ->
                "безопасное окно повторной проверки"
            StoryBeaconMomentKind.FACT_EXPIRY ->
                "снижение свежести подтверждения"
            StoryBeaconMomentKind.START -> "указанный старт события"
            StoryBeaconMomentKind.REVIEW_OPEN ->
                "минимальное окно разбора"
            StoryBeaconMomentKind.COMPLETE -> "маршрут завершен"
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
