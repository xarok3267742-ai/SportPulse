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

internal data class CalibrationMemoryPassport(
    val memory: CalibrationMemory,
    val generatedAt: Long
)

internal object CalibrationMemoryPassportFactory {
    fun create(
        memory: CalibrationMemory,
        generatedAt: Long = System.currentTimeMillis()
    ): CalibrationMemoryPassport {
        require(memory.reviewCount > 0)
        require(generatedAt >= 0L)
        return CalibrationMemoryPassport(memory, generatedAt)
    }

    fun fileName(
        passport: CalibrationMemoryPassport
    ): String {
        return "sport_pulse_memory_${passport.generatedAt}.png"
    }

    fun shareText(
        passport: CalibrationMemoryPassport
    ): String {
        val memory = passport.memory
        return buildString {
            append("Память процесса «Спорт Пульс». Разборов: ")
            append(memory.reviewCount)
            append(". Проверяемых факторов: ")
            append(memory.verifiedFactorCount)
            append(". Покрытие: ")
            append(memory.coveragePercent)
            append("%")
            memory.overallScore?.let {
                append(". Качество данных: ")
                append(it)
                append("/100")
            }
            append(". Статус: ")
            append(statusTitle(memory.status).lowercase())
            memory.focusProfile?.let {
                append(". Фокус: ")
                append(it.factor.title.lowercase())
                append(", ")
                append(it.score ?: 0)
                append("/100")
            }
            memory.trend.delta?.let {
                append(". Тренд: ")
                append(if (it > 0) "+$it" else it)
            }
            append(". Цепочка ")
            append(memory.shortFingerprint)
            append(". Счет, коэффициенты и финансовые результаты не используются.")
        }
    }

    fun statusTitle(
        status: CalibrationMemoryStatus
    ): String {
        return when (status) {
            CalibrationMemoryStatus.LEARNING ->
                "СОБИРАЕМ БАЗУ"
            CalibrationMemoryStatus.STABLE ->
                "УСТОЙЧИВО"
            CalibrationMemoryStatus.UNEVEN ->
                "НЕРОВНО"
            CalibrationMemoryStatus.BLIND_SPOT ->
                "СЛЕПАЯ ЗОНА"
        }
    }
}

