package ru.sportpulse.info

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import kotlin.math.max

private object ActionAccessibilityDelegate : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfo
    ) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = Button::class.java.name
        info.isClickable = host.isClickable
        info.isEnabled = host.isEnabled
        info.isSelected = host.isSelected
    }
}

internal fun <T : View> T.applyAccessibleAction(
    minimumTouchTargetPx: Int
): T {
    minimumWidth = max(minimumWidth, minimumTouchTargetPx)
    minimumHeight = max(minimumHeight, minimumTouchTargetPx)
    isClickable = true
    isFocusable = true
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    accessibilityDelegate = ActionAccessibilityDelegate
    return this
}
