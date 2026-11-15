package com.example.prismstudio.p40b43a88.ui.navigation

import androidx.annotation.DrawableRes
import com.example.prismstudio.p40b43a88.R

/** Top-level places: the project library and the open-project workspace. */
enum class AppDestination { LIBRARY, WORKSPACE }

/** The five post-production stages shown in the top-edge TabBar, in Resolve order. */
enum class WorkspaceStage(val label: String, @DrawableRes val icon: Int) {
    MEDIA("媒体", R.drawable.ic_picture_double),
    EDIT("剪辑", R.drawable.ic_scissors),
    COLOR("调色", R.drawable.ic_color_wheel),
    AUDIO("音频", R.drawable.ic_headphone),
    DELIVER("交付", R.drawable.ic_arrow_outside_up),
}
