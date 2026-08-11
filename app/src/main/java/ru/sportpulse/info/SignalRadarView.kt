package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal class SignalRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = AppColors.line
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = Color.argb(110, 93, 102, 108)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(62, 0, 118, 105)
    }
    private val shadowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(42, 172, 102, 0)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(floatArrayOf(dp(7f), dp(5f)), 0f)
        color = AppColors.warning
    }
    private val shadowPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.warning
    }
    private val signalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeJoin = Paint.Join.ROUND
        color = AppColors.accent
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.accent
    }
    private val weakestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.danger
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.muted
        textSize = sp(11.5f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val signalPath = Path()
    private val shadowPath = Path()
    private val polygonPath = Path()

    private var assessment = SignalAssessment(List(SignalFactor.values().size) { 50 })
    private var claimedAssessment = assessment
    private var weakestIndex = 0
    private var criticalShadowIndex: Int? = null
    private var shadowStatus = ConfidenceShadowStatus.CLEAR

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateAccessibilityDescription()
    }

    fun setAssessment(value: SignalAssessment) {
        assessment = value
        claimedAssessment = value
        weakestIndex = value.values.indices.minByOrNull { value.values[it] } ?: 0
        criticalShadowIndex = null
        shadowStatus = ConfidenceShadowStatus.CLEAR
        updateAccessibilityDescription()
        invalidate()
    }

    fun setComparison(result: ConfidenceShadowResult) {
        assessment = result.supportedAssessment
        claimedAssessment = result.claimedAssessment
        weakestIndex = assessment.values.indices.minByOrNull {
            assessment.values[it]
        } ?: 0
        criticalShadowIndex = result.criticalFactor?.factor?.ordinal
        shadowStatus = result.status
        updateAccessibilityDescription()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = dp(292f).toInt()
        val width = resolveSize(desired, widthMeasureSpec)
        val height = resolveSize(desired, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f + dp(2f)
        val radius = min(width, height) * 0.31f
        val factorCount = SignalFactor.values().size

        for (ring in 1..4) {
            drawPolygon(
                canvas = canvas,
                centerX = centerX,
                centerY = centerY,
                radius = radius * ring / 4f,
                count = factorCount,
                paint = gridPaint
            )
        }

        repeat(factorCount) { index ->
            val angle = angleFor(index, factorCount)
            canvas.drawLine(
                centerX,
                centerY,
                centerX + cos(angle) * radius,
                centerY + sin(angle) * radius,
                axisPaint
            )
        }

        if (claimedAssessment != assessment) {
            buildSignalPath(
                path = shadowPath,
                values = claimedAssessment.values,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                count = factorCount
            )
            canvas.drawPath(shadowPath, shadowFillPaint)
            canvas.drawPath(shadowPath, shadowPaint)

            criticalShadowIndex?.let { index ->
                val angle = angleFor(index, factorCount)
                val pointRadius = radius *
                    claimedAssessment.values[index] / 100f
                canvas.drawCircle(
                    centerX + cos(angle) * pointRadius,
                    centerY + sin(angle) * pointRadius,
                    dp(5f),
                    shadowPointPaint
                )
            }
        }

        buildSignalPath(
            path = signalPath,
            values = assessment.values,
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            count = factorCount
        )
        canvas.drawPath(signalPath, fillPaint)
        canvas.drawPath(signalPath, signalPaint)

        assessment.values.forEachIndexed { index, value ->
            val angle = angleFor(index, factorCount)
            val pointRadius = radius * value / 100f
            val x = centerX + cos(angle) * pointRadius
            val y = centerY + sin(angle) * pointRadius
            canvas.drawCircle(
                x,
                y,
                dp(if (index == weakestIndex) 5f else 3.5f),
                if (index == weakestIndex) weakestPaint else pointPaint
            )
        }

        SignalFactor.values().forEachIndexed { index, factor ->
            val angle = angleFor(index, factorCount)
            val labelRadius = radius + dp(27f)
            var x = centerX + cos(angle) * labelRadius
            val y = centerY + sin(angle) * labelRadius
            val textWidth = labelPaint.measureText(factor.shortTitle)
            x = when {
                cos(angle) > 0.25 -> x
                cos(angle) < -0.25 -> x - textWidth
                else -> x - textWidth / 2f
            }
            x = x.coerceIn(dp(4f), width - textWidth - dp(4f))
            val baseline = y - (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(factor.shortTitle, x, baseline, labelPaint)
        }
    }

    private fun buildSignalPath(
        path: Path,
        values: List<Int>,
        centerX: Float,
        centerY: Float,
        radius: Float,
        count: Int
    ) {
        path.reset()
        values.forEachIndexed { index, value ->
            val angle = angleFor(index, count)
            val pointRadius = radius * value / 100f
            val x = centerX + cos(angle) * pointRadius
            val y = centerY + sin(angle) * pointRadius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    private fun drawPolygon(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        count: Int,
        paint: Paint
    ) {
        polygonPath.reset()
        repeat(count) { index ->
            val angle = angleFor(index, count)
            val x = centerX + cos(angle) * radius
            val y = centerY + sin(angle) * radius
            if (index == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
        }
        polygonPath.close()
        canvas.drawPath(polygonPath, paint)
    }

    private fun angleFor(index: Int, count: Int): Float {
        return (-PI / 2 + 2 * PI * index / count).toFloat()
    }

    private fun updateAccessibilityDescription() {
        contentDescription = if (shadowStatus == ConfidenceShadowStatus.CLEAR) {
            SignalFactor.values().joinToString(
                prefix = "Карта подтвержденного сигнала. ",
                separator = ". "
            ) { factor ->
                "${factor.title}: ${assessment.value(factor)} из 100"
            }
        } else {
            SignalFactor.values().joinToString(
                prefix = "Тень уверенности. Пунктиром исходная оценка, сплошным контуром подтвержденная. ",
                separator = ". "
            ) { factor ->
                "${factor.title}: оценка ${claimedAssessment.value(factor)}, подтверждено ${assessment.value(factor)}"
            }
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            resources.displayMetrics
        )
    }
}
