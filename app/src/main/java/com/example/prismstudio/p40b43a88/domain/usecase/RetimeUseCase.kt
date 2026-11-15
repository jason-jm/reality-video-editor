package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.ClipSpeed
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.SpeedSection
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.keyframesMoved
import com.example.prismstudio.p40b43a88.domain.model.normalizedSections
import kotlin.math.roundToInt

/**
 * `speed-points-block`: Resolve's Retime Controls — speed points split a clip into sections that play at their own speed
 * (a slow-motion stretch inside a shot). A section keeps its source when its speed changes, so its length changes, and
 * everything after the clip on its tracks moves by as much (as a whole clip's speed change does); the clip's keyframes
 * stay with the picture they animate. A clip's linked partners (its own sound) change with it, so picture and sound stay
 * together. Pictures and their sound only — not stills, titles or reversed clips. Frames count from the clip's start.
 * A point can be smoothed into a ramp (`speed-ramp-row`, Resolve's speed ramp): the speed eases from one section's into
 * the next over a stretch centred on the point, playing the same source, so nothing else moves.
 */
class RetimeUseCase {

    /** A speed point [frame] frames into the clip, splitting the section there in two of the same speed. */
    fun addPoint(doc: ProjectDocument, clipId: String, frame: Int): EditResult {
        val clip = retimable(doc, clipId)
        if (frame !in 1 until clip.durationFrames) throw EditRejected("把播放头移到片段内（不在首尾），再添加变速点")
        if (frame in clip.sectionBounds) throw EditRejected("这里已经有变速点")
        val updated = group(doc, clip).associate { member ->
            val sections = sectionsOf(member)
            val bounds = member.sectionBounds
            val i = sectionIndex(bounds, frame)
            val split = sections.flatMapIndexed { j, s ->
                if (j != i) listOf(s) else listOf(s.copy(frames = frame - bounds[i]), SpeedSection(bounds[i + 1] - frame, s.speed))
            }
            member.id to member.withSections(split)
        }
        val count = updated.getValue(clip.id).speedSections.size
        return EditResult(doc.timeline.copy(clips = doc.timeline.clips.map { updated[it.id] ?: it }), clip.id, "已添加变速点 · 片段分为 $count 段")
    }

    /**
     * The section [frame] frames into the clip played at [speed]: its source kept, so it gets longer or shorter — and the
     * clip with it, the clips after it on its tracks moving by as much.
     */
    fun setSectionSpeed(doc: ProjectDocument, clipId: String, frame: Int, speed: ClipSpeed): EditResult {
        val clip = retimable(doc, clipId)
        if (!clip.retimed) throw EditRejected("片段没有变速点")
        val i = sectionIndex(clip.sectionBounds, frame.coerceIn(0, clip.durationFrames - 1))
        if (clip.speedSections[i].speed == speed) throw EditRejected("这一段已经是 ${speed.label}")
        val timeline = retimed(doc, clip) { member ->
            val sections = sectionsOf(member)
            val old = sections[i]
            val frames = (old.frames * old.speed.factor / speed.factor).roundToInt().coerceAtLeast(1)
            sections.mapIndexed { j, s -> if (j == i) s.copy(frames = frames, speed = speed) else s }
        }
        val after = timeline.clip(clip.id)!!
        val fps = doc.format.fps
        return EditResult(timeline, clip.id, "第 ${i + 1} 段速度 ${speed.label}，片段时长 ${Timecode.format(after.durationFrames, fps)}")
    }

    /**
     * The speed point nearest [frame] frames into the clip removed: the sections either side become one, at the earlier
     * one's speed (its source kept, so the clip's length may change).
     */
    fun removePoint(doc: ProjectDocument, clipId: String, frame: Int): EditResult {
        val clip = retimable(doc, clipId)
        if (!clip.retimed) throw EditRejected("片段没有变速点")
        val k = nearestPoint(clip.sectionBounds, frame)
        val timeline = retimed(doc, clip) { member ->
            val sections = sectionsOf(member)
            val left = sections[k - 1]
            val right = sections[k]
            val frames = left.frames + (right.frames * right.speed.factor / left.speed.factor).roundToInt()
            sections.subList(0, k - 1) + left.copy(frames = frames.coerceAtLeast(1)) + sections.subList(k + 1, sections.size)
        }
        val left = timeline.clip(clip.id)!!.speedSections.size
        return EditResult(timeline, clip.id, if (left == 0) "已删除变速点，片段恢复匀速" else "已删除变速点 · 还剩 ${left - 1} 个")
    }

