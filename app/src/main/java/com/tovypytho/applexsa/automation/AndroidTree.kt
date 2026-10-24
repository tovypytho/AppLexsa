package com.tovypytho.applexsa.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.tovypytho.applexsa.core.Box
import com.tovypytho.applexsa.core.UiNode

object AndroidTree {
    fun snapshot(root: AccessibilityNodeInfo): UiNode {
        var count = 0
        fun visit(n: AccessibilityNodeInfo, path: String, depth: Int): UiNode {
            count++
            val bounds = Rect().also { n.getBoundsInScreen(it) }
            val children = if (depth < 35 && count < 1200) (0 until n.childCount.coerceAtMost(150)).mapNotNull { index ->
                n.getChild(index)?.let { visit(it, "$path/$index", depth + 1) }
            } else emptyList()
            return UiNode(path, n.viewIdResourceName.orEmpty(), n.text?.toString().orEmpty(), n.contentDescription?.toString().orEmpty(),
                n.className?.toString().orEmpty(), Box(bounds.left, bounds.top, bounds.right, bounds.bottom),
                n.isClickable, n.isEnabled, n.isVisibleToUser, n.isCheckable, n.isChecked, n.isSelected,
                n.isScrollable, n.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }, children)
        }
        return visit(root, "0", 0)
    }
    fun resolve(root: AccessibilityNodeInfo, path: String): AccessibilityNodeInfo? {
        var current = root
        for (part in path.split('/').drop(1)) current = current.getChild(part.toIntOrNull() ?: return null) ?: return null
        return current.takeIf { it.isVisibleToUser && it.isEnabled }
    }
    fun click(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        repeat(4) {
            val n = current ?: return false
            if (n.isClickable && n.isEnabled) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = n.parent
        }
        return false
    }
}
