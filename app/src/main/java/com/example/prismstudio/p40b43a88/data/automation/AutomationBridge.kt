package com.example.prismstudio.p40b43a88.data.automation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Scripted commands for on-device QA (e.g. `key KEYCODE_2`, `open`). Only the debug build ships a
 * receiver that posts here, so release builds never receive commands.
 */
object AutomationBridge {
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    fun post(command: String) {
        _commands.tryEmit(command.trim())
    }
}
