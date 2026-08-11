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

internal class SourceIntegrityView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.2f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.2f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = cappedSp(9.4f)
        typeface = AppTypography.display(context, bold = true)
        textAlign = Paint.Align.CENTER
    }
    private val tile = RectF()
    private val arrow = Path()
    private var integrity: SourceIntegrityResult? = null

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setIntegrity(
        value: SourceIntegrityResult
    ) {
        integrity = value
        contentDescription = accessibilityDescription(value)
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(130f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(canvas)
        val result = integrity ?: return
        val side = dp(4f)
        val gap = dp(4f)
        val tileWidth = (
            width - side * 2f -
                gap * (result.factors.size - 1)
            ) / result.factors.size
        val top = dp(4f)
        val bottom = height - dp(4f)

        result.factors.forEachIndexed { index, factor ->
            val left = side + index * (tileWidth + gap)
            tile.set(left, top, left + tileWidth, bottom)
            val tone = auditTone(
                state = factor.auditState,
                isQuorumClaim = factor.isQuorumClaim
            )

            fillPaint.color = tone.background
            canvas.drawRoundRect(
                tile,
                dp(6f),
                dp(6f),
                fillPaint
            )
            borderPaint.color = tone.foreground
            canvas.drawRoundRect(
                tile,
                dp(6f),
                dp(6f),
                borderPaint
            )

            labelPaint.color = AppColors.ink
            canvas.drawText(
                factor.factor.shortTitle,
                tile.centerX(),
                top + dp(18f),
                labelPaint
            )

            val claimedY = top + dp(45f)
            val effectiveY = top + dp(82f)
            drawConnector(
                canvas = canvas,
                centerX = tile.centerX(),
                fromY = claimedY + dp(10f),
                toY = effectiveY - dp(10f),
                color = if (factor.isCapped) {
                    tone.foreground
                } else {
                    AppColors.line
                }
            )
            drawEvidenceNode(
                canvas = canvas,
                level = factor.claimedLevel,
                centerX = tile.centerX(),
                centerY = claimedY
            )
            drawEvidenceNode(
                canvas = canvas,
                level = factor.effectiveLevel,
                centerX = tile.centerX(),
                centerY = effectiveY
            )

            labelPaint.color = tone.foreground
            canvas.drawText(
                factor.auditState.shortTitle,
                tile.centerX(),
                bottom - dp(9f),
                labelPaint
            )
        }
    }

    private fun drawConnector(
        canvas: Canvas,
        centerX: Float,
        fromY: Float,
        toY: Float,
        color: Int
    ) {
        borderPaint.color = color
        borderPaint.strokeWidth = dp(1.4f)
        canvas.drawLine(
            centerX,
            fromY,
            centerX,
            toY - dp(3f),
            borderPaint
        )
        arrow.reset()
        arrow.moveTo(centerX, toY)
        arrow.lineTo(centerX - dp(3.2f), toY - dp(5f))
        arrow.lineTo(centerX + dp(3.2f), toY - dp(5f))
        arrow.close()
        fillPaint.color = color
        canvas.drawPath(arrow, fillPaint)
        borderPaint.strokeWidth = dp(1.2f)
    }

    private fun drawEvidenceNode(
        canvas: Canvas,
        level: EvidenceLevel,
        centerX: Float,
        centerY: Float
    ) {
        val tone = evidenceTone(level)
        fillPaint.color = tone.background
        canvas.drawCircle(
            centerX,
            centerY,
            dp(10.5f),
            fillPaint
        )
        borderPaint.color = tone.foreground
        canvas.drawCircle(
            centerX,
            centerY,
            dp(10.5f),
            borderPaint
        )
        nodePaint.color = tone.foreground
        val baseline = centerY -
            (nodePaint.ascent() + nodePaint.descent()) / 2f
        canvas.drawText(
            evidenceCode(level),
            centerX,
            baseline,
            nodePaint
        )
    }

    private fun accessibilityDescription(
        result: SourceIntegrityResult
    ): String {
        val factors = result.factors.joinToString(". ") { factor ->
            "${factor.factor.title}: заявлено ${
                evidenceSpokenLabel(factor.claimedLevel)
            }; учтено ${
                evidenceSpokenLabel(factor.effectiveLevel)
            }; аудит ${auditSpokenLabel(factor.auditState)}"
        }
        return "Антиэхо источников. ${
            integrityBadge(result.verdict)
        }. Принято независимых кворумов ${
            result.acceptedQuorumCount
        } из ${result.claimedQuorumCount}. $factors."
    }

    private fun auditTone(
        state: SourceAuditState,
        isQuorumClaim: Boolean
    ): Tone {
        if (!isQuorumClaim && state == SourceAuditState.UNAUDITED) {
            return Tone(
                foreground = AppColors.muted,
                background = AppColors.surface
            )
        }
        return when (state) {
            SourceAuditState.UNAUDITED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            SourceAuditState.SHARED_LINEAGE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            SourceAuditState.INDEPENDENT ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            SourceAuditState.CONFLICT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun evidenceTone(
        level: EvidenceLevel
    ): Tone {
        return when (level) {
            EvidenceLevel.UNCONFIRMED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            EvidenceLevel.SINGLE_SOURCE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            EvidenceLevel.QUORUM ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun evidenceCode(
        level: EvidenceLevel
    ): String {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> "0"
            EvidenceLevel.SINGLE_SOURCE -> "1"
            EvidenceLevel.QUORUM -> "2+"
        }
    }

    private fun evidenceSpokenLabel(
        level: EvidenceLevel
    ): String {
        return when (level) {
            EvidenceLevel.UNCONFIRMED ->
                "не подтверждено"
            EvidenceLevel.SINGLE_SOURCE ->
                "один источник"
            EvidenceLevel.QUORUM ->
                "кворум, два или более источника"
        }
    }

    private fun auditSpokenLabel(
        state: SourceAuditState
    ): String {
        return when (state) {
            SourceAuditState.UNAUDITED ->
                "не проверено"
            SourceAuditState.SHARED_LINEAGE ->
                "одна первичная цепочка"
            SourceAuditState.INDEPENDENT ->
                "источники независимы"
            SourceAuditState.CONFLICT ->
                "есть расхождение"
        }
    }

    private fun integrityBadge(
        verdict: SourceIntegrityVerdict
    ): String {
        return when (verdict) {
            SourceIntegrityVerdict.NO_QUORUM ->
                "Заявленных кворумов нет"
            SourceIntegrityVerdict.OPEN ->
                "Независимость не проверена"
            SourceIntegrityVerdict.AUDITED ->
                "Кворум независим"
            SourceIntegrityVerdict.ECHO ->
                "Обнаружена общая цепочка"
            SourceIntegrityVerdict.CONFLICT ->
                "Обнаружено расхождение"
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
