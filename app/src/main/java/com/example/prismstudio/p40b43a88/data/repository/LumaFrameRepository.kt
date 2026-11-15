package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.util.Log
import com.example.prismstudio.p40b43a88.data.engine.LumaPictures
import com.example.prismstudio.p40b43a88.data.engine.YuvVideoReader
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.usecase.LumaFrames
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Small luma pictures of a stretch of a video file's frames, for analysis (the window Tracker, scene cut detection). */
interface LumaFrameRepository {
    /**
     * The frames of [asset] from [fromMs] until [untilMs], each boxed down to [width] pixels wide (its height keeping the
     * picture's shape as it is shown); [onProgress] 0…1 as they are read. Throws when the file cannot be read.
     */
    suspend fun read(asset: MediaAsset, fromMs: Long, untilMs: Long, width: Int, onProgress: (Float) -> Unit = {}): LumaFrames {
        val frames = ArrayList<ByteArray>()
        val (w, h, rate, first) = stream(asset, fromMs, untilMs, width, onProgress) { frames += it.copyOf() }
        return LumaFrames(w, h, rate, first, frames)
    }

    /**
     * The same frames handed to [onFrame] one at a time as they are read (the array may be reused: copy what is kept), for
     * long stretches; returns the pictures' width and height, the frame rate and the first frame's index in the file.
     */
    suspend fun stream(asset: MediaAsset, fromMs: Long, untilMs: Long, width: Int, onProgress: (Float) -> Unit = {}, onFrame: (ByteArray) -> Unit): Stream

    /** What a [stream] read: picture size, frame rate, and the file frame the first picture was. */
    data class Stream(val width: Int, val height: Int, val frameRate: Double, val first: Int)

    object None : LumaFrameRepository {
        override suspend fun stream(asset: MediaAsset, fromMs: Long, untilMs: Long, width: Int, onProgress: (Float) -> Unit, onFrame: (ByteArray) -> Unit): Stream =
            throw IllegalStateException("无法读取画面")
    }
}

/** Hands out [frames] whatever is asked, for tests and previews; [asked] records the ranges. */
class FakeLumaFrameRepository(var frames: LumaFrames? = null) : LumaFrameRepository {
    val asked = mutableListOf<Pair<Long, Long>>()

    override suspend fun stream(asset: MediaAsset, fromMs: Long, untilMs: Long, width: Int, onProgress: (Float) -> Unit, onFrame: (ByteArray) -> Unit): LumaFrameRepository.Stream {
        asked += fromMs to untilMs
        val f = frames ?: throw IllegalStateException("无法读取画面")
        f.frames.forEach(onFrame)
        onProgress(1f)
        return LumaFrameRepository.Stream(f.width, f.height, f.frameRate, f.first)
    }
}

/** Reads with [YuvVideoReader] from the sync frame before the stretch, on a background thread. */
class AndroidLumaFrameRepository(context: Context) : LumaFrameRepository {
    private val app = context.applicationContext

    override suspend fun stream(asset: MediaAsset, fromMs: Long, untilMs: Long, width: Int, onProgress: (Float) -> Unit, onFrame: (ByteArray) -> Unit): LumaFrameRepository.Stream =
        withContext(Dispatchers.Default) {
            val began = System.nanoTime()
            val rotation = LumaPictures.rotationOf(app, asset.uri)
            val fps = if (asset.frameRate > 1f) asset.frameRate.toDouble() else 30.0
            val frameUs = 1_000_000.0 / fps
            var count = 0
            var first = -1
            var shown = 0 to 0
            YuvVideoReader(asset.uri, app).use { reader ->
                reader.seekTo(fromMs * 1000)
                while (true) {
                    ensureActive()
                    val frame = reader.next() ?: break
                    // Frames before the stretch come from the sync frame the seek landed on.
                    if (frame.ptsUs < fromMs * 1000 - frameUs / 2) continue
                    if (frame.ptsUs > untilMs * 1000 + frameUs / 2) break
                    if (first < 0) first = (frame.ptsUs / frameUs).roundToInt()
                    // Boxed down in the decoded orientation, then turned as the file is shown.
                    val turnedSideways = rotation % 180 != 0
                    val shownW = if (turnedSideways) frame.height else frame.width
                    val shownH = if (turnedSideways) frame.width else frame.height
                    val th = (width.toDouble() * shownH / shownW).roundToInt().coerceAtLeast(16)
                    val (dw, dh) = if (turnedSideways) th to width else width to th
                    val boxed = LumaPictures.boxed(frame.y, frame.width, frame.height, dw, dh)
                    val bytes = ByteArray(boxed.size) { boxed[it].roundToInt().coerceIn(0, 255).toByte() }
                    val (picture, w, h) = LumaPictures.turned(bytes, dw, dh, rotation)
                    shown = w to h
                    onFrame(picture)
                    count++
                    if (untilMs > fromMs) onProgress(((frame.ptsUs / 1000.0 - fromMs) / (untilMs - fromMs)).toFloat().coerceIn(0f, 1f))
                }
            }
            if (count == 0) throw IllegalStateException("${asset.name}: no frames between $fromMs and $untilMs ms")
            Log.i(TAG, "Read $count frames of ${asset.name} at ${shown.first}×${shown.second} in ${(System.nanoTime() - began) / 1_000_000} ms")
            LumaFrameRepository.Stream(shown.first, shown.second, fps, first)
        }

    private companion object {
        const val TAG = "PrismTrack"
    }
}
