package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class CounterViewBalanceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
        strokeCap = Paint.Cap.ROUND
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val factorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.2f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.LEFT
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.4f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val mirror = Path()
    private val rowRect = RectF()
    private var result: CounterViewResult? = null

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: CounterViewResult) {
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
            resolveSize(dp(178f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = result ?: return
        val mirrorX = width * 0.55f
        val headerY = dp(15f)
        val rowsTop = dp(30f)
        val rowHeight = (height - rowsTop - dp(4f)) /
            current.factors.size
        val barLeft = dp(61f)
        val barRight = mirrorX - dp(25f)
        val valueX = mirrorX - dp(11f)
        val reviewX = width - dp(24f)

        titlePaint.color = AppColors.muted
        canvas.drawText(
            "ИСХОДНАЯ ВЕРСИЯ",
            (barLeft + barRight) / 2f,
            headerY,
            titlePaint
        )
        canvas.drawText(
            "КОНТРПРОВЕРКА",
            (mirrorX + reviewX) / 2f,
            headerY,
            titlePaint
        )

        drawMirror(
            canvas = canvas,
            centerX = mirrorX,
            top = rowsTop - dp(5f),
            bottom = height - dp(3f)
        )

        current.factors.forEachIndexed { index, factor ->
            val centerY = rowsTop + rowHeight * index +
                rowHeight / 2f
            if (factor.factor == current.nextFactor) {
                rowRect.set(
                    0f,
                    centerY - rowHeight / 2f + dp(2f),
                    width.toFloat(),
                    centerY + rowHeight / 2f - dp(2f)
                )
                fillPaint.color = AppColors.warningSoft
                canvas.drawRoundRect(
                    rowRect,
                    dp(6f),
                    dp(6f),
                    fillPaint
                )
            }

            factorPaint.color = AppColors.ink
            canvas.drawText(
                factor.factor.shortTitle,
                dp(3f),
                textBaseline(factorPaint, centerY),
                factorPaint
            )

            strokePaint.color = AppColors.line
            strokePaint.strokeWidth = dp(3f)
            canvas.drawLine(
                barLeft,
                centerY,
                barRight,
                centerY,
                strokePaint
            )
            val progressX = barLeft +
                (barRight - barLeft) *
                factor.supportedValue / 100f
            strokePaint.color = AppColors.accent
            canvas.drawLine(
                barLeft,
                centerY,
                progressX,
                centerY,
                strokePaint
            )
            fillPaint.color = AppColors.accentDark
            canvas.drawCircle(
                progressX,
                centerY,
                dp(4.5f),
                fillPaint
            )

            valuePaint.color = AppColors.ink
            canvas.drawText(
                factor.supportedValue.toString(),
                valueX,
                textBaseline(valuePaint, centerY),
                valuePaint
            )

            val tone = reviewTone(factor.reviewState)
            strokePaint.color = tone.foreground
            strokePaint.strokeWidth = dp(1.4f)
            canvas.drawLine(
                mirrorX + dp(11f),
                centerY,
                reviewX - dp(10f),
                centerY,
                strokePaint
            )
            fillPaint.color = tone.background
            canvas.drawCircle(
                reviewX,
                centerY,
                dp(10f),
                fillPaint
            )
            strokePaint.color = tone.foreground
            canvas.drawCircle(
                reviewX,
                centerY,
                dp(10f),
                strokePaint
            )
            valuePaint.color = tone.foreground
            canvas.drawText(
                factor.reviewState.marker,
                reviewX,
                textBaseline(valuePaint, centerY),
                valuePaint
            )
        }
    }

    private fun drawMirror(
        canvas: Canvas,
        centerX: Float,
        top: Float,
        bottom: Float
    ) {
        mirror.reset()
        mirror.moveTo(centerX, top)
        mirror.lineTo(centerX + dp(7f), top + dp(7f))
        mirror.lineTo(centerX + dp(7f), bottom - dp(7f))
        mirror.lineTo(centerX, bottom)
        mirror.lineTo(centerX - dp(7f), bottom - dp(7f))
        mirror.lineTo(centerX - dp(7f), top + dp(7f))
        mirror.close()
        fillPaint.color = AppColors.signalSoft
        canvas.drawPath(mirror, fillPaint)
        strokePaint.color = AppColors.signal
        strokePaint.strokeWidth = dp(1f)
        canvas.drawPath(mirror, strokePaint)
    }

    private fun accessibilityDescription(
        value: CounterViewResult
    ): String {
        val factors = value.factors.joinToString(". ") {
            "${it.factor.title}: поддержано ${
                it.supportedValue
            } из 100; ${reviewSpokenLabel(it.reviewState)}"
        }
        return "Контрракурс. ${
            verdictSpokenLabel(value.verdict)
        }. Проверено ${value.reviewedCount} из 5. Максимальный вывод: ${
            decisionSpokenLabel(value.decisionCeiling)
        }. $factors."
    }

    private fun reviewTone(state: CounterReviewState): Tone {
        return when (state) {
            CounterReviewState.UNCHECKED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CounterReviewState.CLEAR ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CounterReviewState.MIXED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CounterReviewState.REFUTED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun reviewSpokenLabel(
        state: CounterReviewState
    ): String {
        return when (state) {
            CounterReviewState.UNCHECKED ->
                "альтернативная версия не проверена"
            CounterReviewState.CLEAR ->
                "контрверсия проверена"
            CounterReviewState.MIXED ->
                "есть спорные факты"
            CounterReviewState.REFUTED ->
                "найден контрфакт"
        }
    }

    private fun verdictSpokenLabel(
        verdict: CounterViewVerdict
    ): String {
        return when (verdict) {
            CounterViewVerdict.OPEN ->
                "проверка не завершена"
            CounterViewVerdict.BALANCED ->
                "альтернативная версия проверена"
            CounterViewVerdict.MIXED ->
                "обнаружены спорные факты"
            CounterViewVerdict.REFUTED ->
                "обнаружен контрфакт"
        }
    }

    private fun decisionSpokenLabel(
        decision: SavedDecision
    ): String {
        return when (decision) {
            SavedDecision.SKIP -> "пропустить"
            SavedDecision.OBSERVE -> "наблюдать"
            SavedDecision.DATA_READY -> "факты сверены"
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

    private data class Tone(
        val foreground: Int,
        val background: Int
    )
}
