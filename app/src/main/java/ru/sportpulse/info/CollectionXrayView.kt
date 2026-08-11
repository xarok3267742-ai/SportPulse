package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class CollectionXrayView @JvmOverloads constructor(
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
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = AppTypography.display(context, bold = true)
    }
    private val cellBounds = RectF()
    private val fillBounds = RectF()
    private val eventPositions = FloatArray(CollectionXrayPolicy.MAX_EVENTS)
    private val factorPositions = FloatArray(SignalFactor.values().size)
    private var result: CollectionXrayResult? = null
    private var highlightedCell: CollectionXrayCell? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: CollectionXrayResult) {
        setResult(value, value.focus)
    }

    fun setResult(
        value: CollectionXrayResult,
        highlight: CollectionXrayCell?
    ) {
        require(value.isReady)
        require(
            highlight == null || value.entries.any { entry ->
                entry.eventId == highlight.eventId &&
                    entry.cells[highlight.factor.ordinal] == highlight
            }
        )
        result = value
        highlightedCell = highlight
        contentDescription = buildString {
            append("Рентген подборки. Событий: ")
            append(value.entries.size)
            append(". ")
            append(
                when (value.state) {
                    CollectionXrayState.CLEAR ->
                        "Оценки укладываются в текущие доказательные пределы."
                    CollectionXrayState.GAPS ->
                        "Есть доказательные разрывы без смены статуса."
                    CollectionXrayState.VERDICT_SHIFT ->
                        "Неподтвержденная часть меняет статус не менее одного события."
                    else -> error("Матрица недоступна")
                }
            )
            highlight?.let { focus ->
                val entry = value.entries.single {
                    it.eventId == focus.eventId
                }
                append(" Выделено: ")
                append(entry.match)
                append(", ")
                append(focus.factor.title)
                append(", ")
                append(focus.claimedScore)
                append(" до ")
                append(focus.supportedScore)
                append(".")
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
            resolveSize(dp(222f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = result ?: return
        val labelRight = dp(58f)
        val matrixRight = width - dp(10f)
        val matrixTop = dp(38f)
        val matrixBottom = height - dp(15f)
        val columnWidth = (matrixRight - labelRight) /
            value.entries.size
        val rowHeight = (matrixBottom - matrixTop) /
            SignalFactor.values().size
        value.entries.indices.forEach { index ->
            eventPositions[index] = labelRight +
                columnWidth * (index + 0.5f)
        }
        SignalFactor.values().indices.forEach { index ->
            factorPositions[index] = matrixTop +
                rowHeight * (index + 0.5f)
        }

        textPaint.textSize = cappedSp(9.5f)
        textPaint.color = AppColors.muted
        textPaint.textAlign = Paint.Align.CENTER
        value.entries.indices.forEach { index ->
            canvas.drawText(
                (index + 1).toString(),
                eventPositions[index],
                dp(25f),
                textPaint
            )
        }

        linePaint.strokeWidth = dp(1f)
        linePaint.color = AppColors.line
        SignalFactor.values().forEachIndexed { index, factor ->
            val y = factorPositions[index]
            canvas.drawLine(labelRight, y, matrixRight, y, linePaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = cappedSp(8.5f)
            textPaint.color = AppColors.muted
            canvas.drawText(
                factorCode(factor),
                labelRight - dp(8f),
                y + dp(3f),
                textPaint
            )
        }

        val cellSize = min(
            dp(24f),
            min(columnWidth * 0.66f, rowHeight * 0.68f)
        )
        value.entries.forEachIndexed { eventIndex, entry ->
            entry.cells.forEach { cell ->
                val x = eventPositions[eventIndex]
                val y = factorPositions[cell.factor.ordinal]
                drawCell(
                    canvas,
                    cell,
                    x,
                    y,
                    cellSize,
                    highlightedCell
                )
            }
        }
    }

    private fun drawCell(
        canvas: Canvas,
        cell: CollectionXrayCell,
        x: Float,
        y: Float,
        size: Float,
        focus: CollectionXrayCell?
    ) {
        val half = size / 2f
        cellBounds.set(x - half, y - half, x + half, y + half)
        fillPaint.color = AppColors.background
        canvas.drawRoundRect(cellBounds, dp(4f), dp(4f), fillPaint)

        val tone = cellColor(cell.state)
        val supportedHeight = size * cell.supportedScore / 100f
        if (supportedHeight > 0f) {
            fillBounds.set(
                cellBounds.left + dp(2f),
                cellBounds.bottom - supportedHeight + dp(1f),
                cellBounds.right - dp(2f),
                cellBounds.bottom - dp(2f)
            )
            fillPaint.color = AppColors.accentSoft
            canvas.drawRoundRect(fillBounds, dp(2f), dp(2f), fillPaint)
        }

        linePaint.strokeWidth = if (
            focus?.eventId == cell.eventId &&
            focus.factor == cell.factor
        ) {
            dp(3f)
        } else {
            dp(1.5f)
        }
        linePaint.color = tone
        canvas.drawRoundRect(cellBounds, dp(4f), dp(4f), linePaint)

        val claimedY = cellBounds.bottom -
            size * cell.claimedScore / 100f
        linePaint.strokeWidth = dp(2f)
        linePaint.color = tone
        canvas.drawLine(
            cellBounds.left + dp(3f),
            claimedY,
            cellBounds.right - dp(3f),
            claimedY,
            linePaint
        )
    }

    private fun cellColor(state: CollectionXrayCellState): Int {
        return when (state) {
            CollectionXrayCellState.SUPPORTED -> AppColors.accent
            CollectionXrayCellState.GAP -> AppColors.warning
            CollectionXrayCellState.CRITICAL -> AppColors.danger
        }
    }

    private fun factorCode(factor: SignalFactor): String {
        return when (factor) {
            SignalFactor.FORM -> "ФОРМА"
            SignalFactor.LINEUP -> "СОСТАВ"
            SignalFactor.LOAD -> "НАГР."
            SignalFactor.CONTEXT -> "КОНТ."
            SignalFactor.SOURCES -> "ИСТОЧ."
        }
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun cappedSp(value: Float): Float {
        val metrics = resources.displayMetrics
        return value * min(
            metrics.density * resources.configuration.fontScale,
            metrics.density * 1.2f
        )
    }
}
