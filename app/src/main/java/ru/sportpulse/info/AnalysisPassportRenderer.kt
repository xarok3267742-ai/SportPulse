package ru.sportpulse.info

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class AnalysisPassportTextFitAudit(
    val sourceCharacters: Int,
    val visibleCharacters: Int,
    val lineCount: Int,
    val textSize: Float,
    val height: Int,
    val maxHeight: Int
) {
    val fits: Boolean
        get() = visibleCharacters >= sourceCharacters &&
            height <= maxHeight
}

internal data class AnalysisPassportEventTextAudit(
    val match: AnalysisPassportTextFitAudit,
    val metadata: AnalysisPassportTextFitAudit
) {
    val fits: Boolean
        get() = match.fits && metadata.fits
}

internal class AnalysisPassportRenderer(
    context: Context
) {
    private val resources = context.applicationContext.resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val normal = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    fun render(snapshot: AnalysisPassportSnapshot): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AppColors.background)
        paint.color = AppColors.accent
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)

        drawHeader(canvas)
        drawEventVisual(canvas, snapshot)
        if (snapshot.postEventReviewResult == null) {
            drawAnalysis(canvas, snapshot)
        } else {
            drawPostEventAnalysis(
                canvas,
                snapshot,
                snapshot.postEventReviewResult
            )
        }
        drawFooter(canvas, snapshot)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas) {
        drawText(
            canvas = canvas,
            value = "СПОРТ ПУЛЬС",
            x = 72f,
            baseline = 82f,
            size = 38f,
            color = AppColors.ink,
            typeface = bold
        )
        drawText(
            canvas = canvas,
            value = "Факты отделены от шума",
            x = 72f,
            baseline = 119f,
            size = 22f,
            color = AppColors.muted,
            typeface = normal
        )
        drawPill(
            canvas = canvas,
            value = "ИНФОРМАЦИЯ • 18+",
            rect = RectF(742f, 48f, 1008f, 108f),
            background = AppColors.signalSoft,
            foreground = AppColors.signal,
            textSize = 20f
        )
    }

    private fun drawEventVisual(
        canvas: Canvas,
        snapshot: AnalysisPassportSnapshot
    ) {
        val rect = RectF(72f, 145f, 1008f, 520f)
        val clip = Path().apply { addRoundRect(rect, 28f, 28f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = AppColors.ink
        canvas.drawRect(rect, paint)

        val source = BitmapFactory.decodeResource(resources, snapshot.event.imageRes)
        if (source != null) {
            drawCenterCrop(canvas, source, rect)
            source.recycle()
        }

        paint.shader = LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            intArrayOf(
                Color.argb(8, 12, 18, 22),
                Color.argb(78, 12, 18, 22),
                Color.argb(235, 12, 18, 22)
            ),
            floatArrayOf(0f, 0.46f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()

        drawPill(
            canvas = canvas,
            value = snapshot.event.sport.uppercase(
                Locale.forLanguageTag("ru-RU")
            ),
            rect = RectF(102f, 172f, 330f, 228f),
            background = Color.argb(225, 34, 91, 137),
            foreground = Color.WHITE,
            textSize = 19f
        )
        drawTextLayout(
            canvas = canvas,
            plan = eventMatchTextPlan(snapshot.event.match),
            x = 104f,
            y = EVENT_MATCH_TOP,
            color = Color.WHITE
        )
        drawTextLayout(
            canvas = canvas,
            plan = eventMetadataTextPlan(snapshot.event),
            x = 104f,
            y = EVENT_METADATA_TOP,
            color = Color.rgb(218, 229, 232)
        )
    }

    private fun drawAnalysis(
        canvas: Canvas,
        snapshot: AnalysisPassportSnapshot
    ) {
        val result = snapshot.result
        val tone = tone(result.verdict)

        drawText(
            canvas,
            "ПАСПОРТ СОБЫТИЯ",
            72f,
            582f,
            24f,
            AppColors.accent,
            bold
        )
        snapshot.signalStress?.let { stress ->
            val stressTone = signalStressTone(stress.status)
            drawPill(
                canvas = canvas,
                value = signalStressBadge(stress.status),
                rect = RectF(430f, 548f, 700f, 604f),
                background = stressTone.background,
                foreground = stressTone.foreground,
                textSize = 18f
            )
        }
        snapshot.confidenceShadow?.let { shadow ->
            val shadowTone = confidenceShadowTone(shadow.status)
            drawPill(
                canvas = canvas,
                value = confidenceShadowBadge(shadow),
                rect = RectF(716f, 548f, 1008f, 604f),
                background = shadowTone.background,
                foreground = shadowTone.foreground,
                textSize = 18f
            )
        }
        drawText(canvas, "Ручная оценка проверки", 72f, 630f, 25f, AppColors.muted, bold)
        drawText(
            canvas,
            result.readiness.toString(),
            72f,
            746f,
            112f,
            tone.foreground,
            bold
        )
        drawText(canvas, "из 100", 248f, 730f, 27f, AppColors.muted, normal)
        drawPill(
            canvas = canvas,
            value = AnalysisPassportFactory.verdictLabel(result.verdict),
            rect = RectF(72f, 774f, 405f, 838f),
            background = tone.background,
            foreground = tone.foreground,
            textSize = 21f
        )
        drawText(
            canvas,
            "Шум данных: ${result.noise}/100",
            72f,
            882f,
            25f,
            AppColors.muted,
            bold
        )
        snapshot.evidenceResult?.let { evidence ->
            val integrityLabel = snapshot.sourceIntegrity?.let {
                if (it.claimedQuorumCount == 0) {
                    " • Антиэхо: 0 • ${it.shortFingerprint}"
                } else {
                    " • Антиэхо: ${it.acceptedQuorumCount}/${it.claimedQuorumCount} • ${it.shortFingerprint}"
                }
            }.orEmpty()
            drawText(
                canvas,
                "Кворум фактов: ${evidence.quorumCount}/5$integrityLabel",
                72f,
                914f,
                if (snapshot.sourceIntegrity == null) 20f else 18f,
                snapshot.sourceIntegrity
                    ?.let(::sourceIntegrityTone)
                    ?.foreground
                    ?: if (
                        evidence.quorumCount ==
                        SignalFactor.values().size
                    ) {
                        AppColors.accentDark
                    } else {
                        AppColors.warning
                    },
                bold
            )
        }
        snapshot.counterView?.let { counterView ->
            val counterTone = counterViewTone(
                counterView.verdict
            )
            drawText(
                canvas,
                "Контрракурс: ${counterView.reviewedCount}/5 • предел: ${
                    AnalysisPassportFactory.decisionLabel(
                        counterView.decisionCeiling
                    ).lowercase(
                        Locale.forLanguageTag("ru-RU")
                    )
                } • ${counterView.shortFingerprint}",
                72f,
                938f,
                18f,
                counterTone.foreground,
                bold
            )
        }
        snapshot.freshnessResult?.let { freshness ->
            val hasStaleFactors = freshness.degradedFactors.isNotEmpty() ||
                freshness.expiredFactors.isNotEmpty()
            val label = if (hasStaleFactors) {
                "Срок сигнала: обновить данные"
            } else {
                val remaining = freshness.nextTransitionAt
                    ?.minus(snapshot.generatedAt)
                if (remaining == null) {
                    "Срок сигнала: нет подтверждений"
                } else {
                    "Срок сигнала: ${FreshnessFormatter.duration(remaining)}"
                }
            }
            drawText(
                canvas,
                label,
                72f,
                if (snapshot.counterView == null) 938f else 962f,
                18f,
                if (hasStaleFactors) AppColors.danger else AppColors.signal,
                bold
            )
        }

        drawRadar(
            canvas = canvas,
            assessment = snapshot.assessment,
            claimedAssessment = snapshot.confidenceShadow
                ?.claimedAssessment,
            centerX = 776f,
            centerY = 774f,
            radius = 153f,
            weakestIndex = result.weakestFactor.ordinal,
            criticalShadowIndex = snapshot.confidenceShadow
                ?.criticalFactor
                ?.factor
                ?.ordinal
        )

        val routeTone = snapshot.counterView
            ?.takeIf {
                it.verdict !=
                    CounterViewVerdict.BALANCED
            }
            ?.let { counterViewTone(it.verdict) }
            ?: snapshot.sourceIntegrity
            ?.takeIf {
                it.verdict != SourceIntegrityVerdict.NO_QUORUM &&
                    it.verdict != SourceIntegrityVerdict.AUDITED
            }
            ?.let(::sourceIntegrityTone)
            ?: snapshot.verificationRoute
            ?.let(::verificationRouteTone)
            ?: tone
        val calloutTop = if (
            snapshot.counterView == null
        ) {
            948f
        } else {
            978f
        }
        val callout = RectF(
            72f,
            calloutTop,
            1008f,
            1064f
        )
        drawRoundRect(canvas, callout, 18f, routeTone.background)
        drawTextBlock(
            canvas = canvas,
            value = analysisCallout(snapshot),
            x = 98f,
            y = calloutTop + 18f,
            width = 884,
            size = 20f,
            color = routeTone.foreground,
            typeface = bold,
            maxLines = 3,
            maxHeight = 80
        )

        SignalFactor.values().forEachIndexed { index, factor ->
            val columnWidth = 936f / SignalFactor.values().size
            val center = 72f + columnWidth * index + columnWidth / 2f
            drawTextCentered(
                canvas,
                factor.shortTitle,
                center,
                1090f,
                18f,
                AppColors.muted,
                bold
            )
            drawTextCentered(
                canvas,
                snapshot.assessment.value(factor).toString(),
                center,
                1133f,
                34f,
                when {
                    snapshot.sourceIntegrity
                        ?.cappedFactors
                        ?.contains(factor) == true ->
                        sourceIntegrityTone(
                            snapshot.sourceIntegrity
                        ).foreground
                    factor.ordinal == result.weakestFactor.ordinal ->
                        AppColors.danger
                    snapshot.evidenceResult
                        ?.cappedFactors
                        ?.contains(factor) == true ->
                        AppColors.warning
                    else ->
                        AppColors.accent
                },
                bold
            )
        }

        drawPill(
            canvas = canvas,
            value = decisionPillLabel(snapshot),
            rect = RectF(72f, 1164f, 1008f, 1226f),
            background = AppColors.surface,
            foreground = decisionColor(snapshot.decision),
            textSize = 22f,
            stroke = decisionColor(snapshot.decision)
        )
    }

    private fun drawPostEventAnalysis(
        canvas: Canvas,
        snapshot: AnalysisPassportSnapshot,
        result: PostEventReviewResult
    ) {
        val tone = postEventReviewTone(result.status)
        drawText(
            canvas,
            "ПОСЛЕ СВИСТКА",
            72f,
            582f,
            24f,
            AppColors.accent,
            bold
        )
        drawPill(
            canvas = canvas,
            value = postEventReviewBadge(result.status),
            rect = RectF(716f, 548f, 1008f, 604f),
            background = tone.background,
            foreground = tone.foreground,
            textSize = 18f
        )

        drawText(
            canvas,
            "Качество исходных данных",
            72f,
            638f,
            24f,
            AppColors.muted,
            bold
        )
        val visibleScore = if (
            result.status ==
            PostEventReviewStatus.NOT_ENOUGH_DATA
        ) {
            "—"
        } else {
            result.reliabilityScore?.toString() ?: "—"
        }
        drawText(
            canvas,
            visibleScore,
            72f,
            756f,
            108f,
            tone.foreground,
            bold
        )
        drawText(
            canvas,
            if (visibleScore == "—") {
                "оценка отложена"
            } else {
                "из 100"
            },
            250f,
            741f,
            25f,
            AppColors.muted,
            normal
        )
        drawText(
            canvas,
            "Проверено: ${result.verifiedCount}/5",
            72f,
            793f,
            21f,
            AppColors.muted,
            bold
        )

        val summaryRect = RectF(430f, 622f, 1008f, 790f)
        drawRoundRect(
            canvas,
            summaryRect,
            18f,
            tone.background
        )
        drawTextBlock(
            canvas = canvas,
            value = postEventReviewSummary(result),
            x = 458f,
            y = 649f,
            width = 522,
            size = 21f,
            color = tone.foreground,
            typeface = bold,
            maxLines = 4,
            maxHeight = 116
        )

        drawText(
            canvas,
            "ПЯТЬ ФАКТОРОВ • НЕ ИСХОД МАТЧА",
            72f,
            840f,
            19f,
            AppColors.muted,
            bold
        )
        val columnWidth = 936f / SignalFactor.values().size
        result.factorResults.forEachIndexed { index, factorResult ->
            val left = 72f + columnWidth * index
            val center = left + columnWidth / 2f
            val outcomeTone = postEventOutcomeTone(
                factorResult.outcome
            )
            drawTextCentered(
                canvas,
                factorResult.factor.shortTitle,
                center,
                873f,
                17f,
                AppColors.ink,
                bold
            )
            drawPill(
                canvas = canvas,
                value = factorResult.outcome.shortTitle.uppercase(
                    Locale.forLanguageTag("ru-RU")
                ),
                rect = RectF(
                    left + 6f,
                    891f,
                    left + columnWidth - 6f,
                    947f
                ),
                background = outcomeTone.background,
                foreground = outcomeTone.foreground,
                textSize = if (
                    factorResult.outcome ==
                    PostEventOutcome.UNKNOWN
                ) {
                    14f
                } else {
                    18f
                }
            )
            drawTextCentered(
                canvas,
                "${factorResult.baselineValue} • ${
                    evidenceShortTitle(
                        factorResult.baselineEvidence
                    )
                }",
                center,
                976f,
                14f,
                AppColors.muted,
                bold
            )
        }

        val lessonRect = RectF(72f, 1002f, 1008f, 1108f)
        drawRoundRect(
            canvas,
            lessonRect,
            18f,
            AppColors.surface,
            AppColors.line
        )
        drawTextBlock(
            canvas = canvas,
            value = postEventReviewLesson(result),
            x = 98f,
            y = 1020f,
            width = 884,
            size = 20f,
            color = AppColors.ink,
            typeface = bold,
            maxLines = 3,
            maxHeight = 70
        )

        val decisionMark = result.review
            .decisionFingerprint
            .take(8)
            .uppercase()
        drawPill(
            canvas = canvas,
            value = "СНИМОК $decisionMark  →  АУДИТ ${result.review.shortFingerprint}",
            rect = RectF(72f, 1128f, 1008f, 1190f),
            background = AppColors.signalSoft,
            foreground = AppColors.signal,
            textSize = 20f,
            stroke = AppColors.signal
        )
        drawTextCentered(
            canvas,
            "Счет, коэффициенты и финансовый результат не использованы",
            WIDTH / 2f,
            1228f,
            18f,
            AppColors.muted,
            bold
        )
    }

    private fun decisionPillLabel(
        snapshot: AnalysisPassportSnapshot
    ): String {
        val decision = AnalysisPassportFactory
            .decisionLabel(snapshot.decision)
        val counterView = snapshot.counterView
        if (
            snapshot.decision != null &&
            counterView != null &&
            !counterView.allows(snapshot.decision)
        ) {
            return "РЕШЕНИЕ ОГРАНИЧЕНО • ПРЕДЕЛ: ${
                AnalysisPassportFactory.decisionLabel(
                    counterView.decisionCeiling
                )
            } • ${counterView.shortFingerprint}"
        }
        val trace = snapshot.decisionTrace
            ?: return "РЕШЕНИЕ • $decision"
        val delta = trace.readinessDelta
        val signedDelta = if (delta > 0) "+$delta" else delta.toString()
        return "РЕШЕНИЕ • $decision • Δ $signedDelta • ${trace.snapshot.shortFingerprint}"
    }

    private fun drawRadar(
        canvas: Canvas,
        assessment: SignalAssessment,
        claimedAssessment: SignalAssessment?,
        centerX: Float,
        centerY: Float,
        radius: Float,
        weakestIndex: Int,
        criticalShadowIndex: Int?
    ) {
        val factors = SignalFactor.values()
        val polygon = Path()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = AppColors.line

        for (ring in 1..4) {
            polygon.reset()
            factors.indices.forEach { index ->
                val angle = radarAngle(index, factors.size)
                val ringRadius = radius * ring / 4f
                val x = centerX + cos(angle) * ringRadius
                val y = centerY + sin(angle) * ringRadius
                if (index == 0) polygon.moveTo(x, y) else polygon.lineTo(x, y)
            }
            polygon.close()
            canvas.drawPath(polygon, paint)
        }

        factors.indices.forEach { index ->
            val angle = radarAngle(index, factors.size)
            canvas.drawLine(
                centerX,
                centerY,
                centerX + cos(angle) * radius,
                centerY + sin(angle) * radius,
                paint
            )
        }

        if (claimedAssessment != null && claimedAssessment != assessment) {
            val shadow = radarPath(
                assessment = claimedAssessment,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                factorCount = factors.size
            )
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(48, 172, 102, 0)
            canvas.drawPath(shadow, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.strokeJoin = Paint.Join.ROUND
            paint.pathEffect = DashPathEffect(
                floatArrayOf(16f, 10f),
                0f
            )
            paint.color = AppColors.warning
            canvas.drawPath(shadow, paint)
            paint.pathEffect = null

            criticalShadowIndex?.let { index ->
                val angle = radarAngle(index, factors.size)
                val valueRadius =
                    radius * claimedAssessment.values[index] / 100f
                paint.style = Paint.Style.FILL
                paint.color = AppColors.warning
                canvas.drawCircle(
                    centerX + cos(angle) * valueRadius,
                    centerY + sin(angle) * valueRadius,
                    10f,
                    paint
                )
            }
        }

        val signal = radarPath(
            assessment = assessment,
            centerX = centerX,
            centerY = centerY,
            radius = radius,
            factorCount = factors.size
        )
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, 0, 118, 105)
        canvas.drawPath(signal, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = AppColors.accent
        canvas.drawPath(signal, paint)

        assessment.values.forEachIndexed { index, value ->
            val angle = radarAngle(index, factors.size)
            val valueRadius = radius * value / 100f
            paint.style = Paint.Style.FILL
            paint.color = if (index == weakestIndex) AppColors.danger else AppColors.accent
            canvas.drawCircle(
                centerX + cos(angle) * valueRadius,
                centerY + sin(angle) * valueRadius,
                if (index == weakestIndex) 10f else 7f,
                paint
            )
        }

        factors.forEachIndexed { index, factor ->
            val angle = radarAngle(index, factors.size)
            val labelRadius = radius + 37f
            val x = centerX + cos(angle) * labelRadius
            val y = centerY + sin(angle) * labelRadius + 7f
            drawTextCentered(
                canvas,
                factor.shortTitle,
                x,
                y,
                17f,
                AppColors.muted,
                bold
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun radarPath(
        assessment: SignalAssessment,
        centerX: Float,
        centerY: Float,
        radius: Float,
        factorCount: Int
    ): Path {
        return Path().apply {
            assessment.values.forEachIndexed { index, value ->
                val angle = radarAngle(index, factorCount)
                val valueRadius = radius * value / 100f
                val x = centerX + cos(angle) * valueRadius
                val y = centerY + sin(angle) * valueRadius
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        snapshot: AnalysisPassportSnapshot
    ) {
        paint.color = AppColors.line
        canvas.drawRect(72f, 1250f, 1008f, 1252f, paint)
        drawText(
            canvas,
            "Создано ${formatDate(snapshot.generatedAt)}",
            72f,
            1287f,
            19f,
            AppColors.muted,
            normal
        )
        drawText(
            canvas,
            "0 денежных операций",
            1008f,
            1287f,
            19f,
            AppColors.muted,
            bold,
            Paint.Align.RIGHT
        )
        drawTextCentered(
            canvas,
            "Информационный материал 18+ • Не прогноз • Не принимает ставки",
            WIDTH / 2f,
            1328f,
            19f,
            AppColors.ink,
            bold
        )
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF
    ) {
        val targetRatio = destination.width() / destination.height()
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val source = if (sourceRatio > targetRatio) {
            val cropWidth = (bitmap.height * targetRatio).toInt()
            val left = (bitmap.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, bitmap.height)
        } else {
            val cropHeight = (bitmap.width / targetRatio).toInt()
            val top = (bitmap.height - cropHeight) / 2
            Rect(0, top, bitmap.width, top + cropHeight)
        }
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, source, destination, paint)
        paint.isFilterBitmap = false
    }

    private fun drawText(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
        typeface: Typeface,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.textSize = size
        paint.color = color
        paint.typeface = typeface
        paint.textAlign = align
        canvas.drawText(value, x, baseline, paint)
    }

    private fun drawTextCentered(
        canvas: Canvas,
        value: String,
        centerX: Float,
        baseline: Float,
        size: Float,
        color: Int,
        typeface: Typeface
    ) {
        drawText(
            canvas,
            value,
            centerX,
            baseline,
            size,
            color,
            typeface,
            Paint.Align.CENTER
        )
    }

    internal fun eventTextAudit(
        event: SportEvent
    ): AnalysisPassportEventTextAudit {
        return AnalysisPassportEventTextAudit(
            match = eventMatchTextPlan(event.match).audit(
                EVENT_MATCH_MAX_HEIGHT
            ),
            metadata = eventMetadataTextPlan(event).audit(
                EVENT_METADATA_MAX_HEIGHT
            )
        )
    }

    private fun eventMatchTextPlan(
        match: String
    ): TextLayoutPlan {
        return fittedTextLayout(
            value = match,
            width = EVENT_TEXT_WIDTH,
            maxSize = 49f,
            maxLines = 3,
            maxHeight = EVENT_MATCH_MAX_HEIGHT
        )
    }

    private fun eventMetadataTextPlan(
        event: SportEvent
    ): TextLayoutPlan {
        return fittedTextLayout(
            value = "${event.tournament} • ${event.region}",
            width = EVENT_TEXT_WIDTH,
            maxSize = 22f,
            maxLines = 3,
            maxHeight = EVENT_METADATA_MAX_HEIGHT
        )
    }

    private fun drawTextBlock(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        width: Int,
        size: Float,
        color: Int,
        typeface: Typeface,
        maxLines: Int,
        maxHeight: Int
    ) {
        val plan = fittedTextLayout(
            value = value,
            width = width,
            maxSize = size,
            maxLines = maxLines,
            maxHeight = maxHeight,
            typeface = typeface
        )
        drawTextLayout(canvas, plan, x, y, color)
    }

    private fun fittedTextLayout(
        value: String,
        width: Int,
        maxSize: Float,
        maxLines: Int,
        maxHeight: Int,
        typeface: Typeface = bold
    ): TextLayoutPlan {
        val source = value.trim()
        var candidateSize = maxSize
        var smallestPlan: TextLayoutPlan? = null
        while (candidateSize >= MIN_FITTED_TEXT_SIZE) {
            val plan = TextLayoutPlan(
                source = source,
                layout = buildTextLayout(
                    value = source,
                    width = width,
                    size = candidateSize,
                    typeface = typeface,
                    maxLines = maxLines
                ),
                textSize = candidateSize,
                maxLines = maxLines,
                maxHeight = maxHeight
            )
            if (plan.fits) return plan
            smallestPlan = plan
            candidateSize -= FITTED_TEXT_SIZE_STEP
        }
        return checkNotNull(smallestPlan)
    }

    private fun buildTextLayout(
        value: String,
        width: Int,
        size: Float,
        typeface: Typeface,
        maxLines: Int
    ): StaticLayout {
        textPaint.textSize = size
        textPaint.typeface = typeface
        return StaticLayout.Builder
            .obtain(value, 0, value.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .build()
    }

    private fun drawTextLayout(
        canvas: Canvas,
        plan: TextLayoutPlan,
        x: Float,
        y: Float,
        color: Int
    ) {
        plan.layout.paint.color = color
        canvas.save()
        canvas.translate(x, y)
        plan.layout.draw(canvas)
        canvas.restore()
    }

    private fun TextLayoutPlan.audit(
        maxHeight: Int
    ): AnalysisPassportTextFitAudit {
        return AnalysisPassportTextFitAudit(
            sourceCharacters = source.length,
            visibleCharacters = visibleCharacters,
            lineCount = layout.lineCount,
            textSize = textSize,
            height = layout.height,
            maxHeight = maxHeight
        )
    }

    private fun drawPill(
        canvas: Canvas,
        value: String,
        rect: RectF,
        background: Int,
        foreground: Int,
        textSize: Float,
        stroke: Int = Color.TRANSPARENT
    ) {
        drawRoundRect(canvas, rect, rect.height() / 2f, background, stroke)
        paint.textSize = textSize
        paint.typeface = bold
        paint.textAlign = Paint.Align.CENTER
        paint.color = foreground
        val baseline = rect.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(value, rect.centerX(), baseline, paint)
    }

    private fun drawRoundRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        color: Int,
        stroke: Int = Color.TRANSPARENT
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (stroke != Color.TRANSPARENT) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = stroke
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun radarAngle(index: Int, count: Int): Float {
        return (-PI / 2 + 2 * PI * index / count).toFloat()
    }

    private fun tone(verdict: SignalVerdict): Tone {
        return when (verdict) {
            SignalVerdict.SKIP -> Tone(AppColors.danger, AppColors.dangerSoft)
            SignalVerdict.OBSERVE -> Tone(AppColors.warning, AppColors.warningSoft)
            SignalVerdict.READY -> Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun verificationRouteTone(
        route: VerificationRoute
    ): Tone {
        return when (route.status) {
            VerificationRouteStatus.REACHABLE ->
                Tone(AppColors.signal, AppColors.signalSoft)
            VerificationRouteStatus.FACTS_LIMIT ->
                Tone(AppColors.warning, AppColors.warningSoft)
            VerificationRouteStatus.READY_MAINTAIN ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
        }
    }

    private fun signalStressTone(
        status: SignalStressStatus
    ): Tone {
        return when (status) {
            SignalStressStatus.ROBUST ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            SignalStressStatus.FRAGILE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            SignalStressStatus.NO_BUFFER ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
    }

    private fun signalStressBadge(
        status: SignalStressStatus
    ): String {
        return when (status) {
            SignalStressStatus.ROBUST -> "СТРЕСС • 1 СБОЙ"
            SignalStressStatus.FRAGILE -> "СТРЕСС • ХРУПКО"
            SignalStressStatus.NO_BUFFER -> "СТРЕСС • НЕТ ЗАПАСА"
        }
    }

    private fun confidenceShadowTone(
        status: ConfidenceShadowStatus
    ): Tone {
        return when (status) {
            ConfidenceShadowStatus.CLEAR ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            ConfidenceShadowStatus.CONTAINED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            ConfidenceShadowStatus.VERDICT_SHIFT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun confidenceShadowBadge(
        result: ConfidenceShadowResult
    ): String {
        return when (result.status) {
            ConfidenceShadowStatus.CLEAR ->
                "ТЕНЬ • НЕТ"
            ConfidenceShadowStatus.CONTAINED -> {
                if (result.readinessGap == 0) {
                    "ТЕНЬ • ЕСТЬ"
                } else {
                    "ТЕНЬ • -${result.readinessGap}"
                }
            }
            ConfidenceShadowStatus.VERDICT_SHIFT ->
                "ТЕНЬ • СТАТУС"
        }
    }

    private fun postEventReviewTone(
        status: PostEventReviewStatus
    ): Tone {
        return when (status) {
            PostEventReviewStatus.RELIABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            PostEventReviewStatus.MIXED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PostEventReviewStatus.FRAGILE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            PostEventReviewStatus.NOT_ENOUGH_DATA ->
                Tone(AppColors.signal, AppColors.signalSoft)
        }
    }

    private fun postEventOutcomeTone(
        outcome: PostEventOutcome
    ): Tone {
        return when (outcome) {
            PostEventOutcome.CONFIRMED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            PostEventOutcome.PARTIAL ->
                Tone(AppColors.warning, AppColors.warningSoft)
            PostEventOutcome.DISPROVED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
            PostEventOutcome.UNKNOWN,
            PostEventOutcome.UNREVIEWED ->
                Tone(AppColors.muted, AppColors.background)
        }
    }

    private fun postEventReviewBadge(
        status: PostEventReviewStatus
    ): String {
        return when (status) {
            PostEventReviewStatus.RELIABLE -> "ДАННЫЕ • УСТОЙЧИВО"
            PostEventReviewStatus.MIXED -> "ДАННЫЕ • СМЕШАННО"
            PostEventReviewStatus.FRAGILE -> "ДАННЫЕ • ХРУПКО"
            PostEventReviewStatus.NOT_ENOUGH_DATA ->
                "МАЛО ДАННЫХ"
        }
    }

    private fun postEventReviewSummary(
        result: PostEventReviewResult
    ): String {
        return when (result.status) {
            PostEventReviewStatus.RELIABLE ->
                "Исходные факты выдержали проверку. Это оценка качества данных, а не правильности прогноза."
            PostEventReviewStatus.MIXED ->
                "Картина подтвердилась частично. Слабые факторы требуют отдельной перепроверки."
            PostEventReviewStatus.FRAGILE -> {
                if (result.criticalMisses.isNotEmpty()) {
                    "Ложная уверенность: факт с кворумом источников оказался неверным."
                } else {
                    "Исходная картина не выдержала проверку."
                }
            }
            PostEventReviewStatus.NOT_ENOUGH_DATA ->
                "Проверяемых факторов ${result.verifiedCount}/5. Нужно минимум три."
        }
    }

    private fun postEventReviewLesson(
        result: PostEventReviewResult
    ): String {
        val factor = result.focusFactor
        return when {
            result.criticalMisses.isNotEmpty() &&
                factor != null ->
                "Главный урок: перепроверить независимость источников по фактору «${factor.title}»."
            result.status ==
                PostEventReviewStatus.NOT_ENOUGH_DATA &&
                factor != null ->
                "Главный урок: собрать постфактум данные по фактору «${factor.title}»."
            factor != null ->
                "Главный урок: следующий разбор начать с фактора «${factor.title}»."
            else ->
                "Процесс устойчив, но один разбор еще не формирует закономерность."
        }
    }

    private fun evidenceShortTitle(
        level: EvidenceLevel
    ): String {
        return when (level) {
            EvidenceLevel.UNCONFIRMED -> "нет"
            EvidenceLevel.SINGLE_SOURCE -> "1 ист."
            EvidenceLevel.QUORUM -> "2+ ист."
        }
    }

    private fun verificationRouteCallout(
        route: VerificationRoute
    ): String {
        return when (route.status) {
            VerificationRouteStatus.REACHABLE -> {
                val factors = route.steps.joinToString(", ") {
                    it.factor.title
                }
                "Маршрут: кворум «$factors» → ${route.projectedResult.effectiveSignal.readiness}/${route.targetReadiness}."
            }
            VerificationRouteStatus.FACTS_LIMIT -> {
                val ceiling =
                    route.allQuorumResult.effectiveSignal.readiness
                "Предел текущих фактов: $ceiling/${route.targetReadiness}. Нужны новые данные, не больше источников."
            }
            VerificationRouteStatus.READY_MAINTAIN ->
                "Статус достигнут. Поддерживайте свежесть, не повышайте оценку ради уверенности."
        }
    }

    private fun analysisCallout(
        snapshot: AnalysisPassportSnapshot
    ): String {
        val route = snapshot.verificationRoute
            ?.let(::verificationRouteCallout)
            ?: "Слабое место: ${snapshot.result.weakestFactor.title}. ${shortVerdict(snapshot.result.verdict)}"
        val corridor = snapshot.decisionCorridor
            ?.let(::decisionCorridorCallout)
        val integrity = snapshot.sourceIntegrity
            ?.let(::sourceIntegrityCallout)
        val counterView = snapshot.counterView
            ?.let(::counterViewCallout)
        return when {
            counterView != null && integrity != null ->
                listOf(counterView, integrity).joinToString(" ")
            counterView != null ->
                listOf(counterView, route).joinToString(" ")
            integrity != null ->
                listOf(integrity, route).joinToString(" ")
            else ->
                listOfNotNull(route, corridor)
                    .joinToString(" ")
        }
    }

    private fun counterViewCallout(
        result: CounterViewResult
    ): String? {
        return when (result.verdict) {
            CounterViewVerdict.OPEN ->
                "Контрракурс: проверено ${result.reviewedCount}/5; предел «${
                    AnalysisPassportFactory.decisionLabel(
                        result.decisionCeiling
                    ).lowercase(
                        Locale.forLanguageTag("ru-RU")
                    )
                }»."
            CounterViewVerdict.BALANCED -> null
            CounterViewVerdict.MIXED ->
                "Контрракурс: спорных факторов ${result.mixedCount}; вывод не выше наблюдения."
            CounterViewVerdict.REFUTED ->
                "Контрракурс: контрфактов ${result.refutedCount}; исходная версия остановлена."
        }
    }

    private fun counterViewTone(
        verdict: CounterViewVerdict
    ): Tone {
        return when (verdict) {
            CounterViewVerdict.OPEN,
            CounterViewVerdict.MIXED ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CounterViewVerdict.BALANCED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CounterViewVerdict.REFUTED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun sourceIntegrityCallout(
        result: SourceIntegrityResult
    ): String? {
        return when (result.verdict) {
            SourceIntegrityVerdict.NO_QUORUM,
            SourceIntegrityVerdict.AUDITED -> null
            SourceIntegrityVerdict.OPEN ->
                "Антиэхо: ${result.unauditedQuorumCount} кворум(а) ждут проверки независимости."
            SourceIntegrityVerdict.ECHO ->
                "Антиэхо: ${result.echoQuorumCount} кворум(а) повторяют одну цепочку."
            SourceIntegrityVerdict.CONFLICT ->
                "Антиэхо: расхождений ${result.conflictCount}; спорные факты исключены."
        }
    }

    private fun sourceIntegrityTone(
        result: SourceIntegrityResult
    ): Tone {
        return when (result.verdict) {
            SourceIntegrityVerdict.NO_QUORUM ->
                Tone(AppColors.signal, AppColors.signalSoft)
            SourceIntegrityVerdict.OPEN,
            SourceIntegrityVerdict.ECHO ->
                Tone(AppColors.warning, AppColors.warningSoft)
            SourceIntegrityVerdict.AUDITED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            SourceIntegrityVerdict.CONFLICT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun decisionCorridorCallout(
        corridor: DecisionCorridor
    ): String {
        val boundary = corridor.nearestBoundary
            ?: return "Коридор: одного фактора недостаточно для смены статуса."
        val verdict = AnalysisPassportFactory.verdictLabel(
            boundary.result.effectiveSignal.verdict
        ).lowercase(Locale.forLanguageTag("ru-RU"))
        return "Граница: «${boundary.factor.title}» ${boundary.claimedBefore}→${boundary.claimedAfter}, далее «$verdict»."
    }

    private fun shortVerdict(verdict: SignalVerdict): String {
        return when (verdict) {
            SignalVerdict.SKIP -> "Белых пятен слишком много."
            SignalVerdict.OBSERVE -> "Сигнал пока неустойчив."
            SignalVerdict.READY -> "Основные данные собраны."
        }
    }

    private fun decisionColor(decision: SavedDecision?): Int {
        return when (decision) {
            SavedDecision.SKIP -> AppColors.danger
            SavedDecision.OBSERVE -> AppColors.warning
            SavedDecision.DATA_READY -> AppColors.accent
            null -> AppColors.muted
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat(
            "d MMMM yyyy, HH:mm",
            Locale.forLanguageTag("ru-RU")
        ).format(Date(timestamp))
    }

    private data class Tone(
        val foreground: Int,
        val background: Int
    )

    private data class TextLayoutPlan(
        val source: String,
        val layout: StaticLayout,
        val textSize: Float,
        val maxLines: Int,
        val maxHeight: Int
    ) {
        val visibleCharacters: Int
            get() = if (layout.lineCount == 0) {
                0
            } else {
                layout.getLineEnd(layout.lineCount - 1)
            }

        val fits: Boolean
            get() = layout.lineCount <= maxLines &&
                layout.height <= maxHeight &&
                visibleCharacters >= source.length
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        const val EVENT_TEXT_WIDTH = 830
        const val EVENT_MATCH_TOP = 342f
        const val EVENT_MATCH_MAX_HEIGHT = 116
        const val EVENT_METADATA_TOP = 466f
        const val EVENT_METADATA_MAX_HEIGHT = 47
        const val MIN_FITTED_TEXT_SIZE = 10f
        const val FITTED_TEXT_SIZE_STEP = 0.5f
    }
}

internal class AnalysisPassportExporter(
    context: Context
) {
    private val applicationContext = context.applicationContext
    private val renderer = AnalysisPassportRenderer(applicationContext)

    fun export(snapshot: AnalysisPassportSnapshot): File {
        val directory = File(
            applicationContext.cacheDir,
            AnalysisImageProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Не удалось создать каталог экспорта"
        }

        val output = File(directory, AnalysisPassportFactory.fileName(snapshot))
        val temporary = File(directory, ".${output.name}.tmp")
        val bitmap = renderer.render(snapshot)
        try {
            FileOutputStream(temporary).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Не удалось записать PNG"
                }
            }
            if (output.exists()) output.delete()
            if (!temporary.renameTo(output)) {
                temporary.copyTo(output, overwrite = true)
                temporary.delete()
            }
        } finally {
            bitmap.recycle()
            if (temporary.exists()) temporary.delete()
        }
        cleanup(directory, keep = 8)
        return output
    }

    private fun cleanup(directory: File, keep: Int) {
        directory.listFiles()
            ?.filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending(File::lastModified)
            ?.drop(keep)
            ?.forEach(File::delete)
    }
}