internal class CalibrationMemoryPassportRenderer(
    context: Context
) {
    private val resources = context.applicationContext.resources
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

    fun render(passport: CalibrationMemoryPassport): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIDTH,
            HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(AppColors.background)
        paint.color = AppColors.accent
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)
        drawHeader(canvas)
        drawHero(canvas)
        drawMemory(canvas, passport.memory)
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
            "Факты отделены от результата",
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
        val rect = RectF(72f, 145f, 1008f, 430f)
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
            R.drawable.pulse_workspace
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
                Color.argb(15, 12, 18, 22),
                Color.argb(95, 12, 18, 22),
                Color.argb(238, 12, 18, 22)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()
        drawText(
            canvas,
            "Память процесса",
            104f,
            333f,
            49f,
            Color.WHITE,
            bold
        )
        drawText(
            canvas,
            "Повторяющиеся ошибки видны без оглядки на счет",
            104f,
            379f,
            22f,
            Color.rgb(220, 231, 233),
            bold
        )
    }

    private fun drawMemory(
        canvas: Canvas,
        memory: CalibrationMemory
    ) {
        val tone = tone(memory.status)
        drawText(
            canvas,
            "КАРТА СЛЕПЫХ ЗОН",
            72f,
            492f,
            24f,
            AppColors.accent,
            bold
        )
        drawPill(
            canvas,
            CalibrationMemoryPassportFactory.statusTitle(
                memory.status
            ),
            RectF(716f, 458f, 1008f, 514f),
            tone.background,
            tone.foreground,
            18f
        )

        drawText(
            canvas,
            memory.overallScore?.toString() ?: "—",
            72f,
            656f,
            108f,
            tone.foreground,
            bold
        )
        drawText(
            canvas,
            if (memory.overallScore == null) {
                "нет оценки"
            } else {
                "из 100"
            },
            250f,
            641f,
            25f,
            AppColors.muted,
            normal
        )
        drawText(
            canvas,
            "${memory.reviewCount} ${
                reviewWord(memory.reviewCount)
            } • покрытие ${memory.coveragePercent}%",
            72f,
            693f,
            20f,
            AppColors.muted,
            bold
        )

        val summaryRect = RectF(430f, 535f, 1008f, 690f)
        drawRoundRect(
            canvas,
            summaryRect,
            18f,
            tone.background
        )
        drawTextBlock(
            canvas,
            summary(memory),
            458f,
            560f,
            522,
            21f,
            tone.foreground,
            bold,
            4
        )

        drawText(
            canvas,
            "ПОСЛЕДНИЕ РАЗБОРЫ • БАЛЛ · НАБЛЮДЕНИЯ",
            72f,
            732f,
            18f,
            AppColors.muted,
            bold
        )
        drawMatrix(canvas, memory)

        val visibleRowCount = memory.reviewResults
            .takeLast(MAX_ROWS)
            .size
            .coerceAtLeast(1)
        val focusTop = 814f + visibleRowCount * 40f
        val focusRect = RectF(
            72f,
            focusTop,
            1008f,
            focusTop + 82f
        )
        drawRoundRect(
            canvas,
            focusRect,
            18f,
            AppColors.surface,
            if (
                memory.status ==
                CalibrationMemoryStatus.BLIND_SPOT
            ) {
                AppColors.danger
            } else {
                AppColors.line
            }
        )
        drawTextBlock(
            canvas,
            focus(memory),
            98f,
            focusTop + 20f,
            884,
            19f,
            AppColors.ink,
            bold,
            2
        )
        drawText(
            canvas,
            trend(memory.trend),
            72f,
            focusTop + 113f,
            18f,
            AppColors.muted,
            bold
        )
        drawPill(
            canvas,
            "ЦЕПОЧКА ${memory.shortFingerprint} • ${memory.verifiedFactorCount} ФАКТОРОВ",
            RectF(
                72f,
                focusTop + 132f,
                1008f,
                focusTop + 194f
            ),
            AppColors.signalSoft,
            AppColors.signal,
            19f,
            AppColors.signal
        )
        drawTextCentered(
            canvas,
            "Счет, коэффициенты и финансовый результат не использованы",
            WIDTH / 2f,
            focusTop + 220f,
            17f,
            AppColors.muted,
            bold
        )
    }

    private fun drawMatrix(
        canvas: Canvas,
        memory: CalibrationMemory
    ) {
        val results = memory.reviewResults
            .takeLast(MAX_ROWS)
            .asReversed()
        val left = 112f
        val right = 1008f
        val gap = 12f
        val columnWidth = (
            right - left -
                gap * (SignalFactor.values().size - 1)
            ) / SignalFactor.values().size
        val labels = listOf(
            "Форма",
            "Состав",
            "Нагрузка",
            "Контекст",
            "Источники"
        )
        labels.forEachIndexed { index, label ->
            val center = left +
                index * (columnWidth + gap) +
                columnWidth / 2f
            drawTextCentered(
                canvas,
                label,
                center,
                765f,
                15f,
                AppColors.ink,
                bold
            )
            val profile = memory.factorProfiles[index]
            drawTextCentered(
                canvas,
                "${profile.score ?: "—"} · ${profile.verifiedCount}",
                center,
                788f,
                14f,
                AppColors.muted,
                bold
            )
        }
        results.forEachIndexed { rowIndex, result ->
            val top = 802f + rowIndex * 40f
            drawTextCentered(
                canvas,
                "#${memory.reviewCount - rowIndex}",
                88f,
                top + 26f,
                14f,
                AppColors.muted,
                bold
            )
            result.factorResults.forEachIndexed {
                    columnIndex,
                    factorResult ->
                val cellLeft = left +
                    columnIndex * (columnWidth + gap)
                val rect = RectF(
                    cellLeft,
                    top,
                    cellLeft + columnWidth,
                    top + 32f
                )
                val outcomeTone = outcomeTone(
                    factorResult.outcome
                )
                drawRoundRect(
                    canvas,
                    rect,
                    10f,
                    outcomeTone.background,
                    if (
                        memory.focusProfile?.factor ==
                        factorResult.factor
                    ) {
                        if (
                            memory.status ==
                            CalibrationMemoryStatus.BLIND_SPOT
                        ) {
                            AppColors.danger
                        } else {
                            AppColors.signal
                        }
                    } else {
                        Color.TRANSPARENT
                    }
                )
                drawTextCentered(
                    canvas,
                    outcomeTitle(factorResult.outcome),
                    rect.centerX(),
                    rect.centerY() -
                        textBaselineOffset(15f),
                    15f,
                    outcomeTone.foreground,
                    bold
                )
            }
        }
    }

    private fun summary(memory: CalibrationMemory): String {
        return when (memory.status) {
            CalibrationMemoryStatus.LEARNING ->
                "Профиль предварительный. Нужны минимум три разбора и девять проверяемых факторов."
            CalibrationMemoryStatus.STABLE ->
                "Исходные данные устойчивы на серии разборов. Это качество процесса, не прогнозов."
            CalibrationMemoryStatus.UNEVEN ->
                "Общий балл скрывает неравномерность факторов. Смотрите на слабый столбец."
            CalibrationMemoryStatus.BLIND_SPOT -> {
                if (memory.criticalMissCount > 0) {
                    "Кворум источников не всегда выдерживал проверку. Критических ошибок: ${memory.criticalMissCount}."
                } else {
                    "Повторяющийся фактор ниже 45/100 скрывался за общим средним."
                }
            }
        }
    }

    private fun focus(memory: CalibrationMemory): String {
        val profile = memory.focusProfile
            ?: return "Фокус появится после первого проверяемого фактора."
        val prefix = if (
            memory.status ==
            CalibrationMemoryStatus.BLIND_SPOT
        ) {
            "Слепая зона"
        } else {
            "Фокус следующей проверки"
        }
        return "$prefix: «${profile.factor.title}» • ${profile.score ?: "—"}/100 • ${profile.verifiedCount} наблюдений."
    }

    private fun trend(value: CalibrationTrend): String {
        return when (value.status) {
            CalibrationTrendStatus.INSUFFICIENT ->
                "Тренд появится после четырех проверяемых разборов."
            CalibrationTrendStatus.IMPROVING ->
                "Тренд: ${value.previousScore}→${value.recentScore} (+${value.delta})."
            CalibrationTrendStatus.STABLE ->
                "Тренд: ${value.previousScore}→${value.recentScore} (${signed(value.delta)}), стабильно."
            CalibrationTrendStatus.DECLINING ->
                "Тренд: ${value.previousScore}→${value.recentScore} (${value.delta}), снижение."
        }
    }

    private fun reviewWord(count: Int): String {
        val normalized = count % 100
        return when {
            normalized in 11..14 -> "разборов"
            count % 10 == 1 -> "разбор"
            count % 10 in 2..4 -> "разбора"
            else -> "разборов"
        }
    }

    private fun outcomeTitle(
        outcome: PostEventOutcome
    ): String {
        return when (outcome) {
            PostEventOutcome.CONFIRMED -> "ДА"
            PostEventOutcome.PARTIAL -> "1/2"
            PostEventOutcome.DISPROVED -> "НЕТ"
            PostEventOutcome.UNKNOWN -> "?"
            PostEventOutcome.UNREVIEWED -> "—"
        }
    }

    private fun tone(
        status: CalibrationMemoryStatus
    ): Tone {
        return when (status) {
            CalibrationMemoryStatus.LEARNING ->
                Tone(AppColors.signal, AppColors.signalSoft)
            CalibrationMemoryStatus.STABLE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            CalibrationMemoryStatus.UNEVEN ->
                Tone(AppColors.warning, AppColors.warningSoft)
            CalibrationMemoryStatus.BLIND_SPOT ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun outcomeTone(
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

    private fun drawHeaderLine(
        canvas: Canvas,
        color: Int
    ) {
        paint.color = color
        canvas.drawRect(72f, 1250f, 1008f, 1252f, paint)
    }

    private fun drawFooter(
        canvas: Canvas,
        generatedAt: Long
    ) {
        drawHeaderLine(canvas, AppColors.line)
        drawText(
            canvas,
            "Создано ${formatDate(generatedAt)}",
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

    private fun textBaselineOffset(size: Float): Float {
        paint.textSize = size
        paint.typeface = bold
        return (paint.ascent() + paint.descent()) / 2f
    }

    private fun signed(value: Int?): String {
        val actual = value ?: 0
        return if (actual > 0) "+$actual" else actual.toString()
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

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        const val MAX_ROWS = 5
    }
}

internal class CalibrationMemoryPassportExporter(
    context: Context
) {
    private val applicationContext =
        context.applicationContext
    private val renderer = CalibrationMemoryPassportRenderer(
        applicationContext
    )

    fun export(
        passport: CalibrationMemoryPassport
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
            CalibrationMemoryPassportFactory.fileName(passport)
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
