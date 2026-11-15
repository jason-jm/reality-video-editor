package com.example.prismstudio.p40b43a88.data.repository

import android.content.Context
import android.util.Log
import com.example.prismstudio.p40b43a88.data.engine.LumaPictures
import com.example.prismstudio.p40b43a88.data.engine.YuvVideoReader
import com.example.prismstudio.p40b43a88.domain.model.CameraAnalysis
import com.example.prismstudio.p40b43a88.domain.model.CameraPath
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.MotionEstimator
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Each video file's camera path ([CameraPath]), which a stabilized clip moves its frames against. Analysing a file reads
 * all of its frames once; the result is kept on disk, so a file is analysed only once.
 */
interface StabilizationRepository {
    /** Where each file's analysis stands, by file URI (absent: never asked for). */
    val analyses: StateFlow<Map<String, CameraAnalysis>>

    /** [asset]'s camera path, once analysed. */
    fun path(asset: MediaAsset): CameraPath? = (analyses.value[asset.uri] as? CameraAnalysis.Ready)?.path

    /** The paths analysed so far, by URI. */
    fun paths(): Map<String, CameraPath> = analyses.value.mapNotNull { (uri, a) -> (a as? CameraAnalysis.Ready)?.let { uri to it.path } }.toMap()

    /** Analyses, in the background, the files not analysed yet ([retryFailed]: those that failed too). */
    fun request(assets: Collection<MediaAsset>, retryFailed: Boolean = false)

    /** Analyses the files not analysed yet before returning (a render waits for them). */
    suspend fun await(assets: Collection<MediaAsset>)

    object None : StabilizationRepository {
        override val analyses: StateFlow<Map<String, CameraAnalysis>> = MutableStateFlow(emptyMap())
        override fun request(assets: Collection<MediaAsset>, retryFailed: Boolean) = Unit
        override suspend fun await(assets: Collection<MediaAsset>) = Unit
    }
}

/** Set by hand, for tests and previews: [set] puts an analysis in place; [requests] records what was asked for. */
class FakeStabilizationRepository : StabilizationRepository {
    private val state = MutableStateFlow<Map<String, CameraAnalysis>>(emptyMap())
    override val analyses: StateFlow<Map<String, CameraAnalysis>> = state.asStateFlow()
    val requests = mutableListOf<String>()

    fun set(uri: String, analysis: CameraAnalysis) = state.update { it + (uri to analysis) }

    override fun request(assets: Collection<MediaAsset>, retryFailed: Boolean) {
        requests += assets.map { it.uri }
    }

    override suspend fun await(assets: Collection<MediaAsset>) = Unit
}

/**
 * Analyses with [YuvVideoReader], one file at a time on a background thread: each frame's luma, boxed down to a
 * [MotionEstimator.ANALYSIS_WIDTH]-wide picture, is lined up with the previous frame's. Paths are kept in the cache
 * directory, named after the file's URI and duration.
 */
class AndroidStabilizationRepository(context: Context, private val scope: CoroutineScope) : StabilizationRepository {

    private val app = context.applicationContext
    private val dir = File(app.cacheDir, "stabilization").apply { mkdirs() }
    private val lock = Mutex()
    private val state = MutableStateFlow<Map<String, CameraAnalysis>>(emptyMap())
    override val analyses: StateFlow<Map<String, CameraAnalysis>> = state.asStateFlow()

    override fun request(assets: Collection<MediaAsset>, retryFailed: Boolean) {
        val wanted = candidates(assets).filter { a ->
            when (state.value[a.uri]) {
                null -> true
                is CameraAnalysis.Failed -> retryFailed
                else -> false
            }
        }
        if (wanted.isEmpty()) return
        state.update { m -> m + wanted.associate { it.uri to CameraAnalysis.Analysing(0f) } }
        scope.launch(Dispatchers.Default) { wanted.forEach { analyse(it) } }
    }

    override suspend fun await(assets: Collection<MediaAsset>) {
        val wanted = candidates(assets)
        request(wanted)
        for (asset in wanted) state.first { it[asset.uri] !is CameraAnalysis.Analysing }
    }

    private fun candidates(assets: Collection<MediaAsset>) =
        assets.filter { it.kind == MediaKind.VIDEO && !it.offline }.distinctBy { it.uri }

