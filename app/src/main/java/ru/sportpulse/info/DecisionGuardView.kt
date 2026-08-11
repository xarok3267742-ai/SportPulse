package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class DecisionGuardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = dp(12f)
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = AppColors.danger
        pathEffect = DashPathEffect(
            floatArrayOf(dp(4f), dp(3f)),
            0f
        )
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val markerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(10.5f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val sealPath = Path()

    private var result: DecisionGuardResult? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: DecisionGuardResult) {
        result = value
        contentDescription = accessibilityDescription(value)
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(126f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = result ?: return
        if (value.status == DecisionGuardStatus.SEALED_SKIP) {
            drawSealedSkip(canvas, value)
        } else {
            drawCondition(canvas, value)
        }
    }

    private fun drawCondition(
        canvas: Canvas,
        value: DecisionGuardResult
    ) {
        val condition = value.plan.condition ?: return
        val left = dp(18f)
        val right = width - dp(18f)
        val trackY = dp(61f)
        val floor = condition.scoreFloor

        if (floor == null) {
            drawTrack(
                canvas,
                left,
                right,
                trackY,
                AppColors.signalSoft
            )
        } else {
            val floorX = xFor(floor, left, right)
            drawTrack(
                canvas,
                left,
                floorX,
                trackY,
                AppColors.dangerSoft
            )
            drawTrack(
                canvas,
                floorX,
                right,
                trackY,
                AppColors.accentSoft
            )
            canvas.drawLine(
                floorX,
                dp(24f),
                floorX,
                dp(93f),
                thresholdPaint
            )
            labelPaint.color = AppColors.danger
            drawClampedLabel(
                canvas,
                "стоп ≤ $floor",
                floorX,
                dp(112f),
                left,
                right
            )
        }

        val baselineX = xFor(
            condition.baselineValue,
            left,
            right
        )
        markerPaint.color = AppColors.ink
        canvas.drawCircle(
            baselineX,
            trackY,
            dp(7f),
            markerPaint
        )
        labelPaint.color = AppColors.ink
        drawClampedLabel(
            canvas,
            "пломба ${condition.baselineValue}",
            baselineX,
            dp(17f),
            left,
            right
        )

        val currentValue = value.currentFactorValue
            ?: condition.baselineValue
        val currentX = xFor(currentValue, left, right)
        val currentColor = if (value.isTriggered) {
            AppColors.danger
        } else {
            AppColors.accent
        }
        markerRingPaint.color = currentColor
        canvas.drawCircle(
            currentX,
            trackY,
            dp(11f),
            markerRingPaint
        )
        if (currentX == baselineX) {
            markerPaint.color = currentColor
            canvas.drawCircle(
                currentX,
                trackY,
                dp(3f),
                markerPaint
            )
        }
        labelPaint.color = currentColor
        drawClampedLabel(
            canvas,
            "сейчас $currentValue",
            currentX,
            dp(88f),
            left,
            right
        )
    }

    private fun drawSealedSkip(
        canvas: Canvas,
        value: DecisionGuardResult
    ) {
        val left = dp(18f)
        val right = width - dp(18f)
        val centerY = dp(60f)
        drawTrack(
            canvas,
            left,
            right,
            centerY,
            AppColors.signalSoft
        )
        markerPaint.color = AppColors.signal
        canvas.drawCircle(
            width / 2f,
            centerY,
            dp(22f),
            markerPaint
        )
        sealPath.reset()
        sealPath.moveTo(width / 2f - dp(10f), centerY)
        sealPath.lineTo(width / 2f - dp(3f), centerY + dp(7f))
        sealPath.lineTo(width / 2f + dp(11f), centerY - dp(8f))
        markerRingPaint.color = AppColors.surface
        markerRingPaint.strokeWidth = dp(3f)
        markerRingPaint.style = Paint.Style.STROKE
        canvas.drawPath(sealPath, markerRingPaint)
        markerRingPaint.strokeWidth = dp(2.5f)
        labelPaint.color = AppColors.signal
        drawClampedLabel(
            canvas,
            "ПРОПУСК ЗАПЕЧАТАН",
            width / 2f,
            dp(112f),
            left,
            right
        )
        labelPaint.color = AppColors.ink
        drawClampedLabel(
            canvas,
            "пломба ${value.plan.shortSeal}",
            width / 2f,
            dp(18f),
            left,
            right
        )
    }

    private fun drawTrack(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        y: Float,
        color: Int
    ) {
        trackPaint.color = color
        canvas.drawLine(startX, y, endX, y, trackPaint)
    }

    private fun xFor(
        value: Int,
        left: Float,
        right: Float
    ): Float {
        return left +
            (right - left) * value.coerceIn(0, 100) / 100f
    }

    private fun drawClampedLabel(
        canvas: Canvas,
        value: String,
        centerX: Float,
        baseline: Float,
        left: Float,
        right: Float
    ) {
        val textWidth = labelPaint.measureText(value)
        val x = (centerX - textWidth / 2f)
            .coerceIn(left, right - textWidth)
        canvas.drawText(value, x, baseline, labelPaint)
    }

    private fun accessibilityDescription(
        value: DecisionGuardResult
    ): String {
        if (value.status == DecisionGuardStatus.SEALED_SKIP) {
            return "Стоп-контракт. Решение пропустить запечатано. Пломба ${value.plan.shortSeal}."
        }
        val condition = value.plan.condition
            ?: return "Стоп-контракт."
        val status = if (value.isTriggered) {
            "Контракт сработал"
        } else {
            "Контракт действует"
        }
        val floor = condition.scoreFloor?.let {
            ". Стоп-линия $it"
        }.orEmpty()
        return "$status. Критический фактор ${condition.factor.title}. " +
            "Запечатано ${condition.baselineValue}, сейчас " +
            "${value.currentFactorValue}$floor. " +
            "Пломба ${value.plan.shortSeal}."
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