    /**
     * `speed-ramp-row`: the speed point nearest [frame] frames into the clip smoothed over [rampFrames] (Resolve's speed
     * ramp), centred on it — as far as there is room: halfway to the next change of speed either side, or to the clip's
     * end. 0 makes the change sudden again. A ramp plays the same source as the sudden change, so the clip keeps its
     * length and every frame outside the ramp stays.
     */
    fun setRamp(doc: ProjectDocument, clipId: String, frame: Int, rampFrames: Int): EditResult {
        val clip = retimable(doc, clipId)
        if (!clip.retimed) throw EditRejected("片段没有变速点")
        val k = nearestPoint(clip.sectionBounds, frame)
        if (!clip.changesSpeedAt(k)) throw EditRejected("第 $k 个变速点两侧速度相同，不需要平滑过渡")
        val ramp = rampFrames.coerceIn(0, clip.rampRoom(k))
        val updated = group(doc, clip).associate { member ->
            val sections = member.speedSections.takeIf { member.changesSpeedAt(k) } ?: return@associate member.id to member
            member.id to member.copy(speedSections = sections.mapIndexed { j, s -> if (j == k) s.copy(rampFrames = ramp) else s })
        }
        val fps = doc.format.fps
        val message = if (ramp == 0) "第 $k 个变速点改为立即变速" else "第 $k 个变速点在 ${secondsLabel(ramp, fps)} 秒内平滑过渡"
        return EditResult(doc.timeline.copy(clips = doc.timeline.clips.map { updated[it.id] ?: it }), clip.id, message)
    }

    /** Every speed point removed: the whole clip at 1×, its source kept. */
    fun clear(doc: ProjectDocument, clipId: String): EditResult {
        val clip = retimable(doc, clipId)
        if (!clip.retimed) throw EditRejected("片段没有变速点")
        val timeline = retimed(doc, clip) { member ->
            val frames = sectionsOf(member).sumOf { it.frames * it.speed.factor.toDouble() }.roundToInt().coerceAtLeast(1)
            listOf(SpeedSection(frames, ClipSpeed.NORMAL))
        }
        val fps = doc.format.fps
        return EditResult(timeline, clip.id, "已清除变速点，片段恢复 1×，时长 ${Timecode.format(timeline.clip(clip.id)!!.durationFrames, fps)}")
    }

    /**
     * [clip]'s group with each member's sections replaced by [change] of them: each section's frames move with it (its
     * keyframes too, scaled inside a changed section), the clip's length follows, and later clips on the group's tracks
     * move by the change.
     */
    private fun retimed(doc: ProjectDocument, clip: Clip, change: (Clip) -> List<SpeedSection>): Timeline {
        val members = group(doc, clip)
        val changed = members.associate { member ->
            val before = sectionsOf(member)
            val after = change(member)
            member.id to member.keyframesMoved(frameMap(before, after)).withSections(after)
        }
        val delta = changed.getValue(clip.id).durationFrames - clip.durationFrames
        val tracks = members.map { it.trackId }.toSet()
        val clips = doc.timeline.clips.map { other ->
            when {
                other.id in changed -> changed.getValue(other.id)
                other.trackId in tracks && other.start >= clip.end -> other.copy(start = (other.start + delta).coerceAtLeast(0))
                else -> other
            }
        }
        return doc.timeline.copy(clips = clips)
    }

    private fun retimable(doc: ProjectDocument, clipId: String): Clip {
        val clip = doc.timeline.clip(clipId) ?: throw EditRejected("请先在时间线上选择片段")
        if (clip.kind == ClipKind.TITLE) throw EditRejected("标题片段不能变速")
        if (clip.isCompound) throw EditRejected(COMPOUND_SPEED)
        if (doc.asset(clip.assetId)?.kind == MediaKind.IMAGE) throw EditRejected("图片没有可以变速的画面")
        if (clip.reverse) throw EditRejected("倒放的片段不能添加变速点：先关闭倒放")
        doc.timeline.requireUnlocked(doc.timeline.linkedGroup(clip), clip)
        return clip
    }

