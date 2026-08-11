package ru.sportpulse.info

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import android.text.TextUtils
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class StoryReturnFramePoint(
    val kind: StoryBeaconMomentKind,
    val at: Long,
    val factors: List<SignalFactor>
) {
    init {
        require(at >= 0L)
        require(factors.distinct().size == factors.size)
        require(factors.zipWithNext().all { (left, right) ->
            left.ordinal < right.ordinal
        })
        when (kind) {
            StoryBeaconMomentKind.CHECK_WINDOW,
            StoryBeaconMomentKind.FACT_EXPIRY ->
                require(factors.isNotEmpty())
            StoryBeaconMomentKind.START,
            StoryBeaconMomentKind.REVIEW_OPEN ->
                require(factors.isEmpty())
            StoryBeaconMomentKind.ACTION_NOW,
            StoryBeaconMomentKind.COMPLETE ->
                error("Return frame requires an absolute moment")
        }
    }
}

internal data class StoryReturnFrame(
    val eventId: String,
    val eventLabel: String,
    val chapter: EventStoryChapter,
    val activatedAt: Long,
    val pauseUntil: Long,
    val originalPoint: StoryReturnFramePoint,
    val beforeState: StoryThreadMapState,
    val outcome: StoryReturnCapsuleState,
    val currentState: StoryThreadMapState?,
    val movedPoint: StoryReturnFramePoint?,
    val resultFingerprint: String,
    val selectedZone: RegionalZone,
    val generatedAt: Long,
    val fingerprint: String
) {
    init {
        require(eventId.isNotBlank())
        require(eventLabel.isNotBlank())
        require(activatedAt >= 0L)
        require(pauseUntil > activatedAt)
        require(originalPoint.at >= pauseUntil)
        require(
            pauseUntil == StoryQuietWindowPolicy.pauseUntil(
                now = activatedAt,
                returnAt = originalPoint.at
            )
        )
        require(
            beforeState == StoryThreadMapState.OPEN ||
                beforeState == StoryThreadMapState.MOVED
        )
        require(outcome != StoryReturnCapsuleState.SEALED)
        when (outcome) {
            StoryReturnCapsuleState.SEALED -> Unit
            StoryReturnCapsuleState.LIMIT_REACHED,
            StoryReturnCapsuleState.MISSING ->
                require(currentState == null)
            StoryReturnCapsuleState.UNCHANGED,
            StoryReturnCapsuleState.POINT_MOVED,
            StoryReturnCapsuleState.CHANGED ->
                require(
                    currentState == StoryThreadMapState.OPEN ||
                        currentState == StoryThreadMapState.MOVED
                )
            StoryReturnCapsuleState.RESOLVED ->
                require(currentState == StoryThreadMapState.RESOLVED)
            StoryReturnCapsuleState.MISSED ->
                require(currentState == StoryThreadMapState.MISSED)
            StoryReturnCapsuleState.DETACHED ->
                require(currentState == StoryThreadMapState.DETACHED)
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                require(currentState == StoryThreadMapState.TAMPERED)
        }
        require(
            (outcome == StoryReturnCapsuleState.POINT_MOVED) ==
                (movedPoint != null)
        )
        movedPoint?.let {
            require(it.kind == originalPoint.kind)
            require(it.at > originalPoint.at)
        }
        if (outcome == StoryReturnCapsuleState.LIMIT_REACHED) {
            require(generatedAt >= pauseUntil)
            require(generatedAt < originalPoint.at)
        } else {
            require(generatedAt >= originalPoint.at)
        }
        require(HEX_64.matches(resultFingerprint))
        require(fingerprint.isEmpty() || HEX_64.matches(fingerprint))
    }

    val shortFingerprint: String
        get() = fingerprint.take(8).uppercase()

    val shortResultFingerprint: String
        get() = resultFingerprint.take(8).uppercase()

    private companion object {
        val HEX_64 = Regex("[0-9a-f]{64}")
    }
}

internal object StoryReturnFrameFactory {
    private const val VERSION = "sport-pulse-story-return-frame-v1"
    private val unsafeFileCharacters = Regex("[^A-Za-z0-9_-]+")
    private val hex = "0123456789abcdef".toCharArray()

