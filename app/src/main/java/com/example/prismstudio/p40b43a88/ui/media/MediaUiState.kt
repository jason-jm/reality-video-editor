package com.example.prismstudio.p40b43a88.ui.media

import com.example.prismstudio.p40b43a88.BuildConfig.PRODUCT_NAME
import com.example.prismstudio.p40b43a88.ui.edit.RenameUi
import android.graphics.Bitmap
import com.example.prismstudio.p40b43a88.domain.model.EditTargets
import com.example.prismstudio.p40b43a88.domain.model.FlagColor
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaBin
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.TimelineMarks
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.domain.model.FlagRule
import com.example.prismstudio.p40b43a88.domain.model.UsageRule
import com.example.prismstudio.p40b43a88.domain.usecase.SmartBinsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MediaMetadataUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MediaSort
import com.example.prismstudio.p40b43a88.domain.usecase.SortMediaUseCase
import com.example.prismstudio.p40b43a88.ui.components.SurfaceChange

data class MediaTileUi(
    val id: String,
    val name: String,
    val kind: MediaKind,
    val duration: String,
    val offline: Boolean,
    val poster: PosterColors,
    val frame: Bitmap?,
    val waveform: Waveform?,
    /** Resolve's flags on the media (`flags` part on the tile). */
    val flags: List<FlagColor> = emptyList(),
    /** The list view's columns (`media-list`): 分辨率, 场记 "12 / 3A / 5" (— without), 使用 (0: unused), Good Take. */
    val resolution: String = "",
    val slate: String = "—",
    val uses: Int = 0,
    val goodTake: Boolean = false,
    /** `hdrBadge` part: the media is HDR (HLG or PQ, by its tags or its input colour space). */
    val hdr: Boolean = false,
)

/** `timeline-pool-tile`: a timeline in the media pool's 时间线 bin (see [com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase]). */
data class TimelineTileUi(
    val id: String,
    val name: String,
    val detail: String,
    val open: Boolean,
    val rows: Int,
    val bars: List<com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase.Bar>,
    /** A compound clip's timeline: badged 复合片段. */
    val compound: Boolean = false,
)

data class DeviceTileUi(
    val id: String,
    val name: String,
    val kind: MediaKind,
    val badge: String,
    val selected: Boolean,
    val imported: Boolean,
    val frame: Bitmap?,
)

data class ImportUi(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val permissionRequired: Boolean = false,
    val error: String? = null,
    val items: List<DeviceTileUi> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val summary: String = "未选择素材",
    val importing: Boolean = false,
    /** Kinds not listed because their permission was refused (others were granted): `import-missing-row`. */
    val missing: Set<MediaKind> = emptySet(),
    val limitedVisualAccess: Boolean = false,
) {
    /** `import-missing-note`: which kinds are not listed, and why. */
    val missingNote: String?
        get() = if (missing.isEmpty() || hasError || loading) null else {
            val names = listOfNotNull(
                "视频".takeIf { MediaKind.VIDEO in missing },
                "音乐和音频".takeIf { MediaKind.AUDIO in missing },
                "照片".takeIf { MediaKind.IMAGE in missing },
            ).joinToString("、")
            "没有获得访问${names}的权限，这里不会列出它们"
        }

    val hasError: Boolean get() = permissionRequired || error != null
    val errorMessage: String
        get() = error ?: "${PRODUCT_NAME} 需要“照片和视频”权限才能列出设备中的素材。"
    val nothingSelected: Boolean get() = selectedIds.isEmpty() || loading || hasError || importing
}

/** `user-bin-item`: a bin the user made (Resolve's bins), with how many media are filed in it. */
data class UserBinUi(val id: String, val name: String, val count: Int)

/** `smart-bin-item`: a smart bin (Resolve's Smart Bins) — its name, how many media it finds now, its rules in words. */
data class SmartBinUi(val id: String, val name: String, val count: Int, val rules: String)

