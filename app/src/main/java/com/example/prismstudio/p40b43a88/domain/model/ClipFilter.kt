package com.example.prismstudio.p40b43a88.domain.model

/**
 * Resolve's Color page clip filter: which picture clips the clip strip shows — all of them, the graded or the
 * ungraded ones, those carrying one clip colour, or those whose media is flagged. It narrows the strip only (the
 * session's, not the project's).
 */
sealed interface ClipFilter {
    val label: String

    /** Whether [clip] passes; [flags] are its media's (Resolve's flags belong to the media). */
    fun matches(clip: Clip, flags: Collection<FlagColor> = emptyList()): Boolean

    data object All : ClipFilter {
        override val label = "全部片段"
        override fun matches(clip: Clip, flags: Collection<FlagColor>) = true
    }

    /** A grade changes the clip's picture (see [ColorGrade.isNeutral]). */
    data object Graded : ClipFilter {
        override val label = "已调色片段"
        override fun matches(clip: Clip, flags: Collection<FlagColor>) = !clip.grade.isNeutral
    }

    data object Ungraded : ClipFilter {
        override val label = "未调色片段"
        override fun matches(clip: Clip, flags: Collection<FlagColor>) = clip.grade.isNeutral
    }

    data class Colored(val color: ClipColor) : ClipFilter {
        override val label get() = "${color.label}色片段"
        override fun matches(clip: Clip, flags: Collection<FlagColor>) = clip.color == color
    }

    /** Resolve's Flagged Clips: media carrying [color], or any flag when null. */
    data class Flagged(val color: FlagColor?) : ClipFilter {
        override val label get() = if (color == null) "已标旗标片段" else "${color.label}色旗标片段"
        override fun matches(clip: Clip, flags: Collection<FlagColor>) = if (color == null) flags.isNotEmpty() else color in flags
    }
}
