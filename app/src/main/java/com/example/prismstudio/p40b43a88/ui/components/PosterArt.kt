package com.example.prismstudio.p40b43a88.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors

/**
 * A poster / thumbnail well: picture-black rounded frame showing the decoded [frame], or a
 * three-band gradient sampled from the media while the frame is not decoded yet.
 */
@Composable
fun PosterArt(
    colors: PosterColors,
    frame: Bitmap?,
    radius: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val brush = remember(colors) {
        Brush.verticalGradient(
            listOf(PrismColors.argb(colors.top), PrismColors.argb(colors.middle), PrismColors.argb(colors.bottom)),
        )
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .drawBehind {
                drawRect(PrismColors.PictureBlack)
                if (frame == null) drawRect(brush)
            },
    ) {
        if (frame != null) {
            val image = remember(frame) { frame.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        content()
    }
}
