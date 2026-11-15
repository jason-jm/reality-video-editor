package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SOURCE_EDGE_MS
import com.example.prismstudio.p40b43a88.domain.model.Transition
import com.example.prismstudio.p40b43a88.domain.model.TransitionAlignment
import com.example.prismstudio.p40b43a88.domain.model.TransitionType

/**
 * Where a clip's opening dissolve, push, wipe or dip really plays around its cut. Before the cut the clip shows early,
 * from before its in point (its head handles); after it the clip that ends there keeps playing past its out point (its
 * tail handles). As in DaVinci Resolve, a transition never reaches past either source: the alignment says where it would
 * sit, and a centred one slides toward the side that has picture to keep its length; a transition anchored to the cut
 * plays only as long as there is picture, and with none left it is a cut. The part before the cut takes at most half
 * the clip before it, so that clip keeps the other half to itself (and to its own opening transition).
 */
object TransitionHandles {

    /** Transitions that uncover the outgoing picture (the dips cover it with black or white, the zoom blur with the incoming). */
    val REVEALING: Set<TransitionType> = TransitionType.entries.filter { it.reveals }.toSet()

    /**
     * [transition] as it plays around the cut: [lead] frames before it and [tail] after, where its alignment asked for
     * [wantedLead] and [wantedTail]. [outgoing] is the clip it uncovers, null when nothing adjacent is there (or nothing
     * to uncover); [headLeft] / [tailLeft] are the frames of picture left before the clip's in point and after the
     * outgoing clip's out point (capped at what the transition could use).
     */
    data class Span(
        val transition: Transition,
        val outgoing: Clip?,
        val wantedLead: Int,
        val wantedTail: Int,
        val lead: Int,
        val tail: Int,
        val headLeft: Int,
        val tailLeft: Int,
        /**
         * A dip's middle — all black or white — in frames after the cut (before it when negative): the outgoing clip plays
         * up to it, the incoming one from it. Null for the other kinds, and for a dip with nothing before it.
         */
        val midpoint: Int? = null,
    ) {
        /** The frames it really takes. */
        val frames: Int get() = lead + tail
        val wanted: Int get() = wantedLead + wantedTail
        val shortened: Boolean get() = frames < wanted

        /** Kept its length but slid from where its alignment put it, toward the side with picture. */
        val shifted: Boolean get() = !shortened && lead != wantedLead

        /** The transition as it plays: as long as it really lasts, null when nothing is left of it (a cut). */
        val effective: Transition?
            get() = when {
                frames <= 0 -> null
                frames == transition.frames -> transition
                else -> transition.copy(frames = frames)
            }
    }

