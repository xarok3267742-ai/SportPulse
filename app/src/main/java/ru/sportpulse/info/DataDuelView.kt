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

internal class DataDuelView @JvmOverloads constructor(
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
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.4f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.2f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(10f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val laneRect = RectF()
    private var result: DataDuelResult? = null
    private var leftTitle: String = "Слева"
    private var rightTitle: String = "Справа"

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(
        value: DataDuelResult,
        leftTitle: String,
        rightTitle: String
    ) {
        result = value
        this.leftTitle = leftTitle
        this.rightTitle = rightTitle
        contentDescription = accessibilityDescription(value)
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(238f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = result ?: return
        val centerX = width / 2f
        val sideMargin = dp(8f)
        val centerGap = dp(33f)
        val leftStart = sideMargin
        val leftEnd = centerX - centerGap
        val rightStart = centerX + centerGap
        val rightEnd = width - sideMargin
        val rowsTop = dp(27f)
        val rowHeight = (height - rowsTop - dp(3f)) /
            current.metrics.size

        headerPaint.color = AppColors.accentDark
        headerPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            "СЛЕВА",
            sideMargin,
            dp(16f),
            headerPaint
        )
        headerPaint.color = AppColors.signal
        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            "СПРАВА",
            width - sideMargin,
            dp(16f),
            headerPaint
        )

        linePaint.color = AppColors.line
        linePaint.strokeWidth = dp(1f)
        canvas.drawLine(
            centerX,
            rowsTop - dp(4f),
            centerX,
            height - dp(2f),
            linePaint
        )

        current.metrics.forEachIndexed { index, metric ->
            val top = rowsTop + rowHeight * index
            val centerY = top + rowHeight / 2f
            if (index % 2 == 0) {
                laneRect.set(
                    0f,
                    top + dp(1f),
                    width.toFloat(),
                    top + rowHeight - dp(1f)
                )
                fillPaint.color = AppColors.background
                canvas.drawRoundRect(
                    laneRect,
                    dp(5f),
                    dp(5f),
                    fillPaint
                )
            }

            titlePaint.color = AppColors.muted
            canvas.drawText(
                metric.kind.title.uppercase(
                    Locale.forLanguageTag("ru-RU")
                ),
                centerX,
                textBaseline(
                    titlePaint,
                    centerY - dp(5f)
                ),
                titlePaint
            )

            val barY = centerY + dp(8f)
            linePaint.strokeWidth = dp(4f)
            linePaint.color = AppColors.line
            canvas.drawLine(
                leftStart,
                barY,
                leftEnd,
                barY,
                linePaint
            )
            canvas.drawLine(
                rightStart,
                barY,
                rightEnd,
                barY,
                linePaint
            )

            val leftRatio = metric.leftValue.toFloat() /
                metric.visualMaximum
            val rightRatio = metric.rightValue.toFloat() /
                metric.visualMaximum
            linePaint.color = if (
                metric.leader == DataDuelSide.RIGHT
            ) {
                AppColors.muted
            } else {
                AppColors.accent
            }
            canvas.drawLine(
                leftEnd,
                barY,
                leftEnd - (leftEnd - leftStart) * leftRatio,
                barY,
                linePaint
            )
            linePaint.color = if (
                metric.leader == DataDuelSide.LEFT
            ) {
                AppColors.muted
            } else {
                AppColors.signal
            }
            canvas.drawLine(
                rightStart,
                barY,
                rightStart + (rightEnd - rightStart) * rightRatio,
                barY,
                linePaint
            )

            valuePaint.textAlign = Paint.Align.LEFT
            valuePaint.color = if (
                metric.leader == DataDuelSide.RIGHT
            ) {
                AppColors.muted
            } else {
                AppColors.accentDark
            }
            canvas.drawText(
                displayValue(metric.kind, metric.leftValue),
                leftStart,
                textBaseline(valuePaint, centerY - dp(5f)),
                valuePaint
            )
            valuePaint.textAlign = Paint.Align.RIGHT
            valuePaint.color = if (
                metric.leader == DataDuelSide.LEFT
            ) {
                AppColors.muted
            } else {
                AppColors.signal
            }
            canvas.drawText(
                displayValue(metric.kind, metric.rightValue),
                rightEnd,
                textBaseline(valuePaint, centerY - dp(5f)),
                valuePaint
            )

            fillPaint.color = when (metric.leader) {
                DataDuelSide.LEFT -> AppColors.accent
                DataDuelSide.RIGHT -> AppColors.signal
                DataDuelSide.TIE -> AppColors.line
            }
            canvas.drawCircle(
                centerX,
                barY,
                dp(3.5f),
                fillPaint
            )
        }
    }

    private fun accessibilityDescription(
        value: DataDuelResult
    ): String {
        val lanes = value.metrics.joinToString(". ") { metric ->
            "${metric.kind.title}: $leftTitle ${
                spokenValue(metric.kind, metric.leftValue)
            }, $rightTitle ${
                spokenValue(metric.kind, metric.rightValue)
            }, ${leaderSpokenLabel(metric.leader)}"
        }
        return "Дуэль данных. $leftTitle и $rightTitle. " +
            "Счет дорожек ${value.leftWins}:${
                value.rightWins
            }, ничьих ${value.ties}. $lanes. " +
            "Сравнивается качество данных, а не исход события."
    }

    private fun displayValue(
        kind: DataDuelMetricKind,
        value: Int
    ): String {
        return when (kind) {
            DataDuelMetricKind.QUORUMS,
            DataDuelMetricKind.INDEPENDENCE,
            DataDuelMetricKind.COUNTERCHECKS ->
                "$value/5"
            DataDuelMetricKind.FRESHNESS_RESERVE ->
                if (value == 0) {
                    "0 мин"
                } else {
                    FreshnessFormatter.duration(
                        value * 60_000L
                    )
                }
            DataDuelMetricKind.READINESS,
            DataDuelMetricKind.CLARITY ->
                value.toString()
        }
    }

    private fun spokenValue(
        kind: DataDuelMetricKind,
        value: Int
    ): String {
        return when (kind) {
            DataDuelMetricKind.QUORUMS,
            DataDuelMetricKind.INDEPENDENCE,
            DataDuelMetricKind.COUNTERCHECKS ->
                "$value из 5"
            DataDuelMetricKind.FRESHNESS_RESERVE ->
                FreshnessFormatter.duration(
                    value * 60_000L
                )
            DataDuelMetricKind.READINESS,
            DataDuelMetricKind.CLARITY ->
                "$value из 100"
        }
    }

    private fun leaderSpokenLabel(
        leader: DataDuelSide
    ): String {
        return when (leader) {
            DataDuelSide.LEFT ->
                "преимущество слева"
            DataDuelSide.RIGHT ->
                "преимущество справа"
            DataDuelSide.TIE ->
                "равно"
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
