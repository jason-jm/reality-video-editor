package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import com.pico.spatial.ui.design.ButtonColors
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.ButtonSize
import com.pico.spatial.ui.design.ChipColors
import com.pico.spatial.ui.design.ChipSize
import com.pico.spatial.ui.design.ChipsDefaults
import com.pico.spatial.ui.design.ToggleableChipColors
import com.pico.spatial.ui.design.Button as SdkButton
import com.pico.spatial.ui.design.ButtonChip as SdkButtonChip
import com.pico.spatial.ui.design.SegmentItem as SdkSegmentItem
import com.pico.spatial.ui.design.ToggleableChip as SdkToggleableChip

/*
 * SpatialUI's segments and chips with labels that stay on one line in every language ([LocalFitText]): an English or
 * German word twice as wide as its Chinese source shrinks a little, then is cut, instead of breaking over two lines.
 */

/**
 * SpatialUI's Button, its label fitted to one line. A label that wrapped inside the 32 dp button showed only where its
 * two lines met — bars, not words — once a row squeezed the button (a disabled 标准化 in Chinese, "Render all" in English).
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: ButtonSize = ButtonDefaults.Regular,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    SdkButton(onClick = onClick, modifier = modifier, enabled = enabled, size = size, colors = colors, leadingIcon = leadingIcon) {
        CompositionLocalProvider(LocalFitText provides true) { content() }
    }
}

/** SpatialUI's SegmentItem, its title fitted to one line. */
@Composable
fun RowScope.SegmentItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues? = null,
) {
    val fitted: (@Composable () -> Unit)? = title?.let { { CompositionLocalProvider(LocalFitText provides true) { it() } } }
    if (contentPadding == null) {
        SdkSegmentItem(selected = selected, onClick = onClick, modifier = modifier, title = fitted, icon = icon)
    } else {
        SdkSegmentItem(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            title = fitted,
            icon = icon,
            contentPadding = contentPadding,
        )
    }
}

/** SpatialUI's ButtonChip, its label fitted to one line. */
@Composable
fun ButtonChip(
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    chipSize: ChipSize = ChipsDefaults.Small,
    colors: ChipColors? = null,
) {
    val fitted: @Composable () -> Unit = { CompositionLocalProvider(LocalFitText provides true) { label() } }
    if (colors == null) {
        SdkButtonChip(fitted, onClick, modifier, leadingIcon, trailingIcon, enabled, chipSize)
    } else {
        SdkButtonChip(fitted, onClick, modifier, leadingIcon, trailingIcon, enabled, chipSize, colors)
    }
}

/** SpatialUI's ToggleableChip, its label fitted to one line. */
@Composable
fun ToggleableChip(
    label: @Composable () -> Unit,
    isToggleOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    chipSize: ChipSize = ChipsDefaults.Small,
    colors: ToggleableChipColors? = null,
) {
    val fitted: @Composable () -> Unit = { CompositionLocalProvider(LocalFitText provides true) { label() } }
    if (colors == null) {
        SdkToggleableChip(fitted, isToggleOn, onClick, modifier, leadingIcon, trailingIcon, enabled, chipSize)
    } else {
        SdkToggleableChip(fitted, isToggleOn, onClick, modifier, leadingIcon, trailingIcon, enabled, chipSize, colors)
    }
}

/**
 * A label's share of a row of equal controls: its length in the language shown, so a long word gets the room it needs
 * while labels of one length (every Chinese tab name) still share alike.
 */
@Composable
@ReadOnlyComposable
fun labelWeight(text: String, context: String? = null): Float = tr(text, context).length.coerceAtLeast(3).toFloat()
