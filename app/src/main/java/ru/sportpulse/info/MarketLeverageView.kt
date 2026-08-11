package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class MarketLeverageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.25f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(8.4f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(10.5f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val tile = RectF()
    private var leverage: MarketLeverageResult? = null

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setLeverage(
        value: MarketLeverageResult
    ) {
        leverage = value
        contentDescription =
            accessibilityDescription(value)
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(
                dp(320f).toInt(),
                widthMeasureSpec
            ),
            resolveSize(
                dp(96f).toInt(),
                heightMeasureSpec
            )
        )
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)
        val value = leverage ?: return
        val items = value.baseline.items
        val impacts = value.impacts.associateBy(
            MarketLeverageImpact::kind
        )
        val side = dp(4f)
        val gap = dp(4f)
        val tileWidth = (
            width - side * 2f -
                gap * (items.size - 1)
            ) / items.size
        val top = dp(6f)
        val bottom = height - dp(6f)

        items.forEachIndexed { index, item ->
            val left = side +
                index * (tileWidth + gap)
            val right = left + tileWidth
            tile.set(left, top, right, bottom)
            val impact = impacts[item.guide.kind]
            val currentTone = statusTone(item.status)

            fillPaint.color = when {
                item.status ==
                    MarketLensStatus.NOT_APPLICABLE ->
                    AppColors.background
                impact == null ->
                    AppColors.surface
                else ->
                    currentTone.background
            }
            canvas.drawRoundRect(
                tile,
                dp(6f),
                dp(6f),
                fillPaint
            )

            if (
                impact != null &&
                impact.statusChanged
            ) {
                val save = canvas.save()
                canvas.clipRect(
                    tile.centerX(),
                    tile.top,
                    tile.right,
                    tile.bottom
                )
                fillPaint.color = statusTone(
                    impact.projectedStatus
                ).background
                canvas.drawRoundRect(
                    tile,
                    dp(6f),
                    dp(6f),
                    fillPaint
                )
                canvas.restoreToCount(save)
            }

            borderPaint.color = when {
                impact == null -> AppColors.line
                value.mode ==
                    MarketLeverageMode.MAINTAIN ->
                    AppColors.accent
                else -> AppColors.warning
            }
            canvas.drawRoundRect(
                tile,
                dp(6f),
                dp(6f),
                borderPaint
            )

            labelPaint.color = when {
                item.status ==
                    MarketLensStatus.NOT_APPLICABLE ->
                    AppColors.muted
                impact == null ->
                    AppColors.muted
                else ->
                    AppColors.ink
            }
            canvas.drawText(
                item.guide.kind.shortTitle,
                tile.centerX(),
                top + dp(23f),
                labelPaint
            )

            if (impact == null) {
                metricPaint.color = AppColors.line
                canvas.drawText(
                    "—",
                    tile.centerX(),
                    top + dp(58f),
                    metricPaint
                )
            } else if (impact.statusChanged) {
                drawTransition(
                    canvas = canvas,
                    impact = impact,
                    centerX = tile.centerX(),
                    centerY = top + dp(49f)
                )
                drawGain(
                    canvas,
                    impact.conditionGain,
                    tile.centerX(),
                    top + dp(73f)
                )
            } else {
                metricPaint.color = if (
                    value.mode ==
                    MarketLeverageMode.MAINTAIN
                ) {
                    AppColors.accentDark
                } else {
                    AppColors.warning
                }
                canvas.drawText(
                    if (
                        value.mode ==
                        MarketLeverageMode.MAINTAIN
                    ) {
                        "TTL"
                    } else {
                        "+${impact.conditionGain}"
                    },
                    tile.centerX(),
                    top + dp(59f),
                    metricPaint
                )
            }
        }
    }

    private fun drawTransition(
        canvas: Canvas,
        impact: MarketLeverageImpact,
        centerX: Float,
        centerY: Float
    ) {
        val offset = dp(7f)
        val radius = dp(4.5f)
        borderPaint.color = AppColors.muted
        borderPaint.strokeWidth = dp(1f)
        canvas.drawLine(
            centerX - offset + radius,
            centerY,
            centerX + offset - radius,
            centerY,
            borderPaint
        )
        fillPaint.color = statusTone(
            impact.currentStatus
        ).foreground
        canvas.drawCircle(
            centerX - offset,
            centerY,
            radius,
            fillPaint
        )
        fillPaint.color = statusTone(
            impact.projectedStatus
        ).foreground
        canvas.drawCircle(
            centerX + offset,
            centerY,
            radius,
            fillPaint
        )
        borderPaint.strokeWidth = dp(1.25f)
    }

    private fun drawGain(
        canvas: Canvas,
        gain: Int,
        centerX: Float,
        baseline: Float
    ) {
        metricPaint.color = AppColors.warning
        metricPaint.textSize = cappedSp(8.5f)
        canvas.drawText(
            "+$gain",
            centerX,
            baseline,
            metricPaint
        )
        metricPaint.textSize = cappedSp(10.5f)
    }

    private fun statusTone(
        status: MarketLensStatus
    ): Tone {
        return when (status) {
            MarketLensStatus.NOT_APPLICABLE ->
                Tone(
                    AppColors.muted,
                    AppColors.background
                )
            MarketLensStatus.CLOSED ->
                Tone(
                    AppColors.danger,
                    AppColors.dangerSoft
                )
            MarketLensStatus.CHECK ->
                Tone(
                    AppColors.warning,
                    AppColors.warningSoft
                )
            MarketLensStatus.COVERED ->
                Tone(
                    AppColors.accentDark,
                    AppColors.accentSoft
                )
        }
    }

    private fun accessibilityDescription(
        value: MarketLeverageResult
    ): String {
        return buildString {
            append("Следующая проверка: ")
            append(value.factor.title)
            append(". ")
            if (
                value.mode ==
                MarketLeverageMode.MAINTAIN
            ) {
                append("Обслуживание свежести. ")
            } else {
                append("Контрсценарий проверки. ")
            }
            value.impacts.forEach { impact ->
                append(
                    value.baseline
                        .item(impact.kind)
                        ?.guide
                        ?.title
                        ?: impact.kind.shortTitle
                )
                append(": ")
                append(
                    statusTitle(
                        impact.currentStatus
                    )
                )
                if (impact.statusChanged) {
                    append(", станет ")
                    append(
                        statusTitle(
                            impact.projectedStatus
                        )
                    )
                }
                if (impact.conditionGain > 0) {
                    append(", плюс ")
                    append(impact.conditionGain)
                    append(" ")
                    append(
                        conditionWord(
                            impact.conditionGain
                        )
                    )
                }
                append(". ")
            }
        }
    }

    private fun conditionWord(
        count: Int
    ): String {
        val lastTwo = count % 100
        val last = count % 10
        return when {
            lastTwo in 11..14 -> "условий"
            last == 1 -> "условие"
            last in 2..4 -> "условия"
            else -> "условий"
        }
    }

    private fun statusTitle(
        status: MarketLensStatus
    ): String {
        return when (status) {
            MarketLensStatus.NOT_APPLICABLE ->
                "не применяется"
            MarketLensStatus.CLOSED ->
                "данные закрыты"
            MarketLensStatus.CHECK ->
                "нужна проверка"
            MarketLensStatus.COVERED ->
                "данные покрыты"
        }
    }

    private fun dp(
        value: Float
    ): Float {
        return value *
            resources.displayMetrics.density
    }

    private fun cappedSp(
        value: Float
    ): Float {
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