/** `smart-bin-sheet`: a smart bin being made ([editingId] null) or changed — its name and rules, and what they find. */
data class SmartBinSheetUi(
    val editingId: String? = null,
    val rules: SmartBinsUseCase.Rules = SmartBinsUseCase.Rules(""),
    val matches: Int = 0,
    val problem: String? = null,
) {
    val title: String get() = if (editingId == null) "新建智能媒体夹" else "编辑智能媒体夹"
    val confirmLabel: String get() = if (editingId == null) "创建" else "保存"
    val canConfirm: Boolean get() = problem == null
    /** `smart-bin-matches`: "符合条件：3 个素材", then why it cannot be saved yet. */
    val status: String get() = "符合条件：$matches 个素材" + (problem?.let { " · $it" } ?: "")
    /** The 类型 segment: 不限, 视频, 音频, 图片. */
    val kindIndex: Int get() = KINDS.indexOf(rules.kind)
    val usageIndex: Int get() = rules.usage.ordinal

    companion object {
        val KINDS = listOf(null, MediaKind.VIDEO, MediaKind.AUDIO, MediaKind.IMAGE)
        val KIND_LABELS = listOf("不限", "视频", "音频", "图片")
        val FLAGS = FlagRule.entries
        val USAGES = UsageRule.entries
    }
}

/** `metadata-sheet`: the selected media's slate, keywords, note and Good Take as typed, and why they cannot be saved. */
data class MetadataSheetUi(
    val assetName: String,
    val draft: MediaMetadataUseCase.Draft,
    val problem: String? = null,
    val changed: Boolean = false,
) {
    val canConfirm: Boolean get() = problem == null && changed
    val hint: String get() = problem ?: "关键词用逗号或顿号隔开；媒体池搜索和智能媒体夹都会用到这些信息"
}

/** `relink-choice`: a device file an offline media may be relinked to, and whether it is the one taken. */
data class RelinkChoiceUi(val uri: String, val label: String, val chosen: Boolean)

/**
 * `relink-row`: an offline media in the relink sheet — its name, where it was ("原位置：Movies/Trip/ · 视频 0:10"), the file it
 * will be relinked to or why none ([status]; [warning]: shorter than its clips need, or only the name agrees), and the
 * other files it could be.
 */
data class RelinkRowUi(
    val id: String,
    val name: String,
    /** What the system picker offers for it: videos, sounds or pictures. */
    val kind: MediaKind,
    val was: String,
    val status: String,
    val found: Boolean,
    val warning: Boolean,
    val choices: List<RelinkChoiceUi>,
)

/** `relink-sheet`: Resolve's Relink Media — the offline media, the device searched for their files, a file picked by hand. */
data class RelinkSheetUi(
    val searching: Boolean = true,
    val permissionRequired: Boolean = false,
    val error: String? = null,
    val rows: List<RelinkRowUi> = emptyList(),
    val relinking: Boolean = false,
    val limitedVisualAccess: Boolean = false,
) {
    val foundCount: Int get() = rows.count { it.found }
    /** `relink-summary`: what the search found, or why it could not look. */
    val summary: String
        get() = when {
            permissionRequired -> "需要“照片和视频”和“音乐和音频”权限才能在设备中查找文件；也可以逐个手动选择文件。"
            error != null -> "$error 可以逐个手动选择文件。"
            searching -> "正在设备中按文件名查找…"
            else -> "找到 $foundCount / ${rows.size} 个素材的文件。素材在时间线上的片段、旗标、媒体夹和场记都会保留。"
        }
    val canConfirm: Boolean get() = foundCount > 0 && !relinking && !searching
    val confirmLabel: String get() = if (foundCount > 0) "重新链接 $foundCount 个" else "重新链接"
}

/** One choice of a destination control: a track ([id] null: automatic sound) and its label. */
data class TargetChoiceUi(val id: String?, val label: String)

