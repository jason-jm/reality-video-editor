package com.example.prismstudio.p40b43a88.ui.media

import com.example.prismstudio.p40b43a88.domain.model.hdrByOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.example.prismstudio.p40b43a88.ui.components.timelineLookKey
import com.example.prismstudio.p40b43a88.ui.components.timelineLook
import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import com.example.prismstudio.p40b43a88.domain.model.ColorTransfer
import com.example.prismstudio.p40b43a88.domain.model.InputColorSpace
import com.example.prismstudio.p40b43a88.domain.model.effectiveTransfer
import com.example.prismstudio.p40b43a88.ui.edit.RenameUi
import com.example.prismstudio.p40b43a88.domain.usecase.MediaBinsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SmartBinsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.MediaMetadataUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SortMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ProxyStatus
import com.example.prismstudio.p40b43a88.data.repository.ProxyRepository
import com.example.prismstudio.p40b43a88.domain.usecase.TimelinesUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.TimelinePoolUseCase
import com.example.prismstudio.p40b43a88.domain.model.SourceMarks
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prismstudio.p40b43a88.data.repository.DeviceMediaRepository
import com.example.prismstudio.p40b43a88.data.repository.DeviceMediaResult
import com.example.prismstudio.p40b43a88.data.repository.EditorSessionRepository
import com.example.prismstudio.p40b43a88.data.repository.MediaPreviewRepository
import com.example.prismstudio.p40b43a88.data.repository.ProgramPlaybackRepository
import com.example.prismstudio.p40b43a88.data.repository.SourcePlaybackRepository
import com.example.prismstudio.p40b43a88.domain.usecase.FlagUseCase
import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.IdGenerator
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaBin
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import com.example.prismstudio.p40b43a88.domain.model.Timecode
import com.example.prismstudio.p40b43a88.domain.model.Timeline
import com.example.prismstudio.p40b43a88.domain.model.TrackKind
import com.example.prismstudio.p40b43a88.domain.model.Waveform
import com.example.prismstudio.p40b43a88.domain.model.attempt
import com.example.prismstudio.p40b43a88.domain.usecase.AppendMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.EditTargetsUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEdit
import com.example.prismstudio.p40b43a88.domain.usecase.SourceEditUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.FilterMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.ImportMediaUseCase
import com.example.prismstudio.p40b43a88.domain.usecase.RelinkMediaUseCase
import com.example.prismstudio.p40b43a88.ui.components.route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.prismstudio.p40b43a88.domain.model.SyncedAudio
import com.example.prismstudio.p40b43a88.domain.usecase.AutoSyncAudioUseCase
import com.example.prismstudio.p40b43a88.data.repository.SoundEnvelopeRepository

