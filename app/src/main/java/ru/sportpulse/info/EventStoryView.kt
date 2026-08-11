package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.min

internal class EventStoryView @JvmOverloads constructor(
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
    private val chapterTitles = arrayOf(
        "ИСТОЧНИК",
        "ФАКТЫ",
        "ПЛАН",
        "РЕШЕНИЕ",
        "СТАРТ",
        "РАЗБОР"
    )
    private val states = Array(chapterTitles.size) {
        EventStoryChapterState.LOCKED
    }
    private var currentIndex = 0
    private var hasResult = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setResult(result: EventStoryResult) {
        result.chapters.forEachIndexed { index, chapter ->
            states[index] = chapter.state
        }
        currentIndex = result.currentChapter.ordinal
        contentDescription = buildString {
            append("Сюжет события. Текущая глава: ")
            append(chapterTitles[currentIndex].lowercase(Locale.getDefault()))
            result.chapters.forEachIndexed { index, chapter ->
                append(". ")
                append(chapterTitles[index].lowercase(Locale.getDefault()))
                append(": ")
                append(accessibleState(chapter.state))
            }
        }
        hasResult = true
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
        if (!hasResult) return

        val left = dp(18f)
        val right = width - dp(18f)
        val railY = dp(38f)
        val labelY = dp(82f)
        val interval = (right - left) /
            (chapterTitles.size - 1).toFloat()

        linePaint.strokeWidth = dp(6f)
        linePaint.color = AppColors.line
        canvas.drawLine(left, railY, right, railY, linePaint)

        for (index in 0 until chapterTitles.lastIndex) {
            val nextState = states[index + 1]
            val segmentComplete = states[index] ==
                EventStoryChapterState.COMPLETE &&
                nextState == EventStoryChapterState.COMPLETE
            if (segmentComplete) {
                linePaint.strokeWidth = dp(4f)
                linePaint.color = AppColors.accent
                canvas.drawLine(
                    left + interval * index,
                    railY,
                    left + interval * (index + 1),
                    railY,
                    linePaint
                )
            }
        }

        chapterTitles.indices.forEach { index ->
            val x = left + interval * index
            val state = states[index]
            val color = stateColor(state)

            if (index == currentIndex) {
                linePaint.strokeWidth = dp(2f)
                linePaint.color = color
                canvas.drawCircle(x, railY, dp(12f), linePaint)
            }

            if (
                state == EventStoryChapterState.LOCKED ||
                state == EventStoryChapterState.CONTEXT
            ) {
                fillPaint.color = AppColors.surface
                canvas.drawCircle(x, railY, dp(7f), fillPaint)
                linePaint.strokeWidth = dp(3f)
                linePaint.color = color
                canvas.drawCircle(x, railY, dp(7f), linePaint)
            } else {
                fillPaint.color = color
                canvas.drawCircle(x, railY, dp(8f), fillPaint)
            }

            if (state == EventStoryChapterState.COMPLETE) {
                fillPaint.color = AppColors.surface
                canvas.drawCircle(x, railY, dp(2.6f), fillPaint)
            }
            if (state == EventStoryChapterState.MISSED) {
                linePaint.strokeWidth = dp(2f)
                linePaint.color = AppColors.surface
                canvas.drawLine(
                    x - dp(3f),
                    railY - dp(3f),
                    x + dp(3f),
                    railY + dp(3f),
                    linePaint
                )
                canvas.drawLine(
                    x + dp(3f),
                    railY - dp(3f),
                    x - dp(3f),
                    railY + dp(3f),
                    linePaint
                )
            }

            labelPaint.color = if (index == currentIndex) {
                color
            } else {
                AppColors.muted
            }
            labelPaint.textSize = fittedTextSize(
                value = chapterTitles[index],
                maxWidth = interval - dp(4f),
                maxSp = 8f
            )
            canvas.drawText(
                chapterTitles[index],
                x,
                labelY,
                labelPaint
            )
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
