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

internal data class EventStoryPoster(
    val event: SportEvent,
    val story: EventStoryResult,
    val selectedZone: RegionalZone,
    val generatedAt: Long
) {
    init {
        require(event.id == story.eventId)
        require(event.match == story.eventLabel)
        require(generatedAt >= 0L)
    }
}

internal object EventStoryPosterFactory {
    private val unsafeFileCharacters =
        Regex("[^A-Za-z0-9_-]+")

    fun create(
        event: SportEvent,
        story: EventStoryResult,
        selectedZone: RegionalZone,
        generatedAt: Long = System.currentTimeMillis()
    ): EventStoryPoster {
        return EventStoryPoster(
            event = event,
            story = story,
            selectedZone = selectedZone,
            generatedAt = generatedAt
        )
    }

    fun fileName(poster: EventStoryPoster): String {
        val safeEventId = unsafeFileCharacters
            .replace(poster.event.id, "_")
            .trim('_')
            .take(48)
            .ifBlank { "event" }
        return "sport_pulse_story_${safeEventId}_" +
            "${poster.generatedAt}.png"
    }

    fun shareText(poster: EventStoryPoster): String {
        val story = poster.story
        return buildString {
            append("Сюжет события «")
            append(poster.event.match)
            append("». Глава ")
            append(story.currentChapterNumber)
            append(" из 6: ")
            append(chapterTitle(story.currentChapter).lowercase())
            append(". ")
            append(story.chapter(story.currentChapter).summary)
            append(" Следующий шаг: ")
            append(nextStepTitle(story))
            append(". Завершено глав: ")
            append(story.completedCount)
            append(" из 6. Контрольная метка ")
            append(story.shortFingerprint)
            append(
                ". Это локальный информационный маршрут, " +
                    "не прогноз, не ставка и не гарантия результата."
            )
        }
    }

    fun phaseTitle(phase: EventStoryPhase): String {
        return when (phase) {
            EventStoryPhase.PREPARING -> "МАРШРУТ СОБИРАЕТСЯ"
            EventStoryPhase.READY -> "ГОТОВО К СТАРТУ"
            EventStoryPhase.IN_PROGRESS -> "ОКНО СОБЫТИЯ"
            EventStoryPhase.REVIEW_DUE -> "ОТКРЫТ РАЗБОР"
            EventStoryPhase.COMPLETE -> "ИСТОРИЯ ЗАКРЫТА"
            EventStoryPhase.INCOMPLETE ->
                "ХРОНОЛОГИЯ НЕПОЛНА"
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

    fun chapterStateTitle(
        state: EventStoryChapterState
    ): String {
        return when (state) {
            EventStoryChapterState.COMPLETE -> "ГОТОВО"
            EventStoryChapterState.ACTIVE -> "АКТИВНО"
            EventStoryChapterState.ATTENTION -> "ВНИМАНИЕ"
            EventStoryChapterState.LOCKED -> "ЗАКРЫТО"
            EventStoryChapterState.MISSED -> "УПУЩЕНО"
            EventStoryChapterState.CONTEXT -> "КОНТЕКСТ"
        }
    }

    fun nextStepTitle(story: EventStoryResult): String {
        return when (story.action) {
            EventStoryAction.OPEN_SOURCE -> "Проверить источник"
            EventStoryAction.OPEN_FACTS -> story.actionFactor?.let {
                "Проверить фактор: ${it.title}"
            } ?: "Открыть факты"
            EventStoryAction.OPEN_PLAN -> "Открыть план к старту"
            EventStoryAction.OPEN_DECISION ->
                "Зафиксировать решение"
            EventStoryAction.OPEN_REVIEW -> "Открыть разбор"
            EventStoryAction.NONE -> when (story.phase) {
                EventStoryPhase.READY -> "Дождаться старта"
                EventStoryPhase.IN_PROGRESS ->
                    "Дождаться окна разбора"
                EventStoryPhase.COMPLETE -> "Сюжет закрыт"
                EventStoryPhase.INCOMPLETE ->
                    "Проверить хронологию"
                EventStoryPhase.PREPARING ->
                    "Продолжить подготовку"
                EventStoryPhase.REVIEW_DUE ->
                    "Сверить фактическое завершение"
            }
        }
    }

    fun startTitle(poster: EventStoryPoster): String {
        return poster.story.startAt?.let {
            "СТАРТ • ${TimeBridgeEngine.formatInstant(
                startAt = it,
                selectedZone = poster.selectedZone
            )}"
        } ?: "СТАРТ • НЕ ПОДТВЕРЖДЕН"
    }

    fun reviewTitle(poster: EventStoryPoster): String {
        return poster.story.reviewOpensAt?.let {
            "РАЗБОР НЕ РАНЬШЕ • ${TimeBridgeEngine.formatInstant(
                startAt = it,
                selectedZone = poster.selectedZone
            )}"
        } ?: "РАЗБОР • ЗАКРЫТ БЕЗ ТОЧНОГО СТАРТА"
    }
}

