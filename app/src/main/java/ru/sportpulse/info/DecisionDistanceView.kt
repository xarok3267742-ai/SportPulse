package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class DecisionDistanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private val statePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private val gateRect = RectF()
    private val answers = IntArray(
        DecisionDistanceFactor.values().size
    ) { DecisionDistanceAnswer.UNANSWERED.ordinal }
    private val answerValues = DecisionDistanceAnswer.values()
    private val numberLabels = arrayOf("1", "2", "3", "4")
    private val stateLabels = arrayOf("—", "НЕТ", "ДА")
    private var hasResult = false

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: DecisionDistanceResult) {
        result.assessment.answers.forEachIndexed { index, answer ->
            answers[index] = answer.ordinal
        }
        hasResult = true
        contentDescription = buildString {
            append("Контур дистанции. ")
            DecisionDistanceFactor.values().forEachIndexed {
                    index,
                    factor ->
                if (index > 0) append(" ")
                append(index + 1)
                append(": ")
                append(factor.question)
                append(" ")
                append(
                    when (result.assessment.answer(factor)) {
                        DecisionDistanceAnswer.UNANSWERED ->
                            "нет ответа."
                        DecisionDistanceAnswer.NO -> "нет."
                        DecisionDistanceAnswer.YES -> "да."
                    }
                )
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
            resolveSize(dp(118f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!hasResult) return
        val count = answers.size
        val left = dp(18f)
        val right = width - dp(18f)
        val centerY = dp(48f)
        val step = (right - left) / (count - 1).coerceAtLeast(1)

        linePaint.color = AppColors.line
        linePaint.strokeWidth = dp(5f)
        canvas.drawLine(left, centerY, right, centerY, linePaint)

        answers.indices.forEach { index ->
            val answer = answerValues[
                answers[index]
            ]
            val color = when (answer) {
                DecisionDistanceAnswer.UNANSWERED -> AppColors.muted
                DecisionDistanceAnswer.NO -> AppColors.accent
                DecisionDistanceAnswer.YES -> AppColors.danger
            }
            val x = left + step * index
            gateRect.set(
                x - dp(18f),
                centerY - dp(28f),
                x + dp(18f),
                centerY + dp(28f)
            )
            fillPaint.color = AppColors.surface
            canvas.drawRoundRect(
                gateRect,
                dp(6f),
                dp(6f),
                fillPaint
            )
            linePaint.color = color
            linePaint.strokeWidth = dp(3f)
            canvas.drawRoundRect(
                gateRect,
                dp(6f),
                dp(6f),
                linePaint
            )
            fillPaint.color = color
            canvas.drawCircle(x, centerY, dp(10f), fillPaint)

            numberPaint.color = AppColors.surface
            numberPaint.textSize = cappedSp(8.5f)
            canvas.drawText(
                numberLabels[index],
                x,
                textBaseline(numberPaint, centerY),
                numberPaint
            )

            statePaint.color = color
            statePaint.textSize = cappedSp(8.5f)
            canvas.drawText(
                stateLabels[answer.ordinal],
                x,
                dp(101f),
                statePaint
            )
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
