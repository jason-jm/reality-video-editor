package com.example.prismstudio.p40b43a88.domain.model

/**
 * Resolve's Retime Process: how a clip makes the frames its footage does not have — a slowed clip, or footage slower
 * than the timeline (25 fps on 30). Nearest shows the frame before again; Frame Blend mixes the frames on either
 * side, by where the missing frame falls between them, so slow motion moves on every frame; Optical Flow follows the motion
 * between them ([OpticalFlow]), so what moves stays one sharp picture instead of two faint ones.
 */
enum class RetimeProcess(val label: String) {
    NEAREST("最近帧"),
    FRAME_BLEND("帧混合"),
    OPTICAL_FLOW("光流"),
}
