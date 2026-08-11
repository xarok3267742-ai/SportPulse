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

internal data class StoryThreadPoster(
    val event: SportEvent,
    val result: StoryThreadResult,
    val nextMoment: StoryBeaconMoment?,
    val selectedZone: RegionalZone,
    val generatedAt: Long
) {
    init {
        require(event.id == result.thread.eventId)
        require(generatedAt >= 0L)
    }
}

internal object StoryThreadPosterFactory {
    private val unsafeFileCharacters = Regex("[^A-Za-z0-9_-]+")

    fun create(
        event: SportEvent,
        result: StoryThreadResult,
        nextMoment: StoryBeaconMoment?,
        selectedZone: RegionalZone,
        generatedAt: Long = System.currentTimeMillis()
    ): StoryThreadPoster {
        return StoryThreadPoster(
            event = event,
            result = result,
            nextMoment = nextMoment,
            selectedZone = selectedZone,
            generatedAt = generatedAt
        )
    }

    fun fileName(poster: StoryThreadPoster): String {
        val safeEventId = unsafeFileCharacters
            .replace(poster.event.id, "_")
            .trim('_')
            .take(48)
            .ifBlank { "event" }
        return "sport_pulse_thread_${safeEventId}_" +
            "${poster.generatedAt}.png"
    }

    fun shareText(poster: StoryThreadPoster): String {
        val result = poster.result
        return buildString {
            append("Нить события «")
            append(poster.event.match)
            append("». Вопрос: ")
            append(question(result.thread.chapter))
            append(" Тогда: ")
            append(stateTitle(result.thread.initialState).lowercase())
            append("; сейчас: ")
            append(stateTitle(result.currentState).lowercase())
            append(". Статус: ")
            append(statusTitle(result.status).lowercase())
            append(". ")
            poster.nextMoment?.let {
                append("Ближайшая опорная точка: ")
                append(momentTitle(it))
                append(", ")
                append(momentTime(poster, it))
                append(". ")
            }
            append("Контрольная метка ")
            append(result.shortFingerprint)
            append(
                ". Это локальная информационная карточка, " +
                    "не прогноз, не ставка и не гарантия результата."
            )
        }
    }

    fun question(chapter: EventStoryChapter): String {
        return when (chapter) {
            EventStoryChapter.SOURCE ->
                "Можно ли доверять текущей афише?"
            EventStoryChapter.FACTS ->
                "Хватит ли подтвержденных фактов?"
            EventStoryChapter.PLAN ->
                "Готов ли план проверок к старту?"
            EventStoryChapter.DECISION ->
                "Зафиксирован ли вывод до старта?"
            EventStoryChapter.START ->
                "Наступил ли указанный старт?"
            EventStoryChapter.REVIEW ->
                "Готов ли разбор процесса?"
        }
    }

    fun chapterTitle(chapter: EventStoryChapter): String {
        return when (chapter) {
            EventStoryChapter.SOURCE -> "Источник"
            EventStoryChapter.FACTS -> "Факты"
            EventStoryChapter.PLAN -> "План"
            EventStoryChapter.DECISION -> "Решение"
            EventStoryChapter.START -> "Старт"
            EventStoryChapter.REVIEW -> "Разбор"
        }
    }

    fun stateTitle(state: EventStoryChapterState): String {
        return when (state) {
            EventStoryChapterState.COMPLETE -> "ГОТОВО"
            EventStoryChapterState.ACTIVE -> "АКТИВНО"
            EventStoryChapterState.ATTENTION -> "ВНИМАНИЕ"
            EventStoryChapterState.LOCKED -> "ЗАКРЫТО"
            EventStoryChapterState.MISSED -> "УПУЩЕНО"
            EventStoryChapterState.CONTEXT -> "КОНТЕКСТ"
        }
    }

    fun statusTitle(status: StoryThreadStatus): String {
        return when (status) {
            StoryThreadStatus.OPEN -> "ВОПРОС ОТКРЫТ"
            StoryThreadStatus.MOVED -> "НИТЬ СДВИНУЛАСЬ"
            StoryThreadStatus.RESOLVED -> "ВОПРОС ЗАКРЫТ"
            StoryThreadStatus.MISSED -> "МОМЕНТ УПУЩЕН"
        }
    }