/** Media stage: media pool, source monitor with in / out marks, and device import. */
class MediaViewModel(
    private val session: EditorSessionRepository,
    private val deviceMedia: DeviceMediaRepository,
    private val previews: MediaPreviewRepository,
    private val source: SourcePlaybackRepository,
    private val ids: IdGenerator,
    /** The timeline's monitor: its playhead is where Insert, Overwrite and Place on Top put the source. */
    private val program: ProgramPlaybackRepository,
    private val append: AppendMediaUseCase = AppendMediaUseCase(ids),
    private val edits: SourceEditUseCase = SourceEditUseCase(ids, append),
    private val filter: FilterMediaUseCase = FilterMediaUseCase(),
    private val importer: ImportMediaUseCase = ImportMediaUseCase(),
    private val destinations: EditTargetsUseCase = EditTargetsUseCase(),
    private val flagging: FlagUseCase = FlagUseCase(),
    private val timelinePool: TimelinePoolUseCase = TimelinePoolUseCase(),
    private val timelinesEdits: TimelinesUseCase = TimelinesUseCase(ids),
    private val bins: MediaBinsUseCase = MediaBinsUseCase(ids),
    private val smart: SmartBinsUseCase = SmartBinsUseCase(ids),
    private val clipMeta: MediaMetadataUseCase = MediaMetadataUseCase(),
    private val sorting: SortMediaUseCase = SortMediaUseCase(),
    private val proxies: ProxyRepository = ProxyRepository.None,
    private val proxyRules: ProxyMediaUseCase = ProxyMediaUseCase(),
    /** Resolve's Auto Sync Audio: the files' sound levels, and the matching. */
    private val envelopes: SoundEnvelopeRepository = SoundEnvelopeRepository.None,
    private val autoSync: AutoSyncAudioUseCase = AutoSyncAudioUseCase(),
    private val relinker: RelinkMediaUseCase = RelinkMediaUseCase(),
) : ViewModel() {

    private val _state = MutableStateFlow(MediaUiState())
    /** Working out an Auto Sync Audio match. */
    private var syncing = false
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private val frames = mutableMapOf<String, Bitmap>()
    private val waves = mutableMapOf<String, Waveform>()
    private val requested = mutableSetOf<String>()
    private var deviceItems: List<DeviceMediaItem> = emptyList()
    private val deviceFrames = mutableMapOf<String, Bitmap>()
    private var browseJob: Job? = null
    private var importJob: Job? = null

    /** The relink sheet: the device files found for each offline media, and the file taken for it (found or picked). */
    private var relinkCandidates: Map<String, List<RelinkMediaUseCase.Candidate>> = emptyMap()
    private val relinkChosen = mutableMapOf<String, RelinkPick>()
    private val manualRelinkChoices = mutableSetOf<String>()
    private var relinkSearch: Job? = null
    private var relinkWrite: Job? = null
    private val relinkPicks = mutableMapOf<String, Job>()

    /** A file taken for an offline media: one the search found ([match]), or one picked by hand (already probed). */
    private data class RelinkPick(val item: DeviceMediaItem, val match: RelinkMediaUseCase.Match?, val probed: MediaAsset? = null)

    init {
        viewModelScope.launch { session.document.collect { publish() } }
        // The source marks are the session's: the Edit page's source viewer and Match Frame set them too.
        viewModelScope.launch { session.sourceMarks.collect { publish() } }
        // A proxy's progress and outcome arrive in the background.
        viewModelScope.launch { proxies.proxies.collect { publish() } }
        viewModelScope.launch {
            source.state.collect { p ->
                _state.update { it.copy(sourcePositionFrames = Timecode.positionToFrame(p.positionMs, it.fps), playing = p.playing) }
            }
        }
        viewModelScope.launch {
            program.state.collect { p -> _state.update { it.copy(timelineFrame = Timecode.positionToFrame(p.positionMs, it.fps)) } }
        }
    }

    private val doc: ProjectDocument? get() = session.document.value

    fun onEvent(event: MediaEvent) {
        when (event) {
            is MediaEvent.SelectBin -> {
                _state.update { it.copy(binIndex = event.index.coerceIn(0, MediaBin.entries.lastIndex), userBinId = null, smartBinId = null) }
                publish()
            }
            MediaEvent.NewBin -> binEdit("新建媒体夹") { d -> bins.create(d) }?.binId?.let { id ->
                _state.update { it.copy(userBinId = id, smartBinId = null) }
                publish()
            }
            is MediaEvent.SelectUserBin -> {
                _state.update { it.copy(userBinId = event.id, smartBinId = null) }
                publish()
            }
            is MediaEvent.MoveToBin -> _state.value.selectedId?.let { id -> binEdit("放入媒体夹") { d -> bins.move(d, id, event.binId) } }
            is MediaEvent.InputColor -> _state.value.selectedId?.let { id ->
                val asset = doc?.asset(id) ?: return@let
                if (asset.inputColor == event.space) return@let
                session.edit("输入色彩空间") { d -> d.copy(media = d.media.map { if (it.id == id) it.copy(inputColor = event.space) else it }) }
                // The source monitor follows: HDR by the override is tone mapped there too.
                doc?.let { d -> d.asset(id)?.let { source.load(it, d.colorManagement) } }
                toast("${asset.name} 的输入色彩空间：" + if (event.space == InputColorSpace.AUTO) "按文件标记（${asset.color.label}）" else event.space.label)
            }
            is MediaEvent.OpenRenameBin -> doc?.bins?.firstOrNull { it.id == event.id }?.let { b ->
                _state.update { it.copy(renamingBinId = b.id, binRename = RenameUi(title = "重命名媒体夹", original = b.name, text = b.name, hint = "最多 ${MediaBinsUseCase.MAX_NAME} 个字，不能与其他媒体夹同名")) }
            }
            is MediaEvent.RenameBinText -> {
                val d = doc ?: return
                val id = _state.value.renamingBinId ?: return
                _state.update { s -> s.copy(binRename = s.binRename?.copy(text = event.text, problem = bins.problem(d, id, event.text))) }
            }
            MediaEvent.ConfirmRenameBin -> {
                val id = _state.value.renamingBinId ?: return
                val text = _state.value.binRename?.text ?: return
                if (binEdit("重命名媒体夹") { d -> bins.rename(d, id, text) } != null) _state.update { it.copy(binRename = null, renamingBinId = null) }
            }
            MediaEvent.CloseRenameBin -> _state.update { it.copy(binRename = null, renamingBinId = null) }
            is MediaEvent.DeleteBin -> if (binEdit("删除媒体夹") { d -> bins.delete(d, event.id) } != null && _state.value.userBinId == event.id) {
                _state.update { it.copy(userBinId = null, binIndex = 0) }
                publish()
            }
            MediaEvent.NewSmartBin -> doc?.let { d -> openSmartSheet(null, SmartBinsUseCase.Rules(smart.suggestedName(d))) }
            is MediaEvent.EditSmartBin -> doc?.smartBins?.firstOrNull { it.id == event.id }?.let { b -> openSmartSheet(b.id, SmartBinsUseCase.Rules.of(b)) }
            is MediaEvent.SmartRules -> _state.value.smartSheet?.let { sheet -> openSmartSheet(sheet.editingId, event.rules) }
            MediaEvent.ConfirmSmartBin -> confirmSmartBin()
            MediaEvent.CloseSmartBin -> _state.update { it.copy(smartSheet = null) }
            is MediaEvent.SelectSmartBin -> {
                _state.update { it.copy(smartBinId = event.id, userBinId = null) }
                publish()
            }
            is MediaEvent.DeleteSmartBin -> if (smartEdit("删除智能媒体夹") { d -> smart.delete(d, event.id) } != null && _state.value.smartBinId == event.id) {
                _state.update { it.copy(smartBinId = null, binIndex = 0) }
                publish()
            }
            is MediaEvent.SetListView -> _state.update { it.copy(listView = event.list) }
            is MediaEvent.SortBy -> {
                _state.update { s ->
                    val o = s.order
                    s.copy(order = if (o.by == event.sort) o.copy(ascending = !o.ascending) else SortMediaUseCase.Order(event.sort, true))
                }
                publish()
            }
            is MediaEvent.SortAscending -> {
                _state.update { it.copy(order = it.order.copy(ascending = event.ascending)) }
                publish()
            }
            is MediaEvent.SyncAudio -> syncAudio(event.audioId)
            MediaEvent.ClearAudioSync -> _state.value.selected?.let { video ->
                if (video.syncedAudio == null) return@let
                try {
                    session.edit("取消同步音频") { autoSync.apply(it, video.id, null) }
                    toast("「${video.name}」改回使用自带的声音")
                } catch (e: EditRejected) {
                    toast(e.message ?: "无法取消同步")
                }
            }
            MediaEvent.GenerateProxy -> _state.value.selected?.let { a ->
                if (!proxyRules.canProxy(a)) return toast(proxyRules.label(a, ProxyStatus.None))
                proxies.request(listOf(a))
                toast("开始为“${a.name}”生成代理（${proxyRules.size(a).let { (w, h) -> "${w}×$h" }}）")
            } ?: toast("请先选择素材")
            MediaEvent.GenerateAllProxies -> doc?.let { d ->
                val missing = proxyRules.candidates(d).filter { proxies.proxies.value[it.id] !is ProxyStatus.Ready && proxies.proxies.value[it.id] !is ProxyStatus.Making }
                if (missing.isEmpty()) return toast("媒体池里的视频都已有代理")
                proxies.request(missing)
                toast("开始为 ${missing.size} 个视频生成代理，一次生成一个")
            }
            MediaEvent.DeleteProxy -> _state.value.selected?.let { a ->
                if (proxies.proxies.value[a.id] == null) return toast("“${a.name}”没有代理")
                proxies.delete(a)
                toast("已删除“${a.name}”的代理")
            }
            MediaEvent.OpenMetadata -> _state.value.selected?.let { a ->
                _state.update { it.copy(metadataSheet = MetadataSheetUi(a.name, MediaMetadataUseCase.Draft.of(a.metadata))) }
            } ?: toast("请先选择素材")
            is MediaEvent.MetadataDraft -> _state.value.selected?.let { a ->
                _state.update { s ->
                    s.copy(metadataSheet = s.metadataSheet?.copy(
                        draft = event.draft,
                        problem = clipMeta.problem(event.draft),
                        changed = clipMeta.metadata(event.draft) != a.metadata,
                    ))
                }
            }
            MediaEvent.ConfirmMetadata -> confirmMetadata()
            MediaEvent.CloseMetadata -> _state.update { it.copy(metadataSheet = null) }
            is MediaEvent.Query -> {
                _state.update { it.copy(query = event.text) }
                publish()
            }
            is MediaEvent.Select -> select(event.id)
            is MediaEvent.OpenTimeline -> openTimeline(event.id)
            MediaEvent.EditOpened -> _state.update { it.copy(openEditRequest = false) }
            is MediaEvent.Scrub -> seek(event.frame)
            MediaEvent.MarkIn -> mark(isIn = true)
            MediaEvent.MarkOut -> mark(isIn = false)
            MediaEvent.ClearMarks -> clearMarks()
            is MediaEvent.ToggleFlag -> flag("${event.color.label}色旗标") { d, id -> flagging.toggle(d, listOf(id), event.color) }
            MediaEvent.ClearFlags -> flag("清除旗标") { d, id -> flagging.clear(d, listOf(id)) }
            MediaEvent.Append -> editSelected(SourceEdit.APPEND)
            is MediaEvent.Edit -> editSelected(event.mode)
            is MediaEvent.SetVideoTarget -> retarget { destinations.video(it, event.trackId) }
            is MediaEvent.SetAudioTarget -> retarget { destinations.audio(it, event.trackId) }
            is MediaEvent.SetPlaying -> if (event.playing && !_state.value.nothingSelected) source.play() else source.pause()
            MediaEvent.JumpToIn -> seek(_state.value.inFrame)
            MediaEvent.JumpToOut -> seek(_state.value.outFrame)
            is MediaEvent.Jog -> {
                source.pause()
                seek(_state.value.sourcePositionFrames + event.deltaFrames)
            }
            is MediaEvent.SourceSurface -> source.route(event.change)
            is MediaEvent.RequestThumbnail -> loadPoolPreview(event.id)
            MediaEvent.OpenImport -> openImport()
            MediaEvent.CancelImport -> closeImport()
            is MediaEvent.ToggleDevice -> toggleDevice(event.id)
            is MediaEvent.RequestDeviceThumbnail -> loadDevicePreview(event.id)
            MediaEvent.ConfirmImport -> confirmImport()
            is MediaEvent.PermissionResult -> {
                if (_state.value.import.let { it.visible && !it.importing }) browse()
                if (_state.value.relink?.relinking == false) searchRelink()
            }
            MediaEvent.OpenRelink -> openRelink()
            MediaEvent.CloseRelink -> closeRelink()
            is MediaEvent.RelinkChoose -> chooseRelink(event.assetId, event.uri)
            is MediaEvent.RelinkPicked -> pickRelink(event.assetId, event.uri)
            MediaEvent.ConfirmRelink -> confirmRelink()
            MediaEvent.ToastShown -> _state.update { it.copy(toast = null) }
            is MediaEvent.ShowMessage -> toast(event.message)
        }
    }

    /** One change to the user bins or what is filed in them: one undo step, its outcome (or why not) toasted. */
    private fun binEdit(label: String, change: (ProjectDocument) -> MediaBinsUseCase.Result): MediaBinsUseCase.Result? {
        var result: MediaBinsUseCase.Result? = null
        try {
            session.edit(label) { d -> change(d).also { result = it }.doc }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法完成")
            return null
        }
        result?.let { toast(it.message) }
        return result
    }

    /** The smart bin sheet showing [rules] (for smart bin [id], null: a new one): what they find and why not yet. */
    private fun openSmartSheet(id: String?, rules: SmartBinsUseCase.Rules) {
        val d = doc ?: return
        _state.update { it.copy(smartSheet = SmartBinSheetUi(id, rules, smart.media(d, rules).size, smart.problem(d, id, rules))) }
    }

    private fun confirmSmartBin() {
        val sheet = _state.value.smartSheet ?: return
        val id = sheet.editingId
        val result = smartEdit(if (id == null) "新建智能媒体夹" else "编辑智能媒体夹") { d ->
            if (id == null) smart.create(d, sheet.rules) else smart.update(d, id, sheet.rules)
        } ?: return
        // The new or changed smart bin shows.
        _state.update { it.copy(smartSheet = null, smartBinId = result.binId, userBinId = null) }
        publish()
    }

    private fun proxyStatus(id: String): ProxyStatus = proxies.proxies.value[id] ?: ProxyStatus.None

    /** Saves the sheet as the selected media's clip metadata: one undo step, its message toasted. */
    private fun confirmMetadata() {
        val sheet = _state.value.metadataSheet ?: return
        val id = _state.value.selected?.id ?: return
        var message = ""
        try {
            session.edit("编辑场记") { d -> clipMeta.apply(d, id, sheet.draft).also { message = it.message }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法保存场记")
        }
        _state.update { it.copy(metadataSheet = null) }
        toast(message)
    }

    /** One change to the smart bins: one undo step, its outcome (or why not) toasted. */
    private fun smartEdit(label: String, change: (ProjectDocument) -> SmartBinsUseCase.Result): SmartBinsUseCase.Result? {
        var result: SmartBinsUseCase.Result? = null
        try {
            session.edit(label) { d -> change(d).also { result = it }.doc }
        } catch (e: EditRejected) {
            toast(e.message ?: "无法完成")
            return null
        }
        result?.let { toast(it.message) }
        return result
    }

    /** A timeline card tapped: it becomes the open timeline (one undo step, as the Edit page's menu does), then the Edit page. */
    private fun openTimeline(id: String) {
        val document = doc ?: return
        if (id != document.timelineId) {
            var message = ""
            try {
                session.edit("切换时间线") { d -> timelinesEdits.open(d, id).also { message = it.message }.doc }
            } catch (e: EditRejected) {
                return toast(e.message ?: "无法打开时间线")
            }
            session.select(null)
            toast(message)
        }
        _state.update { it.copy(openEditRequest = true) }
    }

    private fun publish() {
        val document = doc ?: return
        val s = _state.value
        val bin = MediaBin.entries[s.binIndex]
        // A user bin that went away (undone, deleted elsewhere) gives way to 全部素材.
        val userBin = s.userBinId?.takeIf { id -> document.bins.any { it.id == id } }
        if (s.userBinId != null && userBin == null) _state.update { it.copy(userBinId = null) }
        // Likewise a smart bin; it finds its media afresh each time (imports, flags and edits change what it holds).
        val smartBin = document.smartBins.firstOrNull { it.id == s.smartBinId }
        if (s.smartBinId != null && smartBin == null) _state.update { it.copy(smartBinId = null) }
        val usage = smart.usage(document)
        val filtered = when {
            smartBin != null -> filter(smart.media(document, smartBin, usage), MediaBin.ALL, s.query)
            userBin != null -> filter(document.media.filter { it.binId == userBin }, MediaBin.ALL, s.query)
            else -> filter(document.media, bin, s.query)
        }
        // Resolve's sort: both views show the pool in the chosen order.
        val visible = sorting(filtered, s.order, usage)
        // Keep the selection while its asset exists; if it went away (e.g. an import was undone) fall back to the
        // first visible asset, and never leave a removed asset loaded in the source monitor.
        val selected = document.asset(s.selectedId) ?: visible.firstOrNull()
        val fps = document.format.fps
        if (selected?.id != s.selectedId || selected?.uri != s.selected?.uri || selected?.offline != s.selected?.offline) {
            if (selected != null) {
                loadSource(selected, fps)
            } else {
                source.load(null)
                _state.update { it.copy(sourceStill = null) }
            }
        }
        val (inFrame, outFrame) = selected?.let { marksFor(it, fps) } ?: (0 to 0)
        _state.update {
            it.copy(
                timelines = timelinePool(document, s.query).map { c ->
                    TimelineTileUi(
                        c.id, c.name,
                        "${c.format.width}×${c.format.height} · ${Timecode.format(c.durationFrames, c.format.fps)} · ${c.clips} 个片段",
                        c.open, c.rows, c.bars, c.compound,
                    )
                },
                items = visible.map { asset ->
                    val m = asset.metadata
                    MediaTileUi(
                        asset.id, asset.name, asset.kind, asset.durationLabel, asset.offline, asset.poster, frames[asset.id], waves[asset.id], asset.flags,
                        resolution = if (asset.kind == MediaKind.AUDIO) "—" else asset.resolutionLabel,
                        slate = if (m.hasSlate) listOf(m.scene, m.shot, m.take).joinToString(" / ") { it.ifEmpty { "—" } } else "—",
                        uses = usage[asset.id] ?: 0,
                        goodTake = m.goodTake,
                        hdr = asset.kind == MediaKind.VIDEO && asset.effectiveTransfer != ColorTransfer.SDR,
                    )
                },
                poolCount = document.media.size,
                offlineCount = document.media.count { a -> a.offline },
                summary = when {
                    smartBin != null -> "${smartBin.name} · ${visible.size} 个素材 · ${smart.describe(smartBin)}"
                    userBin != null -> "${document.bins.first { it.id == userBin }.name} · ${visible.size} 个素材"
                    bin == MediaBin.TIMELINES -> timelinePool.summary(document)
                    else -> filter.summary(document.media)
                },
                userBins = document.bins.map { UserBinUi(it.id, it.name, bins.count(document, it.id)) },
                smartBins = document.smartBins.map { b -> SmartBinUi(b.id, b.name, smart.media(document, b, usage).size, smart.describe(b)) },
                // The sheet's count follows the pool (an import or a flag while it is open).
                smartSheet = it.smartSheet?.let { sh -> sh.copy(matches = smart.media(document, sh.rules, usage).size, problem = smart.problem(document, sh.editingId, sh.rules)) },
                selectedBinId = bins.binOf(document, selected?.id)?.id,
                selectedId = selected?.id,
                selected = selected,
                metadata = selected?.let { a -> metadata(a, smart.usageLabel(document, a.id)) + clipMeta.rows(a.metadata) }.orEmpty(),
                slateLabel = selected?.let { a -> clipMeta.slateLabel(a.metadata) } ?: "未填写",
                inputColor = selected?.takeIf { a -> a.kind == MediaKind.VIDEO }?.inputColor,
                colorNote = selected?.takeIf { a -> a.kind == MediaKind.VIDEO }?.let { a -> colorNote(a, document.colorManagement) }.orEmpty(),
                proxyLabel = selected?.let { a -> proxyRules.label(a, proxyStatus(a.id)) } ?: "无代理",
                syncLabel = selected?.let { a -> if (syncing) "正在比较声音…" else autoSync.label(a, document.media) }.orEmpty(),
                syncChoices = selected?.let { a ->
                    autoSync.candidates(a, document.media).map { SyncChoiceUi(it.id, it.name, it.id == a.syncedAudio?.assetId) }
                }.orEmpty(),
                syncUnavailable = autoSync.unavailable(selected),
                synced = selected?.syncedAudio != null,
                syncing = syncing,
                canProxy = selected?.let(proxyRules::canProxy) == true,
                hasProxy = selected?.let { proxyStatus(it.id) is ProxyStatus.Ready } == true,
                proxyMaking = selected?.let { proxyStatus(it.id) is ProxyStatus.Making } == true,
                proxiesMissing = proxyRules.candidates(document).count { proxyStatus(it.id) == ProxyStatus.None || proxyStatus(it.id) is ProxyStatus.Failed },
                fps = fps,
                sourceDurationFrames = selected?.let { a -> durationFrames(a, fps) } ?: 0,
                inFrame = inFrame,
                outFrame = outFrame,
                videoTargets = document.timeline.tracks.filter { t -> t.kind == TrackKind.VIDEO }.reversed()
                    .map { t -> TargetChoiceUi(t.id, t.name) },
                audioTargets = listOf(TargetChoiceUi(null, "自动")) +
                    document.timeline.tracks.filter { t -> t.kind == TrackKind.AUDIO }.map { t -> TargetChoiceUi(t.id, t.name) },
                targets = document.timeline.activeTargets,
                timelineMarks = document.timeline.marks,
            )
        }
        // The relink sheet follows the pool (an undo while it is open).
        if (_state.value.relink != null) publishRelink()
    }

    /** Alt+X on the Media stage: the selected item's In / Out back to its whole length. */
    private fun clearMarks() {
        val s = _state.value
        val asset = s.selected ?: return
        if (session.sourceMarks.value[asset.id] == null) return
        session.setSourceMarks(asset.id, null)
        publish()
        toast("已清除源片段入出点")
    }

    /** Resolve's flags on the selected media: one undo step, the use case's message as the toast. */
    private fun flag(label: String, change: (ProjectDocument, String) -> FlagUseCase.Result) {
        val id = _state.value.selected?.id ?: return toast("请先选择素材")
        var message = ""
        try {
            session.edit(label) { d -> change(d, id).also { message = it.message }.doc }
        } catch (e: EditRejected) {
            return toast(e.message ?: "无法标记旗标")
        }
        toast(message)
    }

    /** Sets a destination control: kept with the project, outside the edit history. */
    private fun retarget(change: (Timeline) -> EditTargetsUseCase.Result) {
        val document = doc ?: return
        try {
            val result = change(document.timeline)
            session.update { it.copy(timeline = it.timeline.copy(targets = result.timeline.targets)) }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法设置编辑目标")
        }
    }

    private fun select(id: String) {
        val document = doc ?: return
        val asset = document.asset(id) ?: return
        source.pause()
        _state.update { it.copy(selectedId = id, sourceStill = null) }
        loadSource(asset, document.format.fps)
        publish()
    }

    private fun loadSource(asset: MediaAsset, fps: Int) {
        source.load(asset, doc?.colorManagement ?: ColorManagement.DEFAULT)
        _state.update { it.copy(sourceStill = if (asset.kind == MediaKind.IMAGE) frames[asset.id] else null, sourcePositionFrames = 0) }
        if (asset.kind == MediaKind.IMAGE) {
            viewModelScope.launch {
                val still = previews.thumbnail(asset.uri, asset.kind, 0, STILL_WIDTH)
                if (still != null && _state.value.selectedId == asset.id) _state.update { it.copy(sourceStill = still) }
            }
        }
        marksFor(asset, fps)
    }

    /** [asset]'s source marks in frames (the session keeps them in ms), the whole media without any. */
    private fun marksFor(asset: MediaAsset, fps: Int): Pair<Int, Int> =
        session.sourceMarks.value[asset.id]?.let { Timecode.nearestFrame(it.inMs, fps) to Timecode.nearestFrame(it.outMs, fps) }
            ?: (0 to durationFrames(asset, fps))

    private fun durationFrames(asset: MediaAsset, fps: Int) = Timecode.msToFrames(asset.durationMs, fps).coerceAtLeast(1)

    private fun seek(frame: Int) {
        val s = _state.value
        if (s.selected == null) return
        val clamped = frame.coerceIn(0, s.sourceDurationFrames)
        source.seekTo(Timecode.frameToSeekMs(clamped, s.fps))
        _state.update { it.copy(sourcePositionFrames = clamped) }
    }

    private fun mark(isIn: Boolean) {
        val s = _state.value
        val asset = s.selected ?: return
        if (asset.kind == MediaKind.IMAGE) {
            toast("图片素材固定为 5 秒，无需标记入出点")
            return
        }
        val position = s.sourcePositionFrames.coerceIn(0, s.sourceDurationFrames)
        var (inFrame, outFrame) = marksFor(asset, s.fps)
        if (isIn) {
            inFrame = position
            if (outFrame <= inFrame) outFrame = s.sourceDurationFrames
        } else {
            outFrame = position
            if (outFrame <= inFrame) inFrame = 0
        }
        if (outFrame <= inFrame) {
            toast("入点必须早于出点")
            return
        }
        session.setSourceMarks(asset.id, SourceMarks(Timecode.framesToMs(inFrame, s.fps), Timecode.framesToMs(outFrame, s.fps)))
        _state.update { it.copy(inFrame = inFrame, outFrame = outFrame) }
        toast(if (isIn) "已标记入点 ${Timecode.format(inFrame, s.fps)}" else "已标记出点 ${Timecode.format(outFrame, s.fps)}")
    }

    /**
     * Resolve's Auto Sync Audio: matches the selected video's own sound against [audioId]'s by waveform and, when they
     * share it, takes the video's sound from that file from now on (one undo step, "同步音频"); clips already on the
     * timeline keep theirs.
     */
    private fun syncAudio(audioId: String) {
        if (syncing) return
        val document = doc ?: return
        val video = _state.value.selected ?: return toast("请先选择视频")
        autoSync.unavailable(video)?.let { return toast(it) }
        val audio = document.asset(audioId) ?: return
        syncing = true
        publish()
        viewModelScope.launch {
            try {
                val v = envelopes.envelope(video)
                val a = envelopes.envelope(audio)
                val offset = if (v != null && a != null) autoSync.offset(video, v, a) else null
                if (offset == null) {
                    toast("找不到「${audio.name}」与「${video.name}」相同的声音：确认它们是同一条录制")
                } else {
                    session.edit("同步音频") { autoSync.apply(it, video.id, SyncedAudio(audio.id, offset)) }
                    doc?.asset(video.id)?.let { toast(autoSync.label(it, doc?.media.orEmpty()) + "；之后放到时间线的片段会使用它") }
                }
            } catch (e: EditRejected) {
                toast(e.message ?: "无法同步")
            } finally {
                syncing = false
                publish()
            }
        }
    }

    /**
     * Puts the selected item's marked range onto the timeline ([SourceEdit]); edits at the playhead move it
     * past the new clip, as in Resolve, so the next edit follows on. A replace lines the source viewer's
     * playhead up with the timeline's and leaves the playhead where it is.
     */
    private fun editSelected(mode: SourceEdit) {
        val s = _state.value
        val asset = s.selected ?: return
        val document = doc ?: return
        val fps = document.format.fps
        val at = s.timelineFrame
        try {
            val result = edits(
                document.timeline, asset,
                inMs = Timecode.framesToMs(s.inFrame, fps),
                outMs = Timecode.framesToMs(s.outFrame, fps),
                at = at,
                mode = mode,
                fps = fps,
                syncMs = Timecode.framesToMs(s.sourcePositionFrames, fps),
            )
            val label = when (mode) {
                SourceEdit.APPEND -> "添加到时间线"
                SourceEdit.REPLACE -> "替换片段"
                SourceEdit.FIT_TO_FILL -> "适配填充"
                else -> "${mode.label}到时间线"
            }
            // A synced video brings its recorder's sound (Auto Sync Audio).
            session.edit(label) { it.copy(timeline = autoSync.resynced(document.timeline, result.timeline, it.media, fps)) }
            session.select(result.selectedClipId)
            if (mode != SourceEdit.APPEND && mode != SourceEdit.REPLACE) {
                val end = result.timeline.clip(result.selectedClipId)?.end ?: at
                program.seekTo(Timecode.frameToSeekMs(end, fps))
            }
            toast(result.message)
        } catch (e: EditRejected) {
            toast(e.message ?: "无法添加到时间线")
        }
    }

    private fun loadPoolPreview(id: String) {
        val asset = doc?.asset(id) ?: return
        // The file is part of the key: a relinked media's frame and waveform are read again.
        // So is how the timeline shows it: a new input colour override reads the tile again, tone mapped.
        val color = doc?.colorManagement ?: ColorManagement.DEFAULT
        if (!requested.add("pool:$id:${asset.uri}:${timelineLookKey(asset, color)}")) return
        viewModelScope.launch {
            if (asset.kind == MediaKind.AUDIO) {
                previews.waveform(asset.uri)?.let { waves[id] = it; publish() }
            } else {
                val at = if (asset.kind == MediaKind.VIDEO) minOf(1_000L, asset.durationMs / 2) else 0L
                previews.thumbnail(asset.uri, asset.kind, at, TILE_WIDTH)?.let { raw ->
                    val it = withContext(Dispatchers.Default) { timelineLook(raw, asset, color) }
                    frames[id] = it
                    if (_state.value.selectedId == id && asset.kind == MediaKind.IMAGE && _state.value.sourceStill == null) {
                        _state.update { s -> s.copy(sourceStill = it) }
                    }
                    publish()
                }
            }
        }
    }

    /** `relink-sheet`: opened on the offline media, the device searched straight away. */
    private fun openRelink() {
        if (_state.value.relink != null) return
        relinkCandidates = emptyMap()
        relinkChosen.clear()
        manualRelinkChoices.clear()
        _state.update { it.copy(relink = RelinkSheetUi(searching = true)) }
        publishRelink()
        searchRelink()
    }

    private fun closeRelink() {
        relinkSearch?.cancel()
        relinkWrite?.cancel()
        relinkPicks.values.forEach { it.cancel() }
        relinkPicks.clear()
        _state.update { it.copy(relink = null) }
    }

    private fun searchRelink() {
        val original = doc ?: return
        relinkSearch?.cancel()
        relinkSearch = viewModelScope.launch {
            _state.update { it.copy(relink = it.relink?.copy(searching = true, permissionRequired = false, error = null)) }
            val result = deviceMedia.browse()
            val offlineUris = original.media.filter { it.offline }.map { it.uri }.distinct()
            val recovered = offlineUris.filter { attempt { deviceMedia.isAvailable(it) }.getOrDefault(false) }
            ensureActive()
            if (doc?.id != original.id) return@launch
            if (recovered.isNotEmpty()) session.update { it.withMediaAvailability(recovered.associateWith { true }) }
            val listing = result as? DeviceMediaResult.Items
            val offline = doc?.media.orEmpty().filter { it.offline }
            relinkCandidates = offline.associate { it.id to relinker.candidates(it, listing?.items.orEmpty()) }
            relinkCandidates.forEach { (id, found) ->
                val choice = relinkChosen[id]
                if (id !in manualRelinkChoices) {
                    val best = relinker.pick(found)
                    if (best == null) relinkChosen.remove(id) else relinkChosen[id] = RelinkPick(best.item, best.match)
                } else if (choice != null && choice.probed == null && found.none { it.item.uri == choice.item.uri }) {
                    relinkChosen.remove(id)
                }
            }
            _state.update { s ->
                s.copy(relink = s.relink?.copy(searching = false, limitedVisualAccess = listing?.limitedVisualAccess == true,
                    permissionRequired = result == DeviceMediaResult.PermissionRequired, error = (result as? DeviceMediaResult.Failed)?.message))
            }
            publishRelink()
        }
    }

    /** `relink-choice`: one of the files found for [assetId]; an empty [uri] leaves it offline. */
    private fun chooseRelink(assetId: String, uri: String) {
        if (_state.value.relink?.relinking != false) return
        relinkPicks.remove(assetId)?.cancel()
        manualRelinkChoices.add(assetId)
        val candidate = relinkCandidates[assetId]?.firstOrNull { it.item.uri == uri }
        if (candidate == null) relinkChosen.remove(assetId) else relinkChosen[assetId] = RelinkPick(candidate.item, candidate.match)
        publishRelink()
    }

    /** `relink-pick-button`: a file picked by hand (Resolve's Relink Selected Clips): read, checked for its kind, taken. */
    private fun pickRelink(assetId: String, uri: String) {
        if (_state.value.relink?.relinking != false) return
        val asset = doc?.asset(assetId) ?: return
        relinkPicks.remove(assetId)?.cancel()
        relinkPicks[assetId] = viewModelScope.launch {
            val item = attempt { deviceMedia.describe(uri) }.getOrNull()
            when {
                item == null -> toast("无法读取所选文件：请选择视频、音频或图片")
                item.kind != asset.kind -> toast("类型不同：“${asset.name}”是${kindLabel(asset.kind)}，所选文件是${kindLabel(item.kind)}")
                else -> {
                    val probed = attempt { deviceMedia.probe(item, assetId) }.getOrNull()
                    ensureActive()
                    if (probed == null) toast("无法读取所选文件") else {
                        manualRelinkChoices.add(assetId)
                        relinkChosen[assetId] = RelinkPick(item, null, probed)
                    }
                }
            }
            publishRelink()
        }
    }

    private fun publishRelink() {
        val document = doc ?: return
        val sheet = _state.value.relink ?: return
        val rows = document.media.filter { it.offline }.map { asset ->
            val found = relinkCandidates[asset.id].orEmpty()
            val chosen = relinkChosen[asset.id]
            val length = chosen?.probed?.durationMs ?: chosen?.item?.durationMs
            val short = if (asset.kind == MediaKind.IMAGE || length == null) null
            else (relinker.usedMs(document, asset.id) - length).takeIf { it > RelinkMediaUseCase.LENGTH_SLACK_MS }
            val status = when {
                chosen?.probed != null -> "所选文件：${chosen.item.relativePath}${chosen.probed.name} · ${chosen.probed.durationLabel}"
                chosen != null -> "将链接到 ${chosen.item.relativePath}${chosen.item.name} · ${chosen.match?.label.orEmpty()}"
                sheet.searching -> "正在查找…"
                found.isEmpty() -> "设备中没有找到这个文件，可以手动选择"
                else -> "找到同名文件，但时长或大小不同：请确认后从候选中选择"
            } + (short?.let { "（比片段用到的短 ${RelinkMediaUseCase.seconds(it)}）" } ?: "")
            RelinkRowUi(
                id = asset.id,
                name = asset.name,
                kind = asset.kind,
                was = "原位置：${asset.relativePath.ifEmpty { "—" }} · ${kindLabel(asset.kind)}" +
                    if (asset.kind == MediaKind.IMAGE) "" else " ${asset.durationLabel}",
                status = status,
                found = chosen != null,
                warning = short != null || chosen?.match?.sure == false,
                choices = found.map { RelinkChoiceUi(it.item.uri, it.label, chosen?.item?.uri == it.item.uri) },
            )
        }
        _state.update { it.copy(relink = it.relink?.copy(rows = rows)) }
    }

    /** `relink-confirm-button`: every media with a file taken relinked as one undo step. */
    private fun confirmRelink() {
        val sheet = _state.value.relink ?: return
        if (!sheet.canConfirm) return
        val original = doc ?: return
        val picks = relinkChosen.filterKeys { id -> original.asset(id)?.offline == true }
        _state.update { it.copy(relink = it.relink?.copy(relinking = true)) }
        relinkWrite = viewModelScope.launch {
            val probed = picks.mapNotNull { (id, pick) -> (pick.probed ?: attempt { deviceMedia.probe(pick.item, id) }.getOrNull())?.let { id to it } }.toMap()
            ensureActive()
            val current = doc
            if (current?.id != original.id) return@launch closeRelink()
            val files = probed.filterKeys { id -> current.asset(id)?.let { it.offline && it.uri == original.asset(id)?.uri } == true }
            val unreadable = picks.size - probed.size
            if (unreadable == 0 && files.isEmpty()) {
                _state.update { it.copy(relink = it.relink?.copy(relinking = false)) }
                searchRelink()
                return@launch
            }
            var message = ""
            try {
                session.edit("重新链接素材") { d -> relinker.relinkAll(d, files).also { message = it.message }.doc }
            } catch (e: EditRejected) {
                _state.update { it.copy(relink = it.relink?.copy(relinking = false)) }
                toast(e.message ?: "无法重新链接")
                return@launch
            }
            closeRelink()
            // Their tiles show the new files (a tile asks for its preview only when it first shows), and the source monitor
            // plays the relinked file now.
            files.keys.forEach { id ->
                frames.remove(id)
                waves.remove(id)
                loadPoolPreview(id)
            }
            publish()
            toast(message + if (unreadable > 0) "；$unreadable 个文件无法读取" else "")
        }
    }

    private fun kindLabel(kind: MediaKind) = when (kind) {
        MediaKind.VIDEO -> "视频"
        MediaKind.AUDIO -> "音频"
        MediaKind.IMAGE -> "图片"
    }

    private fun openImport() {
        if (_state.value.import.visible) return
        _state.update { it.copy(import = ImportUi(visible = true, loading = true)) }
        browse()
    }

    private fun closeImport() {
        browseJob?.cancel()
        importJob?.cancel()
        deviceItems = emptyList()
        _state.update { it.copy(import = ImportUi()) }
    }

    private fun browse() {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            _state.update { it.copy(import = it.import.copy(loading = true, error = null, permissionRequired = false)) }
            val result = deviceMedia.browse()
            ensureActive()
            val listing = result as? DeviceMediaResult.Items
            deviceItems = listing?.items.orEmpty()
            val error = when {
                result is DeviceMediaResult.Failed -> result.message
                listing != null && deviceItems.isEmpty() && listing.missing.isEmpty() && !listing.limitedVisualAccess -> "设备上还没有视频、音频或图片。"
                else -> null
            }
            val selected = importer.pending(deviceItems, _state.value.import.selectedIds, doc?.media.orEmpty()).map { it.id }.toSet()
            _state.update {
                it.copy(import = it.import.copy(missing = listing?.missing.orEmpty(), limitedVisualAccess = listing?.limitedVisualAccess == true,
                    permissionRequired = result == DeviceMediaResult.PermissionRequired, selectedIds = selected))
            }
            publishDevice(loading = false, error = error)
        }
    }

    private fun publishDevice(loading: Boolean = _state.value.import.loading, error: String? = _state.value.import.error) {
        val pool = doc?.media.orEmpty()
        val selected = _state.value.import.selectedIds
        _state.update {
            it.copy(
                import = it.import.copy(
                    loading = loading,
                    error = error,
                    items = deviceItems.map { item ->
                        DeviceTileUi(item.id, item.name, item.kind, item.badge, item.id in selected, importer.isImported(item, pool), deviceFrames[item.id])
                    },
                    summary = importer.summary(deviceItems, selected),
                ),
            )
        }
    }

    private fun toggleDevice(id: String) {
        if (_state.value.import.let { !it.visible || it.loading || it.importing }) return
        val item = deviceItems.firstOrNull { it.id == id } ?: return
        val selected = importer.toggle(_state.value.import.selectedIds, item, doc?.media.orEmpty())
        _state.update { it.copy(import = it.import.copy(selectedIds = selected)) }
        publishDevice()
    }

    private fun loadDevicePreview(id: String) {
        val item = deviceItems.firstOrNull { it.id == id } ?: return
        if (item.kind == MediaKind.AUDIO || !requested.add("device:$id")) return
        viewModelScope.launch {
            val at = if (item.kind == MediaKind.VIDEO) minOf(1_000L, item.durationMs / 2) else 0L
            previews.thumbnail(item.uri, item.kind, at, TILE_WIDTH)?.let {
                if (!_state.value.import.visible || deviceItems.none { current -> current.id == id && current.uri == item.uri }) {
                    requested.remove("device:$id")
                    return@launch
                }
                deviceFrames[id] = it
                publishDevice()
            }
        }
    }

    private fun confirmImport() {
        val sheet = _state.value.import
        val original = doc ?: return
        if (!sheet.visible || sheet.nothingSelected) return
        val pending = importer.pending(deviceItems, sheet.selectedIds, original.media)
        if (pending.isEmpty()) return
        _state.update { it.copy(import = it.import.copy(importing = true)) }
        importJob = viewModelScope.launch {
            val probed = pending.mapNotNull { item -> attempt { deviceMedia.probe(item, ids.next("a")) }.getOrNull() }
            ensureActive()
            val current = doc
            if (current?.id != original.id) {
                closeImport()
                return@launch
            }
            if (probed.isEmpty()) {
                _state.update { it.copy(import = it.import.copy(importing = false), toast = "导入失败：无法读取所选文件") }
                return@launch
            }
            val known = current.media.mapTo(mutableSetOf()) { it.uri }
            val assets = probed.filter { known.add(it.uri) }
            if (assets.isEmpty()) {
                _state.update { it.copy(import = ImportUi(), toast = "所选素材已在媒体池中") }
                return@launch
            }
            session.edit("导入 ${assets.size} 个素材") { it.copy(media = it.media + assets) }
            _state.update { it.copy(import = ImportUi(), selectedId = assets.first().id, binIndex = 0, query = "") }
            select(assets.first().id)
            val failed = pending.size - probed.size
            toast(if (failed > 0) "已导入 ${assets.size} 个素材，${failed} 个无法读取" else "已导入 ${assets.size} 个素材")
        }
    }

    /** `source-meta`: the media's facts, and where it is used (Resolve's Usage) after its length. */
    private fun metadata(asset: MediaAsset, usage: String): List<Pair<String, String>> = listOfNotNull(
        "分辨率" to asset.resolutionLabel,
        "帧率" to asset.frameRateLabel,
        "时长" to asset.durationLabel,
        "使用" to usage,
        "编码" to asset.videoCodec,
        ("色彩" to asset.color.label).takeIf { asset.kind == MediaKind.VIDEO },
        "音频" to asset.audioDescription,
        "文件" to asset.fileLabel,
    )

    /** `source-color-note`: how the media comes into the Rec.709 timeline — as it is, or tone mapped (and how). */
    private fun colorNote(asset: MediaAsset, color: ColorManagement): String {
        val source = if (asset.inputColor == InputColorSpace.AUTO) "按文件标记：${asset.color.label}" else "已手动设为 ${asset.inputColor.label}（文件标记：${asset.color.label}）"
        return when (asset.effectiveTransfer) {
            ColorTransfer.SDR -> "$source。直接进入 ${ColorManagement.TIMELINE_LABEL} 时间线"
            // HDR by the override is tone mapped in the monitors too; a tagged file is shown there as the system decodes it.
            ColorTransfer.HLG -> "$source。${where(asset)}按「${color.toneMapping.label}」色调映射到 Rec.709"
            ColorTransfer.PQ -> "$source。${where(asset)}按「${color.toneMapping.label}」色调映射到 Rec.709，母版峰值按 ${color.pqPeakNits} nits"
        }
    }

    private fun where(asset: MediaAsset) = if (asset.hdrByOverride) "监视器和导出都" else "导出时"

    private fun toast(message: String) = _state.update { it.copy(toast = message) }

    private companion object {
        const val TILE_WIDTH = 320
        const val STILL_WIDTH = 1280
    }
}
