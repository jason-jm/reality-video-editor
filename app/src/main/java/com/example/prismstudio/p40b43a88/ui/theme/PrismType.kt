package com.example.prismstudio.p40b43a88.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.pico.spatial.ui.design.PicoTheme

/** The design's typography roles routed onto PicoTheme roles of the same size and weight. */
object PrismType {
    /** 32/40 bold. */
    val display: TextStyle @Composable get() = PicoTheme.typography.displayMedium

    /** 24/30 bold. */
    val headline: TextStyle @Composable get() = PicoTheme.typography.headlineLarge

    /** 20/26 semibold. */
    val title: TextStyle @Composable get() = PicoTheme.typography.titleLarge

    /** 16 medium, single line. */
    val body: TextStyle @Composable get() = PicoTheme.typography.bodyLarge

    /** 16 medium, paragraphs. */
    val paragraph: TextStyle @Composable get() = PicoTheme.typography.bodyLargeMultiline

    /** 14/18 medium. */
    val label: TextStyle @Composable get() = PicoTheme.typography.bodyMedium

    /** 12/16 medium. */
    val caption: TextStyle @Composable get() = PicoTheme.typography.bodySmall

    /** 20/26 semibold monospace for timecode. */
    val timecode: TextStyle @Composable
        get() = PicoTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)

    /** 12/16 monospace for ruler and readouts. */
    val timecodeSmall: TextStyle @Composable
        get() = PicoTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
}
