package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.prismstudio.p40b43a88.domain.i18n.localized
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors
import com.pico.spatial.ui.design.PicoTheme
import java.util.Locale

/**
 * The `level-meter` renderer: a horizontal peak meter from -60 dB to 0 dB, green below
 * [hotFromDb], amber up to [clipFromDb] and red above.
 */
@Composable
fun LevelMeter(
    levelDb: Float,
    modifier: Modifier = Modifier,
    hotFromDb: Float = -12f,
    clipFromDb: Float = -1f,
    description: String = "电平表",
) {
    val track = PicoTheme.colorScheme.fillTertiary
    Canvas(modifier.semantics { contentDescription = "${description.localized()} ${String.format(Locale.US, "%.1f", levelDb)} dB" }) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, cornerRadius = r)
        fun x(db: Float) = ((db + 60f) / 60f).coerceIn(0f, 1f) * size.width
        val level = x(levelDb)
        if (level <= 0f) return@Canvas
        val hot = x(hotFromDb)
        val clip = x(clipFromDb)
        drawRoundRect(PrismColors.MeterSafe, size = Size(minOf(level, hot), size.height), cornerRadius = r)
        if (level > hot) {
            drawRect(PrismColors.MeterHot, topLeft = Offset(hot, 0f), size = Size(minOf(level, clip) - hot, size.height))
        }
        if (level > clip) {
            drawRoundRect(PrismColors.MeterClip, topLeft = Offset(clip, 0f), size = Size(level - clip, size.height), cornerRadius = r)
        }
    }
}