    fun create(
        result: StoryReturnCapsuleResult,
        selectedZone: RegionalZone,
        generatedAt: Long = System.currentTimeMillis()
    ): StoryReturnFrame {
        require(result.state != StoryReturnCapsuleState.SEALED)
        val capsule = result.capsule
        val movedMoment = result.currentEntry?.nextMoment?.takeIf {
            result.state == StoryReturnCapsuleState.POINT_MOVED
        }
        val draft = StoryReturnFrame(
            eventId = capsule.eventId,
            eventLabel = capsule.eventLabel,
            chapter = capsule.chapter,
            activatedAt = capsule.activatedAt,
            pauseUntil = capsule.pauseUntil,
            originalPoint = StoryReturnFramePoint(
                kind = capsule.momentKind,
                at = capsule.returnAt,
                factors = capsule.momentFactors.toList()
            ),
            beforeState = capsule.baselineEntryState,
            outcome = result.state,
            currentState = result.currentEntry?.state,
            movedPoint = movedMoment?.let {
                StoryReturnFramePoint(
                    kind = it.kind,
                    at = checkNotNull(it.at),
                    factors = it.factors.toList()
                )
            },
            resultFingerprint = result.fingerprint,
            selectedZone = selectedZone,
            generatedAt = generatedAt,
            fingerprint = ""
        )
        return draft.copy(fingerprint = fingerprintFor(draft))
    }

    fun fileName(frame: StoryReturnFrame): String {
        val safeEventId = unsafeFileCharacters
            .replace(frame.eventId, "_")
            .trim('_')
            .take(48)
            .ifBlank { "event" }
        return "sport_pulse_return_${safeEventId}_" +
            "${frame.generatedAt}.png"
    }

    fun shareText(frame: StoryReturnFrame): String {
        return buildString {
            append("Кадр возвращения «")
            append(frame.eventLabel)
            append("». Вопрос: ")
            append(StoryThreadPosterFactory.question(frame.chapter))
            append(" Исходная точка: ")
            append(pointTitle(frame.originalPoint).lowercase())
            append(", ")
            append(formatInstant(frame, frame.originalPoint.at))
            append(". Тогда: ")
            append(mapStateTitle(frame.beforeState).lowercase())
            append("; сейчас: ")
            append(currentStateTitle(frame).lowercase())
            append(". Итог: ")
            append(outcomeTitle(frame.outcome).lowercase())
            append(". Контрольная метка ")
            append(frame.shortFingerprint)
            append(
                ". Это локальный информационный кадр, " +
                    "не прогноз, не ставка и не подтверждение " +
                    "внешнего результата."
            )
        }
    }

    fun outcomeTitle(state: StoryReturnCapsuleState): String {
        return when (state) {
            StoryReturnCapsuleState.SEALED ->
                error("Sealed capsule has no return frame")
            StoryReturnCapsuleState.LIMIT_REACHED ->
                "ТОЧКА ЕЩЕ ВПЕРЕДИ"
            StoryReturnCapsuleState.UNCHANGED ->
                "БЕЗ ИЗМЕНЕНИЙ"
            StoryReturnCapsuleState.POINT_MOVED ->
                "ТОЧКА ПЕРЕНЕСЕНА"
            StoryReturnCapsuleState.CHANGED ->
                "ВЕРСИЯ ИЗМЕНИЛАСЬ"
            StoryReturnCapsuleState.RESOLVED ->
                "ВОПРОС ЗАКРЫТ"
            StoryReturnCapsuleState.MISSED ->
                "МОМЕНТ УПУЩЕН"
            StoryReturnCapsuleState.DETACHED ->
                "СОБЫТИЕ ВНЕ КАТАЛОГА"
            StoryReturnCapsuleState.MISSING ->
                "НИТЬ НЕ НАЙДЕНА"
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                "СВЯЗЬ НЕ ПРОШЛА ПРОВЕРКУ"
        }
    }

