package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.min

internal class ChronoLensView @JvmOverloads constructor(
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
        textSize = cappedSp(8.6f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.2f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.RIGHT
    }
    private val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(7.6f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val rowRect = RectF()
    private var result: ChronoLensResult? = null

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: ChronoLensResult) {
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
            resolveSize(dp(220f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = result ?: return
        val left = dp(70f)
        val right = width - dp(34f)
        val rowsTop = dp(27f)
        val rowHeight = dp(31f)
        val range = (
            current.horizonAt - current.now
            ).coerceAtLeast(1L)
        val selectedX = timeX(
            at = current.selectedAt,
            now = current.now,
            range = range,
            left = left,
            right = right
        )

        labelPaint.color = AppColors.accentDark
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "СЕЙЧАС",
            left,
            dp(16f),
            labelPaint
        )
        labelPaint.color = AppColors.muted
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "+${FreshnessFormatter.duration(range)}",
            right,
            dp(16f),
            labelPaint
        )

        SignalFactor.values().forEachIndexed { index, factor ->
            val top = rowsTop + rowHeight * index
            val centerY = top + rowHeight / 2f
            if (index % 2 == 0) {
                rowRect.set(
                    0f,
                    top,
                    width.toFloat(),
                    top + rowHeight
                )
                fillPaint.color = AppColors.background
                canvas.drawRoundRect(
                    rowRect,
                    dp(5f),
                    dp(5f),
                    fillPaint
                )
            }

            labelPaint.textAlign = Paint.Align.LEFT
            labelPaint.color = AppColors.muted
            canvas.drawText(
                factor.shortTitle.uppercase(
                    Locale.forLanguageTag("ru-RU")
                ),
                dp(5f),
                textBaseline(labelPaint, centerY),
                labelPaint
            )

            linePaint.strokeWidth = dp(3f)
            linePaint.color = AppColors.line
            canvas.drawLine(
                left,
                centerY,
                right,
                centerY,
                linePaint
            )
            linePaint.color = AppColors.signalSoft
            canvas.drawLine(
                left,
                centerY,
                selectedX,
                centerY,
                linePaint
            )

            current.checkpoints.forEach { checkpoint ->
                checkpoint.changes
                    .filter { it.factor == factor }
                    .forEach { change ->
                        val x = timeX(
                            at = checkpoint.at,
                            now = current.now,
                            range = range,
                            left = left,
                            right = right
                        )
                        when (change.kind) {
                            ChronoLensChangeKind.EXPIRING -> {
                                fillPaint.color =
                                    AppColors.warning
                                canvas.drawCircle(
                                    x,
                                    centerY,
                                    dp(3.2f),
                                    fillPaint
                                )
                            }
                            ChronoLensChangeKind.LEVEL_DROP -> {
                                linePaint.color =
                                    AppColors.danger
                                linePaint.strokeWidth = dp(2f)
                                canvas.drawLine(
                                    x,
                                    centerY - dp(6f),
                                    x,
                                    centerY + dp(6f),
                                    linePaint
                                )
                            }
                        }
                    }
            }

            valuePaint.color = evidenceColor(
                current.selected.freshness
                    .effectiveEvidence.level(factor)
            )
            canvas.drawText(
                levelMarker(
                    current.selected.freshness
                        .effectiveEvidence.level(factor)
                ),
                width - dp(5f),
                textBaseline(valuePaint, centerY),
                valuePaint
            )
        }

        linePaint.color = AppColors.signal
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(
            selectedX,
            rowsTop - dp(5f),
            selectedX,
            rowsTop + rowHeight *
                SignalFactor.values().size + dp(2f),
            linePaint
        )
        fillPaint.color = AppColors.signal
        canvas.drawCircle(
            selectedX,
            rowsTop - dp(5f),
            dp(4f),
            fillPaint
        )

        drawLegend(
            canvas = canvas,
            x = dp(6f),
            color = AppColors.warning,
            label = "СКОРО"
        )
        drawLegend(
            canvas = canvas,
            x = dp(91f),
            color = AppColors.danger,
            label = "СНИЖЕНИЕ"
        )
        drawLegend(
            canvas = canvas,
            x = dp(211f),
            color = AppColors.signal,
            label = "ВЫБРАНО"
        )
    }

    private fun drawLegend(
        canvas: Canvas,
        x: Float,
        color: Int,
        label: String
    ) {
        val y = height - dp(9f)
        fillPaint.color = color
        canvas.drawCircle(x + dp(3f), y - dp(2f), dp(2.7f), fillPaint)
        legendPaint.color = AppColors.muted
        legendPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            label,
            x + dp(10f),
            y,
            legendPaint
        )
    }

    private fun accessibilityDescription(
        value: ChronoLensResult
    ): String {
        val offset = value.selectedAt - value.now
        val selectedMoment = if (offset == 0L) {
            "текущий момент"
        } else {
            "момент через ${
                FreshnessFormatter.duration(offset)
            }"
        }
        val changed = if (value.changedFactors.isEmpty()) {
            "Уровни подтверждения не изменились"
        } else {
            "Снизятся факторы: ${
                value.changedFactors.joinToString(", ") {
                    it.shortTitle
                }
            }"
        }
        val guard = value.selected.decisionGuard?.let {
            "Стоп-контракт: ${
                when (it.status) {
                    DecisionGuardStatus.SEALED_SKIP ->
                        "закрыт выводом пропустить"
                    DecisionGuardStatus.ARMED ->
                        "не сработает"
                    DecisionGuardStatus.TRIGGERED ->
                        "сработает"
                }
            }."
        } ?: "Стоп-контракт не зафиксирован."
        return "Хронолинза. Выбран $selectedMoment. " +
            "Полнота ${value.baseline.readiness} станет ${
            value.selected.readiness
        }. Статус ${spokenVerdict(value.selected.verdict)}. " +
            "$changed. Покрыто рынков ${
                value.baseline.marketLens.coveredCount
            } станет ${
                value.selected.marketLens.coveredCount
            }. $guard Это симуляция срока данных, а не исхода события."
    }

    private fun spokenVerdict(
        verdict: SignalVerdict
    ): String {
        return when (verdict) {
            SignalVerdict.SKIP -> "пропустить"
            SignalVerdict.OBSERVE -> "наблюдать"
            SignalVerdict.READY -> "факты сверены"
        }
    }

    private fun evidenceColor(
        level: EvidenceLevel
    ): Int {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> AppColors.danger
            EvidenceLevel.SINGLE_SOURCE -> AppColors.warning
            EvidenceLevel.QUORUM -> AppColors.accent
        }
    }

    private fun levelMarker(
        level: EvidenceLevel
    ): String {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> "0"
            EvidenceLevel.SINGLE_SOURCE -> "1"
            EvidenceLevel.QUORUM -> "2+"
        }
    }

    private fun timeX(
        at: Long,
        now: Long,
        range: Long,
        left: Float,
        right: Float
    ): Float {
        val ratio = (
            (at - now).coerceIn(0L, range).toDouble() /
                range.toDouble()
            ).toFloat()
        return left + (right - left) * ratio
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