    /** Where [incoming]'s opening transition plays; null without one. */
    fun span(doc: ProjectDocument, incoming: Clip, fps: Int): Span? {
        val transition = incoming.transitionIn ?: return null
        if (transition.frames <= 0) return null
        // The zoom blur, and transitions with nothing adjacent to show, start at the cut and play as set.
        val asSet = Span(transition, null, 0, transition.frames, 0, transition.frames, 0, 0)
        if (!transition.type.showsOutgoing) return asSet
        val outgoing = doc.timeline.clipsOn(incoming.trackId)
            .firstOrNull { it.kind == ClipKind.VIDEO && it.end == incoming.start && it.id != incoming.id } ?: return asSet
        val outgoingAsset = doc.asset(outgoing.assetId)?.takeUnless { it.offline } ?: return asSet
        val wanted = minOf(transition.frames, incoming.durationFrames)
        val wantedLead = transition.alignment.lead(wanted)
        val wantedTail = wanted - wantedLead
        // After the cut: the outgoing source past its out point (a still holds as long as needed).
        val tailRoom = if (outgoingAsset.kind == MediaKind.IMAGE) {
            wanted
        } else {
            val usedEndMs = outgoing.sourceAt(outgoing.end, fps, outgoingAsset)
            framesOf(outgoingAsset.durationMs - usedEndMs, fps, outgoing.tailSpeed)
        }.coerceIn(0, wanted)
        // Before the cut: the clip's own source before its in point...
        val incomingAsset = doc.asset(incoming.assetId)?.takeUnless { it.offline }
        val headFrames = when {
            incomingAsset == null -> 0
            incomingAsset.kind == MediaKind.IMAGE -> wanted
            else -> framesOf(incoming.sourceAt(incoming.start, fps, incomingAsset), fps, incoming.headSpeed)
        }.coerceIn(0, wanted)
        // ...over at most half the clip before it, and never over the frames that clip's own opening transition may play
        // beneath it (the handles layer holds one picture at a time).
        val ownTail = outgoing.transitionIn?.takeIf { it.type.showsOutgoing }?.let { minOf(it.frames, outgoing.durationFrames) } ?: 0
        val leadCap = minOf(wanted, outgoing.durationFrames / 2, outgoing.durationFrames - ownTail).coerceAtLeast(0)
        if (transition.type.dips) return dipSpan(transition, outgoing, wanted, wantedLead, wantedTail, headFrames, tailRoom, leadCap)
        val headRoom = minOf(headFrames, leadCap)
        val (lead, tail) = when (transition.alignment) {
            TransitionAlignment.START -> 0 to minOf(wanted, tailRoom)
            TransitionAlignment.END -> minOf(wanted, headRoom) to 0
            // Centred: each side takes what the other could not, as far as its picture goes.
            TransitionAlignment.CENTER ->
                minOf(wanted - minOf(wantedTail, tailRoom), headRoom) to minOf(wanted - minOf(wantedLead, headRoom), tailRoom)
        }
        return Span(transition, outgoing, wantedLead, wantedTail, lead, tail, headRoom, tailRoom)
    }

    /**
     * A dip shows one picture at a time: the outgoing one up to its middle, the incoming one from there. Its alignment puts
     * that middle on the cut (centred: it needs no handles at all), half its length after it (the outgoing clip plays on
     * past its out point to the middle) or before it (the incoming one shows from before its in point); a source running
     * short slides it toward the cut, keeping its length. Only a clip before it too short to dip shortens it.
     */
    @Suppress("LongParameterList")
    private fun dipSpan(
        transition: Transition,
        outgoing: Clip,
        wanted: Int,
        wantedLead: Int,
        wantedTail: Int,
        headFrames: Int,
        tailFrames: Int,
        leadCap: Int,
    ): Span {
        val half = wanted / 2
        // The middle lies (half − lead) frames after the cut: past the outgoing source's end it needs tail frames, before
        // the incoming clip's start head frames.
        val lo = maxOf(0, half - tailFrames)
        val hi = minOf(leadCap, half + headFrames, wanted)
        if (lo <= hi) {
            val lead = wantedLead.coerceIn(lo, hi)
            return Span(transition, outgoing, wantedLead, wantedTail, lead, wanted - lead, headFrames, tailFrames, midpoint = half - lead)
        }
        // The clip before it is too short: as long a dip as it can fade out in, its middle where that clip's picture ends.
        val shortHalf = minOf(half, hi + tailFrames)
        return Span(transition, outgoing, wantedLead, wantedTail, hi, 2 * shortHalf - hi, headFrames, tailFrames, midpoint = shortHalf - hi)
    }

    /** [incoming]'s opening transition as it plays: shortened to the sources, null for a cut. */
    fun effective(doc: ProjectDocument, incoming: Clip, fps: Int): Transition? = span(doc, incoming, fps)?.effective

    /** Whole frames in [ms] of source at [speed] (a rounded position one short of a frame still counts it). */
    private fun framesOf(ms: Long, fps: Int, speed: Float): Int = ((ms + SOURCE_EDGE_MS) * fps / (1000.0 * speed)).toInt().coerceAtLeast(0)
}
