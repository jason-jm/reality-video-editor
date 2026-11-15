package com.example.prismstudio.p40b43a88.ui.components

import com.example.prismstudio.p40b43a88.data.repository.MonitorPlayback

/** Attaches a ready surface, or detaches exactly the one a destroyed view held. */
fun MonitorPlayback.route(change: SurfaceChange) = when (change) {
    is SurfaceChange.Ready -> attachSurface(change.surface, change.width, change.height, change.layer)
    is SurfaceChange.Gone -> detachSurface(change.surface, change.layer)
}
