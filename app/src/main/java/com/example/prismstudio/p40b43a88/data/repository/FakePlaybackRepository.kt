package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.model.ColorManagement
import android.view.Surface
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.ProjectDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Transport-only monitor for tests: no decoding, position moves only when seeked. */
open class FakeMonitorPlayback : MonitorPlayback {
    protected val mutableState = MutableStateFlow(PlaybackState(ready = true))
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    var surfaceAttached = false
        private set

    override fun attachSurface(surface: Surface, width: Int, height: Int, layer: Int) {
        surfaceAttached = true
    }

    override fun detachSurface(surface: Surface, layer: Int) {
        surfaceAttached = false
    }

    override fun play() = mutableState.update { it.copy(playing = true) }
    override fun pause() = mutableState.update { it.copy(playing = false) }
    override fun seekTo(positionMs: Long) =
        mutableState.update { it.copy(positionMs = positionMs.coerceIn(0, maxOf(it.durationMs, 0))) }

    override fun release() = mutableState.update { PlaybackState() }
}

class FakeProgramPlaybackRepository : FakeMonitorPlayback(), ProgramPlaybackRepository {
    var document: ProjectDocument? = null
        private set
    var compareClipId: String? = null
        private set
    var highlightClipId: String? = null
        private set
    var monitorMuted = false
        private set

    override fun setMonitorMuted(muted: Boolean) {
        monitorMuted = muted
    }

    override fun setProject(document: ProjectDocument?) {
        this.document = document
        val duration = document?.let { it.durationFrames * 1000L / it.format.fps } ?: 0L
        mutableState.update { it.copy(durationMs = duration, positionMs = it.positionMs.coerceAtMost(duration)) }
    }

    override fun setCompare(clipId: String?, split: Float) {
        compareClipId = clipId
    }

    var keyMatteClipId: String? = null

    override fun setKeyMatte(clipId: String?) {
        keyMatteClipId = clipId
    }

    override fun setHighlight(clipId: String?) {
        highlightClipId = clipId
    }
}

class FakeSourcePlaybackRepository : FakeMonitorPlayback(), SourcePlaybackRepository {
    var asset: MediaAsset? = null
        private set

    override fun load(asset: MediaAsset?, color: ColorManagement) {
        this.asset = asset
        mutableState.update { PlaybackState(durationMs = asset?.durationMs ?: 0, ready = asset != null) }
    }
}
