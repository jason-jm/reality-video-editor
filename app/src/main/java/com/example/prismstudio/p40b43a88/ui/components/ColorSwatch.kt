package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.foundation.layout.offset

/**
 * `color-swatch`: one colour to pick (a marker's, a title's, a clip's). The picked one is fuller and lifted toward
 * the viewer, as picked items are — a ring would be an app-drawn border. On a [plate] (a title colour
 * on the picture it reads on) the dot sits inside it.
 */
@Composable
fun ColorSwatch(
    fill: Color,
    description: String,
    picked: Boolean,
    onClick: () -> Unit,
    plate: Color? = null,
    enabled: Boolean = true,
    size: Dp = 32.dp,
) {
    // Insets as shares of the swatch, so a smaller one (a menu row) keeps its look.
    val inset = size * when {
        plate == null -> if (picked) 0.06f else 0.22f
        else -> if (picked) 0.16f else 0.28f
    }
    Box(
        modifier = Modifier
            .size(size)
            .offset(z = if (picked) 8.dp else 0.dp)
            .clip(CircleShape)
            .spatialHoverEffect(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.selected = picked }
            .drawBehind { plate?.let(::drawRect) }
            .padding(inset)
            .clip(CircleShape)
            .drawBehind { drawRect(fill) }
            .semantics { contentDescription = (description + if (picked) "（当前）" else "").localized() },
    )
}
