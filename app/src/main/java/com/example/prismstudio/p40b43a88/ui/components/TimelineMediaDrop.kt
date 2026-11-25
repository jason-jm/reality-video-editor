package com.example.prismstudio.p40b43a88.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.prismstudio.p40b43a88.domain.model.TrackKind

/** Bridges the media tile and the timeline canvas, which have separate local coordinates in the same spatial window. */
class TimelineMediaDrop(val insert: (assetId: String, trackId: String, frame: Int) -> Unit) {
    data class Destination(val trackId: String, val frame: Int)
    internal var locate: ((Offset, TrackKind) -> Destination?)? = null
    internal var destination by mutableStateOf<Destination?>(null)

    internal fun finish(assetId: String) {
        val at = destination
        destination = null
        if (at != null) insert(assetId, at.trackId, at.frame)
    }
}

@Composable
internal fun Modifier.mediaDragSource(assetId: String, trackKind: TrackKind?, drop: TimelineMediaDrop?): Modifier {
    if (drop == null || trackKind == null) return this
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return onGloballyPositioned { coordinates = it }.pointerInput(assetId, trackKind, drop) {
        fun move(at: Offset) {
            val tile = coordinates?.takeIf { it.isAttached }
            drop.destination = tile?.let { drop.locate?.invoke(it.localToWindow(at), trackKind) }
        }
        try {
            detectDragGesturesAfterLongPress(
                onDragStart = ::move,
                onDrag = { change, _ -> move(change.position); change.consume() },
                onDragEnd = { drop.finish(assetId) },
                onDragCancel = { drop.destination = null },
            )
        } finally { drop.destination = null }
    }
}
