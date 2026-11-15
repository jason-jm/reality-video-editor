package com.example.prismstudio.p40b43a88.domain.model

enum class MediaKind { VIDEO, AUDIO, IMAGE }

/** A source file referenced by a project. Files are never copied; [uri] points at device storage. */
data class MediaAsset(
    val id: String,
    val uri: String,
    val name: String,
    val kind: MediaKind,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val videoCodec: String,
    val audioDescription: String,
    val hasAudio: Boolean,
    val sizeBytes: Long,
    val relativePath: String,
    val poster: PosterColors = PosterColors.NEUTRAL,
    val offline: Boolean = false,
    /** Resolve's flags on the media, in palette order: every clip made from it shows them. */
    val flags: List<FlagColor> = emptyList(),
    /** The user bin it is filed in (Resolve's bins in the media pool); null: the pool's root. */
    val binId: String? = null,
    /** Resolve's clip metadata (Shot & Scene): what the editor wrote about the media. */
    val metadata: ClipMetadata = ClipMetadata.NONE,
    /** Resolve's Auto Sync Audio: this video's sound comes from another file (a separate recorder's) when edited in. */
    val syncedAudio: SyncedAudio? = null,
    /** What the file says about its colour (primaries, transfer, bit depth), read at import. */
    val color: SourceColor = SourceColor.UNKNOWN,
    /** Resolve's Input Color Space: the file's own tags, or set by hand. */
    val inputColor: InputColorSpace = InputColorSpace.AUTO,
) {
    val resolutionLabel: String get() = if (width > 0 && height > 0) "$width×$height" else "—"
    val frameRateLabel: String
        get() = if (frameRate > 0f) "${if (frameRate % 1f == 0f) frameRate.toInt() else frameRate} fps" else "—"
    val durationLabel: String get() = Timecode.shortDuration(durationMs)
    val sizeLabel: String get() = Bytes.label(sizeBytes)
    val fileLabel: String get() = if (offline) OFFLINE_FILE_LABEL else "$relativePath$name · $sizeLabel"

    companion object {
        const val OFFLINE_FILE_LABEL = "原文件已被移动或删除"
        const val IMAGE_DEFAULT_DURATION_MS = 5_000L
    }
}

/**
 * Resolve's clip metadata, the Shot & Scene group: the slate (scene, shot, take), keywords, a note and Good Take. The search
 * and smart bins read it; every clip made from the media shares it.
 */
data class ClipMetadata(
    val scene: String = "",
    val shot: String = "",
    val take: String = "",
    val keywords: List<String> = emptyList(),
    val notes: String = "",
    val goodTake: Boolean = false,
) {
    val isEmpty: Boolean get() = this == NONE
    val hasSlate: Boolean get() = scene.isNotEmpty() || shot.isNotEmpty() || take.isNotEmpty()

    /** Whether [query] (already trimmed, not empty) is part of the slate, a keyword or the note, case ignored. */
    fun mentions(query: String): Boolean =
        listOf(scene, shot, take, notes).any { it.contains(query, ignoreCase = true) } || keywords.any { it.contains(query, ignoreCase = true) }

    companion object {
        val NONE = ClipMetadata()
    }
}

/** An item found on the device when browsing for import. */
data class DeviceMediaItem(
    val id: String,
    val uri: String,
    val name: String,
    val kind: MediaKind,
    val durationMs: Long,
    val sizeBytes: Long,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val poster: PosterColors = PosterColors.NEUTRAL,
) {
    val badge: String get() = if (kind == MediaKind.IMAGE) "图片" else Timecode.shortDuration(durationMs)
}

/** Media pool bins shown in the Media stage side navigation. */
enum class MediaBin(val label: String) {
    ALL("全部素材"),
    VIDEO("视频"),
    AUDIO("音频"),
    IMAGE("图片"),

    /** The project's timelines (Resolve keeps them in the media pool too), no media. */
    TIMELINES("时间线");

    fun accepts(kind: MediaKind): Boolean = when (this) {
        ALL -> true
        VIDEO -> kind == MediaKind.VIDEO
        AUDIO -> kind == MediaKind.AUDIO
        IMAGE -> kind == MediaKind.IMAGE
        TIMELINES -> false
    }
}

/** A bin the user made in the media pool (Resolve's New Bin): its media are those whose [MediaAsset.binId] is [id]. */
data class UserBin(val id: String, val name: String)

object Bytes {
    fun label(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "${bytes / (1L shl 20)} MB"
        else -> "${maxOf(1, bytes / 1024)} KB"
    }
}

/**
 * Resolve's Smart Bins: a saved search over the media pool. It shows every piece of media that all its rules hold for, and
 * stays up to date as media are imported, flagged or used. Nothing is filed in it: one piece of media can show in any
 * number of smart bins, and still sits in its own bin.
 */
data class SmartBin(
    val id: String,
    val name: String,
    /** null: any kind. */
    val kind: MediaKind? = null,
    val flag: FlagRule = FlagRule.ANY,
    val usage: UsageRule = UsageRule.ANY,
    /** Part of the name, case ignored; blank: any name. */
    val nameContains: String = "",
    /** Part of one of the media's keywords (its clip metadata), case ignored; blank: any. */
    val keyword: String = "",
    /** Only media marked Good Take. */
    val goodTake: Boolean = false,
)

/** A smart bin's flag rule: any media, flagged with any colour, not flagged, or flagged with one colour. */
enum class FlagRule(val label: String, val color: FlagColor? = null) {
    ANY("不限"),
    FLAGGED("任意旗标"),
    UNFLAGGED("无旗标"),
    BLUE("蓝色旗标", FlagColor.BLUE),
    CYAN("青色旗标", FlagColor.CYAN),
    GREEN("绿色旗标", FlagColor.GREEN),
    YELLOW("黄色旗标", FlagColor.YELLOW),
    RED("红色旗标", FlagColor.RED),
    PINK("粉色旗标", FlagColor.PINK),
    PURPLE("紫色旗标", FlagColor.PURPLE);

    fun accepts(flags: List<FlagColor>): Boolean = when (this) {
        ANY -> true
        FLAGGED -> flags.isNotEmpty()
        UNFLAGGED -> flags.isEmpty()
        else -> color in flags
    }
}

/** A smart bin's usage rule (Resolve's Usage column): used by a clip on any of the project's timelines, or by none. */
enum class UsageRule(val label: String) {
    ANY("不限"),
    USED("已使用"),
    UNUSED("未使用"),
}

/**
 * A video file's sound taken from another file ([assetId]), lined up by waveform: that file's time is the video's plus
 * [offsetMs] (positive when the recorder started first).
 */
data class SyncedAudio(val assetId: String, val offsetMs: Long)