internal class EventStoryPosterRenderer(
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

    fun render(poster: EventStoryPoster): Bitmap {
        val bitmap = Bitmap.createBitmap(
            WIDTH,
            HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(AppColors.background)
        paint.color = phaseTone(poster.story.phase).foreground
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)
        drawHeader(canvas)
        drawHero(canvas)
        drawStory(canvas, poster)
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
            "Один маршрут от источника до разбора",
            72f,
            119f,
            22f,
            AppColors.muted,
            normal
        )
        drawPill(
            canvas,
            "ПОСТЕР СЮЖЕТА • 18+",
            RectF(714f, 48f, 1008f, 108f),
            AppColors.signalSoft,
            AppColors.signal,
            18f
        )
    }

    private fun drawHero(canvas: Canvas) {
        val rect = RectF(72f, 145f, 1008f, 404f)
        val clip = Path().apply {
            addRoundRect(rect, 28f, 28f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        paint.color = AppColors.ink
        canvas.drawRect(rect, paint)
        val source = BitmapFactory.decodeResource(
            resources,
            R.drawable.event_story
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
                Color.argb(38, 9, 14, 17),
                Color.argb(236, 9, 14, 17)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
        paint.shader = null
        canvas.restore()
        drawText(
            canvas,
            "Сюжет события",
            104f,
            330f,
            49f,
            Color.WHITE,
            bold
        )
        drawText(
            canvas,
            "Шесть проверяемых глав • один следующий шаг",
            104f,
            376f,
            21f,
            Color.rgb(220, 234, 232),
            bold
        )
    }

    private fun drawStory(
        canvas: Canvas,
        poster: EventStoryPoster
    ) {
        val story = poster.story
        val tone = phaseTone(story.phase)
        drawText(
            canvas,
            "ТЕКУЩИЙ МАРШРУТ",
            72f,
            466f,
            22f,
            tone.foreground,
            bold
        )
        drawPill(
            canvas,
            EventStoryPosterFactory.phaseTitle(story.phase),
            RectF(600f, 429f, 1008f, 487f),
            tone.background,
            tone.foreground,
            16f
        )
        drawTextBlock(
            canvas,
            poster.event.match,
            72f,
            508f,
            936,
            37f,
            AppColors.ink,
            bold,
            2
        )
        drawTextBlock(
            canvas,
            "${poster.event.sport} • ${poster.event.tournament} • " +
                poster.event.region,
            72f,
            598f,
            936,
            19f,
            AppColors.muted,
            bold,
            1
        )

        val current = story.chapter(story.currentChapter)
        val currentTone = chapterTone(current.state)
        drawText(
            canvas,
            story.currentChapterNumber
                .toString()
                .padStart(2, '0'),
            72f,
            708f,
            92f,
            currentTone.foreground,
            bold
        )
        drawText(
            canvas,
            "/ 06",
            190f,
            697f,
            31f,
            AppColors.muted,
            bold
        )
        drawText(
            canvas,
            EventStoryPosterFactory.chapterTitle(
                story.currentChapter
            ),
            310f,
            652f,
            34f,
            currentTone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            current.summary,
            310f,
            674f,
            698,
            21f,
            AppColors.ink,
            normal,
            2
        )

        drawRail(canvas, story)
        drawNextStep(canvas, story, tone)
        drawSchedule(canvas, poster)
    }

    private fun drawRail(
        canvas: Canvas,
        story: EventStoryResult
    ) {
        val left = 96f
        val right = 984f
        val y = 790f
        val interval = (right - left) / 5f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 12f
        paint.color = AppColors.line
        canvas.drawLine(left, y, right, y, paint)
        story.chapters.dropLast(1).forEachIndexed { index, chapter ->
            val next = story.chapters[index + 1]
            if (
                chapter.state == EventStoryChapterState.COMPLETE &&
                next.state == EventStoryChapterState.COMPLETE
            ) {
                paint.strokeWidth = 7f
                paint.color = AppColors.accent
                canvas.drawLine(
                    left + interval * index,
                    y,
                    left + interval * (index + 1),
                    y,
                    paint
                )
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
        story.chapters.forEachIndexed { index, chapter ->
            val x = left + interval * index
            val tone = chapterTone(chapter.state)
            if (chapter.chapter == story.currentChapter) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f
                paint.color = tone.foreground
                canvas.drawCircle(x, y, 27f, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = if (
                chapter.state == EventStoryChapterState.LOCKED ||
                chapter.state == EventStoryChapterState.CONTEXT
            ) {
                AppColors.surface
            } else {
                tone.foreground
            }
            canvas.drawCircle(x, y, 18f, paint)
            if (
                chapter.state == EventStoryChapterState.LOCKED ||
                chapter.state == EventStoryChapterState.CONTEXT
            ) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = tone.foreground
                canvas.drawCircle(x, y, 18f, paint)
            }
            if (chapter.state == EventStoryChapterState.COMPLETE) {
                paint.style = Paint.Style.FILL
                paint.color = AppColors.surface
                canvas.drawCircle(x, y, 5f, paint)
            }
            if (chapter.state == EventStoryChapterState.MISSED) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = AppColors.surface
                canvas.drawLine(x - 7f, y - 7f, x + 7f, y + 7f, paint)
                canvas.drawLine(x + 7f, y - 7f, x - 7f, y + 7f, paint)
            }
            drawText(
                canvas,
                EventStoryPosterFactory.chapterTitle(chapter.chapter)
                    .uppercase(Locale.forLanguageTag("ru-RU")),
                x,
                842f,
                14f,
                if (chapter.chapter == story.currentChapter) {
                    tone.foreground
                } else {
                    AppColors.muted
                },
                bold,
                Paint.Align.CENTER
            )
            drawText(
                canvas,
                EventStoryPosterFactory.chapterStateTitle(
                    chapter.state
                ),
                x,
                865f,
                11f,
                tone.foreground,
                bold,
                Paint.Align.CENTER
            )
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawNextStep(
        canvas: Canvas,
        story: EventStoryResult,
        tone: Tone
    ) {
        val rect = RectF(72f, 880f, 1008f, 1004f)
        drawRoundRect(
            canvas,
            rect,
            22f,
            tone.background,
            tone.foreground
        )
        drawText(
            canvas,
            "СЛЕДУЮЩИЙ ШАГ",
            104f,
            922f,
            17f,
            tone.foreground,
            bold
        )
        drawTextBlock(
            canvas,
            EventStoryPosterFactory.nextStepTitle(story),
            104f,
            942f,
            872,
            30f,
            tone.foreground,
            bold,
            1
        )
    }

    private fun drawSchedule(
        canvas: Canvas,
        poster: EventStoryPoster
    ) {
        val rect = RectF(72f, 1028f, 1008f, 1146f)
        drawRoundRect(
            canvas,
            rect,
            20f,
            AppColors.surface,
            AppColors.line
        )
        drawTextBlock(
            canvas,
            EventStoryPosterFactory.startTitle(poster),
            104f,
            1052f,
            872,
            18f,
            AppColors.ink,
            bold,
            1
        )
        drawTextBlock(
            canvas,
            EventStoryPosterFactory.reviewTitle(poster),
            104f,
            1097f,
            872,
            17f,
            AppColors.muted,
            bold,
            1
        )
    }

    private fun drawFooter(
        canvas: Canvas,
        poster: EventStoryPoster
    ) {
        val story = poster.story
        paint.color = AppColors.line
        canvas.drawRect(72f, 1184f, 1008f, 1186f, paint)
        drawText(
            canvas,
            "ГОТОВО ${story.completedCount}/6 • SHA-256 " +
                story.shortFingerprint,
            72f,
            1230f,
            19f,
            AppColors.signal,
            bold
        )
        drawText(
            canvas,
            formatDate(poster.generatedAt),
            1008f,
            1230f,
            17f,
            AppColors.muted,
            normal,
            Paint.Align.RIGHT
        )
        drawTextBlock(
            canvas,
            "Информационный локальный маршрут. Не прогноз, " +
                "не ставка и не гарантия результата. " +
                "Фактическое завершение события сверяется отдельно.",
            72f,
            1264f,
            936,
            18f,
            AppColors.muted,
            normal,
            2
        )
    }

    private fun phaseTone(phase: EventStoryPhase): Tone {
        return when (phase) {
            EventStoryPhase.PREPARING ->
                Tone(AppColors.signal, AppColors.signalSoft)
            EventStoryPhase.READY,
            EventStoryPhase.COMPLETE ->
                Tone(AppColors.accentDark, AppColors.accentSoft)
            EventStoryPhase.IN_PROGRESS,
            EventStoryPhase.REVIEW_DUE ->
                Tone(AppColors.warning, AppColors.warningSoft)
            EventStoryPhase.INCOMPLETE ->
                Tone(AppColors.danger, AppColors.dangerSoft)
        }
    }

    private fun chapterTone(
        state: EventStoryChapterState
    ): Tone {
        return when (state) {
            EventStoryChapterState.COMPLETE ->
                Tone(AppColors.accent, AppColors.accentSoft)
            EventStoryChapterState.ACTIVE,
            EventStoryChapterState.CONTEXT ->
                Tone(AppColors.signal, AppColors.signalSoft)
            EventStoryChapterState.ATTENTION ->
                Tone(AppColors.warning, AppColors.warningSoft)
            EventStoryChapterState.LOCKED ->
                Tone(AppColors.muted, AppColors.background)
            EventStoryChapterState.MISSED ->
                Tone(AppColors.danger, AppColors.dangerSoft)
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

    private data class Tone(
        val foreground: Int,
        val background: Int
    )

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
    }
}

internal class EventStoryPosterExporter(
    context: Context
) {
    private val applicationContext = context.applicationContext
    private val renderer = EventStoryPosterRenderer(applicationContext)

    fun export(poster: EventStoryPoster): File {
        val directory = File(
            applicationContext.cacheDir,
            AnalysisImageProvider.SHARE_DIRECTORY
        )
        check(directory.exists() || directory.mkdirs()) {
            "Не удалось создать каталог экспорта"
        }
        val output = File(
            directory,
            EventStoryPosterFactory.fileName(poster)
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