    /** The clip and its linked partners (they share its length, so they share its sections). */
    private fun group(doc: ProjectDocument, clip: Clip): List<Clip> =
        doc.timeline.linkedGroup(clip).filter { it.durationFrames == clip.durationFrames && !it.reverse }.ifEmpty { listOf(clip) }

    companion object {
        /** [frames] of ramp in seconds, as the inspector and messages say it: "0.5", "1", "0.13". */
        fun secondsLabel(frames: Int, fps: Int): String =
            String.format(java.util.Locale.ROOT, "%.2f", frames / fps.coerceAtLeast(1).toDouble()).trimEnd('0').trimEnd('.')

        /** The speed point (inner bound k, between sections k − 1 and k) nearest [frame]. */
        private fun nearestPoint(bounds: List<Int>, frame: Int): Int = (1 until bounds.lastIndex).minBy { kotlin.math.abs(bounds[it] - frame) }

        /** A clip's sections, one for the whole of a clip without speed points. */
        private fun sectionsOf(clip: Clip): List<SpeedSection> = clip.speedSections.ifEmpty { listOf(SpeedSection(clip.durationFrames, clip.speed)) }

        /** The section [frame] lies in, by the clip's section bounds. */
        private fun sectionIndex(bounds: List<Int>, frame: Int): Int = (bounds.indexOfLast { it <= frame }).coerceIn(0, bounds.size - 2)

        /** Where a frame of sections [before] lands in sections [after] (as many, or one fewer where two joined). */
        private fun frameMap(before: List<SpeedSection>, after: List<SpeedSection>): (Int) -> Int {
            val from = before.runningFold(0) { at, s -> at + s.frames }
            if (after.size != before.size) {
                // Two sections joined, or all became one: scale each source frame by where it now plays.
                val total = from.last().coerceAtLeast(1)
                val length = after.sumOf { it.frames }
                if (after.size == 1) return { f -> (f.toLong() * length / total).toInt() }
                val k = before.indices.first { it >= after.size || before[it] != after[it] }
                val joinedFrom = from[k]
                val joinedOld = before[k].frames + before[k + 1].frames
                val joinedNew = after[k].frames
                return { f ->
                    when {
                        f < joinedFrom -> f
                        f < joinedFrom + joinedOld -> joinedFrom + ((f - joinedFrom).toLong() * joinedNew / joinedOld).toInt()
                        else -> f - joinedOld + joinedNew
                    }
                }
            }
            val to = after.runningFold(0) { at, s -> at + s.frames }
            return { f ->
                val i = (from.indexOfLast { it <= f }).coerceIn(0, before.size - 1)
                if (f >= from.last()) f - from.last() + to.last()
                else to[i] + ((f - from[i]).toLong() * after[i].frames / before[i].frames).toInt()
            }
        }

        /**
         * [this] with [sections] (normalized), its length theirs. Each ramp is kept inside the clip: one whose section got
         * shorter takes no more than the room left before the clip's start or after its end (the room between points is
         * shared as the ramps play, see `Clip.rampAt`).
         */
        private fun Clip.withSections(sections: List<SpeedSection>): Clip {
            val (speed, kept) = normalizedSections(sections, speed)
            val clip = copy(speed = speed, speedSections = kept, durationFrames = sections.sumOf { it.frames }.coerceAtLeast(1))
            if (kept.none { it.rampFrames > 0 }) return clip
            val bounds = clip.sectionBounds
            return clip.copy(
                speedSections = kept.mapIndexed { k, s ->
                    if (s.rampFrames <= 0 || !clip.changesSpeedAt(k)) s
                    else s.copy(rampFrames = minOf(s.rampFrames, 2 * bounds[k], 2 * (clip.durationFrames - bounds[k])))
                },
            )
        }
    }
}
