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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class DecisionGuardPassport(
    val event: SportEvent,
    val guard: DecisionGuardResult,
    val generatedAt: Long
)

internal object DecisionGuardPassportFactory {
    private val unsafeFileCharacters =
        Regex("[^A-Za-z0-9_-]+")

    fun create(
        event: SportEvent,
        guard: DecisionGuardResult,
        generatedAt: Long = System.currentTimeMillis()
    ): DecisionGuardPassport {
        require(event.id == guard.plan.eventId)
        require(generatedAt >= 0L)
        return DecisionGuardPassport(
            event = event,
            guard = guard,
            generatedAt = generatedAt
        )
    }

    fun fileName(
        passport: DecisionGuardPassport
    ): String {
        val safeEventId = unsafeFileCharacters
            .replace(passport.event.id, "_")
            .trim('_')
            .take(48)
            .ifBlank { "event" }
        return "sport_pulse_guard_${safeEventId}_" +
            "${passport.generatedAt}.png"
    }

    fun shareText(
        passport: DecisionGuardPassport
    ): String {
        val guard = passport.guard
        return buildString {
            append("Стоп-контракт «")
            append(passport.event.match)
            append("». ")
            append(statusTitle(guard.status))
            append(". Локальная пломба ")
            append(guard.plan.shortSeal)
            guard.plan.condition?.let { condition ->
                append(". Критический фактор: ")
                append(condition.factor.title)
                append(", запечатано ")
                append(condition.baselineValue)
                condition.scoreFloor?.let {
                    append(", стоп-линия ≤ ")
                    append(it)
                }
                condition.requiredEvidence?.let {
                    append(", минимум подтверждения: ")
                    append(it.title)
                }
            }
            guard.breach?.let { breach ->
                append(". Первое нарушение зафиксировано ")
                append(formatDate(breach.triggeredAt))
                append(", метка нарушения ")
                append(breach.shortFingerprint)
            }
            append(
                ". Пломба фиксирует заранее выбранное условие " +
                    "отмены решения; это не прогноз, не ставка " +
                    "и не гарантия результата."
            )
        }
    }

    private fun formatDate(
        timestamp: Long
    ): String {
        return SimpleDateFormat(
            "d MMMM yyyy, HH:mm",
            Locale.forLanguageTag("ru-RU")
        ).format(Date(timestamp))
    }

    internal fun statusTitle(
        status: DecisionGuardStatus
    ): String {
        return when (status) {
            DecisionGuardStatus.SEALED_SKIP ->
                "Пропуск запечатан"
            DecisionGuardStatus.ARMED ->
                "Условие действует"
            DecisionGuardStatus.TRIGGERED ->
                "Стоп-контракт сработал"
        }
    }
}

