package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.panKeyframesFor
import com.example.prismstudio.p40b43a88.domain.model.effectsFor
import com.example.prismstudio.p40b43a88.domain.model.Clip
import com.example.prismstudio.p40b43a88.domain.model.ClipFades
import com.example.prismstudio.p40b43a88.domain.model.ClipKind
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.EditResult
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.keyframesFor
import com.example.prismstudio.p40b43a88.domain.model.volumeKeyframesFor
import com.example.prismstudio.p40b43a88.domain.model.keysFor
import com.example.prismstudio.p40b43a88.domain.model.maskFor

/**
 * What Paste Attributes can carry from the copied clip; [picture] ones come from its picture, the rest from its sound —
 * the fades from both, each onto its own kind.
 */
enum class ClipAttribute(val label: String, val detail: String, val picture: Boolean) {
    TRANSFORM("画面变换", "缩放方式、缩放、位置、旋转、不透明度、合成模式、裁切、动态缩放，含关键帧", true),
    EFFECTS("特效", "模糊、锐化、暗角等片段特效，以及抠像", true),
    GRADE("调色", "一级校色、曲线、LUT、窗口与限定器", true),
    VOLUME("音量与声像", "片段音量和声像，含关键帧", false),
    EQ("均衡器", "片段自己的均衡器", false),
    PITCH("音调", "片段的变调（半音），长度不变", false),
    FADES("淡入淡出", "画面和声音的淡入、淡出时长", false),

    /**
     * Resolve's Speed Change: the copied clip's speed, reverse and retime process onto the selected clip and its linked
     * partner. Its media range stays, so its length changes and the rest of its tracks moves with it (as the speed control
     * does); off by default. A clip with speed points has no one speed to give.
     */
    SPEED("变速", "播放速度、倒放和变速处理；素材范围不变，时长随之变化，后面的片段跟着移动", false),
}

/**
 * Resolve's Paste Attributes (Alt+V): chosen attributes of the copied clip go onto the selected one —
 * its picture's onto the selected picture, its sound's onto the selected sound (each clip with its
 * linked partner). Keyframes keep their timing from the clip's start; those past the end of a shorter
 * clip are cut where it ends, so every frame keeps its value. One undo step.
 */
class PasteAttributesUseCase(private val props: ClipPropertiesUseCase = ClipPropertiesUseCase()) {

    /** The copied picture and sound (either may be missing) and the picture and sound they would go onto. */
    data class Pairing(
        val sourcePicture: Clip?,
        val sourceSound: Clip?,
        val targetPicture: Clip?,
        val targetSound: Clip?,
        /** The media ids that are stills: a still has no speed to give or take. */
        val stills: Set<String> = emptySet(),
    )

    fun pairing(timeline: Timeline, content: ClipboardUseCase.Content, targetId: String, stills: Set<String> = emptySet()): Pairing {
        val target = timeline.clip(targetId) ?: throw EditRejected(NO_TARGET)
        val group = timeline.linkedGroup(target)
        return Pairing(
            sourcePicture = content.clips.firstOrNull { it.kind != ClipKind.AUDIO },
            sourceSound = content.clips.firstOrNull { it.kind == ClipKind.AUDIO },
            targetPicture = group.firstOrNull { it.kind != ClipKind.AUDIO },
            targetSound = group.firstOrNull { it.kind == ClipKind.AUDIO },
            stills = stills,
        )
    }

    /**
     * What [pairing] can paste: a transform onto any picture, effects and grade from video onto video, sound onto sound,
     * and a speed from a clip that plays at one onto a clip that is not a title.
     */
    fun available(pairing: Pairing): Set<ClipAttribute> = buildSet {
        val speedFrom = pairing.sourcePicture?.takeIf { it.kind == ClipKind.VIDEO } ?: pairing.sourceSound
        val speedOnto = pairing.targetPicture?.takeIf { it.kind == ClipKind.VIDEO } ?: pairing.targetSound
        if (speedFrom != null && speedOnto != null && !speedFrom.retimed && speedFrom.assetId !in pairing.stills && speedOnto.assetId !in pairing.stills) {
            add(ClipAttribute.SPEED)
        }
        val from = pairing.sourcePicture
        val onto = pairing.targetPicture
        if (from != null && onto != null) {
            add(ClipAttribute.TRANSFORM)
            if (from.kind == ClipKind.VIDEO && onto.kind == ClipKind.VIDEO) {
                add(ClipAttribute.EFFECTS)
                add(ClipAttribute.GRADE)
            }
            if (ClipFades.takes(from) && ClipFades.takes(onto)) add(ClipAttribute.FADES)
        }
        if (pairing.sourceSound != null && pairing.targetSound != null) {
            add(ClipAttribute.VOLUME)
            add(ClipAttribute.EQ)
            add(ClipAttribute.PITCH)
            add(ClipAttribute.FADES)
        }
    }