    fun statusSummary(result: StoryThreadResult): String {
        return when (result.status) {
            StoryThreadStatus.OPEN ->
                "Состояние выбранной главы пока не изменилось."
            StoryThreadStatus.MOVED ->
                "Состояние изменилось, но вопрос еще не закрыт."
            StoryThreadStatus.RESOLVED ->
                "Выбранная глава завершена проверяемым состоянием."
            StoryThreadStatus.MISSED ->
                "Предстартовый момент прошел без завершения главы."
        }
    }

    fun startedTitle(poster: StoryThreadPoster): String {
        return "ЗАКРЕПЛЕНО • ${TimeBridgeEngine.formatInstant(
            startAt = poster.result.thread.startedAt,
            selectedZone = poster.selectedZone
        )}"
    }

    fun momentTitle(moment: StoryBeaconMoment): String {
        return when (moment.kind) {
            StoryBeaconMomentKind.ACTION_NOW -> when (moment.action) {
                EventStoryAction.OPEN_SOURCE -> "Проверить источник"
                EventStoryAction.OPEN_FACTS -> moment.factors
                    .singleOrNull()
                    ?.let { "Проверить фактор: ${it.title}" }
                    ?: "Открыть факты"
                EventStoryAction.OPEN_PLAN ->
                    "Открыть план к старту"
                EventStoryAction.OPEN_DECISION ->
                    "Зафиксировать решение"
                EventStoryAction.OPEN_REVIEW -> "Открыть разбор"
                EventStoryAction.NONE,
                null -> "Действие сейчас"
            }
            StoryBeaconMomentKind.CHECK_WINDOW ->
                "Окно проверки: ${factorTitles(moment.factors)}"
            StoryBeaconMomentKind.FACT_EXPIRY ->
                "Срок факта: ${factorTitles(moment.factors)}"
            StoryBeaconMomentKind.START -> "Указанный старт"
            StoryBeaconMomentKind.REVIEW_OPEN -> "Откроется разбор"
            StoryBeaconMomentKind.COMPLETE -> "История закрыта"
        }
    }

    fun momentTime(
        poster: StoryThreadPoster,
        moment: StoryBeaconMoment
    ): String {
        return moment.at?.let {
            TimeBridgeEngine.formatInstant(
                startAt = it,
                selectedZone = poster.selectedZone
            )
        } ?: if (moment.kind == StoryBeaconMomentKind.COMPLETE) {
            "ГОТОВО"
        } else {
            "СЕЙЧАС"
        }
    }

    private fun factorTitles(
        factors: List<SignalFactor>
    ): String {
        return factors.joinToString(", ") { it.title }
    }
}

