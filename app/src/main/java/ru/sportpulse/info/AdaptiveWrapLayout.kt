package ru.sportpulse.info

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

internal class AdaptiveWrapLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    var lineSpacingPx: Int = 0
        set(value) {
            val normalized = value.coerceAtLeast(0)
            if (field != normalized) {
                field = normalized
                requestLayout()
            }
        }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val constrainedWidth = MeasureSpec.getMode(widthMeasureSpec) !=
            MeasureSpec.UNSPECIFIED
        val availableWidth = if (constrainedWidth) {
            (MeasureSpec.getSize(widthMeasureSpec) -
                paddingLeft - paddingRight).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        var lineWidth = 0
        var lineHeight = 0
        var widestLine = 0
        var contentHeight = 0

        visibleChildren().forEach { child ->
            measureChildWithMargins(
                child,
                widthMeasureSpec,
                paddingLeft + paddingRight,
                heightMeasureSpec,
                paddingTop + paddingBottom
            )
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth +
                params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight +
                params.topMargin + params.bottomMargin
            if (lineWidth > 0 && lineWidth + childWidth > availableWidth) {
                widestLine = max(widestLine, lineWidth)
                contentHeight += lineHeight + lineSpacingPx
                lineWidth = childWidth
                lineHeight = childHeight
            } else {
                lineWidth += childWidth
                lineHeight = max(lineHeight, childHeight)
            }
        }
        widestLine = max(widestLine, lineWidth)
        contentHeight += lineHeight

        val desiredWidth = widestLine + paddingLeft + paddingRight
        val desiredHeight = contentHeight + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSizeAndState(
                max(desiredWidth, suggestedMinimumWidth),
                widthMeasureSpec,
                0
            ),
            resolveSizeAndState(
                max(desiredHeight, suggestedMinimumHeight),
                heightMeasureSpec,
                0
            )
        )
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val availableRight = right - left - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        visibleChildren().forEach { child ->
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth +
                params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight +
                params.topMargin + params.bottomMargin
            if (x > paddingLeft && x + childWidth > availableRight) {
                x = paddingLeft
                y += lineHeight + lineSpacingPx
                lineHeight = 0
            }
            val childLeft = x + params.leftMargin
            val childTop = y + params.topMargin
            child.layout(
                childLeft,
                childTop,
                childLeft + child.measuredWidth,
                childTop + child.measuredHeight
            )
            x += childWidth
            lineHeight = max(lineHeight, childHeight)
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return MarginLayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    override fun generateLayoutParams(params: LayoutParams?): LayoutParams {
        return when (params) {
            is MarginLayoutParams -> MarginLayoutParams(params)
            null -> generateDefaultLayoutParams()
            else -> MarginLayoutParams(params)
        }
    }

    override fun checkLayoutParams(params: LayoutParams?): Boolean {
        return params is MarginLayoutParams
    }

    private fun visibleChildren(): Sequence<View> {
        return (0 until childCount)
            .asSequence()
            .map(::getChildAt)
            .filter { it.visibility != View.GONE }
    }
}

internal object AdaptiveGroupTags {
    const val TIMELAPSE_HORIZONS = "adaptive_timelapse_horizons"
    const val SPORT_FILTERS = "adaptive_sport_filters"
    const val TIME_FILTERS = "adaptive_time_filters"
    const val EVENT_TAGS = "adaptive_event_tags"
    const val DECISION_MARKETS = "adaptive_decision_markets"
    const val MARKET_TEMPLATES = "adaptive_market_templates"
}
