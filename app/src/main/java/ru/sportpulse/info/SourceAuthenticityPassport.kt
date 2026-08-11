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

internal data class SourceAuthenticityPassport(
    val eventPackage: SportEventPackage,
    val generatedAt: Long
)

internal object SourceAuthenticityPassportFactory {
    private val unsafeFileCharacters =
        Regex("[^A-Za-z0-9_-]+")

    fun create(
        eventPackage: SportEventPackage,
        generatedAt: Long = System.currentTimeMillis()
    ): SourceAuthenticityPassport {
        require(generatedAt >= 0L)
        return SourceAuthenticityPassport(
            eventPackage,
            generatedAt
        )
    }

    fun fileName(
        passport: SourceAuthenticityPassport
    ): String {
        val safePackageId = unsafeFileCharacters
            .replace(passport.eventPackage.packageId, "_")
            .trim('_')
            .take(48)
            .ifBlank { "source" }
        return "sport_pulse_source_${safePackageId}_" +
            "${passport.generatedAt}.png"
    }

    fun shareText(
        passport: SourceAuthenticityPassport
    ): String {
        val eventPackage = passport.eventPackage
        val authenticity = eventPackage.authenticity
        return buildString {
            append("Паспорт источника «")
            append(eventPackage.sourceLabel)
            append("». Событий: ")
            append(eventPackage.events.size)
            append(". Payload SHA-256: ")
            append(eventPackage.shortFingerprint)
            if (authenticity.isAuthenticated) {
                append(". Подпись проверена ключом ")
                append(authenticity.keyId)
                append(". Отпечаток подписи: ")
                append(
                    authenticity.shortSignatureFingerprint
                )
                if (
                    authenticity.keyEnvironment ==
                    EventPackageKeyEnvironment.DEVELOPMENT
                ) {
                    append(". Использован ключ разработки")
                }
            } else {
                append(
                    ". Криптографическая подпись отсутствует, " +
                        "автор файла не подтвержден"
                )
            }
            append(
                ". Подпись подтверждает байты пакета, " +
                    "но не истинность спортивных фактов."
            )
        }
    }
}

