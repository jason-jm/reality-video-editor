package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase
import com.example.prismstudio.p40b43a88.ui.theme.PrismColors

/**
 * A timeline in miniature on black: [rows] tracks (the top one first), each clip a bar in its kind's colour — picture blue,
 * sound green, titles violet. The media pool's timeline cards and the Edit page library's timeline tiles draw it.
 */
@Composable
fun TimelineMiniature(rows: Int, bars: List<TimelinePoolUseCase.Bar>, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp))) {
        drawRect(PrismColors.PictureBlack)
        val count = rows.coerceAtLeast(1)
        val pad = size.height * 0.1f
        val rowH = (size.height - pad * 2) / count
        val gap = rowH * 0.18f
        for (bar in bars) {
            val color = when (bar.kind) {
                ClipKind.AUDIO -> PrismColors.AudioClip
                ClipKind.TITLE -> PrismColors.TitleClip
                ClipKind.VIDEO -> PrismColors.VideoClip
            }
            val left = pad + bar.from * (size.width - pad * 2)
            val width = ((bar.until - bar.from) * (size.width - pad * 2) - 2f).coerceAtLeast(2f)
            drawRoundRect(color, topLeft = Offset(left, pad + bar.row * rowH + gap / 2), size = Size(width, rowH - gap), cornerRadius = CornerRadius(3.dp.toPx()))
        }
    }
}