internal class StoryThreadPosterRenderer(
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

    fun render(poster: StoryThreadPoster): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIDTH,
            HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val tone = statusTone(poster.result.status)
        canvas.drawColor(AppColors.background)
        paint.color = tone.foreground
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)
        drawHeader(canvas)
        drawHero(canvas)
        drawThread(canvas, poster, tone)
        drawFooter(canvas, poster)
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
            "Один вопрос • одно проверяемое изменение",
            72f,
            119f,
            22f,
            AppColors.muted,
            normal
        )
        drawPill(
            canvas,
            "КАРТОЧКА НИТИ • 18+",
            RectF(694f, 48f, 1008f, 108f),
            AppColors.signalSoft,
            AppColors.signal,
            17f
        )
    }

    private fun drawHero(canvas: Canvas) {
        val rect = RectF(72f, 145f, 1008f, 405f)
        val clip = Path().apply {
            addRoundRect(rect, 28f, 28f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = AppColors.ink
        canvas.drawRect(rect, paint)
        val source = BitmapFactory.decodeResource(
            resources,
            R.drawable.story_thread
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
                Color.argb(238, 9, 14, 17)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()
        drawText(
            canvas,
            "Нить события",
            104f,
            330f,
            49f,
            Color.WHITE,
            bold
        )
        drawText(
            canvas,
            "Тогда → сейчас • без прогноза исхода",
            104f,
            376f,
            21f,
            Color.rgb(220, 234, 232),
            bold
        )
    }

    private fun drawThread(
        canvas: Canvas,
        poster: StoryThreadPoster,
        tone: PosterTone
    ) {
        val result = poster.result
        drawText(
            canvas,
            "НИТЬ СОБЫТИЯ",
            72f,
            466f,
            21f,
            tone.foreground,
            bold
        )
        drawPill(
            canvas,
            StoryThreadPosterFactory.statusTitle(result.status),
            RectF(642f, 429f, 1008f, 487f),
            tone.background,
            tone.foreground,
            16f
        )
        drawTextBlock(
            canvas,
            poster.event.match,
            72f,
            500f,
            936,
            36f,
            AppColors.ink,
            bold,
            2
        )
        drawTextBlock(
            canvas,
            "${poster.event.sport} • ${poster.event.tournament} • " +
                poster.event.region,
            72f,
            586f,
            936,
            19f,
            AppColors.muted,
            bold,
            1
        )
        drawText(
            canvas,
            "ВОПРОС • ${StoryThreadPosterFactory.chapterTitle(
                result.thread.chapter
            ).uppercase(Locale.forLanguageTag("ru-RU"))}",
            72f,
            649f,
            18f,
            tone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            StoryThreadPosterFactory.question(
                result.thread.chapter
            ),
            72f,
            672f,
            936,
            35f,
            AppColors.ink,
            bold,
            2
        )
        drawRail(canvas, result, tone)
        drawOutcome(canvas, result, tone)
        drawMoment(canvas, poster, tone)
    }

    private fun drawRail(
        canvas: Canvas,
        result: StoryThreadResult,
        tone: PosterTone
    ) {
        val left = 122f
        val right = 958f
        val y = 816f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 14f
        paint.color = AppColors.line
        canvas.drawLine(left, y, right, y, paint)
        paint.strokeWidth = 7f
        paint.color = tone.foreground
        canvas.drawLine(left, y, right, y, paint)
        drawAnchor(
            canvas = canvas,
            x = left,
            y = y,
            color = stateColor(result.thread.initialState),
            filled = true
        )
        drawAnchor(
            canvas = canvas,
            x = right,
            y = y,
            color = stateColor(result.currentState),
            filled = result.status != StoryThreadStatus.OPEN
        )
        if (result.status == StoryThreadStatus.MOVED) {
            paint.style = Paint.Style.FILL
            paint.color = tone.foreground
            canvas.drawCircle(WIDTH / 2f, y, 13f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = AppColors.surface
            canvas.drawCircle(WIDTH / 2f, y, 5f, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        drawText(
            canvas,
            "ТОГДА",
            left,
            866f,
            15f,
            AppColors.muted,
            bold,
            Paint.Align.CENTER
        )
        drawText(
            canvas,
            "СЕЙЧАС",
            right,
            866f,
            15f,
            AppColors.muted,
            bold,
            Paint.Align.CENTER
        )
        drawPill(
            canvas,
            StoryThreadPosterFactory.stateTitle(
                result.thread.initialState
            ),
            RectF(72f, 883f, 430f, 941f),
            stateBackground(result.thread.initialState),
            stateColor(result.thread.initialState),
            16f
        )
        drawPill(
            canvas,
            StoryThreadPosterFactory.stateTitle(result.currentState),
            RectF(650f, 883f, 1008f, 941f),
            stateBackground(result.currentState),
            stateColor(result.currentState),
            16f
        )
    }

    private fun drawOutcome(
        canvas: Canvas,
        result: StoryThreadResult,
        tone: PosterTone
    ) {
        val rect = RectF(72f, 969f, 1008f, 1078f)
        drawRoundRect(
            canvas,
            rect,
            22f,
            tone.background,
            tone.foreground
        )
        drawText(
            canvas,
            "ТЕКУЩИЙ ИТОГ",
            104f,
            1008f,
            16f,
            tone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            StoryThreadPosterFactory.statusSummary(result),
            104f,
            1027f,
            872,
            20f,
            tone.foreground,
            bold,
            2
        )
    }

    private fun drawMoment(
        canvas: Canvas,
        poster: StoryThreadPoster,
        tone: PosterTone
    ) {
        val rect = RectF(72f, 1100f, 1008f, 1184f)
        drawRoundRect(
            canvas,
            rect,
            20f,
            AppColors.surface,
            AppColors.line
        )
        drawText(
            canvas,
            if (poster.nextMoment == null) {
                "СЛЕДУЮЩАЯ ТОЧКА НЕ ОПРЕДЕЛЕНА"
            } else {
                "СЛЕДУЮЩАЯ ОПОРНАЯ ТОЧКА"
            },
            104f,
            1134f,
            15f,
            tone.foreground,
            bold
        )
        val detail = poster.nextMoment?.let {
            "${StoryThreadPosterFactory.momentTitle(it)} • " +
                StoryThreadPosterFactory.momentTime(poster, it)
        } ?: StoryThreadPosterFactory.startedTitle(poster)
        drawTextBlock(
            canvas,
            detail,
            104f,
            1149f,
            872,
            18f,
            AppColors.ink,
            bold,
            1
        )
    }

    private fun drawFooter(
        canvas: Canvas,
        poster: StoryThreadPoster
    ) {
        paint.style = Paint.Style.FILL
        paint.color = AppColors.line
        canvas.drawRect(72f, 1210f, 1008f, 1212f, paint)
        drawText(
            canvas,
            "SHA-256 ${poster.result.shortFingerprint}",
            72f,
            1250f,
            19f,
            statusTone(poster.result.status).foreground,
            bold
        )
        drawText(
            canvas,
            formatDate(poster.generatedAt),
            1008f,
            1250f,
            17f,
            AppColors.muted,
            normal,
            Paint.Align.RIGHT
        )
        drawTextBlock(
            canvas,
            "Локальная информационная карточка. Не прогноз, " +
                "не ставка и не гарантия результата.",
            72f,
            1276f,
            936,
            18f,
            AppColors.muted,
            normal,
            2
        )
    }

    private fun drawAnchor(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        filled: Boolean
    ) {
        paint.style = Paint.Style.FILL
        paint.color = AppColors.surface
        canvas.drawCircle(x, y, 28f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.color = color
        canvas.drawCircle(x, y, 23f, paint)
        if (filled) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawCircle(x, y, 12f, paint)
        }
    }

    private fun statusTone(status: StoryThreadStatus): PosterTone {
        return when (status) {
            StoryThreadStatus.OPEN ->
                PosterTone(AppColors.signal, AppColors.signalSoft)
            StoryThreadStatus.MOVED ->
                PosterTone(AppColors.warning, AppColors.warningSoft)
            StoryThreadStatus.RESOLVED ->
                PosterTone(AppColors.accentDark, AppColors.accentSoft)
            StoryThreadStatus.MISSED ->
                PosterTone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun stateColor(state: EventStoryChapterState): Int {
        return when (state) {
            EventStoryChapterState.COMPLETE -> AppColors.accent
            EventStoryChapterState.ACTIVE -> AppColors.signal
            EventStoryChapterState.ATTENTION -> AppColors.warning
            EventStoryChapterState.LOCKED -> AppColors.muted
            EventStoryChapterState.MISSED -> AppColors.danger
            EventStoryChapterState.CONTEXT -> AppColors.signal
        }
    }

    private fun stateBackground(
        state: EventStoryChapterState
    ): Int {
        return when (state) {
            EventStoryChapterState.COMPLETE -> AppColors.accentSoft
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.CONTEXT -> AppColors.signalSoft
            EventStoryChapterState.ATTENTION -> AppColors.warningSoft
            EventStoryChapterState.LOCKED -> AppColors.background
            EventStoryChapterState.MISSED -> AppColors.dangerSoft
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

internal class StoryThreadPosterExporter(
    context: Context
) {
    private val applicationContext = context.applicationContext
    private val renderer = StoryThreadPosterRenderer(applicationContext)

    fun export(poster: StoryThreadPoster): File {
        val directory = File(
            applicationContext.cacheDir,
            AnalysisImageProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Не удалось создать каталог экспорта"
        }
        val output = File(
            directory,
            StoryThreadPosterFactory.fileName(poster)
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