    private suspend fun analyse(asset: MediaAsset) = lock.withLock {
        if (state.value[asset.uri] is CameraAnalysis.Ready) return@withLock
        val file = File(dir, hash("${asset.uri}|${asset.durationMs}|$FORMAT_VERSION") + ".bin")
        val result = try {
            val path = readCached(file) ?: read(asset).also { writeCached(file, it) }
            CameraAnalysis.Ready(path)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot analyse ${asset.name}", e)
            CameraAnalysis.Failed("无法读取这个视频的画面")
        }
        state.update { it + (asset.uri to result) }
    }

    /** Reads every frame of [asset] and adds up the content's shift from each to the next, turned as the file shows. */
    private fun read(asset: MediaAsset): CameraPath {
        val began = System.nanoTime()
        val rotation = LumaPictures.rotationOf(app, asset.uri)
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        var previous: FloatArray? = null
        var estimator: MotionEstimator? = null
        var size = 0 to 0
        var lastPts = 0L
        var lastReport = 0L
        YuvVideoReader(asset.uri, app).use { reader ->
            while (true) {
                val frame = reader.next() ?: break
                if (estimator == null) {
                    size = MotionEstimator.analysisSize(frame.width, frame.height)
                    estimator = MotionEstimator(size.first, size.second)
                }
                // 0…1 luma, boxed down to the analysis size.
                val luma = LumaPictures.boxed(frame.y, frame.width, frame.height, size.first, size.second).also { l -> for (k in l.indices) l[k] /= 255f }
                val prev = previous
                if (prev == null) {
                    xs += 0f; ys += 0f
                } else {
                    val (dx, dy) = estimator!!.shift(prev, luma)
                    // Shifts in the decoded picture, turned as the file is shown, as shares of the shown picture.
                    val (sx, sy, w, h) = turned(dx, dy, size.first.toFloat(), size.second.toFloat(), rotation)
                    xs += xs.last() + sx / w
                    ys += ys.last() + sy / h
                }
                previous = luma
                lastPts = frame.ptsUs
                if (asset.durationMs > 0 && lastPts - lastReport > REPORT_US) {
                    lastReport = lastPts
                    val progress = (lastPts / 1000f / asset.durationMs).coerceIn(0f, 0.99f)
                    state.update { it + (asset.uri to CameraAnalysis.Analysing(progress)) }
                }
            }
        }
        if (xs.size < 2) throw IllegalStateException("${asset.name}: fewer than two frames")
        // The file's own rate where it is known, else what its timestamps say.
        val fps = if (asset.frameRate > 1f) asset.frameRate.toDouble() else (xs.size - 1) / (lastPts / 1e6).coerceAtLeast(1e-3)
        Log.i(TAG, "Camera path of ${asset.name}: ${xs.size} frames in ${(System.nanoTime() - began) / 1_000_000} ms")
        return CameraPath(fps, xs.toFloatArray(), ys.toFloatArray())
    }

    /**
     * A shift ([dx], [dy]) in a decoded picture [w] × [h] as it shows once turned [rotation] degrees clockwise, with the
     * shown picture's width and height.
     */
    private fun turned(dx: Float, dy: Float, w: Float, h: Float, rotation: Int): Turned = when ((rotation % 360 + 360) % 360) {
        90 -> Turned(-dy, dx, h, w)
        180 -> Turned(-dx, -dy, w, h)
        270 -> Turned(dy, -dx, h, w)
        else -> Turned(dx, dy, w, h)
    }

    private data class Turned(val x: Float, val y: Float, val width: Float, val height: Float)

    private fun readCached(file: File): CameraPath? = runCatching {
        if (!file.isFile) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            val fps = input.readDouble()
            val n = input.readInt()
            if (n < 2 || n > MAX_FRAMES) return null
            val x = FloatArray(n) { input.readFloat() }
            val y = FloatArray(n) { input.readFloat() }
            CameraPath(fps, x, y)
        }
    }.getOrNull()

    private fun writeCached(file: File, path: CameraPath) {
        runCatching {
            DataOutputStream(file.outputStream().buffered()).use { out ->
                out.writeDouble(path.frameRate)
                out.writeInt(path.frames)
                path.x.forEach(out::writeFloat)
                path.y.forEach(out::writeFloat)
            }
        }.onFailure { Log.w(TAG, "Cannot keep the camera path ${file.name}", it) }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "PrismStabilize"
        const val FORMAT_VERSION = 1
        const val REPORT_US = 250_000L
        const val MAX_FRAMES = 2_000_000
    }
}
