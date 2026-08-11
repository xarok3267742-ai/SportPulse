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

internal data class FactExpressPoster(
    val result: FactExpressResult,
    val generatedAt: Long
) {
    init {
        require(result.isReady)
        require(generatedAt >= 0L)
        require(generatedAt / MINUTE_MILLIS == result.evaluatedAtMinute)
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
    }
}

internal object FactExpressPosterFactory {
    fun create(
        result: FactExpressResult,
        generatedAt: Long = System.currentTimeMillis()
    ): FactExpressPoster {
        return FactExpressPoster(
            result = result,
            generatedAt = generatedAt
        )
    }

    fun fileName(poster: FactExpressPoster): String {
        return "sport_pulse_fact_express_${
            poster.result.shortFingerprint.lowercase()
        }_${poster.generatedAt}.png"
    }

    fun shareText(poster: FactExpressPoster): String {
        val result = poster.result
        return buildString {
            append("Маршрут фактов: ")
            append(result.entries.size)
            append(" события без коэффициентов. ")
            result.entries.forEachIndexed { index, entry ->
                append(index + 1)
                append(". ")
                append(entry.match)
                append(" — ")
                append(FactExpressText.actionTitle(entry).lowercase())
                append(". ")
                entry.nextMoment?.let { moment ->
                    append("Точка: ")
                    append(
                        FactExpressText.momentTitle(moment)
                            .lowercase()
                    )
                    append(", ")
                    append(
                        FactExpressText.momentTime(
                            moment,
                            result.selectedZone
                        )
                    )
                    append(". ")
                }
            }
            append("Контрольная метка ")
            append(result.shortFingerprint)
            append(
                ". Это локальный информационный маршрут: " +
                    "не ставка, не экспресс исходов, без " +
                    "коэффициентов и без расчета выплаты."
            )
        }
    }
}

