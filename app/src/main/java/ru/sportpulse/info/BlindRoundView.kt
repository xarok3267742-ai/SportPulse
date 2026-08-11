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

internal class BlindRoundView @JvmOverloads constructor(
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
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private val bounds = RectF()
    private val selectorPath = Path()
    private val positions = FloatArray(FactExpressPolicy.MAX_EVENTS)
    private var session: BlindRoundSession? = null
    private var selectedToken: String? = null
    private var revealed = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setState(
        session: BlindRoundSession,
        selectedToken: String? = null,
        revealed: Boolean = false
    ) {
        require(
            selectedToken == null ||
                session.cards.any { it.token == selectedToken }
        )
        this.session = session
        this.selectedToken = selectedToken
        this.revealed = revealed
        contentDescription = buildString {
            append("Слепой раунд. Анонимных досье: ")
            append(session.cards.size)
            append(". ")
            if (selectedToken == null) {
                append("Названия команд скрыты, выбор еще не сделан.")
            } else {
                val code = session.cards.single {
                    it.token == selectedToken
                }.code
                append("Выбрано досье $code. ")
                append(
                    if (revealed) {
                        "События раскрыты."
                    } else {
                        "События еще скрыты."
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
            resolveSize(dp(300f).toInt(), widthMeasureSpec),
            resolveSize(dp(108f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = session ?: return
        val left = dp(30f)
        val gateCenter = width - dp(34f)
        val right = gateCenter - dp(48f)
        val y = dp(45f)
        val span = right - left
        value.cards.indices.forEach { index ->
            positions[index] = if (value.cards.size == 1) {
                left
            } else {
                left + span * index / (value.cards.size - 1)
            }
        }

        linePaint.strokeWidth = dp(7f)
        linePaint.color = AppColors.line
        canvas.drawLine(left, y, gateCenter, y, linePaint)
        linePaint.strokeWidth = dp(3f)
        linePaint.color = if (revealed) {
            AppColors.accent
        } else {
            AppColors.muted
        }
        canvas.drawLine(left, y, gateCenter, y, linePaint)

        value.cards.forEachIndexed { index, card ->
            val x = positions[index]
            val selected = card.token == selectedToken
            val tone = if (revealed) {
                stateColor(card.state)
            } else if (selected) {
                AppColors.accent
            } else {
                AppColors.muted
            }
            bounds.set(
                x - dp(20f),
                y - dp(15f),
                x + dp(20f),
                y + dp(15f)
            )
            fillPaint.color = if (revealed) {
                AppColors.surface
            } else {
                AppColors.ink
            }
            canvas.drawRoundRect(bounds, dp(6f), dp(6f), fillPaint)
            linePaint.strokeWidth = if (selected) dp(4f) else dp(2.5f)
            linePaint.color = tone
            canvas.drawRoundRect(bounds, dp(6f), dp(6f), linePaint)
            if (revealed) {
                fillPaint.color = tone
                canvas.drawCircle(x, y, dp(5f), fillPaint)
            } else {
                linePaint.strokeWidth = dp(2f)
                linePaint.color = ColorTokens.shutter
                canvas.drawLine(
                    x - dp(12f),
                    y,
                    x + dp(12f),
                    y,
                    linePaint
                )
            }
            if (selected) {
                selectorPath.reset()
                selectorPath.moveTo(x, y + dp(18f))
                selectorPath.lineTo(x - dp(6f), y + dp(27f))
                selectorPath.lineTo(x + dp(6f), y + dp(27f))
                selectorPath.close()
                fillPaint.color = AppColors.accent
                canvas.drawPath(selectorPath, fillPaint)
            }
            drawLabel(
                canvas,
                card.code,
                x,
                dp(88f),
                tone
            )
        }

        bounds.set(
            gateCenter - dp(21f),
            y - dp(23f),
            gateCenter + dp(21f),
            y + dp(23f)
        )
        fillPaint.color = AppColors.surface
        canvas.drawRoundRect(bounds, dp(8f), dp(8f), fillPaint)
        linePaint.strokeWidth = dp(4f)
        linePaint.color = if (revealed) {
            AppColors.accentDark
        } else {
            AppColors.signal
        }
        canvas.drawRoundRect(bounds, dp(8f), dp(8f), linePaint)
        if (revealed) {
            fillPaint.color = AppColors.accentDark
            canvas.drawCircle(gateCenter, y, dp(8f), fillPaint)
        } else {
            linePaint.strokeWidth = dp(3f)
            canvas.drawLine(
                gateCenter - dp(11f),
                y,
                gateCenter + dp(11f),
                y,
                linePaint
            )
        }
        drawLabel(
            canvas,
            "ФАКТ",
            gateCenter,
            dp(88f),
            if (revealed) AppColors.accentDark else AppColors.signal
        )
    }

    private fun stateColor(state: FactExpressEntryState): Int {
        return when (state) {
            FactExpressEntryState.ACTION_NOW -> AppColors.signal
            FactExpressEntryState.WAITING -> AppColors.warning
            FactExpressEntryState.UNSCHEDULED -> AppColors.danger
            FactExpressEntryState.COMPLETE -> AppColors.accentDark
        }
    }

    private fun drawLabel(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        color: Int
    ) {
        textPaint.color = color
        textPaint.textSize = cappedSp(10f)
        canvas.drawText(value, x, baseline, textPaint)
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun cappedSp(value: Float): Float {
        val metrics = resources.displayMetrics
        return value * min(
            metrics.density * resources.configuration.fontScale,
            metrics.density * 1.25f
        )
    }

    private object ColorTokens {
        val shutter = android.graphics.Color.rgb(111, 124, 130)
    }
}