    fun outcomeSummary(state: StoryReturnCapsuleState): String {
        return when (state) {
            StoryReturnCapsuleState.SEALED ->
                error("Sealed capsule has no return frame")
            StoryReturnCapsuleState.LIMIT_REACHED ->
                "Предел паузы пройден, но достижение исходной точки не заявляется."
            StoryReturnCapsuleState.UNCHANGED ->
                "Точка наступила, а локальная версия выбранного вопроса осталась прежней."
            StoryReturnCapsuleState.POINT_MOVED ->
                "Тот же вид проверяемой точки получил более позднее время."
            StoryReturnCapsuleState.CHANGED ->
                "Исходная пломба и текущая нить показывают смысловой переход."
            StoryReturnCapsuleState.RESOLVED ->
                "Текущий локальный сюжет закрыл выбранный вопрос."
            StoryReturnCapsuleState.MISSED ->
                "Предстартовый момент прошел без завершения выбранного вопроса."
            StoryReturnCapsuleState.DETACHED ->
                "Нить сохранена, но исходного события больше нет в каталоге."
            StoryReturnCapsuleState.MISSING ->
                "Связанная локальная нить отсутствует; исходная пломба сохранена."
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                "Текущая запись отклонена: SHA-256-проверка не пройдена."
        }
    }

    fun mapStateTitle(state: StoryThreadMapState): String {
        return when (state) {
            StoryThreadMapState.EMPTY -> "НЕТ"
            StoryThreadMapState.TAMPERED -> "НЕ ПРОВЕРЯЕТСЯ"
            StoryThreadMapState.DETACHED -> "ВНЕ КАТАЛОГА"
            StoryThreadMapState.MOVED -> "СДВИНУЛАСЬ"
            StoryThreadMapState.MISSED -> "УПУЩЕНА"
            StoryThreadMapState.OPEN -> "ОТКРЫТА"
            StoryThreadMapState.RESOLVED -> "ЗАКРЫТА"
        }
    }

    fun currentStateTitle(frame: StoryReturnFrame): String {
        return frame.currentState?.let(::mapStateTitle) ?: when (
            frame.outcome
        ) {
            StoryReturnCapsuleState.LIMIT_REACHED -> "НЕ ВСКРЫТО"
            StoryReturnCapsuleState.MISSING -> "НЕТ НИТИ"
            else -> error("Return frame requires current state")
        }
    }

    fun pointTitle(point: StoryReturnFramePoint): String {
        return when (point.kind) {
            StoryBeaconMomentKind.CHECK_WINDOW ->
                "Окно проверки: ${factorTitles(point.factors)}"
            StoryBeaconMomentKind.FACT_EXPIRY ->
                "Срок факта: ${factorTitles(point.factors)}"
            StoryBeaconMomentKind.START -> "Указанный старт"
            StoryBeaconMomentKind.REVIEW_OPEN -> "Откроется разбор"
            StoryBeaconMomentKind.ACTION_NOW,
            StoryBeaconMomentKind.COMPLETE ->
                error("Return frame requires an absolute moment")
        }
    }

    fun formatInstant(frame: StoryReturnFrame, timestamp: Long): String {
        return TimeBridgeEngine.formatInstant(
            startAt = timestamp,
            selectedZone = frame.selectedZone
        )
    }

    internal fun fingerprintFor(frame: StoryReturnFrame): String {
        val payload = buildString {
            appendToken(VERSION)
            appendToken(frame.eventId)
            appendToken(frame.eventLabel)
            appendToken(frame.chapter.name)
            appendToken(frame.activatedAt.toString())
            appendToken(frame.pauseUntil.toString())
            appendPoint(frame.originalPoint)
            appendToken(frame.beforeState.name)
            appendToken(frame.outcome.name)
            appendToken(frame.currentState?.name.orEmpty())
            val movedPoint = frame.movedPoint
            if (movedPoint == null) {
                appendToken("")
            } else {
                appendPoint(movedPoint)
            }
            appendToken(frame.resultFingerprint)
            appendToken(frame.selectedZone.name)
            appendToken(frame.generatedAt.toString())
        }
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val number = byte.toInt() and 0xff
                append(hex[number ushr 4])
                append(hex[number and 0x0f])
            }
        }
    }

    private fun StringBuilder.appendPoint(
        point: StoryReturnFramePoint
    ) {
        appendToken(point.kind.name)
        appendToken(point.at.toString())
        appendToken(point.factors.joinToString(",") { it.name })
    }

    private fun StringBuilder.appendToken(value: String) {
        append(value.length)
        append(':')
        append(value)
        append('|')
    }

    private fun factorTitles(factors: List<SignalFactor>): String {
        return factors.joinToString(", ") { it.title }
    }
}