internal class FactExpressPosterRenderer(context: Context) {
    private val resources = context.applicationContext.resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val normal = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    fun render(poster: FactExpressPoster): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIDTH,
            HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(AppColors.background)
        paint.color = AppColors.accentDark
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)
        drawHeader(canvas)
        drawHero(canvas)
        drawRoute(canvas, poster)
        drawFooter(canvas, poster)
        return bitmap
    }

    private fun drawHeader(canvas: Canvas) {
        drawText(canvas, "СПОРТ ПУЛЬС", 72f, 82f, 38f, AppColors.ink, bold)
        drawText(
            canvas,
            "Соберите проверки, не исходы",
            72f,
            119f,
            22f,
            AppColors.muted,
            normal
        )
        drawPill(
            canvas,
            "МАРШРУТ ФАКТОВ • 18+",
            RectF(685f, 48f, 1008f, 108f),
            AppColors.accentSoft,
            AppColors.accentDark,
            16f
        )
    }

    private fun drawHero(canvas: Canvas) {
        val rect = RectF(72f, 145f, 1008f, 385f)
        val clip = Path().apply {
            addRoundRect(rect, 24f, 24f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = AppColors.ink
        canvas.drawRect(rect, paint)
        val source = BitmapFactory.decodeResource(
            resources,
            R.drawable.fact_express
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
                Color.argb(0, 9, 14, 17),
                Color.argb(48, 9, 14, 17),
                Color.argb(242, 9, 14, 17)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()
        drawText(
            canvas,
            "Маршрут фактов",
            104f,
            316f,
            49f,
            Color.WHITE,
            bold
        )
        drawText(
            canvas,
            "2–4 события • один маршрут проверки",
            104f,
            357f,
            21f,
            Color.rgb(220, 239, 234),
            bold
        )
    }

    private fun drawRoute(
        canvas: Canvas,
        poster: FactExpressPoster
    ) {
        val result = poster.result
        drawText(
            canvas,
            "МАРШРУТ СОБРАН",
            72f,
            438f,
            19f,
            AppColors.accentDark,
            bold
        )
        drawPill(
            canvas,
            "${result.entries.size} СОБЫТИЯ",
            RectF(754f, 405f, 1008f, 463f),
            AppColors.accentSoft,
            AppColors.accentDark,
            16f
        )
        drawText(
            canvas,
            "Проверок сейчас ${result.actionNowCount} • " +
                "точек времени ${result.scheduledCount}",
            72f,
            487f,
            23f,
            AppColors.ink,
            bold
        )
        drawTextBlock(
            canvas,
            "Порядок: действие сейчас → ближайшая точка → " +
                "неопределенное время → завершенный сюжет.",
            72f,
            507f,
            936,
            18f,
            AppColors.muted,
            normal,
            2
        )
        drawRail(canvas, result)
        val entriesTop = 663f
        val entriesHeight = 444f
        val entrySlotHeight = entriesHeight / result.entries.size
        result.entries.forEachIndexed { index, entry ->
            val slotTop = entriesTop + index * entrySlotHeight
            drawEntry(
                canvas = canvas,
                number = index + 1,
                entry = entry,
                selectedZone = result.selectedZone,
                top = slotTop + (entrySlotHeight - 111f) / 2f,
                separatorTop = slotTop + entrySlotHeight - 6f
            )
        }
        val contractTop = entriesTop + entriesHeight + 8f
        drawContract(canvas, contractTop)
    }

    private fun drawRail(
        canvas: Canvas,
        result: FactExpressResult
    ) {
        val left = 132f
        val gate = 948f
        val right = gate - 62f
        val y = 611f
        val count = result.entries.size
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 13f
        paint.color = AppColors.line
        canvas.drawLine(left, y, gate, y, paint)
        paint.strokeWidth = 6f
        paint.color = AppColors.accent
        canvas.drawLine(left, y, gate, y, paint)
        result.entries.forEachIndexed { index, entry ->
            val x = if (count == 1) {
                left
            } else {
                left + (right - left) * index / (count - 1)
            }
            val color = entryTone(entry.state).foreground
            paint.style = Paint.Style.FILL
            paint.color = AppColors.surface
            canvas.drawRoundRect(
                RectF(x - 27f, y - 18f, x + 27f, y + 18f),
                9f,
                9f,
                paint
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = color
            canvas.drawRoundRect(
                RectF(x - 27f, y - 18f, x + 27f, y + 18f),
                9f,
                9f,
                paint
            )
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 7f, paint)
            if (entry.nextMoment != null) {
                paint.color = AppColors.warning
                canvas.drawCircle(x + 19f, y - 18f, 5f, paint)
            }
            drawText(
                canvas,
                (index + 1).toString(),
                x,
                651f,
                14f,
                color,
                bold,
                Paint.Align.CENTER
            )
        }
        paint.style = Paint.Style.FILL
        paint.color = AppColors.surface
        canvas.drawCircle(gate, y, 25f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = AppColors.accentDark
        canvas.drawCircle(gate, y, 21f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(gate, y, 9f, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun drawEntry(
        canvas: Canvas,
        number: Int,
        entry: FactExpressEntry,
        selectedZone: RegionalZone,
        top: Float,
        separatorTop: Float
    ) {
        val tone = entryTone(entry.state)
        paint.style = Paint.Style.FILL
        paint.color = AppColors.line
        canvas.drawRect(
            72f,
            separatorTop,
            1008f,
            separatorTop + 2f,
            paint
        )
        paint.color = tone.background
        canvas.drawCircle(96f, top + 31f, 24f, paint)
        drawText(
            canvas,
            number.toString(),
            96f,
            top + 39f,
            20f,
            tone.foreground,
            bold,
            Paint.Align.CENTER
        )
        drawText(
            canvas,
            FactExpressText.stateTitle(entry.state),
            140f,
            top + 21f,
            15f,
            tone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            entry.match,
            140f,
            top + 31f,
            868,
            23f,
            AppColors.ink,
            bold,
            1
        )
        drawTextBlock(
            canvas,
            FactExpressText.actionTitle(entry),
            140f,
            top + 61f,
            868,
            17f,
            tone.foreground,
            bold,
            1
        )
        val point = entry.nextMoment?.let {
            "${FactExpressText.momentTitle(it)} • ${
                FactExpressText.momentTime(it, selectedZone)
            }"
        } ?: if (entry.state == FactExpressEntryState.COMPLETE) {
            "Все главы завершены"
        } else {
            "Абсолютная следующая точка не доказана"
        }
        drawTextBlock(
            canvas,
            point,
            140f,
            top + 84f,
            868,
            15f,
            AppColors.muted,
            normal,
            1
        )
    }

    private fun drawContract(canvas: Canvas, top: Float) {
        val rect = RectF(72f, top, 1008f, top + 76f)
        drawRoundRect(
            canvas,
            rect,
            18f,
            AppColors.signalSoft,
            AppColors.signal
        )
        drawText(
            canvas,
            "БЕЗ СТАВКИ",
            104f,
            top + 30f,
            15f,
            AppColors.signal,
            bold
        )
        drawTextBlock(
            canvas,
            "Нет коэффициентов, вероятности, общего исхода или расчета выплаты.",
            104f,
            top + 43f,
            872,
            17f,
            AppColors.ink,
            bold,
            1
        )
    }

    private fun drawFooter(
        canvas: Canvas,
        poster: FactExpressPoster
    ) {
        paint.style = Paint.Style.FILL
        paint.color = AppColors.line
        canvas.drawRect(72f, 1230f, 1008f, 1232f, paint)
        drawText(
            canvas,
            "SHA-256 ${poster.result.shortFingerprint}",
            72f,
            1268f,
            18f,
            AppColors.accentDark,
            bold
        )
        drawText(
            canvas,
            formatDate(poster.generatedAt),
            1008f,
            1268f,
            16f,
            AppColors.muted,
            normal,
            Paint.Align.RIGHT
        )
        drawTextBlock(
            canvas,
            "Локальный информационный маршрут. Не ставка, " +
                "не экспресс исходов и без расчета выплаты.",
            72f,
            1290f,
            936,
            17f,
            AppColors.muted,
            normal,
            2
        )
    }

    private fun entryTone(state: FactExpressEntryState): PosterTone {
        return when (state) {
            FactExpressEntryState.ACTION_NOW ->
                PosterTone(AppColors.signal, AppColors.signalSoft)
            FactExpressEntryState.WAITING ->
                PosterTone(AppColors.warning, AppColors.warningSoft)
            FactExpressEntryState.UNSCHEDULED ->
                PosterTone(AppColors.danger, AppColors.dangerSoft)
            FactExpressEntryState.COMPLETE ->
                PosterTone(AppColors.accentDark, AppColors.accentSoft)
        }
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

    private data class PosterTone(
        val foreground: Int,
        val background: Int
    )

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
    }
}

internal class FactExpressPosterExporter(context: Context) {
    private val applicationContext = context.applicationContext
    private val renderer = FactExpressPosterRenderer(applicationContext)

    fun export(poster: FactExpressPoster): File {
        val directory = File(
            applicationContext.cacheDir,
            AnalysisImageProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Не удалось создать каталог экспорта"
        }
        val output = File(
            directory,
            FactExpressPosterFactory.fileName(poster)
        )
        val temporary = File(directory, ".${output.name}.tmp")
        val bitmap = renderer.render(poster)
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
