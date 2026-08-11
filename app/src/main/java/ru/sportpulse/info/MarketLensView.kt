package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

internal class MarketLensView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(8.5f)
        typeface = AppTypography.display(context, bold = true)
        color = AppColors.muted
        textAlign = Paint.Align.CENTER
    }
    private val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(10f)
        typeface = AppTypography.display(context, bold = true)
        color = AppColors.ink
        textAlign = Paint.Align.LEFT
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.5f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val cell = RectF()
    private val rowRect = RectF()

    private var lens: MarketLensResult? = null
    private var selectedKind: MarketKind =
        MarketKind.ONE_X_TWO
    private var onMarketSelected:
        ((MarketKind) -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setOnMarketSelectedListener(
        listener: (MarketKind) -> Unit
    ) {
        onMarketSelected = listener
    }

    fun setLens(
        value: MarketLensResult,
        selected: MarketKind
    ) {
        lens = value
        selectedKind = selected
        contentDescription =
            accessibilityDescription(value, selected)
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(300f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = lens ?: return
        val left = dp(10f)
        val labelRight = dp(79f)
        val matrixLeft = dp(88f)
        val matrixRight = width - dp(10f)
        val columnWidth =
            (matrixRight - matrixLeft) /
                SignalFactor.values().size
        val headers = listOf(
            "ФОР",
            "СОС",
            "НАГ",
            "КОН",
            "ИСТ"
        )
        headers.forEachIndexed { index, title ->
            canvas.drawText(
                title,
                matrixLeft +
                    columnWidth * index +
                    columnWidth / 2f,
                dp(26f),
                headerPaint
            )
        }

        value.items.forEachIndexed { index, item ->
            val top = dp(38f) + index * dp(42f)
            val bottom = top + dp(36f)
            rowRect.set(left, top, matrixRight, bottom)
            if (item.guide.kind == selectedKind) {
                fillPaint.color = AppColors.signalSoft
                canvas.drawRoundRect(
                    rowRect,
                    dp(7f),
                    dp(7f),
                    fillPaint
                )
            }
            fillPaint.color = statusColor(item.status)
            canvas.drawRoundRect(
                left,
                top + dp(5f),
                left + dp(4f),
                bottom - dp(5f),
                dp(2f),
                dp(2f),
                fillPaint
            )
            rowPaint.color = if (
                item.status ==
                MarketLensStatus.NOT_APPLICABLE
            ) {
                AppColors.muted
            } else {
                AppColors.ink
            }
            canvas.drawText(
                item.guide.kind.shortTitle,
                left + dp(10f),
                top + dp(23f) -
                    (
                        rowPaint.ascent() +
                            rowPaint.descent()
                        ) / 2f,
                rowPaint
            )
            SignalFactor.values().forEachIndexed {
                    factorIndex,
                    factor ->
                val coverage = item.factor(factor)
                val centerX = matrixLeft +
                    columnWidth * factorIndex +
                    columnWidth / 2f
                val centerY = (top + bottom) / 2f
                val size = min(
                    dp(25f),
                    columnWidth - dp(7f)
                )
                cell.set(
                    centerX - size / 2f,
                    centerY - size / 2f,
                    centerX + size / 2f,
                    centerY + size / 2f
                )
                if (
                    item.status ==
                    MarketLensStatus.NOT_APPLICABLE ||
                    !coverage.required
                ) {
                    valuePaint.color = AppColors.line
                    canvas.drawText(
                        "—",
                        centerX,
                        centerY -
                            (
                                valuePaint.ascent() +
                                    valuePaint.descent()
                                ) / 2f,
                        valuePaint
                    )
                } else {
                    val tone = factorTone(coverage.state)
                    fillPaint.color = tone.background
                    canvas.drawRoundRect(
                        cell,
                        dp(6f),
                        dp(6f),
                        fillPaint
                    )
                    if (coverage.critical) {
                        borderPaint.color = tone.foreground
                        canvas.drawRoundRect(
                            cell,
                            dp(6f),
                            dp(6f),
                            borderPaint
                        )
                    }
                    valuePaint.color = tone.foreground
                    canvas.drawText(
                        evidenceCount(coverage.evidence).toString(),
                        centerX,
                        centerY -
                            (
                                valuePaint.ascent() +
                                    valuePaint.descent()
                                ) / 2f,
                        valuePaint
                    )
                }
            }
        }

        headerPaint.color = AppColors.muted
        canvas.drawText(
            "0/1/2 = источники • рамка = критический",
            (labelRight + matrixRight) / 2f,
            height - dp(6f),
            headerPaint
        )
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (!isEnabled) return false
        if (event.action == MotionEvent.ACTION_UP) {
            val index = (
                (event.y - dp(38f)) /
                    dp(42f)
                ).toInt()
            val item = lens?.items?.getOrNull(index)
            if (item != null) {
                selectedKind = item.guide.kind
                contentDescription =
                    lens?.let {
                        accessibilityDescription(
                            it,
                            selectedKind
                        )
                    }
                invalidate()
                performClick()
                onMarketSelected?.invoke(selectedKind)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun statusColor(
        status: MarketLensStatus
    ): Int {
        return when (status) {
            MarketLensStatus.NOT_APPLICABLE ->
                AppColors.line
            MarketLensStatus.CLOSED ->
                AppColors.danger
            MarketLensStatus.CHECK ->
                AppColors.warning
            MarketLensStatus.COVERED ->
                AppColors.accent
        }
    }

    private fun factorTone(
        state: MarketFactorState
    ): Tone {
        return when (state) {
            MarketFactorState.UNUSED ->
                Tone(AppColors.muted, AppColors.background)
            MarketFactorState.BLOCKED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            MarketFactorState.PARTIAL ->
                Tone(AppColors.warning, AppColors.warningSoft)
            MarketFactorState.COVERED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun accessibilityDescription(
        value: MarketLensResult,
        selected: MarketKind
    ): String {
        return buildString {
            append("Чек-листы рынков. ")
            value.items.forEach { item ->
                append(item.guide.title)
                append(": ")
                append(
                    when (item.status) {
                        MarketLensStatus.NOT_APPLICABLE ->
                            "не применяется"
                        MarketLensStatus.CLOSED ->
                            "нет критических подтверждений"
                        MarketLensStatus.CHECK ->
                            "нужна сверка"
                        MarketLensStatus.COVERED ->
                            "чек-лист заполнен"
                    }
                )
                if (item.guide.kind == selected) {
                    append(", выбрано")
                }
                append(". ")
            }
        }
    }

    private fun evidenceCount(level: EvidenceLevel): Int {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> 0
            EvidenceLevel.SINGLE_SOURCE -> 1
            EvidenceLevel.QUORUM -> 2
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

    private data class Tone(
        val foreground: Int,
        val background: Int
    )
}
