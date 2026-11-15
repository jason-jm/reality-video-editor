package com.example.prismstudio.p40b43a88.data.repository

import com.example.prismstudio.p40b43a88.domain.usecase.VoiceTake

/** The microphone, recording one take at a time into the app's files. */
interface VoiceRecorderRepository {
    enum class Failure { MICROPHONE, STORAGE }

    /** The cause when starting or capturing failed; cleared on the next start. */
    val failure: Failure? get() = null

    /** Starts a take; false when the microphone cannot be used (no permission, busy, no device). */
    fun start(): Boolean

    /** The input's peak level since the last call, 0…1 (0 while not recording). */
    fun level(): Float

    /** Ends the take: the file, or null when nothing usable was recorded (the file is then removed). */
    fun stop(): VoiceTake?

    /** Ends the take and throws it away. */
    fun cancel()

    /** A recorder with no microphone (previews, tests). */
    object None : VoiceRecorderRepository {
        override fun start() = false
        override fun level() = 0f
        override fun stop(): VoiceTake? = null
        override fun cancel() = Unit
    }
}
