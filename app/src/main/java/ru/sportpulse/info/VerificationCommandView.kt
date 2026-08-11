package ru.sportpulse.info

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

internal class VerificationCommandView @JvmOverloads constructor(
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
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.surface
        textAlign = Paint.Align.CENTER
        textSize = cappedSp(12f)
        typeface = AppTypography.display(context, bold = true)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppTypography.display(context, bold = true)
    }
    private val backgroundRect = RectF()
    private var tasks: List<VerificationCommandTask> = emptyList()

    init {
        importantForAccessibility =
            IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setTasks(value: List<VerificationCommandTask>) {
        tasks = value.take(VerificationCommandPolicy.VISIBLE_TASKS)
        contentDescription = if (tasks.isEmpty()) {
            "Очередь проверки пуста"
        } else {
            "Очередь проверки. " + tasks.mapIndexed {
                    index,
                    task ->
                "Шаг ${index + 1}: ${task.priority.title}, " +
                    "${task.title}."
            }.joinToString(" ")
        }
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        setMeasuredDimension(
            resolveSize(dp(320f).toInt(), widthMeasureSpec),
            resolveSize(dp(126f).toInt(), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (tasks.isEmpty()) return

        backgroundRect.set(
            0f,
            dp(8f),
            width.toFloat(),
            height - dp(8f)
        )
        fillPaint.color = AppColors.background
        canvas.drawRoundRect(
            backgroundRect,
            dp(7f),
            dp(7f),
            fillPaint
        )

        val count = tasks.size
        val slotWidth = width.toFloat() / count.toFloat()
        val centerY = dp(54f)
        val radius = dp(16f)
        val centers = List(count) { index ->
            slotWidth * (index + 0.5f)
        }

        if (centers.size > 1) {
            linePaint.color = AppColors.line
            linePaint.strokeWidth = dp(5f)
            canvas.drawLine(
                centers.first(),
                centerY,
                centers.last(),
                centerY,
                linePaint
            )
        }

        tasks.forEachIndexed { index, task ->
            val x = centers[index]
            val color = priorityColor(task.priority)
            drawFittedLabel(
                canvas = canvas,
                value = task.factor?.shortTitle
                    ?: task.priority.title,
                x = x,
                baseline = dp(25f),
                maxWidth = slotWidth - dp(10f),
                maxSp = 9f,
                color = AppColors.muted
            )
            fillPaint.color = color
            canvas.drawCircle(x, centerY, radius, fillPaint)
            canvas.drawText(
                (index + 1).toString(),
                x,
                textBaseline(numberPaint, centerY),
                numberPaint
            )
            drawFittedLabel(
                canvas = canvas,
                value = task.priority.shortTitle,
                x = x,
                baseline = dp(88f),
                maxWidth = slotWidth - dp(10f),
                maxSp = 8.2f,
                color = color
            )
            drawFittedLabel(
                canvas = canvas,
                value = "${task.modules.size} мод.",
                x = x,
                baseline = dp(107f),
                maxWidth = slotWidth - dp(10f),
                maxSp = 7.6f,
                color = AppColors.muted
            )
        }
    }

    private fun drawFittedLabel(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        maxSp: Float,
        color: Int
    ) {
        labelPaint.textSize = cappedSp(maxSp)
        val measured = labelPaint.measureText(value)
        if (measured > maxWidth && measured > 0f) {
            labelPaint.textSize *= maxWidth / measured
        }
        labelPaint.color = color
        canvas.drawText(value, x, baseline, labelPaint)
    }

    private fun priorityColor(
        priority: VerificationCommandPriority
    ): Int {
        return when (priority) {
            VerificationCommandPriority.STOP -> AppColors.danger
            VerificationCommandPriority.REPAIR,
            VerificationCommandPriority.REFRESH -> AppColors.warning
            VerificationCommandPriority.CHALLENGE,
            VerificationCommandPriority.UNBLOCK -> AppColors.signal
            VerificationCommandPriority.MAINTAIN -> AppColors.accent
        }
    }

    private fun textBaseline(
        paint: Paint,
        centerY: Float
    ): Float {
        return centerY -
            (paint.ascent() + paint.descent()) / 2f
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    private fun cappedSp(value: Float): Float {
        val metrics = resources.displayMetrics
        return value * min(
            metrics.density *
                resources.configuration.fontScale,
            metrics.density * 1.25f
        )
    }
}
