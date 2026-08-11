package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.abs

internal class VerificationRouteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = dp(12f)
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
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
    private val projectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = AppColors.signal
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        typeface = AppTypography.display(context, bold = true)
    }

    private var route: VerificationRoute? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setRoute(value: VerificationRoute) {
        route = value
        contentDescription = accessibilityDescription(value)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = dp(320f).toInt()
        val desiredHeight = dp(122f).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = route ?: return
        val left = dp(16f)
        val right = width - dp(16f)
        val trackY = dp(71f)

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

        value.targetReadiness?.let { target ->
            val targetX = xFor(target, left, right)
            canvas.drawLine(
                targetX,
                dp(27f),
                targetX,
                dp(85f),
                targetPaint
            )
            labelPaint.color = AppColors.muted
            drawCenteredLabel(
                canvas,
                "цель $target",
                targetX,
                dp(20f),
                left,
                right
            )
        }

        val current = value.baselineResult.effectiveSignal.readiness
        val projected = if (
            value.status == VerificationRouteStatus.READY_MAINTAIN
        ) {
            null
        } else {
            value.projectedResult.effectiveSignal.readiness
        }
        val closeMarkers = projected != null &&
            abs(projected - current) <= 4
        val currentX = xFor(current, left, right)
        val currentY = trackY + if (closeMarkers) dp(6f) else 0f
        canvas.drawCircle(currentX, currentY, dp(7f), currentPaint)
        labelPaint.color = AppColors.ink
        drawCenteredLabel(
            canvas,
            "сейчас $current",
            currentX,
            dp(107f),
            left,
            right
        )

        projected?.let { projectedValue ->
            val projectedX = xFor(projectedValue, left, right)
            val projectedY = trackY - if (closeMarkers) dp(6f) else 0f
            projectedPaint.color = when (value.status) {
                VerificationRouteStatus.REACHABLE -> AppColors.accent
                VerificationRouteStatus.FACTS_LIMIT -> AppColors.warning
                VerificationRouteStatus.READY_MAINTAIN -> AppColors.signal
            }
            canvas.drawCircle(
                projectedX,
                projectedY,
                dp(if (projectedValue == current) 10f else 7f),
                projectedPaint
            )
            labelPaint.color = projectedPaint.color
            val prefix = if (
                value.status == VerificationRouteStatus.REACHABLE
            ) {
                "после"
            } else {
                "предел"
            }
            drawCenteredLabel(
                canvas,
                "$prefix $projectedValue",
                projectedX,
                dp(49f),
                left,
                right
            )
        }
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

    private fun drawCenteredLabel(
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

    private fun accessibilityDescription(value: VerificationRoute): String {
        val current = value.baselineResult.effectiveSignal.readiness
        return when (value.status) {
            VerificationRouteStatus.REACHABLE ->
                "Маршрут проверки. Сейчас $current. После минимальной проверки ${value.projectedResult.effectiveSignal.readiness}. Цель ${value.targetReadiness}."
            VerificationRouteStatus.FACTS_LIMIT ->
                "Маршрут проверки. Сейчас $current. Предел текущих фактов ${value.allQuorumResult.effectiveSignal.readiness}. Цель ${value.targetReadiness}."
            VerificationRouteStatus.READY_MAINTAIN ->
                "Маршрут проверки. Статус достигнут, полнота $current из 100."
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