internal class SourceAuthenticityPassportRenderer(
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

    fun render(
        passport: SourceAuthenticityPassport
    ): Bitmap {
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
        drawPassport(
            canvas,
            passport.eventPackage,
            passport.generatedAt
        )
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
            "Факты отделены от происхождения файла",
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
            R.drawable.source_authenticity
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
                Color.argb(10, 8, 14, 18),
                Color.argb(70, 8, 14, 18),
                Color.argb(235, 8, 14, 18)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()
        drawText(
            canvas,
            "Контур подлинности",
            104f,
            333f,
            48f,
            Color.WHITE,
            bold
        )
        drawText(
            canvas,
            "Ключ, payload и подпись собраны в одном паспорте",
            104f,
            379f,
            21f,
            Color.rgb(220, 231, 233),
            bold
        )
    }

    private fun drawPassport(
        canvas: Canvas,
        eventPackage: SportEventPackage,
        generatedAt: Long
    ) {
        val tone = tone(eventPackage, generatedAt)
        drawText(
            canvas,
            "ПАСПОРТ ИСТОЧНИКА",
            72f,
            492f,
            24f,
            AppColors.accent,
            bold
        )
        drawPill(
            canvas,
            statusTitle(eventPackage, generatedAt),
            RectF(650f, 458f, 1008f, 514f),
            tone.background,
            tone.foreground,
            17f,
            tone.foreground
        )
        drawTextBlock(
            canvas,
            eventPackage.sourceLabel,
            72f,
            532f,
            936,
            36f,
            AppColors.ink,
            bold,
            2
        )
        drawText(
            canvas,
            "${eventPackage.events.size} событий • выпуск ${
                formatDate(eventPackage.generatedAt)
            } • до ${formatDate(eventPackage.validUntil)}",
            72f,
            646f,
            18f,
            AppColors.muted,
            bold
        )
        drawText(
            canvas,
            "КОНТУР ПРОВЕРКИ",
            72f,
            687f,
            19f,
            AppColors.muted,
            bold
        )
        drawStep(
            canvas,
            1,
            "СТРУКТУРА",
            "EVENT PACK v${eventPackage.schemaVersion} • STRICT",
            710f,
            AppColors.accentDark,
            AppColors.accentSoft
        )
        drawStep(
            canvas,
            2,
            "PAYLOAD",
            "SHA-256 • ${eventPackage.shortFingerprint}",
            790f,
            AppColors.signal,
            AppColors.signalSoft
        )
        val authenticity = eventPackage.authenticity
        drawStep(
            canvas,
            3,
            "ПОДПИСЬ",
            if (authenticity.isAuthenticated) {
                "${EventPackageEnvelopeCodec.ALGORITHM} • ${
                    authenticity.shortSignatureFingerprint
                }"
            } else {
                "НЕ ПРИМЕНЯЛАСЬ"
            },
            870f,
            if (authenticity.isAuthenticated) {
                tone.foreground
            } else {
                AppColors.warning
            },
            if (authenticity.isAuthenticated) {
                tone.background
            } else {
                AppColors.warningSoft
            }
        )
        drawKeyCard(canvas, eventPackage)
        drawTextCentered(
            canvas,
            if (authenticity.isAuthenticated) {
                "Подпись подтверждает точные байты и разрешенное имя источника, но не истинность фактов"
            } else {
                "SHA-256 обнаруживает изменение файла, но не подтверждает его автора"
            },
            WIDTH / 2f,
            1214f,
            16f,
            AppColors.muted,
            bold
        )
        drawFooter(canvas, generatedAt)
    }

    private fun drawStep(
        canvas: Canvas,
        number: Int,
        title: String,
        value: String,
        top: Float,
        foreground: Int,
        background: Int
    ) {
        val rect = RectF(72f, top, 1008f, top + 68f)
        drawRoundRect(
            canvas,
            rect,
            16f,
            AppColors.surface,
            AppColors.line
        )
        drawPill(
            canvas,
            number.toString().padStart(2, '0'),
            RectF(90f, top + 12f, 148f, top + 56f),
            background,
            foreground,
            17f
        )
        drawText(
            canvas,
            title,
            170f,
            top + 42f,
            19f,
            AppColors.ink,
            bold
        )
        drawText(
            canvas,
            value,
            986f,
            top + 42f,
            17f,
            foreground,
            bold,
            Paint.Align.RIGHT
        )
    }

    private fun drawKeyCard(
        canvas: Canvas,
        eventPackage: SportEventPackage
    ) {
        val authenticity = eventPackage.authenticity
        val rect = RectF(72f, 966f, 1008f, 1174f)
        drawRoundRect(
            canvas,
            rect,
            20f,
            AppColors.surface,
            AppColors.line
        )
        val title = if (authenticity.isAuthenticated) {
            authenticity.keyLabel ?: "Доверенный ключ"
        } else {
            "Локальный файл без ключа"
        }
        drawText(
            canvas,
            title,
            98f,
            1010f,
            24f,
            AppColors.ink,
            bold
        )
        drawTextBlock(
            canvas,
            when (authenticity.keyEnvironment) {
                EventPackageKeyEnvironment.PRODUCTION ->
                    "${authenticity.keyId}\nПубличный ключ закреплен в приложении."
                EventPackageKeyEnvironment.DEVELOPMENT ->
                    "${authenticity.keyId}\nКлюч разработки, не удостоверяет лицензированного поставщика."
                null ->
                    "Криптографический автор отсутствует.\nМатрица построена по SHA-256 payload."
            },
            98f,
            1030f,
            510,
            18f,
            AppColors.muted,
            bold,
            3
        )
        drawText(
            canvas,
            if (authenticity.isAuthenticated) {
                "ОТПЕЧАТОК КЛЮЧА"
            } else {
                "ОТПЕЧАТОК PAYLOAD"
            },
            674f,
            1007f,
            15f,
            AppColors.muted,
            bold
        )
        drawKeyprint(
            canvas,
            authenticity.keyFingerprint
                ?: eventPackage.fingerprint,
            674f,
            1025f
        )
        drawText(
            canvas,
            (
                authenticity.shortKeyFingerprint
                    ?: eventPackage.shortFingerprint
                ),
            986f,
            1007f,
            16f,
            AppColors.signal,
            bold,
            Paint.Align.RIGHT
        )
    }

    private fun drawKeyprint(
        canvas: Canvas,
        fingerprint: String,
        left: Float,
        top: Float
    ) {
        val bits = fingerprint
            .lowercase()
            .take(32)
            .flatMap { char ->
                val value = char.digitToIntOrNull(16) ?: 0
                (3 downTo 0).map { bit ->
                    (value shr bit) and 1
                }
            }
        val cell = 15f
        val gap = 3f
        repeat(8) { row ->
            repeat(16) { column ->
                val index = row * 16 + column
                paint.color = if (bits.getOrElse(index) { 0 } == 1) {
                    AppColors.signal
                } else {
                    AppColors.background
                }
                canvas.drawRoundRect(
                    RectF(
                        left + column * (cell + gap),
                        top + row * (cell + gap),
                        left + column * (cell + gap) + cell,
                        top + row * (cell + gap) + cell
                    ),
                    3f,
                    3f,
                    paint
                )
            }
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        generatedAt: Long
    ) {
        paint.color = AppColors.line
        canvas.drawRect(72f, 1250f, 1008f, 1252f, paint)
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

    private fun statusTitle(
        eventPackage: SportEventPackage,
        now: Long
    ): String {
        if (eventPackage.isExpired(now)) {
            return "СРОК ИСТЕК"
        }
        return when (
            eventPackage.authenticity.keyEnvironment
        ) {
            EventPackageKeyEnvironment.PRODUCTION ->
                "ПОДПИСЬ ПРОВЕРЕНА"
            EventPackageKeyEnvironment.DEVELOPMENT ->
                "ДЕМО-ПОДПИСЬ"
            null ->
                "БЕЗ ПОДПИСИ"
        }
    }

    private fun tone(
        eventPackage: SportEventPackage,
        now: Long
    ): Tone {
        if (eventPackage.isExpired(now)) {
            return Tone(AppColors.danger, AppColors.dangerSoft)
        }
        return when (
            eventPackage.authenticity.keyEnvironment
        ) {
            EventPackageKeyEnvironment.PRODUCTION ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            EventPackageKeyEnvironment.DEVELOPMENT ->
                Tone(AppColors.signal, AppColors.signalSoft)
            null ->
                Tone(AppColors.warning, AppColors.warningSoft)
        }
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
    }
}

internal class SourceAuthenticityPassportExporter(
    context: Context
) {
    private val applicationContext =
        context.applicationContext
    private val renderer = SourceAuthenticityPassportRenderer(
        applicationContext
    )

    fun export(
        passport: SourceAuthenticityPassport
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
            SourceAuthenticityPassportFactory.fileName(passport)
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