data class MediaUiState(
    val binIndex: Int = 0,
    /** The user's bins (Resolve's New Bin), and the one showing — it takes the place of [binIndex] while set. */
    val userBins: List<UserBinUi> = emptyList(),
    val userBinId: String? = null,
    /** `source-bin-row`: the bin the selected media is filed in (null: the pool's root). */
    val selectedBinId: String? = null,
    /** `rename-sheet` for a bin, and which. */
    val binRename: RenameUi? = null,
    val renamingBinId: String? = null,
    /** Smart bins, the one showing (it takes the place of [binIndex] and [userBinId] while set), and the sheet. */
    val smartBins: List<SmartBinUi> = emptyList(),
    val smartBinId: String? = null,
    val smartSheet: SmartBinSheetUi? = null,
    /** `source-slate-chip`: the selected media's slate in short ("场 3 · 镜 2A · 条 4 · 好条", 未填写), and its sheet. */
    val slateLabel: String = "未填写",
    /**
     * `source-proxy-row`: the selected media's proxy (Resolve's proxy media) — 无代理 / 生成中 45% / 代理 960×540 · 12 MB —
     * whether it can have one, has one, and how many of the pool's videos still lack one.
     */
    val proxyLabel: String = "无代理",
    val canProxy: Boolean = false,
    val hasProxy: Boolean = false,
    val proxyMaking: Boolean = false,
    val proxiesMissing: Int = 0,
    /**
     * `source-sync-row`: Resolve's Auto Sync Audio for the selected video — what its sound is synced to ([syncLabel]), the
     * pool's sound files it can take ([syncChoices]), why it cannot ([syncUnavailable]), and a match being worked out.
     */
    val syncLabel: String = "",
    val syncChoices: List<SyncChoiceUi> = emptyList(),
    val syncUnavailable: String? = null,
    val synced: Boolean = false,
    val syncing: Boolean = false,
    /** `pool-view-segment` and `pool-sort-chip`: the list view instead of thumbnails, and the pool's order. */
    val listView: Boolean = false,
    val order: SortMediaUseCase.Order = SortMediaUseCase.Order(),
    val metadataSheet: MetadataSheetUi? = null,
    val query: String = "",
    val items: List<MediaTileUi> = emptyList(),
    /** The 时间线 bin's cards. */
    val timelines: List<TimelineTileUi> = emptyList(),
    val poolCount: Int = 0,
    val summary: String = "",
    val selectedId: String? = null,
    val selected: MediaAsset? = null,
    val metadata: List<Pair<String, String>> = emptyList(),
    /** `source-color-row`: the selected video's input colour space (Resolve's Input Color Space) and what it does. */
    val inputColor: com.example.prismstudio.p40b43a88.domain.model.InputColorSpace? = null,
    val colorNote: String = "",
    val fps: Int = 30,
    val sourceDurationFrames: Int = 0,
    val sourcePositionFrames: Int = 0,
    val inFrame: Int = 0,
    val outFrame: Int = 0,
    val playing: Boolean = false,
    /** The timeline's playhead: where Insert, Overwrite and Place on Top put the marked range. */
    val timelineFrame: Int = 0,
    val sourceStill: Bitmap? = null,
    /** `edit-target-row`: the destination controls — video tracks V1 up, then 自动 and the audio tracks. */
    val videoTargets: List<TargetChoiceUi> = emptyList(),
    val audioTargets: List<TargetChoiceUi> = emptyList(),
    val targets: EditTargets = EditTargets(),
    /** The timeline's In / Out: where insert, overwrite and place on top land instead of the playhead. */
    val timelineMarks: TimelineMarks = TimelineMarks(),
    val import: ImportUi = ImportUi(),
    /** `offline-banner`: how many of the pool's media are offline (their files moved or deleted). */
    val offlineCount: Int = 0,
    val relink: RelinkSheetUi? = null,
    val toast: String? = null,
    /** Set when a timeline was opened from the pool: the workspace should bring up the Edit page. */
    val openEditRequest: Boolean = false,
) {
    val bins: List<MediaBin> get() = MediaBin.entries
    val isEmpty: Boolean get() = poolCount == 0
    /** The 时间线 bin is showing (its cards instead of media). */
    val showsTimelines: Boolean get() = userBinId == null && smartBinId == null && MediaBin.entries[binIndex] == MediaBin.TIMELINES
    val nothingSelected: Boolean get() = selected == null || selected.offline
    /** `media-empty-filter`: why the pool shows nothing — a smart bin's rules, an empty user bin, or the search / bin. */
    val emptyTitle: String
        get() = when {
            smartBinId != null -> "没有符合条件的素材"
            userBinId != null && query.isBlank() -> "媒体夹是空的"
            else -> "没有匹配的素材"
        }
    val emptyBody: String
        get() = when {
            smartBinId != null -> "旗标、使用情况变了或导入新素材后，这里会自动更新。"
            userBinId != null && query.isBlank() -> "在右侧“媒体夹”中把选中的素材放进来。"
            else -> "换个关键词或分类试试。"
        }
    val inLabel: String get() = "入点 ${Timecode.format(inFrame, fps)}"
    val outLabel: String get() = "出点 ${Timecode.format(outFrame, fps)}"
    val rangeLabel: String get() = "选段 ${Timecode.format((outFrame - inFrame).coerceAtLeast(0), fps)}"
    val sourceTimecode: String get() = Timecode.format(sourcePositionFrames, fps)
    /**
     * `source-edit-point`: where the playhead-based edits land — the timeline's In / Out when it has both (a
     * ripple overwrite then replaces that range), else the playhead (a ripple overwrite replaces the clip there).
     */
    val editPointLabel: String
        get() {
            val tc = { frame: Int -> Timecode.format(frame, fps) }
            val inFrame = timelineMarks.inFrame
            val outFrame = timelineMarks.outFrame
            return when {
                inFrame != null && outFrame != null ->
                    "插入 / 覆盖 / 波纹覆盖 / 适配填充 / 叠加到时间线入点 ${tc(inFrame)} → 出点 ${tc(outFrame)}（${tc(outFrame - inFrame)}），替换在播放头"
                inFrame != null -> "插入 / 覆盖 / 叠加到时间线入点 ${tc(inFrame)}，替换在播放头"
                outFrame != null -> "插入 / 覆盖 / 叠加到时间线出点 ${tc(outFrame)} 之前，替换在播放头"
                else -> "插入 / 覆盖 / 波纹覆盖 / 替换 / 叠加到时间线播放头 ${tc(timelineFrame)}"
            }
        }
    /** Fit to Fill needs both timeline marks. */
    val canFitToFill: Boolean get() = timelineMarks.inFrame != null && timelineMarks.outFrame != null
    val pictureAspect: Float
        get() = selected?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
}

