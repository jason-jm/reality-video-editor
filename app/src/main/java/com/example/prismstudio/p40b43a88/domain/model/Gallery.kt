package com.example.prismstudio.p40b43a88.domain.model

/**
 * A Gallery still (Resolve's Grab Still): a frame as the grade showed it, kept with that grade — to wipe
 * against other shots while matching them, and to grade other clips the same way. [grade] is the clip's
 * grade frozen at the grabbed frame (keyframes resolved); [image] names the stored picture, [width] ×
 * [height] in the timeline's aspect.
 */
data class GalleryStill(
    val id: String,
    /** 1, 2, … in the order they were grabbed (never reused). */
    val number: Int,
    val clipName: String,
    /** The timeline timecode it was grabbed at. */
    val timecode: String,
    val grade: ColorGrade,
    val image: String,
    val width: Int,
    val height: Int,
) {
    val label: String get() = "静帧 $number"
}
