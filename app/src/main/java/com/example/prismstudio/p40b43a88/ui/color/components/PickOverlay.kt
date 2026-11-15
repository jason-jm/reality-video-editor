package com.example.prismstudio.p40b43a88.ui.color.components

import androidx.compose.foundation.gestures.detectTapGestures
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.components.PictureMapping
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.ui.color.PickOverlayUi
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismType
import com.pico.spatial.ui.design.PicoTheme
import com.example.prismstudio.p40b43a88.ui.components.Text

/**
 * `qualifier-pick-overlay`: while the qualifier's eyedropper is on, a tap on the monitor picks the colour
 * the selected clip shows there — mapped back through the clip's transform to its own picture — or adds it
 * to the selection or subtracts it, as the hint at the top says.
 */
@Composable
fun BoxScope.PickOverlay(overlay: PickOverlayUi, pictureAspect: Float, onPick: (Float, Float) -> Unit) {
    val current by rememberUpdatedState(overlay)
    val pick by rememberUpdatedState(onPick)
    Box(
        Modifier
            .matchParentSize()
            .semantics { contentDescription = "吸管：点击画面中的颜色".localized() }
            .pointerInput(pictureAspect) {
                detectTapGestures { position ->
                    val p = PictureMapping(current.transform, current.mirrored, pictureAspect, size.height.toFloat()).toPicture(position)
                    pick(p.x, p.y)
                }
            },
    ) {
        Text(
            overlay.hint,
            style = PrismType.caption,
            color = PicoTheme.colorScheme.labelPrimaryLight,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .drawBehind { drawRect(PrismColors.PictureBlack, alpha = 0.6f) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