sealed interface MediaEvent {
    data class SelectBin(val index: Int) : MediaEvent
    /** User bins: make one, show one, file the selected media in one (null: the root), rename (the sheet) and delete. */
    data object NewBin : MediaEvent
    data class SelectUserBin(val id: String) : MediaEvent
    data class MoveToBin(val binId: String?) : MediaEvent
    /** `source-color-segment`: the selected media's input colour space. */
    data class InputColor(val space: com.example.prismstudio.p40b43a88.domain.model.InputColorSpace) : MediaEvent
    data class OpenRenameBin(val id: String) : MediaEvent
    data class RenameBinText(val text: String) : MediaEvent
    data object ConfirmRenameBin : MediaEvent
    data object CloseRenameBin : MediaEvent
    data class DeleteBin(val id: String) : MediaEvent
    /** Smart bins (Resolve's Smart Bins): the sheet for a new one or [id]'s rules, its edits, show one, delete one. */
    data object NewSmartBin : MediaEvent
    data class EditSmartBin(val id: String) : MediaEvent
    data class SmartRules(val rules: SmartBinsUseCase.Rules) : MediaEvent
    data object ConfirmSmartBin : MediaEvent
    data object CloseSmartBin : MediaEvent
    data class SelectSmartBin(val id: String) : MediaEvent
    data class DeleteSmartBin(val id: String) : MediaEvent
    /** Clip metadata (Resolve's Metadata panel): the sheet for the selected media, its edits, save, close. */
    /** Resolve's media pool views and sort: 缩略图 or 列表, and the column it is ordered by (the same column again flips it). */
    data class SetListView(val list: Boolean) : MediaEvent
    data class SortBy(val sort: MediaSort) : MediaEvent
    data class SortAscending(val ascending: Boolean) : MediaEvent
    /** Resolve's Generate Proxy Media for the selected media, for every video lacking one, and deleting the selected one's. */
    data object GenerateProxy : MediaEvent
    data object GenerateAllProxies : MediaEvent

