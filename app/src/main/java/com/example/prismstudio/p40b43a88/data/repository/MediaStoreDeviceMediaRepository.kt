package com.example.prismstudio.p40b43a88.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import com.example.prismstudio.p40b43a88.domain.model.SourceColor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.prismstudio.p40b43a88.domain.model.DeviceMediaItem
import com.example.prismstudio.p40b43a88.domain.model.MediaAsset
import com.example.prismstudio.p40b43a88.domain.model.MediaKind
import com.example.prismstudio.p40b43a88.domain.model.PosterColors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Lists device media through MediaStore and probes files with the platform media APIs. */
class MediaStoreDeviceMediaRepository(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DeviceMediaRepository {

    override val permissions: List<String> = listOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_IMAGES,
    )

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    override fun hasPermission(): Boolean =
        permissions.any(::granted) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    override suspend fun browse(): DeviceMediaResult = withContext(io) {
        if (!hasPermission()) return@withContext DeviceMediaResult.PermissionRequired
        runCatching {
            val visualSelected = granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            val allVideos = granted(Manifest.permission.READ_MEDIA_VIDEO)
            val allImages = granted(Manifest.permission.READ_MEDIA_IMAGES)
            val audio = granted(Manifest.permission.READ_MEDIA_AUDIO)
            val items = buildList {
                if (allVideos || visualSelected) addAll(query(MediaKind.VIDEO))
                if (audio) addAll(query(MediaKind.AUDIO))
                if (allImages || visualSelected) addAll(query(MediaKind.IMAGE))
            }
            val missing = buildSet {
                if (!allVideos && !visualSelected) add(MediaKind.VIDEO)
                if (!audio) add(MediaKind.AUDIO)
                if (!allImages && !visualSelected) add(MediaKind.IMAGE)
            }
            DeviceMediaResult.Items(items.sortedByDescending { it.second }.map { it.first }, missing,
                limitedVisualAccess = visualSelected && !(allImages && allVideos))
        }.getOrElse {
            Log.w(TAG, "MediaStore query failed", it)
            DeviceMediaResult.Failed("读取设备媒体时出错：${it.message ?: it.javaClass.simpleName}")
        }
    }

    private fun query(kind: MediaKind): List<Pair<DeviceMediaItem, Long>> {
        val collection = when (kind) {
            MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            MediaKind.IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (kind != MediaKind.IMAGE) add(MediaStore.MediaColumns.DURATION)
            if (kind == MediaKind.IMAGE) add(MediaStore.MediaColumns.ORIENTATION)
        }.toTypedArray()
        val result = mutableListOf<Pair<DeviceMediaItem, Long>>()
        context.contentResolver.query(
            collection, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val path = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val modified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val width = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val height = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val duration = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val orientation = c.getColumnIndex(MediaStore.MediaColumns.ORIENTATION)
            while (c.moveToNext() && result.size < MAX_ITEMS) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(id))
                // A photo stored on its side (EXIF orientation 90 / 270) shows, and decodes, upright: its size swaps.
                val turned = orientation >= 0 && c.getInt(orientation) % 180 != 0
                result += DeviceMediaItem(
                    id = "${kind.name.lowercase(Locale.ROOT)}-${c.getLong(id)}",
                    uri = uri.toString(),
                    name = c.getString(name) ?: "未命名",
                    kind = kind,
                    durationMs = if (duration >= 0) c.getLong(duration) else MediaAsset.IMAGE_DEFAULT_DURATION_MS,
                    sizeBytes = c.getLong(size),
                    relativePath = c.getString(path).orEmpty(),
                    width = c.getInt(if (turned) height else width),
                    height = c.getInt(if (turned) width else height),
                ) to c.getLong(modified)
            }
        }
        return result
    }

    override suspend fun probe(item: DeviceMediaItem, assetId: String): MediaAsset = withContext(io) {
        val uri = Uri.parse(item.uri)
        var width = item.width
        var height = item.height
        var durationMs = item.durationMs
        var hasAudio = item.kind == MediaKind.AUDIO
        var poster = PosterColors.NEUTRAL
        var frameRate = 0f
        var videoCodec = "—"
        var audioDescription = "无音频"
        var color = SourceColor.UNKNOWN
        if (item.kind == MediaKind.IMAGE) {
            durationMs = MediaAsset.IMAGE_DEFAULT_DURATION_MS
            try {
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    width = info.size.width
                    height = info.size.height
                    // Read the oriented source dimensions, then decode only enough pixels for the three poster bands.
                    decoder.setTargetSampleSize((maxOf(width, height) / 64).coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                try { poster = posterOf(bitmap) } finally { bitmap.recycle() }
            } catch (e: Exception) {
                throw UnreadableMediaException(item.name).apply { initCause(e) }
            }
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
                hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                if (item.kind == MediaKind.VIDEO) {
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (w != null && h != null) {
                        width = if (rotation % 180 == 0) w else h
                        height = if (rotation % 180 == 0) h else w
                    }
                    val at = (minOf(1_000L, durationMs / 2)).coerceAtLeast(0) * 1000
                    retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 48, 27)?.let {
                        poster = posterOf(it)
                        it.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot read metadata of ${item.name}", e)
            } finally {
                retriever.release()
            }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("video/") && videoCodec == "—") {
                        videoCodec = codecLabel(mime)
                        frameRate = frameRateOf(format)
                        color = colorOf(mime, format)
                    } else if (mime.startsWith("audio/") && audioDescription == "无音频") {
                        hasAudio = true
                        audioDescription = audioLabel(mime, format)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot inspect tracks of ${item.name}", e)
            } finally {
                extractor.release()
            }
            // Release round R15: a file with nothing to play (random bytes, an empty file, text named .mp3) was imported as
            // 1 ms of 0 × 0 picture; it is unreadable, and the import says so.
            val playable = if (item.kind == MediaKind.VIDEO) videoCodec != "—" else audioDescription != "无音频"
            if (!playable) throw UnreadableMediaException(item.name)
        }
        MediaAsset(
            id = assetId,
            uri = item.uri,
            name = item.name,
            kind = item.kind,
            durationMs = durationMs.coerceAtLeast(1),
            width = width,
            height = height,
            frameRate = frameRate,
            videoCodec = videoCodec,
            audioDescription = audioDescription,
            hasAudio = hasAudio && item.kind != MediaKind.IMAGE,
            sizeBytes = item.sizeBytes,
            relativePath = item.relativePath,
            poster = poster,
            color = color,
        )
    }

    /** The video track's colour tags (primaries, HLG / PQ transfer) and whether its profile is a 10-bit one. */
    private fun colorOf(mime: String, format: MediaFormat): SourceColor {
        fun int(key: String): Int? = if (format.containsKey(key)) runCatching { format.getInteger(key) }.getOrNull() else null
        val profile = int(MediaFormat.KEY_PROFILE)
        val tenBit = when (mime) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> profile in setOf(
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )
            MediaFormat.MIMETYPE_VIDEO_VP9 -> profile in setOf(
                MediaCodecInfo.CodecProfileLevel.VP9Profile2, MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR,
                MediaCodecInfo.CodecProfileLevel.VP9Profile3, MediaCodecInfo.CodecProfileLevel.VP9Profile3HDR,
            )
            MediaFormat.MIMETYPE_VIDEO_AV1 -> profile in setOf(
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10, MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
                MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus,
            )
            else -> false
        }
        val transfer = int(MediaFormat.KEY_COLOR_TRANSFER)
        return SourceColor.fromMediaFormat(int(MediaFormat.KEY_COLOR_STANDARD), transfer, tenBit || transfer == MediaFormat.COLOR_TRANSFER_HLG || transfer == MediaFormat.COLOR_TRANSFER_ST2084)
    }

    override suspend fun isAvailable(uri: String): Boolean = withContext(io) {
        runCatching { context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false }.getOrDefault(false)
    }

    override suspend fun describe(uri: String): DeviceMediaItem? = withContext(io) {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver
        val kind = when (resolver.getType(parsed)?.substringBefore('/')) {
            "video" -> MediaKind.VIDEO
            "audio" -> MediaKind.AUDIO
            "image" -> MediaKind.IMAGE
            else -> return@withContext null
        }
        runCatching {
            resolver.query(parsed, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                fun text(column: String) = c.getColumnIndex(column).takeIf { it >= 0 }?.let { c.getString(it) }
                fun number(column: String) = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
                DeviceMediaItem(
                    id = "picked-${uri.hashCode()}",
                    uri = uri,
                    name = text(OpenableColumns.DISPLAY_NAME) ?: parsed.lastPathSegment ?: "未命名",
                    kind = kind,
                    // The probe reads the length and picture size from the file itself.
                    durationMs = number(MediaStore.MediaColumns.DURATION) ?: MediaAsset.IMAGE_DEFAULT_DURATION_MS,
                    sizeBytes = number(OpenableColumns.SIZE) ?: 0L,
                    // A media document has its folder; other providers' files show without one.
                    relativePath = text(MediaStore.MediaColumns.RELATIVE_PATH).orEmpty(),
                    width = 0,
                    height = 0,
                )
            }
        }.onFailure { Log.w(TAG, "Cannot describe the picked file", it) }.getOrNull()
    }

    private fun frameRateOf(format: MediaFormat): Float = runCatching {
        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) return@runCatching 0f
        runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
            .getOrElse { format.getFloat(MediaFormat.KEY_FRAME_RATE) }
    }.getOrDefault(0f).let { if (it > 0f) (it * 100).toInt() / 100f else 0f }

    private fun posterOf(bitmap: Bitmap): PosterColors {
        fun band(from: Int, to: Int): Long {
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (y in from until maxOf(from + 1, to)) for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            }
            if (n == 0L) return 0xFF22252EL
            return (0xFFL shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        val h = bitmap.height
        return PosterColors(band(0, h / 3), band(h / 3, 2 * h / 3), band(2 * h / 3, bitmap.height))
    }

    companion object {
        private const val TAG = "PrismMedia"
        private const val MAX_ITEMS = 400

        fun codecLabel(mime: String): String = when (mime) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> "HEVC (H.265)"
            MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264"
            MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
            MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
            MediaFormat.MIMETYPE_VIDEO_VP8 -> "VP8"
            MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "MPEG-4"
            else -> mime.removePrefix("video/").uppercase(Locale.ROOT)
        }

        fun audioLabel(mime: String, format: MediaFormat): String {
            val codec = when (mime) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC"
                MediaFormat.MIMETYPE_AUDIO_MPEG -> "MP3"
                MediaFormat.MIMETYPE_AUDIO_OPUS -> "Opus"
                MediaFormat.MIMETYPE_AUDIO_FLAC -> "FLAC"
                MediaFormat.MIMETYPE_AUDIO_RAW -> "PCM"
                MediaFormat.MIMETYPE_AUDIO_VORBIS -> "Vorbis"
                else -> mime.removePrefix("audio/").uppercase(Locale.ROOT)
            }
            val rate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull()
            val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull()
            val layout = when (channels) {
                null -> ""
                1 -> " 单声道"
                2 -> " 立体声"
                else -> " $channels 声道"
            }
            // Exact, trailing zeros dropped: 48, 44.1, 22.05, 11.025 kHz (a one-decimal round read 22.05 as "22.0", R16).
            val khz = rate?.let { " ${java.math.BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString()} kHz" }.orEmpty()
            return "$codec$khz$layout"
        }
    }
}

/** A device file with nothing the app can play or show (no video or audio track, or an image that does not decode). */
class UnreadableMediaException(name: String) : java.io.IOException("Unreadable media: $name")
