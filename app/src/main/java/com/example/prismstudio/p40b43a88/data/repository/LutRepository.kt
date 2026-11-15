package com.example.prismstudio.p40b43a88.data.repository

import android.util.Log
import com.example.prismstudio.p40b43a88.domain.model.CubeLut
import com.example.prismstudio.p40b43a88.domain.model.LutInfo
import com.example.prismstudio.p40b43a88.domain.model.LutSource
import com.example.prismstudio.p40b43a88.domain.usecase.CubeLutParser
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What an import did: the LUT, and whether the library had it already. */
data class LutImport(val info: LutInfo, val alreadyThere: Boolean)

/**
 * The headset's LUT library, shared by every project like Resolve's LUT folder: imported `.cube` files,
 * parsed once. Grades name a LUT by its id (taken from its content); [cube] serves the grading code.
 */
interface LutRepository : LutSource {
    /** The imported LUTs, oldest first. */
    val library: StateFlow<List<LutInfo>>

    /** Adds the `.cube` file [fileName] holding [bytes]; throws [com.example.prismstudio.p40b43a88.domain.usecase.LutFormatException]. */
    suspend fun import(fileName: String, bytes: ByteArray): LutImport

    /** Takes the LUT [id] out of the library; grades naming it then show it missing. */
    suspend fun remove(id: String)
}

/** A library kept in memory: for tests and previews. */
class InMemoryLutRepository : LutRepository {
    private val cubes = mutableMapOf<String, CubeLut>()
    private val mutable = MutableStateFlow<List<LutInfo>>(emptyList())
    private val generations = AtomicInteger()

    override val library: StateFlow<List<LutInfo>> = mutable.asStateFlow()
    override val generation: Int get() = generations.get()

    override fun cube(id: String): CubeLut? = synchronized(cubes) { cubes[id] }

    override suspend fun import(fileName: String, bytes: ByteArray): LutImport {
        val parsed = CubeLutParser.parse(CubeLutParser.decode(bytes), fileName)
        mutable.value.firstOrNull { it.id == parsed.id }?.let { return LutImport(it, alreadyThere = true) }
        val info = LutInfo(parsed.id, parsed.name, parsed.lut.size, parsed.lut.threeD)
        synchronized(cubes) { cubes[parsed.id] = parsed.lut }
        mutable.update { it + info }
        generations.incrementAndGet()
        return LutImport(info, alreadyThere = false)
    }

    override suspend fun remove(id: String) {
        if (mutable.value.none { it.id == id }) return
        synchronized(cubes) { cubes.remove(id) }
        mutable.update { list -> list.filterNot { it.id == id } }
        generations.incrementAndGet()
    }
}

/**
 * The library in [dir]: one `<id>.plut` file per LUT — a header (format, size, domain, import time,
 * name) and the entries as 32-bit floats — so a 65³ cube loads in milliseconds instead of being parsed
 * again. The last few cubes used stay in memory.
 */
