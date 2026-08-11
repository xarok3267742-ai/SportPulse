package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

internal class DecisionCorridorView @JvmOverloads constructor(
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
        strokeWidth = dp(1.3f)
        color = AppColors.muted
        pathEffect = DashPathEffect(
            floatArrayOf(dp(4f), dp(3f)),
            0f
        )
    }
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.ink
    }
    private val lowerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        color = AppColors.danger
    }
    private val upperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        color = AppColors.accent
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10.5f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val arrowPath = Path()

    private var corridor: DecisionCorridor? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setCorridor(value: DecisionCorridor) {
        corridor = value
        contentDescription = accessibilityDescription(value)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(320f).toInt()
        val desiredHeight = dp(148f).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = corridor ?: return
        val left = dp(16f)
        val right = width - dp(16f)
        val trackY = dp(76f)

        drawTrackSegment(
            canvas,
            left,
            xFor(SignalThresholds.OBSERVE, left, right),
            trackY,
            AppColors.dangerSoft
        )
        drawTrackSegment(
            canvas,
            xFor(SignalThresholds.OBSERVE, left, right),
            xFor(SignalThresholds.READY, left, right),
            trackY,
            AppColors.warningSoft
        )
        drawTrackSegment(
            canvas,
            xFor(SignalThresholds.READY, left, right),
            right,
            trackY,
            AppColors.accentSoft
        )

        drawThreshold(
            canvas,
            SignalThresholds.OBSERVE,
            left,
            right,
            trackY
        )
        drawThreshold(
            canvas,
            SignalThresholds.READY,
            left,
            right,
            trackY
        )

        val current = value.baseline.effectiveSignal.readiness
        val currentX = xFor(current, left, right)
        value.lowerBoundary?.let { boundary ->
            drawBoundary(
                canvas = canvas,
                boundary = boundary,
                currentX = currentX,
                y = trackY - dp(9f),
                left = left,
                right = right,
                paint = lowerPaint,
                labelBaseline = dp(49f),
                prefix = "вниз"
            )
        }
        value.upperBoundary?.let { boundary ->
            drawBoundary(
                canvas = canvas,
                boundary = boundary,
                currentX = currentX,
                y = trackY + dp(9f),
                left = left,
                right = right,
                paint = upperPaint,
                labelBaseline = dp(116f),
                prefix = "вверх"
            )
        }

        canvas.drawCircle(currentX, trackY, dp(7f), currentPaint)
        labelPaint.color = AppColors.ink
        drawClampedLabel(
            canvas,
            "сейчас $current",
            currentX,
            dp(143f),
            left,
            right
        )
    }

    private fun drawThreshold(
        canvas: Canvas,
        threshold: Int,
        left: Float,
        right: Float,
        trackY: Float
    ) {
        val x = xFor(threshold, left, right)
        canvas.drawLine(
            x,
            dp(22f),
            x,
            trackY + dp(22f),
            thresholdPaint
        )
        labelPaint.color = AppColors.muted
        drawClampedLabel(
            canvas,
            threshold.toString(),
            x,
            dp(16f),
            left,
            right
        )
    }

    private fun drawBoundary(
        canvas: Canvas,
        boundary: DecisionBoundary,
        currentX: Float,
        y: Float,
        left: Float,
        right: Float,
        paint: Paint,
        labelBaseline: Float,
        prefix: String
    ) {
        val readiness = boundary.result.effectiveSignal.readiness
        val boundaryX = xFor(readiness, left, right)
        canvas.drawLine(currentX, y, boundaryX, y, paint)
        drawArrowHead(
            canvas = canvas,
            tipX = boundaryX,
            y = y,
            pointsLeft = boundaryX < currentX,
            paint = paint
        )
        canvas.drawCircle(boundaryX, y, dp(6f), paint)
        labelPaint.color = paint.color
        drawClampedLabel(
            canvas,
            "$prefix $readiness",
            boundaryX,
            labelBaseline,
            left,
            right
        )
    }

    private fun drawArrowHead(
        canvas: Canvas,
        tipX: Float,
        y: Float,
        pointsLeft: Boolean,
        paint: Paint
    ) {
        val direction = if (pointsLeft) 1f else -1f
        arrowPath.reset()
        arrowPath.moveTo(tipX, y)
        arrowPath.lineTo(
            tipX + direction * dp(7f),
            y - dp(4f)
        )
        arrowPath.moveTo(tipX, y)
        arrowPath.lineTo(
            tipX + direction * dp(7f),
            y + dp(4f)
        )
        canvas.drawPath(arrowPath, paint)
    }

    private fun drawTrackSegment(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        y: Float,
        color: Int
    ) {
        trackPaint.color = color
        canvas.drawLine(startX, y, endX, y, trackPaint)
    }

    private fun xFor(value: Int, left: Float, right: Float): Float {
        return left + (right - left) * value.coerceIn(0, 100) / 100f
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
        value: DecisionCorridor
    ): String {
        val current = value.baseline.effectiveSignal.readiness
        val boundaries = listOfNotNull(
            value.lowerBoundary?.let {
                "Нижняя граница: ${it.factor.title} ${it.claimedBefore} до ${it.claimedAfter}, полнота ${it.result.effectiveSignal.readiness}"
            },
            value.upperBoundary?.let {
                "Верхняя граница: ${it.factor.title} ${it.claimedBefore} до ${it.claimedAfter}, полнота ${it.result.effectiveSignal.readiness}"
            }
        )
        return if (boundaries.isEmpty()) {
            "Коридор решения. Сейчас $current. Изменения одного фактора недостаточно для смены статуса."
        } else {
            "Коридор решения. Сейчас $current. ${boundaries.joinToString(". ")}."
        }
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
}
