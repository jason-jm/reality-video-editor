package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.Marker
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline

/** Drops a numbered marker at the playhead; a second marker on the same frame is refused. */
class AddMarkerUseCase(private val ids: IdGenerator) {

    operator fun invoke(timeline: Timeline, frame: Int, selectedClipId: String?, fps: Int): EditResult {
        val at = frame.coerceAtLeast(0)
        if (timeline.markers.any { it.frame == at }) throw EditRejected("该位置已有标记")
        // Numbered past the highest "标记 N" so a deleted marker's number is not given out again.
        val taken = timeline.markers.mapNotNull { it.label.removePrefix("标记 ").toIntOrNull() }.maxOrNull() ?: 0
        val label = "标记 ${maxOf(taken, timeline.markers.size) + 1}"
        val markers = (timeline.markers + Marker(ids.next("m"), at, label)).sortedBy { it.frame }
        return EditResult(timeline.copy(markers = markers), selectedClipId, "已在 ${Timecode.format(at, fps)} 添加$label")
    }
}
