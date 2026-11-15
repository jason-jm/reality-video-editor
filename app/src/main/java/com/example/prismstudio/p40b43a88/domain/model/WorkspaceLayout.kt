package com.example.prismstudio.p40b43a88.domain.model

/** The window's size range in dp, as the root view asks the window container for it. */
data class WindowSizeRange(val minWidth: Float, val maxWidth: Float, val minHeight: Float, val maxHeight: Float)

/**
 * The studio's two window layouts. PICO OS caps a planar window at 2700 × 1800 dp (about 63° wide at the default
 * 1.75 m — measured on the emulator: 1000 dp ≈ 0.8 m) and has no curved panels, so the ultrawide layout is the widest
 * flat window: the timeline across its whole width like an ultrawide monitor, the viewers, media and inspector above it.
 */
object WorkspaceLayout {
    /** The window PICO OS allows at most (dp). */
    const val MAX_WINDOW_WIDTH = 2700f
    const val MAX_WINDOW_HEIGHT = 1800f

    /** The standard layout: the window the user sizes between these (it opens at the manifest's 1600 × 960). */
    val STANDARD = WindowSizeRange(minWidth = 1280f, maxWidth = 2400f, minHeight = 820f, maxHeight = 1440f)

    /** The ultrawide layout: the window at its widest, tall enough for two 16:9 viewers above the timeline. */
    val ULTRAWIDE = WindowSizeRange(minWidth = MAX_WINDOW_WIDTH, maxWidth = MAX_WINDOW_WIDTH, minHeight = 1200f, maxHeight = MAX_WINDOW_HEIGHT)

    /**
     * Back from ultrawide: briefly the size the window opens at, so it shrinks to it (the standard range alone would
     * leave it at 2400 dp), then the standard range again.
     */
    val RESTORE = WindowSizeRange(minWidth = 1600f, maxWidth = 1600f, minHeight = 960f, maxHeight = 960f)

    /** From this width (dp) the Edit page shows the source and timeline viewers side by side (Resolve's dual viewer). */
    const val DUAL_VIEWER_MIN_WIDTH = 2200f

    fun window(ultrawide: Boolean, restoring: Boolean): WindowSizeRange = when {
        ultrawide -> ULTRAWIDE
        restoring -> RESTORE
        else -> STANDARD
    }

    fun dualViewer(widthDp: Float): Boolean = widthDp >= DUAL_VIEWER_MIN_WIDTH
}
