package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import kotlinx.coroutines.launch

/**
 * Keyboard focus made visible (release round R13, checklist D14): a control reached with Tab or the arrow keys draws a
 * two-tone ring, as the web's :focus-visible does — never after a controller press, which focuses controls too. SpatialUI
 * draws hover only, so a keyboard user could not see where focus was.
 */
object FocusVisibility {
    /** True after a key that moves focus, false again after a pointer press. */
    var fromKeyboard by mutableStateOf(false)
        internal set

    internal fun isNavigationKey(key: Key): Boolean =
        key == Key.Tab || key == Key.DirectionUp || key == Key.DirectionDown || key == Key.DirectionLeft || key == Key.DirectionRight
}

/** On the window's root: notes whether focus last moved by keyboard or the pointer ([FocusVisibility]). */
fun Modifier.trackFocusVisibility(): Modifier = this
    .onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && FocusVisibility.isNavigationKey(event.key)) FocusVisibility.fromKeyboard = true
        false
    }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // A press only: the controller's ray sends hover moves all the time, which must not hide the ring.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && FocusVisibility.fromKeyboard) FocusVisibility.fromKeyboard = false
            }
        }
    }

/** [content] with every control's press feedback ([LocalIndication]) also drawing the keyboard focus ring. */
@Composable
fun ProvideFocusRing(content: @Composable () -> Unit) {
    val base = LocalIndication.current
    if (base !is IndicationNodeFactory) return content()
    val ring = FocusRingIndication(base, PicoTheme.colorScheme.labelPrimary, PicoTheme.colorScheme.labelPrimaryLight)
    CompositionLocalProvider(LocalIndication provides ring, content = content)
}

private class FocusRingIndication(private val base: IndicationNodeFactory, private val outer: Color, private val inner: Color) :
    IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusRingNode(interactionSource, base, outer, inner)

    override fun equals(other: Any?): Boolean =
        other is FocusRingIndication && other.base == base && other.outer == outer && other.inner == inner

    override fun hashCode(): Int = (base.hashCode() * 31 + outer.hashCode()) * 31 + inner.hashCode()
}

private class FocusRingNode(
    private val source: InteractionSource,
    base: IndicationNodeFactory,
    private val outer: Color,
    private val inner: Color,
) : DelegatingNode(), DrawModifierNode {
    private var focused = false

    init {
        delegate(base.create(source))
    }

    override fun onAttach() {
        coroutineScope.launch {
            val focuses = mutableListOf<FocusInteraction.Focus>()
            source.interactions.collect { interaction ->
                when (interaction) {
                    is FocusInteraction.Focus -> focuses += interaction
                    is FocusInteraction.Unfocus -> focuses -= interaction.focus
                }
                val now = focuses.isNotEmpty()
                if (now != focused) {
                    focused = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        // Read in draw, so a change of input redraws the ring.
        if (focused && FocusVisibility.fromKeyboard) {
            // Inside the control, rounded as its shape most likely is: round for a small control (icon buttons), a pill
            // for a low one, a card's corner for the rest.
            val wide = RING.toPx()
            val radius = if (size.minDimension <= SMALL.toPx()) size.minDimension / 2f else minOf(size.height / 2f, CORNER.toPx())
            fun ring(color: Color, inset: Float) = drawRoundRect(
                color,
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
                style = Stroke(wide),
            )
            ring(inner, wide / 2f)
            ring(outer, wide * 1.5f)
        }
    }

    private companion object {
        val RING = 2.dp
        val CORNER = 16.dp
        val SMALL = 64.dp
    }
}
