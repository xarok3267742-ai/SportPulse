package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class DecisionLedgerView @JvmOverloads constructor(
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
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private val nodeRect = RectF()
    private val decisionColors = IntArray(MAX_VISIBLE_NODES) {
        AppColors.line
    }
    private val decisionFills = IntArray(MAX_VISIBLE_NODES) {
        AppColors.background
    }
    private var integrity = DecisionLedgerIntegrity.EMPTY
    private var visibleCount = 0
    private var totalCount = 0L
    private var hasAnchor = false
    private var totalLabel = "0 ЗАПИСЕЙ"
    private var statusLabel = "ПЕРВОЕ ЗВЕНО"

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: DecisionLedgerReadResult) {
        integrity = result.integrity
        val ledger = result.ledger
        visibleCount = ledger?.records
            ?.size
            ?.coerceAtMost(MAX_VISIBLE_NODES)
            ?: 0
        totalCount = ledger?.totalRecordCount ?: 0L
        hasAnchor = (ledger?.anchorSequence ?: 0L) > 0L
        totalLabel = "$totalCount ${recordWord(totalCount)}"
        statusLabel = when (result.integrity) {
            DecisionLedgerIntegrity.EMPTY -> "ПЕРВОЕ ЗВЕНО"
            DecisionLedgerIntegrity.INTACT ->
                if (hasAnchor) {
                    "ЯКОРЬ • $totalLabel"
                } else {
                    totalLabel
                }
            DecisionLedgerIntegrity.TAMPERED ->
                "РАЗРЫВ ЦЕПОЧКИ"
        }
        decisionColors.fill(AppColors.line)
        decisionFills.fill(AppColors.background)
        ledger?.records
            ?.takeLast(MAX_VISIBLE_NODES)
            ?.forEachIndexed { index, record ->
                decisionColors[index] = when (record.decision) {
                    SavedDecision.SKIP -> AppColors.danger
                    SavedDecision.OBSERVE -> AppColors.warning
                    SavedDecision.DATA_READY -> AppColors.accent
                }
                decisionFills[index] = when (record.decision) {
                    SavedDecision.SKIP -> AppColors.dangerSoft
                    SavedDecision.OBSERVE -> AppColors.warningSoft
                    SavedDecision.DATA_READY -> AppColors.accentSoft
                }
            }
        contentDescription = when (result.integrity) {
            DecisionLedgerIntegrity.EMPTY ->
                "Бортовой журнал решений пуст."
            DecisionLedgerIntegrity.INTACT ->
                "Цепочка Бортового журнала цела. Всего записей $totalCount. В проверяемом окне ${ledger?.records?.size ?: 0}."
            DecisionLedgerIntegrity.TAMPERED ->
                "Цепочка Бортового журнала нарушена. Новые записи заблокированы до явного сброса."
        }
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(112f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(8f)
        val right = width - dp(8f)
        val centerY = dp(50f)
        val nodeCount = when (integrity) {
            DecisionLedgerIntegrity.EMPTY -> 6
            DecisionLedgerIntegrity.INTACT ->
                visibleCount.coerceAtLeast(1)
            DecisionLedgerIntegrity.TAMPERED -> 6
        }
        val gap = dp(5f)
        val nodeWidth = (
            right - left - gap * (nodeCount - 1)
            ) / nodeCount
        val nodeHeight = dp(34f)

        linePaint.strokeWidth = dp(4f)
        linePaint.color = when (integrity) {
            DecisionLedgerIntegrity.EMPTY -> AppColors.line
            DecisionLedgerIntegrity.INTACT -> AppColors.accent
            DecisionLedgerIntegrity.TAMPERED -> AppColors.danger
        }
        if (integrity == DecisionLedgerIntegrity.TAMPERED) {
            val breakX = (left + right) / 2f
            canvas.drawLine(
                left,
                centerY,
                breakX - dp(13f),
                centerY,
                linePaint
            )
            canvas.drawLine(
                breakX + dp(13f),
                centerY,
                right,
                centerY,
                linePaint
            )
            linePaint.strokeWidth = dp(3f)
            canvas.drawLine(
                breakX - dp(7f),
                centerY - dp(8f),
                breakX + dp(7f),
                centerY + dp(8f),
                linePaint
            )
            canvas.drawLine(
                breakX + dp(7f),
                centerY - dp(8f),
                breakX - dp(7f),
                centerY + dp(8f),
                linePaint
            )
        } else {
            canvas.drawLine(
                left,
                centerY,
                right,
                centerY,
                linePaint
            )
        }

        repeat(nodeCount) { index ->
            val nodeLeft = left + index * (nodeWidth + gap)
            nodeRect.set(
                nodeLeft,
                centerY - nodeHeight / 2f,
                nodeLeft + nodeWidth,
                centerY + nodeHeight / 2f
            )
            fillPaint.color = when (integrity) {
                DecisionLedgerIntegrity.EMPTY -> AppColors.background
                DecisionLedgerIntegrity.INTACT ->
                    decisionFills[index]
                DecisionLedgerIntegrity.TAMPERED ->
                    if (index == nodeCount / 2) {
                        AppColors.dangerSoft
                    } else {
                        AppColors.background
                    }
            }
            canvas.drawRoundRect(
                nodeRect,
                dp(4f),
                dp(4f),
                fillPaint
            )
            linePaint.color = when (integrity) {
                DecisionLedgerIntegrity.EMPTY -> AppColors.muted
                DecisionLedgerIntegrity.INTACT ->
                    decisionColors[index]
                DecisionLedgerIntegrity.TAMPERED -> AppColors.danger
            }
            linePaint.strokeWidth = dp(1.5f)
            canvas.drawRoundRect(
                nodeRect,
                dp(4f),
                dp(4f),
                linePaint
            )
        }

        labelPaint.textSize = cappedSp(8.5f)
        labelPaint.color = when (integrity) {
            DecisionLedgerIntegrity.EMPTY -> AppColors.muted
            DecisionLedgerIntegrity.INTACT -> AppColors.accentDark
            DecisionLedgerIntegrity.TAMPERED -> AppColors.danger
        }
        canvas.drawText(
            statusLabel,
            (left + right) / 2f,
            dp(96f),
            labelPaint
        )
    }

    private fun recordWord(count: Long): String {
        val normalized = count % 100L
        return when {
            normalized in 11L..14L -> "ЗАПИСЕЙ"
            count % 10L == 1L -> "ЗАПИСЬ"
            count % 10L in 2L..4L -> "ЗАПИСИ"
            else -> "ЗАПИСЕЙ"
        }
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

    private companion object {
        const val MAX_VISIBLE_NODES = 8
    }
}