class FileLutRepository(
    private val dir: File,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LutRepository {

    private val mutable = MutableStateFlow<List<LutInfo>>(emptyList())
    private val generations = AtomicInteger()
    private val cache = object : LinkedHashMap<String, CubeLut>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CubeLut>): Boolean = size > CACHED_CUBES
    }

    override val library: StateFlow<List<LutInfo>> = mutable.asStateFlow()
    override val generation: Int get() = generations.get()

    init {
        scope.launch(io) {
            val found = dir.listFiles { f -> f.name.endsWith(SUFFIX) }.orEmpty()
                .mapNotNull { f -> runCatching { readHeader(f.name.removeSuffix(SUFFIX), ByteBuffer.wrap(f.readBytes())) }.getOrNull() }
                .sortedBy { it.second }
                .map { it.first }
            mutable.value = found
            generations.incrementAndGet()
        }
    }

    override fun cube(id: String): CubeLut? {
        synchronized(cache) { cache[id]?.let { return it } }
        val file = File(dir, id + SUFFIX)
        if (!file.isFile) return null
        val cube = try {
            read(ByteBuffer.wrap(file.readBytes()))
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read LUT $id", e)
            return null
        }
        synchronized(cache) { cache[id] = cube }
        return cube
    }

    override suspend fun import(fileName: String, bytes: ByteArray): LutImport {
        val parsed = withContext(Dispatchers.Default) { CubeLutParser.parse(CubeLutParser.decode(bytes), fileName) }
        val info = LutInfo(parsed.id, parsed.name, parsed.lut.size, parsed.lut.threeD)
        mutable.value.firstOrNull { it.id == parsed.id }?.let { return LutImport(it, alreadyThere = true) }
        withContext(io) {
            dir.mkdirs()
            val target = File(dir, parsed.id + SUFFIX)
            val temp = File(dir, parsed.id + ".tmp")
            temp.writeBytes(encode(info, parsed.lut, System.currentTimeMillis()))
            if (!temp.renameTo(target)) {
                temp.delete()
                throw java.io.IOException("Cannot store LUT ${parsed.id}")
            }
        }
        synchronized(cache) { cache[parsed.id] = parsed.lut }
        mutable.update { list -> list.filterNot { it.id == info.id } + info }
        generations.incrementAndGet()
        Log.i(TAG, "Imported LUT ${info.id} '${info.name}' ${info.detail}")
        return LutImport(info, alreadyThere = false)
    }

    override suspend fun remove(id: String) {
        if (mutable.value.none { it.id == id }) return
        withContext(io) { File(dir, id + SUFFIX).delete() }
        synchronized(cache) { cache.remove(id) }
        mutable.update { list -> list.filterNot { it.id == id } }
        generations.incrementAndGet()
        Log.i(TAG, "Removed LUT $id")
    }

    companion object {
        private const val TAG = "PrismLuts"
        private const val SUFFIX = ".plut"
        private const val MAGIC = 0x504C5554 // "PLUT"
        private const val VERSION = 1
        private const val CACHED_CUBES = 6

        internal fun encode(info: LutInfo, lut: CubeLut, importedAt: Long): ByteArray {
            val name = info.name.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(4 * 4 + 6 * 4 + 8 + 4 + name.size + 4 * lut.data.size)
            buffer.putInt(MAGIC).putInt(VERSION).putInt(if (lut.threeD) 3 else 1).putInt(lut.size)
            lut.domainMin.forEach { buffer.putFloat(it) }
            lut.domainMax.forEach { buffer.putFloat(it) }
            buffer.putLong(importedAt).putInt(name.size).put(name)
            buffer.asFloatBuffer().put(lut.data)
            return buffer.array()
        }

        /** The library entry (the id is the file's name) and import time of a stored LUT. */
        internal fun readHeader(id: String, buffer: ByteBuffer): Pair<LutInfo, Long> {
            require(buffer.getInt() == MAGIC && buffer.getInt() == VERSION) { "not a stored LUT" }
            val threeD = buffer.getInt() == 3
            val size = buffer.getInt()
            buffer.position(buffer.position() + 6 * 4)
            val importedAt = buffer.getLong()
            val name = ByteArray(buffer.getInt()).also { buffer.get(it) }.toString(Charsets.UTF_8)
            return LutInfo(id = id, name = name, size = size, threeD = threeD) to importedAt
        }

        internal fun read(buffer: ByteBuffer): CubeLut {
            require(buffer.getInt() == MAGIC && buffer.getInt() == VERSION) { "not a stored LUT" }
            val threeD = buffer.getInt() == 3
            val size = buffer.getInt()
            val min = FloatArray(3) { buffer.getFloat() }
            val max = FloatArray(3) { buffer.getFloat() }
            buffer.getLong()
            val nameLength = buffer.getInt()
            buffer.position(buffer.position() + nameLength)
            val data = FloatArray(3 * if (threeD) size * size * size else size)
            buffer.asFloatBuffer().get(data)
            return CubeLut(size, threeD, data, min, max)
        }
    }
}
