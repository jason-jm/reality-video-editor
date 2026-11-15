package com.example.prismstudio.p40b43a88.domain.usecase

import com.example.prismstudio.p40b43a88.domain.model.EditRejected
import com.example.prismstudio.p40b43a88.domain.model.MixerBus
import com.example.prismstudio.p40b43a88.domain.model.MixerChannel
import com.example.prismstudio.p40b43a88.domain.model.MixerState

/**
 * Fairlight's submix buses: up to [MixerState.MAX_BUSES] of them (总线 1 … 3); each audio track goes to one bus or straight to
 * the main output, and a bus that goes away hands its tracks back to the main output.
 */
class BusesUseCase {

    /** What an edit leaves: the mixer, the bus it concerns, and what to tell the user. */
    data class Result(val mixer: MixerState, val busId: String?, val message: String)

    /** A new bus with the lowest free number; with [trackId] (named [trackName]) that track goes to it. */
    fun add(mixer: MixerState, trackId: String? = null, trackName: String? = null): Result {
        if (mixer.buses.size >= MixerState.MAX_BUSES) throw EditRejected("最多 ${MixerState.MAX_BUSES} 条总线")
        val n = (1..MixerState.MAX_BUSES).first { k -> mixer.buses.none { it.id == "b$k" } }
        val bus = MixerBus(id = "b$n", name = "总线 $n")
        val added = mixer.copy(buses = (mixer.buses + bus).sortedBy { it.id })
        if (trackId == null) return Result(added, bus.id, "已添加${bus.name}：在轨道的名称上选择输出到它")
        return Result(route(added, trackId, bus.id), bus.id, "已添加${bus.name}，${trackName ?: trackId} 输出到它")
    }

    /** [trackId] (named [trackName]) goes to bus [busId], or to the main output for null. */
    fun assign(mixer: MixerState, trackId: String, trackName: String, busId: String?): Result {
        val bus = busId?.let { mixer.bus(it) ?: throw EditRejected("这条总线已不存在") }
        if (mixer.channel(trackId).bus == bus?.id) return Result(mixer, bus?.id, "$trackName 已经输出到${bus?.name ?: "主输出"}")
        return Result(route(mixer, trackId, bus?.id), bus?.id, "$trackName 输出到${bus?.name ?: "主输出"}")
    }

    /** Bus [busId] removed; its tracks (named by [names]) go back to the main output. */
    fun remove(mixer: MixerState, busId: String, names: Map<String, String>): Result {
        val bus = mixer.bus(busId) ?: throw EditRejected("这条总线已不存在")
        val members = mixer.membersOf(busId)
        val freed = mixer.copy(
            buses = mixer.buses.filter { it.id != busId },
            channels = mixer.channels.map { if (it.bus == busId) it.copy(bus = null) else it },
        )
        val back = if (members.isEmpty()) "" else "，${members.joinToString("、") { names[it] ?: it }} 回到主输出"
        return Result(freed, null, "已删除${bus.name}$back")
    }

    /** Bus [busId] changed by [change] (its fader at most +12 dB and down to silence). */
    fun update(mixer: MixerState, busId: String, change: (MixerBus) -> MixerBus): MixerState {
        mixer.bus(busId) ?: throw EditRejected("这条总线已不存在")
        return mixer.copy(buses = mixer.buses.map { b ->
            if (b.id != busId) b else change(b).let { it.copy(volumeDb = it.volumeDb.coerceIn(MixerState.FADER_FLOOR_DB, 12f)) }
        })
    }

    /**
     * What a bus strip says it carries: its tracks by name, or that none goes to it yet.
     */
    fun membersLabel(mixer: MixerState, busId: String, names: Map<String, String>): String =
        mixer.membersOf(busId).takeIf { it.isNotEmpty() }?.joinToString("、") { names[it] ?: it } ?: "没有轨道"

    private fun route(mixer: MixerState, trackId: String, busId: String?): MixerState =
        if (mixer.channels.any { it.trackId == trackId }) mixer.replace(mixer.channel(trackId).copy(bus = busId))
        else mixer.copy(channels = mixer.channels + MixerChannel(trackId, bus = busId))
}
