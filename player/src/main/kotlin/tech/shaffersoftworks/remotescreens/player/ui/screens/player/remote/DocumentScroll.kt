@file:Suppress("RestrictedApi")

package tech.shaffersoftworks.remotescreens.player.ui.screens.player.remote

import androidx.compose.remote.core.operations.TouchExpression
import androidx.compose.remote.core.operations.Utils
import androidx.compose.remote.core.operations.layout.Component
import androidx.compose.remote.core.operations.layout.LayoutComponent
import androidx.compose.remote.core.operations.layout.modifiers.ComponentModifiers
import androidx.compose.remote.core.operations.layout.modifiers.ScrollModifierOperation
import androidx.compose.remote.player.view.RemoteComposePlayer
import java.lang.reflect.Field

/**
 * Copies list positions from the document on screen onto the one that replaces it.
 * [Mark.offset] is the modifier's own value: 0 at the start, negative once the list has moved.
 * Paint reads a positive distance from a float, and the touch expression would otherwise write 0
 * back into that float until the next drag. [Mark.max] is how far the list can still travel.
 * Restoring the offset without that range leaves a later drag clamped at the old (or zero) end.
 */
internal object DocumentScroll {
    data class Mark(
        val componentId: Int,
        val isVertical: Boolean,
        val offset: Float,
        val max: Float,
    )

    fun capture(player: RemoteComposePlayer): List<Mark> {
        val root = rootOf(player) ?: return emptyList()
        return capture(root)
    }

    fun capture(root: Component): List<Mark> {
        val marks = mutableListOf<Mark>()
        walk(root) { component ->
            val scroll = scrollModifier(component) ?: return@walk
            // componentId() stores the id on getId(). Generated layout ids are negative.
            val componentId = component.id
            if (componentId <= 0) return@walk
            val isVertical = scroll.isVerticalScroll()
            val offset = if (isVertical) component.scrollY else component.scrollX
            val max = maxField(isVertical).getFloat(scroll)
            if (offset != 0f || max != 0f) {
                marks += Mark(componentId, isVertical, offset, max)
            }
        }
        return marks
    }

    fun restore(player: RemoteComposePlayer, marks: List<Mark>) {
        if (marks.isEmpty()) return
        val root = rootOf(player) ?: return
        val context = remoteContextOf(player)
        restore(root, marks) { id, value -> context?.loadFloat(id, value) }
    }

    fun restore(root: Component, marks: List<Mark>, writeFloat: (Int, Float) -> Unit) {
        if (marks.isEmpty()) return
        val byId = marks.associateBy { it.componentId }
        walk(root) { component ->
            val mark = byId[component.id] ?: return@walk
            val scroll = scrollModifier(component) ?: return@walk
            putBack(scroll, mark, writeFloat)
        }
    }

    private fun rootOf(player: RemoteComposePlayer): Component? = runCatching {
        player.document.document.rootLayoutComponent
    }.getOrNull()

    private fun scrollModifier(component: Component): ScrollModifierOperation? {
        val direct = component.selfOrModifier(ScrollModifierOperation::class.java)
        if (direct != null) return direct
        var found: ScrollModifierOperation? = null
        for (operation in component.mList) {
            val modifiers = operation as? ComponentModifiers ?: continue
            for (modifier in modifiers.list) {
                if (modifier is ScrollModifierOperation && found == null) found = modifier
            }
        }
        return found
    }

    private fun walk(component: Component, visit: (Component) -> Unit) {
        val seen = HashSet<Component>()
        fun descend(current: Component) {
            if (!seen.add(current)) return
            visit(current)
            // Parsed children live in the operation list. childrenComponents fills in only after measure.
            for (operation in current.mList) {
                val nested = operation as? Component ?: continue
                descend(nested)
            }
            val laidOut = (current as? LayoutComponent)?.childrenComponents ?: return
            for (child in laidOut) descend(child)
        }
        descend(component)
    }

    /** Writes [mark] onto [scroll] and into the floats its paint and later drags copy from. */
    private fun putBack(scroll: ScrollModifierOperation, mark: Mark, writeFloat: (Int, Float) -> Unit) {
        val offsetField = if (mark.isVertical) SCROLL_Y else SCROLL_X
        val initialField = if (mark.isVertical) INITIAL_SCROLL_Y else INITIAL_SCROLL_X
        offsetField.setFloat(scroll, mark.offset)
        initialField.setFloat(scroll, mark.offset)
        maxField(mark.isVertical).setFloat(scroll, mark.max)
        val position = -mark.offset
        val positionExpression = scroll.positionExpression
        if (Utils.isVariable(positionExpression)) {
            writeFloat(Utils.idFromNan(positionExpression), position)
        }
        val maxExpression = MAX_EXPRESSION.getFloat(scroll)
        if (Utils.isVariable(maxExpression)) {
            writeFloat(Utils.idFromNan(maxExpression), mark.max)
        }
        val touch = TOUCH_EXPRESSION.get(scroll) as? TouchExpression ?: return
        UNMODIFIED.setBoolean(touch, false)
        CURRENT_VALUE.setFloat(touch, position)
        TOUCH_MAX.setFloat(touch, mark.max)
        TOUCH_DOWN.setBoolean(touch, false)
        TOUCH_EASING.setBoolean(touch, false)
    }

    private fun field(owner: Class<*>, name: String): Field =
        owner.getDeclaredField(name).apply { isAccessible = true }

    private val SCROLL_X = field(ScrollModifierOperation::class.java, "mScrollX")
    private val SCROLL_Y = field(ScrollModifierOperation::class.java, "mScrollY")
    private val INITIAL_SCROLL_X = field(ScrollModifierOperation::class.java, "mInitialScrollX")
    private val INITIAL_SCROLL_Y = field(ScrollModifierOperation::class.java, "mInitialScrollY")
    private val MAX_SCROLL_X = field(ScrollModifierOperation::class.java, "mMaxScrollX")
    private val MAX_SCROLL_Y = field(ScrollModifierOperation::class.java, "mMaxScrollY")
    private val MAX_EXPRESSION = field(ScrollModifierOperation::class.java, "mMax")
    private val TOUCH_EXPRESSION = field(ScrollModifierOperation::class.java, "mTouchExpression")
    private val UNMODIFIED = field(TouchExpression::class.java, "mUnmodified")
    private val CURRENT_VALUE = field(TouchExpression::class.java, "mCurrentValue")
    private val TOUCH_MAX = field(TouchExpression::class.java, "mOutMax")
    private val TOUCH_DOWN = field(TouchExpression::class.java, "mTouchDown")
    private val TOUCH_EASING = field(TouchExpression::class.java, "mEasingToStop")

    private fun maxField(isVertical: Boolean): Field = if (isVertical) MAX_SCROLL_Y else MAX_SCROLL_X
}
