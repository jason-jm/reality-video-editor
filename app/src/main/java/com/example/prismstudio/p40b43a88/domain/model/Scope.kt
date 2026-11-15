package com.example.prismstudio.p40b43a88.domain.model

enum class ScopeMode(val label: String) { WAVEFORM("波形"), PARADE("RGB 分量"), VECTORSCOPE("矢量示波器"), HISTOGRAM("直方图") }

/**
 * Density plots for the colour page. Grids are row-major with row 0 at the top (100 IRE);
 * every value is normalised to 0..1.
 */
class ScopeData(
    val mode: ScopeMode,
    val columns: Int,
    val rows: Int,
    /** One grid for luma / vectorscope, three (R, G, B) for the parade and histogram. */
    val planes: List<FloatArray>,
)