internal class StoryReturnFrameRenderer(context: Context) {
    private val resources = context.applicationContext.resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val normal = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    fun render(frame: StoryReturnFrame): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIDTH,
            HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val tone = outcomeTone(frame.outcome)
        canvas.drawColor(AppColors.background)
        paint.color = tone.foreground
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)
        drawHeader(canvas)
        drawHero(canvas)
        drawStory(canvas, frame, tone)
        drawFooter(canvas, frame, tone)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas) {
        drawText(canvas, "СПОРТ ПУЛЬС", 72f, 82f, 38f, AppColors.ink, bold)
        drawText(
            canvas,
            "Один вопрос • честное возвращение",
            72f,
            119f,
            22f,
            AppColors.muted,
            normal
        )
        drawPill(
            canvas,
            "КАДР ВОЗВРАЩЕНИЯ • 18+",
            RectF(660f, 48f, 1008f, 108f),
            AppColors.accentSoft,
            AppColors.accentDark,
            16f
        )
    }

    private fun drawHero(canvas: Canvas) {
        val rect = RectF(72f, 145f, 1008f, 390f)
        val clip = Path().apply {
            addRoundRect(rect, 24f, 24f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = AppColors.ink
        canvas.drawRect(rect, paint)
        val source = BitmapFactory.decodeResource(
            resources,
            R.drawable.story_return_capsule
        )
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
                Color.argb(8, 9, 14, 17),
                Color.argb(66, 9, 14, 17),
                Color.argb(240, 9, 14, 17)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()
        drawText(
            canvas,
            "Кадр возвращения",
            104f,
            321f,
            48f,
            Color.WHITE,
            bold
        )
        drawText(
            canvas,
            "Исходная пломба → проверенный итог",
            104f,
            361f,
            21f,
            Color.rgb(220, 239, 234),
            bold
        )
    }

    private fun drawStory(
        canvas: Canvas,
        frame: StoryReturnFrame,
        tone: FrameTone
    ) {
        drawText(
            canvas,
            "ПОСЛЕ ТИШИНЫ",
            72f,
            444f,
            19f,
            tone.foreground,
            bold
        )
        drawPill(
            canvas,
            StoryReturnFrameFactory.outcomeTitle(frame.outcome),
            RectF(588f, 410f, 1008f, 468f),
            tone.background,
            tone.foreground,
            15f
        )
        drawTextBlock(
            canvas,
            frame.eventLabel,
            72f,
            478f,
            936,
            35f,
            AppColors.ink,
            bold,
            2
        )
        drawText(
            canvas,
            "ВОПРОС • ${StoryThreadPosterFactory.chapterTitle(
                frame.chapter
            ).uppercase(Locale.forLanguageTag("ru-RU"))}",
            72f,
            582f,
            17f,
            tone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            StoryThreadPosterFactory.question(frame.chapter),
            72f,
            604f,
            936,
            25f,
            AppColors.ink,
            bold,
            2
        )
        drawText(
            canvas,
            "ИСХОДНАЯ ТОЧКА",
            72f,
            694f,
            16f,
            AppColors.muted,
            bold
        )
        drawTextBlock(
            canvas,
            StoryReturnFrameFactory.pointTitle(frame.originalPoint),
            72f,
            713f,
            936,
            20f,
            AppColors.ink,
            bold,
            1
        )
        drawText(
            canvas,
            StoryReturnFrameFactory.formatInstant(
                frame,
                frame.originalPoint.at
            ),
            72f,
            761f,
            18f,
            tone.foreground,
            bold
        )
        drawTransition(canvas, frame, tone)
        drawOutcome(canvas, frame, tone)
        drawReturnDetail(canvas, frame, tone)
    }

    private fun drawTransition(
        canvas: Canvas,
        frame: StoryReturnFrame,
        tone: FrameTone
    ) {
        val left = 150f
        val center = WIDTH / 2f
        val right = 930f
        val y = 818f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 14f
        paint.color = AppColors.line
        canvas.drawLine(left, y, right, y, paint)
        paint.strokeWidth = 7f
        paint.color = AppColors.signal
        canvas.drawLine(left, y, center, y, paint)
        paint.color = tone.foreground
        canvas.drawLine(center, y, right, y, paint)
        drawAnchor(canvas, left, y, AppColors.signal)
        drawAnchor(canvas, right, y, tone.foreground)
        paint.style = Paint.Style.FILL
        paint.color = AppColors.surface
        canvas.drawRoundRect(
            RectF(center - 30f, y - 18f, center + 30f, y + 18f),
            15f,
            15f,
            paint
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = AppColors.ink
        canvas.drawRoundRect(
            RectF(center - 30f, y - 18f, center + 30f, y + 18f),
            15f,
            15f,
            paint
        )
        paint.style = Paint.Style.FILL
        paint.color = AppColors.accent
        canvas.drawRoundRect(
            RectF(center - 5f, y - 25f, center + 5f, y + 25f),
            4f,
            4f,
            paint
        )
        drawText(
            canvas,
            "ТОГДА",
            left,
            861f,
            15f,
            AppColors.muted,
            bold,
            Paint.Align.CENTER
        )
        drawText(
            canvas,
            "СЕЙЧАС",
            right,
            861f,
            15f,
            AppColors.muted,
            bold,
            Paint.Align.CENTER
        )
        drawPill(
            canvas,
            StoryReturnFrameFactory.mapStateTitle(frame.beforeState),
            RectF(72f, 879f, 444f, 937f),
            AppColors.signalSoft,
            AppColors.signal,
            16f
        )
        drawPill(
            canvas,
            StoryReturnFrameFactory.currentStateTitle(frame),
            RectF(636f, 879f, 1008f, 937f),
            tone.background,
            tone.foreground,
            16f
        )
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawOutcome(
        canvas: Canvas,
        frame: StoryReturnFrame,
        tone: FrameTone
    ) {
        val rect = RectF(72f, 962f, 1008f, 1088f)
        drawRoundRect(
            canvas,
            rect,
            20f,
            tone.background,
            tone.foreground
        )
        drawText(
            canvas,
            "ЧЕСТНЫЙ ИТОГ",
            104f,
            1001f,
            16f,
            tone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            StoryReturnFrameFactory.outcomeSummary(frame.outcome),
            104f,
            1020f,
            872,
            20f,
            tone.foreground,
            bold,
            2
        )
    }

    private fun drawReturnDetail(
        canvas: Canvas,
        frame: StoryReturnFrame,
        tone: FrameTone
    ) {
        val rect = RectF(72f, 1111f, 1008f, 1195f)
        drawRoundRect(
            canvas,
            rect,
            18f,
            AppColors.surface,
            AppColors.line
        )
        val label: String
        val detail: String
        when {
            frame.movedPoint != null -> {
                label = "НОВАЯ ТОЧКА"
                detail = "${StoryReturnFrameFactory.pointTitle(
                    frame.movedPoint
                )} • ${StoryReturnFrameFactory.formatInstant(
                    frame,
                    frame.movedPoint.at
                )}"
            }
            frame.outcome == StoryReturnCapsuleState.LIMIT_REACHED -> {
                label = "ПРЕДЕЛ ПАУЗЫ"
                detail = "24 часа • исходная точка еще впереди"
            }
            else -> {
                label = "ПОВТОРНАЯ ПРОВЕРКА"
                detail = StoryReturnFrameFactory.formatInstant(
                    frame,
                    frame.generatedAt
                )
            }
        }
        drawText(
            canvas,
            label,
            104f,
            1143f,
            15f,
            tone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            detail,
            104f,
            1158f,
            872,
            18f,
            AppColors.ink,
            bold,
            1
        )
    }

    private fun drawFooter(
        canvas: Canvas,
        frame: StoryReturnFrame,
        tone: FrameTone
    ) {
        paint.style = Paint.Style.FILL
        paint.color = AppColors.line
        canvas.drawRect(72f, 1220f, 1008f, 1222f, paint)
        drawText(
            canvas,
            "SHA-256 ${frame.shortFingerprint} • ИТОГ " +
                frame.shortResultFingerprint,
            72f,
            1260f,
            18f,
            tone.foreground,
            bold
        )
        drawText(
            canvas,
            formatDate(frame.generatedAt),
            1008f,
            1260f,
            16f,
            AppColors.muted,
            normal,
            Paint.Align.RIGHT
        )
        drawTextBlock(
            canvas,
            "Локальный информационный кадр. Не прогноз, " +
                "не ставка и не подтверждение внешнего результата.",
            72f,
            1284f,
            936,
            17f,
            AppColors.muted,
            normal,
            2
        )
    }

    private fun outcomeTone(state: StoryReturnCapsuleState): FrameTone {
        return when (state) {
            StoryReturnCapsuleState.SEALED ->
                error("Sealed capsule has no return frame")
            StoryReturnCapsuleState.UNCHANGED ->
                FrameTone(AppColors.signal, AppColors.signalSoft)
            StoryReturnCapsuleState.RESOLVED ->
                FrameTone(AppColors.accentDark, AppColors.accentSoft)
            StoryReturnCapsuleState.LIMIT_REACHED,
            StoryReturnCapsuleState.POINT_MOVED,
            StoryReturnCapsuleState.CHANGED,
            StoryReturnCapsuleState.DETACHED ->
                FrameTone(AppColors.warning, AppColors.warningSoft)
            StoryReturnCapsuleState.MISSED,
            StoryReturnCapsuleState.MISSING,
            StoryReturnCapsuleState.CURRENT_TAMPERED ->
                FrameTone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun drawAnchor(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = AppColors.surface
        canvas.drawCircle(x, y, 24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = color
        canvas.drawCircle(x, y, 20f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x, y, 9f, paint)
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

    private fun drawTextBlock(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        width: Int,
        size: Float,
        color: Int,
        typeface: Typeface,
        maxLines: Int
    ) {
        textPaint.textSize = size
        textPaint.color = color
        textPaint.typeface = typeface
        val layout = StaticLayout.Builder
            .obtain(value, 0, value.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawPill(
        canvas: Canvas,
        value: String,
        rect: RectF,
        background: Int,
        foreground: Int,
        textSize: Float
    ) {
        drawRoundRect(canvas, rect, rect.height() / 2f, background)
        paint.textSize = textSize
        paint.typeface = bold
        paint.textAlign = Paint.Align.CENTER
        paint.color = foreground
        val baseline = rect.centerY() -
            (paint.ascent() + paint.descent()) / 2f
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

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat(
            "d MMMM yyyy, HH:mm",
            Locale.forLanguageTag("ru-RU")
        ).format(Date(timestamp))
    }

    private data class FrameTone(
        val foreground: Int,
        val background: Int
    )

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
    }
}

internal class StoryReturnFrameExporter(context: Context) {
    private val applicationContext = context.applicationContext
    private val renderer = StoryReturnFrameRenderer(applicationContext)

    fun export(frame: StoryReturnFrame): File {
        val directory = File(
            applicationContext.cacheDir,
            AnalysisImageProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Не удалось создать каталог экспорта"
        }
        val output = File(
            directory,
            StoryReturnFrameFactory.fileName(frame)
        )
        val temporary = File(directory, ".${output.name}.tmp")
        val bitmap = renderer.render(frame)
        try {
            FileOutputStream(temporary).use { stream ->
                check(
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        stream
                    )
                ) {
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
            ?.filter {
                it.isFile &&
                    it.extension.equals("png", ignoreCase = true)
            }
            ?.sortedByDescending(File::lastModified)
            ?.drop(keep)
            ?.forEach(File::delete)
    }
}
