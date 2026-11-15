package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SOURCE_EDGE_MS

/**
 * Resolve's audio cross fade at the start of a sound clip ([Clip.crossFadeFrames]): the sound that ends where it
 * starts keeps playing past its out point for the fade's length, fading out with equal power while this one fades
 * in (Resolve's Cross Fade +3 dB: the two gains' squares always add up to one). As with a dissolve, the fade never
 * reaches past the outgoing source: when that sound runs out first it is shortened, and with none left it is a cut.
 */
object AudioCrossFades {

    /** [outgoing] under a cross fade set to [wanted] frames, with [available] frames of source past its out point. */
    data class Coverage(val outgoing: Clip, val asset: MediaAsset, val wanted: Int, val available: Int) {
        /** The frames the fade really takes. */
        val frames: Int get() = minOf(wanted, available)
        val shortened: Boolean get() = available < wanted
    }

    /** The sound before [incoming] on its track, ending where it starts; null when there is none to fade from. */
    fun outgoing(doc: ProjectDocument, incoming: Clip): Clip? =
        if (incoming.kind != ClipKind.AUDIO) null
        else doc.timeline.clipsOn(incoming.trackId).firstOrNull { it.kind == ClipKind.AUDIO && it.end == incoming.start && it.id != incoming.id && it.enabled }

    /** What [incoming]'s cross fade fades from; null without a fade or a sound right before it. */
    fun coverage(doc: ProjectDocument, incoming: Clip, fps: Int): Coverage? {
        val wanted = minOf(incoming.crossFadeFrames, incoming.durationFrames)
        if (wanted <= 0) return null
        val outgoing = outgoing(doc, incoming) ?: return null
        val asset = doc.asset(outgoing.assetId)?.takeUnless { it.offline } ?: return null
        val usedEndMs = outgoing.sourceAt(outgoing.end, fps, asset)
        val left = ((asset.durationMs - usedEndMs + SOURCE_EDGE_MS) * fps / (1000.0 * outgoing.tailSpeed)).toInt()
        return Coverage(outgoing, asset, wanted, left.coerceIn(0, wanted))
    }
}