internal class DecisionGuardPassportRenderer(
    context: Context
) {
    private val resources =
        context.applicationContext.resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Typeface.create(
        Typeface.DEFAULT,
        Typeface.BOLD
    )
    private val normal = Typeface.create(
        Typeface.DEFAULT,
        Typeface.NORMAL
    )

    fun render(
        passport: DecisionGuardPassport
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIDTH,
            HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(AppColors.background)
        paint.color = AppColors.danger
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)
        drawHeader(canvas)
        drawHero(canvas)
        drawContract(canvas, passport)
        drawFooter(canvas, passport.generatedAt)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas) {
        drawText(
            canvas,
            "СПОРТ ПУЛЬС",
            72f,
            82f,
            38f,
            AppColors.ink,
            bold
        )
        drawText(
            canvas,
            "Условие отмены фиксируется до новых данных",
            72f,
            119f,
            22f,
            AppColors.muted,
            normal
        )
        drawPill(
            canvas,
            "ЛОКАЛЬНО • 18+",
            RectF(760f, 48f, 1008f, 108f),
            AppColors.signalSoft,
            AppColors.signal,
            19f
        )
    }

    private fun drawHero(canvas: Canvas) {
        val rect = RectF(72f, 145f, 1008f, 432f)
        val clip = Path().apply {
            addRoundRect(
                rect,
                28f,
                28f,
                Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = AppColors.ink
        canvas.drawRect(rect, paint)
        val source = BitmapFactory.decodeResource(
            resources,
            R.drawable.decision_guard
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
                Color.argb(4, 10, 14, 17),
                Color.argb(38, 10, 14, 17),
                Color.argb(238, 10, 14, 17)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()
        drawText(
            canvas,
            "Стоп-контракт",
            104f,
            334f,
            49f,
            Color.WHITE,
            bold
        )
        drawText(
            canvas,
            "Критический факт запечатан до обновления решения",
            104f,
            381f,
            21f,
            Color.rgb(224, 234, 235),
            bold
        )
    }

    private fun drawContract(
        canvas: Canvas,
        passport: DecisionGuardPassport
    ) {
        val guard = passport.guard
        val tone = tone(guard.status)
        drawText(
            canvas,
            "СТОП-КОНТРАКТ",
            72f,
            492f,
            24f,
            AppColors.danger,
            bold
        )
        drawPill(
            canvas,
            statusTitle(guard.status),
            RectF(618f, 456f, 1008f, 514f),
            tone.background,
            tone.foreground,
            16f,
            tone.foreground
        )
        drawTextBlock(
            canvas,
            passport.event.match,
            72f,
            532f,
            936,
            37f,
            AppColors.ink,
            bold,
            2
        )
        drawTextBlock(
            canvas,
            "${passport.event.tournament} • ${passport.event.region}",
            72f,
            630f,
            936,
            19f,
            AppColors.muted,
            bold,
            1
        )

        val card = RectF(72f, 676f, 1008f, 1002f)
        drawRoundRect(
            canvas,
            card,
            24f,
            AppColors.surface,
            AppColors.line
        )
        drawText(
            canvas,
            "ПЛОМБА ${guard.plan.shortSeal}",
            104f,
            724f,
            21f,
            tone.foreground,
            bold
        )
        drawText(
            canvas,
            formatDate(guard.plan.armedAt),
            976f,
            724f,
            18f,
            AppColors.muted,
            bold,
            Paint.Align.RIGHT
        )
        val condition = guard.plan.condition
        if (condition == null) {
            drawSealedSkip(canvas, guard)
        } else {
            drawCondition(canvas, guard, condition)
        }

        drawCurrentState(
            canvas = canvas,
            guard = guard,
            tone = tone
        )
    }

    private fun drawSealedSkip(
        canvas: Canvas,
        guard: DecisionGuardResult
    ) {
        drawText(
            canvas,
            "РЕШЕНИЕ: ПРОПУСТИТЬ",
            104f,
            784f,
            32f,
            AppColors.signal,
            bold
        )
        drawTextBlock(
            canvas,
            "Изменения карты не повышают старый вывод задним числом. Для нового статуса нужен новый снимок решения.",
            104f,
            818f,
            840,
            22f,
            AppColors.ink,
            normal,
            3
        )
        drawSealMark(
            canvas,
            860f,
            904f,
            AppColors.signal
        )
        drawText(
            canvas,
            "СНИМОК ${guard.plan.snapshotFingerprint.take(8).uppercase()}",
            104f,
            953f,
            19f,
            AppColors.muted,
            bold
        )
    }

    private fun drawCondition(
        canvas: Canvas,
        guard: DecisionGuardResult,
        condition: DecisionGuardCondition
    ) {
        drawText(
            canvas,
            condition.factor.title,
            104f,
            780f,
            34f,
            AppColors.ink,
            bold
        )
        val floorText = condition.scoreFloor?.let {
            " • стоп ≤ $it"
        }.orEmpty()
        drawText(
            canvas,
            "запечатано ${condition.baselineValue}$floorText",
            976f,
            780f,
            20f,
            AppColors.muted,
            bold,
            Paint.Align.RIGHT
        )
        drawGauge(
            canvas = canvas,
            guard = guard,
            condition = condition,
            rect = RectF(104f, 812f, 976f, 902f)
        )
        val evidence = condition.requiredEvidence?.let {
            "Минимум: ${it.title}"
        } ?: "Подтверждение не входило в пломбу"
        val expiry = condition.evidenceValidUntil?.let {
            " • контроль до ${formatDate(it)}"
        }.orEmpty()
        drawTextBlock(
            canvas,
            evidence + expiry,
            104f,
            926f,
            840,
            19f,
            AppColors.muted,
            bold,
            2
        )
    }

    private fun drawGauge(
        canvas: Canvas,
        guard: DecisionGuardResult,
        condition: DecisionGuardCondition,
        rect: RectF
    ) {
        val y = rect.centerY()
        val floor = condition.scoreFloor
        paint.strokeWidth = 18f
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.STROKE
        if (floor == null) {
            paint.color = AppColors.signalSoft
            canvas.drawLine(
                rect.left,
                y,
                rect.right,
                y,
                paint
            )
        } else {
            val floorX = gaugeX(floor, rect)
            paint.color = AppColors.dangerSoft
            canvas.drawLine(
                rect.left,
                y,
                floorX,
                y,
                paint
            )
            paint.color = AppColors.accentSoft
            canvas.drawLine(
                floorX,
                y,
                rect.right,
                y,
                paint
            )
            paint.style = Paint.Style.FILL
            paint.color = AppColors.danger
            canvas.drawRect(
                floorX - 2f,
                rect.top + 4f,
                floorX + 2f,
                rect.bottom - 4f,
                paint
            )
        }
        paint.style = Paint.Style.FILL
        val baselineX = gaugeX(
            condition.baselineValue,
            rect
        )
        paint.color = AppColors.ink
        canvas.drawCircle(baselineX, y, 12f, paint)
        val current = guard.currentFactorValue
            ?: condition.baselineValue
        val currentX = gaugeX(current, rect)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.color = if (guard.isTriggered) {
            AppColors.danger
        } else {
            AppColors.accent
        }
        canvas.drawCircle(currentX, y, 20f, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        drawText(
            canvas,
            "0",
            rect.left,
            rect.bottom,
            16f,
            AppColors.muted,
            bold
        )
        drawText(
            canvas,
            "100",
            rect.right,
            rect.bottom,
            16f,
            AppColors.muted,
            bold,
            Paint.Align.RIGHT
        )
        val currentLabel = "сейчас $current"
        paint.textSize = 17f
        paint.typeface = bold
        val labelHalfWidth =
            paint.measureText(currentLabel) / 2f
        val currentLabelX = currentX.coerceIn(
            rect.left + labelHalfWidth,
            rect.right - labelHalfWidth
        )
        drawText(
            canvas,
            currentLabel,
            currentLabelX,
            rect.top + 6f,
            17f,
            if (guard.isTriggered) {
                AppColors.danger
            } else {
                AppColors.accentDark
            },
            bold,
            Paint.Align.CENTER
        )
    }

    private fun drawCurrentState(
        canvas: Canvas,
        guard: DecisionGuardResult,
        tone: Tone
    ) {
        drawText(
            canvas,
            guard.breach?.let {
                "СОСТОЯНИЕ СЕЙЧАС • НАРУШЕНИЕ " +
                    it.shortFingerprint
            } ?: "СОСТОЯНИЕ СЕЙЧАС",
            72f,
            1054f,
            19f,
            AppColors.muted,
            bold
        )
        val baseline =
            guard.baselineResult.effectiveSignal.readiness
        val current =
            guard.currentResult.effectiveSignal.readiness
        drawText(
            canvas,
            "$baseline → $current",
            72f,
            1113f,
            44f,
            tone.foreground,
            bold
        )
        drawPill(
            canvas,
            verdictTitle(
                guard.currentResult.effectiveSignal.verdict
            ),
            RectF(680f, 1067f, 1008f, 1122f),
            tone.background,
            tone.foreground,
            17f
        )
        drawTextBlock(
            canvas,
            statusSummary(guard),
            72f,
            1145f,
            936,
            21f,
            AppColors.ink,
            normal,
            3
        )
    }

    private fun drawFooter(
        canvas: Canvas,
        generatedAt: Long
    ) {
        paint.color = AppColors.line
        canvas.drawRect(72f, 1245f, 1008f, 1247f, paint)
        drawText(
            canvas,
            "Создан ${formatDate(generatedAt)} • локальная SHA-256-метка, не электронная подпись",
            72f,
            1280f,
            17f,
            AppColors.muted,
            normal
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

    private fun statusSummary(
        guard: DecisionGuardResult
    ): String {
        if (guard.status == DecisionGuardStatus.SEALED_SKIP) {
            return "Пропуск остается исходным выводом. Новая карта требует нового снимка."
        }
        if (!guard.isTriggered) {
            return "Пломба цела: текущие данные не пересекли заранее зафиксированное условие отмены."
        }
        if (guard.isRecoveredAfterBreach) {
            val breach = requireNotNull(guard.breach)
            return "Пломба была нарушена ${
                formatDate(breach.triggeredAt)
            }. Текущая карта восстановилась, но нужен новый снимок."
        }
        return guard.effectiveCauses.joinToString(" ") { cause ->
            when (cause) {
                DecisionGuardCause.DECISION_ABOVE_SIGNAL ->
                    "Вывод был выше уровня подтвержденных данных уже при фиксации."
                DecisionGuardCause.SIGNAL_BELOW_CONTRACT ->
                    "Текущий статус ниже зафиксированного уровня."
                DecisionGuardCause.FACTOR_FLOOR -> {
                    val condition = requireNotNull(
                        guard.plan.condition
                    )
                    "Фактор «${condition.factor.title}» " +
                        "пересек стоп-линию " +
                        "${condition.scoreFloor} " +
                        "на значении ${
                            guard.breach?.factorValue
                                ?: guard.currentFactorValue
                        }."
                }
                DecisionGuardCause.EVIDENCE_LOSS -> {
                    val condition = requireNotNull(
                        guard.plan.condition
                    )
                    "Подтверждение «${condition.factor.title}» стало слабее пломбы."
                }
                DecisionGuardCause.COUNTERVIEW_LIMIT ->
                    "Контрракурс ограничил допустимый вывод ниже сохраненного решения."
            }
        }
    }

    private fun statusTitle(
        status: DecisionGuardStatus
    ): String {
        return when (status) {
            DecisionGuardStatus.SEALED_SKIP ->
                "ПРОПУСК ЗАПЕЧАТАН"
            DecisionGuardStatus.ARMED ->
                "УСЛОВИЕ ДЕЙСТВУЕТ"
            DecisionGuardStatus.TRIGGERED ->
                "КОНТРАКТ СРАБОТАЛ"
        }
    }

    private fun verdictTitle(
        verdict: SignalVerdict
    ): String {
        return when (verdict) {
            SignalVerdict.SKIP -> "ПРОПУСТИТЬ"
            SignalVerdict.OBSERVE -> "НАБЛЮДАТЬ"
            SignalVerdict.READY -> "ФАКТЫ СВЕРЕНЫ"
        }
    }

    private fun tone(
        status: DecisionGuardStatus
    ): Tone {
        return when (status) {
            DecisionGuardStatus.SEALED_SKIP ->
                Tone(AppColors.signal, AppColors.signalSoft)
            DecisionGuardStatus.ARMED ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            DecisionGuardStatus.TRIGGERED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun drawSealMark(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        color: Int
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(centerX, centerY, 54f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 9f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.WHITE
        val path = Path().apply {
            moveTo(centerX - 24f, centerY)
            lineTo(centerX - 7f, centerY + 18f)
            lineTo(centerX + 28f, centerY - 21f)
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun gaugeX(
        value: Int,
        rect: RectF
    ): Float {
        return rect.left +
            rect.width() * value.coerceIn(0, 100) / 100f
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF
    ) {
        val targetRatio =
            destination.width() / destination.height()
        val sourceRatio =
            bitmap.width.toFloat() / bitmap.height.toFloat()
        val source = if (sourceRatio > targetRatio) {
            val cropWidth =
                (bitmap.height * targetRatio).toInt()
            val left = (bitmap.width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, bitmap.height)
        } else {
            val cropHeight =
                (bitmap.width / targetRatio).toInt()
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
        textSize: Float,
        stroke: Int = Color.TRANSPARENT
    ) {
        drawRoundRect(
            canvas,
            rect,
            rect.height() / 2f,
            background,
            stroke
        )
        paint.textSize = textSize
        paint.typeface = bold
        paint.textAlign = Paint.Align.CENTER
        paint.color = foreground
        val baseline = rect.centerY() -
            (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(
            value,
            rect.centerX(),
            baseline,
            paint
        )
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

    private fun formatDate(
        timestamp: Long
    ): String {
        return SimpleDateFormat(
            "d MMMM yyyy, HH:mm",
            Locale.forLanguageTag("ru-RU")
        ).format(Date(timestamp))
    }

    private data class Tone(
        val foreground: Int,
        val background: Int
    )

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
    }
}

internal class DecisionGuardPassportExporter(
    context: Context
) {
    private val applicationContext =
        context.applicationContext
    private val renderer = DecisionGuardPassportRenderer(
        applicationContext
    )

    fun export(
        passport: DecisionGuardPassport
    ): File {
        val directory = File(
            applicationContext.cacheDir,
            AnalysisImageProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Не удалось создать каталог экспорта"
        }
        val output = File(
            directory,
            DecisionGuardPassportFactory.fileName(passport)
        )
        val temporary = File(
            directory,
            ".${output.name}.tmp"
        )
        val bitmap = renderer.render(passport)
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

    private fun cleanup(
        directory: File,
        keep: Int
    ) {
        directory.listFiles()
            ?.filter {
                it.isFile &&
                    it.extension.equals(
                        "png",
                        ignoreCase = true
                    )
            }
            ?.sortedByDescending(File::lastModified)
            ?.drop(keep)
            ?.forEach(File::delete)
    }
}
