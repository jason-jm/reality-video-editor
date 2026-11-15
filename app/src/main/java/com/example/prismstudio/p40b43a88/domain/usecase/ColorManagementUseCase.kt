package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.effectiveTransfer

/**
 * Resolve's Color Management on delivery: which of the timeline's clips are HDR (by their media's tags or input colour
 * space), and what the render does with them — tone maps them into the Rec.709 timeline.
 */
class ColorManagementUseCase {

    /** The timeline's video clips by the transfer they play with: HLG and PQ ones only. */
    fun hdrClips(doc: ProjectDocument): Map<ColorTransfer, Int> = doc.timeline.clips
        .filter { it.kind == ClipKind.VIDEO && it.enabled }
        .mapNotNull { c -> doc.asset(c.assetId)?.effectiveTransfer?.takeIf { it != ColorTransfer.SDR } }
        .groupingBy { it }.eachCount()
        .toSortedMap()

    /** `deliver-hdr-note`: the HDR clips and that they are tone mapped (and how); null when the timeline has none. */
    fun note(doc: ProjectDocument): String? {
        val hdr = hdrClips(doc).takeIf { it.isNotEmpty() } ?: return null
        val count = hdr.entries.joinToString("、") { (t, n) -> "${t.label} $n 段" }
        return "含 HDR 素材（$count）：时间线为 ${ColorManagement.TIMELINE_LABEL}，导出时按「${doc.colorManagement.toneMapping.label}」色调映射" +
            if (ColorTransfer.PQ in hdr) "，PQ 母版峰值按 ${doc.colorManagement.pqPeakNits} nits" else ""
    }
}
