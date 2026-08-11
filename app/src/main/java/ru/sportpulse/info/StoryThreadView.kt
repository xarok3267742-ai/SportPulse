package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class StoryThreadView @JvmOverloads constructor(
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
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private var result: StoryThreadResult? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(value: StoryThreadResult) {
        result = value
        contentDescription = buildString {
            append("Нить события. ")
            append(chapterTitle(value.thread.chapter))
            append(". Тогда: ")
            append(accessibleState(value.thread.initialState))
            append(". Сейчас: ")
            append(accessibleState(value.currentState))
            append(". Статус: ")
            append(accessibleStatus(value.status))
        }
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(108f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val value = result ?: return
        val left = dp(32f)
        val right = width - dp(32f)
        val railY = dp(43f)
        val statusColor = statusColor(value.status)

        linePaint.strokeWidth = dp(7f)
        linePaint.color = AppColors.line
        canvas.drawLine(left, railY, right, railY, linePaint)

        linePaint.strokeWidth = dp(3.5f)
        linePaint.color = statusColor
        canvas.drawLine(left, railY, right, railY, linePaint)

        drawAnchor(
            canvas = canvas,
            x = left,
            y = railY,
            color = stateColor(value.thread.initialState),
            filled = true
        )
        drawAnchor(
            canvas = canvas,
            x = right,
            y = railY,
            color = stateColor(value.currentState),
            filled = value.status != StoryThreadStatus.OPEN
        )

        if (value.status == StoryThreadStatus.MOVED) {
            val knotX = width / 2f
            fillPaint.color = statusColor
            canvas.drawCircle(knotX, railY, dp(5f), fillPaint)
            linePaint.strokeWidth = dp(2f)
            linePaint.color = AppColors.surface
            canvas.drawCircle(knotX, railY, dp(2f), linePaint)
        }

        labelPaint.textSize = cappedSp(8.5f)
        labelPaint.color = AppColors.muted
        canvas.drawText("ТОГДА", left, dp(80f), labelPaint)
        canvas.drawText("СЕЙЧАС", right, dp(80f), labelPaint)

        val chapter = chapterTitle(value.thread.chapter).uppercase()
        labelPaint.color = statusColor
        labelPaint.textSize = fittedTextSize(
            value = chapter,
            maxWidth = right - left - dp(80f),
            maxSp = 9f
        )
        canvas.drawText(chapter, width / 2f, dp(96f), labelPaint)
    }

    private fun drawAnchor(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        filled: Boolean
    ) {
        fillPaint.color = AppColors.surface
        canvas.drawCircle(x, y, dp(11f), fillPaint)
        linePaint.strokeWidth = dp(3f)
        linePaint.color = color
        canvas.drawCircle(x, y, dp(9f), linePaint)
        if (filled) {
            fillPaint.color = color
            canvas.drawCircle(x, y, dp(5f), fillPaint)
        }
    }

    private fun statusColor(status: StoryThreadStatus): Int {
        return when (status) {
            StoryThreadStatus.OPEN -> AppColors.signal
            StoryThreadStatus.MOVED -> AppColors.warning
            StoryThreadStatus.RESOLVED -> AppColors.accent
            StoryThreadStatus.MISSED -> AppColors.danger
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

    private fun chapterTitle(chapter: EventStoryChapter): String {
        return when (chapter) {
            EventStoryChapter.SOURCE -> "Источник"
            EventStoryChapter.FACTS -> "Факты"
            EventStoryChapter.PLAN -> "План"
            EventStoryChapter.DECISION -> "Решение"
            EventStoryChapter.START -> "Старт"
            EventStoryChapter.REVIEW -> "Разбор"
        }
    }

    private fun accessibleState(
        state: EventStoryChapterState
    ): String {
        return when (state) {
            EventStoryChapterState.COMPLETE -> "готово"
            EventStoryChapterState.ACTIVE -> "активно"
            EventStoryChapterState.ATTENTION -> "требует внимания"
            EventStoryChapterState.LOCKED -> "закрыто"
            EventStoryChapterState.MISSED -> "упущено"
            EventStoryChapterState.CONTEXT -> "контекст"
        }
    }

    private fun accessibleStatus(status: StoryThreadStatus): String {
        return when (status) {
            StoryThreadStatus.OPEN -> "вопрос открыт"
            StoryThreadStatus.MOVED -> "состояние изменилось"
            StoryThreadStatus.RESOLVED -> "вопрос закрыт"
            StoryThreadStatus.MISSED -> "момент упущен"
        }
    }

    private fun fittedTextSize(
        value: String,
        maxWidth: Float,
        maxSp: Float
    ): Float {
        val preferred = cappedSp(maxSp)
        labelPaint.textSize = preferred
        val measured = labelPaint.measureText(value)
        return if (measured > maxWidth && measured > 0f) {
            preferred * maxWidth / measured
        } else {
            preferred
        }
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
}