    operator fun invoke(
        timeline: Timeline,
        content: ClipboardUseCase.Content,
        targetId: String,
        attributes: Set<ClipAttribute>,
        fps: Int,
        stills: Set<String> = emptySet(),
    ): EditResult {
        val first = pairing(timeline, content, targetId, stills)
        val target = timeline.clip(targetId) ?: throw EditRejected(NO_TARGET)
        val chosen = ClipAttribute.entries.filter { it in attributes && it in available(first) }
        if (chosen.isEmpty()) throw EditRejected("没有可以粘贴到这个片段的属性")
        timeline.requireUnlocked(timeline.linkedGroup(target), target)
        // The speed goes first: it sets the clip's length, which the keyframes and fades pasted after it fit.
        var result = if (ClipAttribute.SPEED in chosen) speedLike(timeline, first, target) else timeline
        val pairing = if (result === timeline) first else pairing(result, content, targetId, stills)
        val picture = pairing.targetPicture
        val from = pairing.sourcePicture
        if (picture != null && from != null && chosen.any { it.picture || it == ClipAttribute.FADES }) {
            var c = picture
            if (ClipAttribute.TRANSFORM in chosen) {
                c = c.copy(transform = from.transform, keyframes = from.keyframesFor(0, c.durationFrames), dynamicZoom = from.dynamicZoom)
                // The composite mode belongs to pictures; titles draw over the finished picture.
                // So do the mask (it belongs to the picture, like the crop) and the composite mode.
                if (from.kind == ClipKind.VIDEO && c.kind == ClipKind.VIDEO) c = c.copy(composite = from.composite, scaling = from.scaling, mask = from.maskFor(0, c.durationFrames))
            }
            // Keyframed effects keep their animation over the target's length, as the transform does.
            // The keyer comes with them (Resolve's keyers are effects).
            if (ClipAttribute.EFFECTS in chosen) c = c.copy(effects = from.effectsFor(0, c.durationFrames), chromaKey = from.chromaKey)
            if (ClipAttribute.GRADE in chosen) c = c.copy(grade = from.grade.keysFor(0, c.durationFrames))
            if (ClipAttribute.FADES in chosen && ClipFades.takes(from) && ClipFades.takes(c)) c = fadedLike(c, from, fps)
            result = result.replaceClip(c)
        }
        val sound = pairing.targetSound
        val heard = pairing.sourceSound
        if (sound != null && heard != null && chosen.any { !it.picture }) {
            var c = sound
            if (ClipAttribute.VOLUME in chosen) {
                c = c.copy(
                    volumeDb = heard.volumeDb,
                    volumeKeyframes = heard.volumeKeyframesFor(0, c.durationFrames),
                    pan = heard.pan,
                    panKeyframes = heard.panKeyframesFor(0, c.durationFrames),
                )
            }
            if (ClipAttribute.EQ in chosen) c = c.copy(eq = heard.eq)
            if (ClipAttribute.PITCH in chosen) c = c.copy(pitchCents = heard.pitchCents)
            if (ClipAttribute.FADES in chosen) c = fadedLike(c, heard, fps)
            result = result.replaceClip(c)
        }
        val source = (from ?: heard)!!.name
        return EditResult(result, targetId, "已将“$source”的${chosen.joinToString("、") { it.label }}粘贴到“${target.name}”")
    }

    /** What can be pasted onto at least one of [targetIds]. */
    fun available(timeline: Timeline, content: ClipboardUseCase.Content, targetIds: List<String>, stills: Set<String> = emptySet()): Set<ClipAttribute> =
        timeline.selectedGroups(targetIds).flatMapTo(mutableSetOf()) { group -> available(pairing(timeline, content, group.first().id, stills)) }

    /**
     * The chosen attributes onto every selected clip that can take them (a linked picture and sound
     * once), in one undo step; locked clips and those that can take none of them are left as they are.
     */
    operator fun invoke(
        timeline: Timeline,
        content: ClipboardUseCase.Content,
        targetIds: List<String>,
        attributes: Set<ClipAttribute>,
        fps: Int,
        stills: Set<String> = emptySet(),
    ): EditResult {
        if (targetIds.size == 1) return invoke(timeline, content, targetIds.first(), attributes, fps, stills)
        var result = timeline
        var pasted = 0
        var skipped = 0
        for (group in timeline.selectedGroups(targetIds)) {
            try {
                result = invoke(result, content, group.first().id, attributes, fps, stills).timeline
                pasted++
            } catch (_: EditRejected) {
                skipped++
            }
        }
        if (pasted == 0) throw EditRejected("所选片段都不能接受这些属性")
        val source = (content.clips.firstOrNull { it.kind != ClipKind.AUDIO } ?: content.clips.first()).name
        val chosen = ClipAttribute.entries.filter { it in attributes }.joinToString("、") { it.label }
        val message = "已将“$source”的${chosen}粘贴到 $pasted 个片段" + if (skipped > 0) "（$skipped 个不适用或已锁定，未改动）" else ""
        return EditResult(result, targetIds.first(), message, targetIds)
    }

    /** [timeline] with the target's speed, reverse and retime process as the copied clip's (its picture's, else its sound's). */
    private fun speedLike(timeline: Timeline, pairing: Pairing, target: Clip): Timeline {
        val from = pairing.sourcePicture?.takeIf { it.kind == ClipKind.VIDEO } ?: pairing.sourceSound ?: return timeline
        val onto = pairing.targetPicture?.takeIf { it.kind == ClipKind.VIDEO } ?: pairing.targetSound ?: return timeline
        var result = props.speed(timeline, onto.id, from.speed)
        if (result.clip(onto.id)?.reverse != from.reverse) result = props.reverse(result, onto.id, from.reverse)
        val group = result.linkedGroup(result.clip(onto.id) ?: target).map { it.id }.toSet()
        return result.copy(clips = result.clips.map { if (it.id in group && it.kind == ClipKind.VIDEO) it.copy(retime = from.retime) else it })
    }

    /** [clip] with [source]'s fades and their curves, each within the longest [clip] takes. */
    private fun fadedLike(clip: Clip, source: Clip, fps: Int): Clip {
        val longest = ClipFades.max(clip, fps)
        return clip.copy(
            fadeInSeconds = source.fadeInSeconds.coerceAtMost(longest), fadeOutSeconds = source.fadeOutSeconds.coerceAtMost(longest),
            fadeInCurve = source.fadeInCurve, fadeOutCurve = source.fadeOutCurve,
        )
    }

    private companion object {
        const val NO_TARGET = "请先在时间线上选择要粘贴到的片段"
    }
}
