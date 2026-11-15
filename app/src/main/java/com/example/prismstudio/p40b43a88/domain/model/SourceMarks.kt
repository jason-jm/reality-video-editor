package com.example.prismstudio.p40b43a88.domain.model

/**
 * Resolve's source marks: the part of a media the source viewer marked, [inMs] to [outMs] into it, which the edit buttons
 * put on the timeline. Without marks a media edits whole.
 */
data class SourceMarks(val inMs: Long, val outMs: Long) {
    init {
        require(outMs > inMs) { "a source range ends after it starts" }
    }

    val durationMs: Long get() = outMs - inMs

    companion object {
        /** The marks a media whole [durationMs] long has with a new In at [atMs] (Out kept when after it, else the end). */
        fun withIn(current: SourceMarks?, atMs: Long, durationMs: Long): SourceMarks? {
            val out = current?.outMs?.takeIf { it > atMs } ?: durationMs
            return if (out > atMs) SourceMarks(atMs.coerceAtLeast(0), out) else null
        }

        /** The marks with a new Out at [atMs] (In kept when before it, else the start). */
        fun withOut(current: SourceMarks?, atMs: Long): SourceMarks? {
            val start = current?.inMs?.takeIf { it < atMs } ?: 0L
            return if (atMs > start) SourceMarks(start, atMs) else null
        }
    }
}
