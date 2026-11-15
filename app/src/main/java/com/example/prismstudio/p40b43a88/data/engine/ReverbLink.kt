package com.example.prismstudio.p40b43a88.data.engine

import com.example.prismstudio.p40b43a88.domain.model.ReverbTank
import kotlin.math.abs

/**
 * One sound lane's reverb, handed from item to item: an item that ends puts its ringing tank here with the lane time it
 * ended at, and the next item takes it when it starts right there at the same sample rate — so a cut between two clips
 * does not start the room over cold. An item that starts anywhere else (a seek, a different rate) starts a silent room.
 * The lane's submix bus EQ is handed on the same way: a bus hears its tracks without a break at a cut, so its filters carry
 * their memory across instead of starting over (rev 155).
 */
class ReverbLink {
    private var tank: ReverbTank? = null
    private var endUs = 0L
    private var eqMemory: DoubleArray? = null
    private var eqRate = 0
    private var eqEndUs = 0L

    /** The bus EQ's filter memory as an item ended at [laneEndUs]. */
    @Synchronized
    fun putEq(memory: DoubleArray, sampleRate: Int, laneEndUs: Long) {
        eqMemory = memory
        eqRate = sampleRate
        eqEndUs = laneEndUs
    }

    /** The bus EQ memory left at [laneStartUs] at [sampleRate], once; null when none was. */
    @Synchronized
    fun takeEq(sampleRate: Int, laneStartUs: Long): DoubleArray? {
        val m = eqMemory ?: return null
        if (eqRate != sampleRate || abs(eqEndUs - laneStartUs) > TOLERANCE_US) return null
        eqMemory = null
        return m
    }

    @Synchronized
    fun put(tank: ReverbTank, laneEndUs: Long) {
        this.tank = tank
        endUs = laneEndUs
    }

    /** The tank left at [laneStartUs] at [sampleRate], once; null when none was. */
    @Synchronized
    fun take(sampleRate: Int, laneStartUs: Long): ReverbTank? {
        val t = tank ?: return null
        if (t.sampleRate != sampleRate || abs(endUs - laneStartUs) > TOLERANCE_US) return null
        tank = null
        return t
    }

    private companion object {
        /**
         * A render's items meet to the sample, but the preview's are not trimmed: one may run an AAC frame (~23 ms) past its
         * planned end. Within this the next item counts as starting where the last ended.
         */
        const val TOLERANCE_US = 60_000L
    }
}