    /** `source-sync-option`: take the selected video's sound from [audioId], lined up by waveform. */
    data class SyncAudio(val audioId: String) : MediaEvent

    /** `source-sync-clear`: the selected video's own sound again. */
    data object ClearAudioSync : MediaEvent
    data object DeleteProxy : MediaEvent
    data object OpenMetadata : MediaEvent
    data class MetadataDraft(val draft: MediaMetadataUseCase.Draft) : MediaEvent
    data object ConfirmMetadata : MediaEvent
    data object CloseMetadata : MediaEvent
    data class Query(val text: String) : MediaEvent
    data class Select(val id: String) : MediaEvent

    /** `timeline-pool-tile`: open that timeline (it becomes the Edit page's) and go to the Edit page. */
    data class OpenTimeline(val id: String) : MediaEvent

    /** The Edit page was brought up for [MediaUiState.openEditRequest]. */
    data object EditOpened : MediaEvent
    data class Scrub(val frame: Int) : MediaEvent
    data object MarkIn : MediaEvent
    data object MarkOut : MediaEvent
    /** Alt+X: the selected item's marks back to its whole length. */
    data object ClearMarks : MediaEvent

    /** Resolve's flags: [color] on or off the selected media (`source-flag-row`). */
    data class ToggleFlag(val color: FlagColor) : MediaEvent

    /** Every flag off the selected media. */
    data object ClearFlags : MediaEvent
    data object Append : MediaEvent
    /** Resolve's edit from the source viewer: the marked range onto the timeline ([SourceEdit]). */
    data class Edit(val mode: SourceEdit) : MediaEvent
    /** The destination controls: pictures to video track [trackId]; sound to audio track [trackId] (null: automatic). */
    data class SetVideoTarget(val trackId: String) : MediaEvent
    data class SetAudioTarget(val trackId: String?) : MediaEvent
    data class SetPlaying(val playing: Boolean) : MediaEvent
    data object JumpToIn : MediaEvent
    data object JumpToOut : MediaEvent
    data class Jog(val deltaFrames: Int) : MediaEvent
    data class SourceSurface(val change: SurfaceChange) : MediaEvent
    data class RequestThumbnail(val id: String) : MediaEvent
    data object OpenImport : MediaEvent
    data object CancelImport : MediaEvent
    data class ToggleDevice(val id: String) : MediaEvent
    data class RequestDeviceThumbnail(val id: String) : MediaEvent
    data object ConfirmImport : MediaEvent
    data class PermissionResult(val granted: Boolean) : MediaEvent
    /** `relink-button`: the relink sheet, the device searched for the offline media's files. */
    data object OpenRelink : MediaEvent
    data object CloseRelink : MediaEvent
    /** `relink-choice`: [uri], one of the device files found, for [assetId]. */
    data class RelinkChoose(val assetId: String, val uri: String) : MediaEvent
    /** `relink-pick-button`: a file picked in the system's document picker for [assetId]. */
    data class RelinkPicked(val assetId: String, val uri: String) : MediaEvent
    data object ConfirmRelink : MediaEvent
    data object ToastShown : MediaEvent
    /** A message the screen has to say (the system settings could not be opened). */
    data class ShowMessage(val message: String) : MediaEvent
}

/** A sound file the selected video can take its sound from (`source-sync-option`), and whether it is the one in use. */
data class SyncChoiceUi(val id: String, val name: String, val selected: Boolean)
