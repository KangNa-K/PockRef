package com.pockref.app.data

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class PurFileParser {

    data class PurHeader(
        val formatVersion: String,
        val sqliteOffset: Long,
        val appVersion: String,
        val uuid: String,
        val thumbnailSize: Int = 0,
        val thumbnailOffset: Int = 0,
        val numImages: Int = 0,
        val canvasX: Double = 0.0,
        val canvasY: Double = 0.0,
        val canvasW: Double = 0.0,
        val canvasH: Double = 0.0,
        val zoom: Double = 1.0,
        val headerSize: Int = 0xE0,
    )

    // RAF for the currently-open .pur file. Set by [bindSourceFile]
    // after parseFromFile succeeds. Used by [readImageBytes] and
    // [openImageInputStream] for lazy decoding.
    @Volatile
    private var dbFile: RandomAccessFile? = null

    @Volatile
    private var dbTempFile: File? = null

    /**
     * Bind a RandomAccessFile to this parser for lazy byte reading.
     * Called by the scene-loading code after parseFromFile succeeds.
     * The caller is responsible for closing the RAF (typically via
     * SceneImageDecoder.release()).
     */
    fun bindSourceFile(file: File) {
        // Close previous binding if any
        try { dbFile?.close() } catch (_: Exception) {}
        dbFile = RandomAccessFile(file, "r")
        dbTempFile = null
    }

    fun parse(input: InputStream): PockRefScene {
        val inputFile = File.createTempFile("pockref_input_", ".tmp")
        inputFile.deleteOnExit()
        try {
            inputFile.outputStream().use { out -> input.copyTo(out, 65536) }
            return parseFromFile(inputFile, deleteAfter = true)
        } finally {
            inputFile.delete()
        }
    }

    fun parseFromFile(file: File, deleteAfter: Boolean = false): PockRefScene {
        val inputRaf = RandomAccessFile(file, "r")
        try {
            val headerBytes = ByteArray(minOf(file.length().toInt(), 0x100))
            inputRaf.readFully(headerBytes)
            val header = parseHeader(headerBytes, file.length())

            val scene = parseV1Format(inputRaf, file, header)
            return scene
        } finally {
            inputRaf.close()
        }
    }

    fun readImageBytes(imageByteOffset: Int, imageByteLength: Int): ByteArray? {
        if (imageByteOffset < 0 || imageByteLength <= 0) return null
        val file = dbFile ?: return null
        return try {
            synchronized(file) {
                file.seek(imageByteOffset.toLong())
                val bytes = ByteArray(imageByteLength)
                file.readFully(bytes)
                bytes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read image bytes from file", e)
            null
        }
    }

    fun openImageInputStream(imageByteOffset: Int, imageByteLength: Int): InputStream? {
        if (imageByteOffset < 0 || imageByteLength <= 0) return null
        dbFile?.let { file ->
            return RandomAccessFileRegionInputStream(file, imageByteOffset.toLong(), imageByteLength.toLong())
        }
        return null
    }

    // ── Decode methods removed ─────────────────────────────────
    // decodeImageBitmap, decodeImageBitmapSafe, decodeLargeImageBitmap,
    // decodeSampledBitmap, and calculateSampleSize have been moved to
    // SceneImageDecoder.kt. That class uses BitmapRegionDecoder instead
    // of temp files and adds a disk cache + decode ladder.

    fun release() {
        try {
            dbFile?.close()
        } catch (_: Exception) {}
        dbFile = null
        try {
            dbTempFile?.delete()
        } catch (_: Exception) {}
        dbTempFile = null
    }

    /**
     * The currently-bound source RAF. Exposed so SceneImageDecoder can
     * use the same file handle for BitmapRegionDecoder creation.
     */
    val sourceRaf: RandomAccessFile? get() = dbFile

    fun parseHeader(bytes: ByteArray, fileLength: Long = 0): PurHeader {
        require(bytes.size >= 8) {
            "File too small to be a .pur file (${bytes.size} bytes)"
        }

        var pos = 0
        val versionByteLen = readUInt32BE(bytes, pos).toInt()
        pos += 4
        require(versionByteLen in 2..100 && pos + versionByteLen <= bytes.size) {
            "Invalid version length: $versionByteLen"
        }
        val formatVersion = decodeUtf16BE(bytes, pos, versionByteLen)
        pos += versionByteLen

        return parseV1Header(bytes, fileLength, pos, formatVersion)
    }

    private fun parseV1Header(bytes: ByteArray, fileLength: Long, startPos: Int, formatVersion: String): PurHeader {
        var pos = startPos

        val numImages = if (pos + 2 <= bytes.size) readUInt16BE(bytes, pos) else 0
        pos += 2
        pos += 2

        val indexOffset = if (pos + 8 <= bytes.size) readUInt64BE(bytes, pos) else 0L
        pos += 8

        val appVersionByteLen = if (pos + 4 <= bytes.size) readUInt32BE(bytes, pos).toInt() else 0
        pos += 4
        val appVersion = if (appVersionByteLen in 2..100 && pos + appVersionByteLen <= bytes.size) {
            decodeUtf16BE(bytes, pos, appVersionByteLen)
        } else ""
        pos += appVersionByteLen

        val uuidByteLen = if (pos + 4 <= bytes.size) readUInt32BE(bytes, pos).toInt() else 0
        pos += 4
        val uuid = if (uuidByteLen in 2..200 && pos + uuidByteLen <= bytes.size) {
            decodeUtf16BE(bytes, pos, uuidByteLen)
        } else ""
        pos += uuidByteLen

        var canvasX = 0.0
        var canvasY = 0.0
        var canvasW = 0.0
        var canvasH = 0.0
        var zoom = 1.0
        var thumbnailSize = 0
        var thumbnailOffset = 0

        if (pos + 4 <= bytes.size) {
            val numImagesAgain = readUInt32BE(bytes, pos).toInt()
            pos += 4

            canvasX = if (pos + 8 <= bytes.size) readDoubleBE(bytes, pos) else 0.0
            pos += 8
            canvasY = if (pos + 8 <= bytes.size) readDoubleBE(bytes, pos) else 0.0
            pos += 8
            canvasW = if (pos + 8 <= bytes.size) readDoubleBE(bytes, pos) else 0.0
            pos += 8
            canvasH = if (pos + 8 <= bytes.size) readDoubleBE(bytes, pos) else 0.0
            pos += 8
            zoom = if (pos + 8 <= bytes.size) readDoubleBE(bytes, pos) else 1.0
            pos += 8

            pos += 8
            pos += 8
            pos += 8
            pos += 8

            val headerEnd = 0xE0
            if (pos < headerEnd) {
                thumbnailOffset = pos
                thumbnailSize = headerEnd - pos
            }
        }

        return PurHeader(
            formatVersion = formatVersion,
            sqliteOffset = indexOffset,
            appVersion = appVersion,
            uuid = uuid,
            thumbnailSize = thumbnailSize,
            thumbnailOffset = thumbnailOffset,
            numImages = numImages,
            canvasX = canvasX,
            canvasY = canvasY,
            canvasW = canvasW,
            canvasH = canvasH,
            zoom = zoom,
            headerSize = 0xE0,
        )
    }

    private fun readDoubleBE(bytes: ByteArray, offset: Int): Double {
        if (offset + 8 > bytes.size) return 0.0
        val buf = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN)
        return buf.double
    }

    private fun parseV1Format(raf: RandomAccessFile, file: File, header: PurHeader): PockRefScene {
        val fileLength = file.length()
        val indexOffset = header.sqliteOffset
        val numImages = header.numImages

        if (fileLength < 220L) {
            Log.w(TAG, "File too small for V1 format, returning empty scene")
            // [name=""] is intentional: the V1 .pur format doesn't
            // store a scene-name field, so the parser returns blank
            // and lets the [loadScene] call site fall back to the
            // file's display name via [SceneNaming.fromPurFileName].
            // Returning the placeholder "PockRef Import" here would
            // win over the file-name fallback in the `ifBlank` chain
            // and stick the user with a non-descriptive title. See
            // [PureRefViewModelScene.loadScene] for the fallback.
            return PockRefScene(
                version = 1,
                name = "",
                backgroundColor = Color(0xFF111111),
                images = emptyList(),
            )
        }

        val indexSize = if (indexOffset in 1 until fileLength) (fileLength - indexOffset).toInt() else 0

        val desktopEntrySize = 12
        val legacyEntrySize = 20
        val desktopEntries = if (indexSize > 0) indexSize / desktopEntrySize else 0
        val legacyEntries = if (indexSize > 0) indexSize / legacyEntrySize else 0

        val entrySize: Int
        val actualImages: Int
        val isLegacyIndex: Boolean

        if (legacyEntries == numImages && legacyEntries > 0) {
            entrySize = legacyEntrySize
            actualImages = legacyEntries
            isLegacyIndex = true
        } else if (desktopEntries == numImages && desktopEntries > 0) {
            entrySize = desktopEntrySize
            actualImages = desktopEntries
            isLegacyIndex = false
        } else if (desktopEntries > 0 && indexSize % desktopEntrySize == 0) {
            entrySize = desktopEntrySize
            actualImages = desktopEntries
            isLegacyIndex = false
        } else if (legacyEntries > 0 && indexSize % legacyEntrySize == 0) {
            entrySize = legacyEntrySize
            actualImages = legacyEntries
            isLegacyIndex = true
        } else {
            entrySize = desktopEntrySize
            actualImages = desktopEntries
            isLegacyIndex = false
        }

        if (actualImages <= 0) {
            Log.w(TAG, "V1 index area empty, returning empty scene")
            // [name=""] — same rationale as the file-too-small
            // branch above: V1 .pur has no name field, the call
            // site derives the display name from the file name.
            return PockRefScene(
                version = 1,
                name = "",
                backgroundColor = Color(0xFF111111),
                images = emptyList(),
            )
        }

        val indexData = ByteArray(indexSize)
        raf.seek(indexOffset)
        raf.readFully(indexData)

        data class IndexEntry(val id: Int, val start: Long, val end: Long)

        val indexEntries = mutableListOf<IndexEntry>()
        for (i in 0 until actualImages) {
            val entryStart = i * entrySize
            val startOffset: Long
            val endOffset: Long
            val id: Int
            if (entrySize == legacyEntrySize) {
                id = readUInt32BE(indexData, entryStart).toInt()
                startOffset = readUInt64BE(indexData, entryStart + 4)
                endOffset = readUInt64BE(indexData, entryStart + 12)
            } else {
                id = readUInt32BE(indexData, entryStart).toInt()
                startOffset = readUInt32BE(indexData, entryStart + 4)
                endOffset = readUInt32BE(indexData, entryStart + 8)
            }
            if (startOffset in 1 until fileLength && endOffset > startOffset && endOffset <= fileLength) {
                indexEntries.add(IndexEntry(id, startOffset, endOffset))
            } else {
                Log.w(TAG, "V1 image $i: invalid offsets start=$startOffset end=$endOffset")
            }
        }

        val transforms = if (isLegacyIndex && indexEntries.isNotEmpty()) {
            val lastImgEnd = indexEntries.maxOf { it.end }
            val transformSize = (indexOffset - lastImgEnd).toInt()
            if (transformSize > 0) {
                try {
                    raf.seek(lastImgEnd)
                    val transformData = ByteArray(transformSize)
                    raf.readFully(transformData)
                    parseLegacyTransforms(transformData, numImages, lastImgEnd)
                } catch (e: Exception) {
                    Log.w(TAG, "V1: failed to parse legacy transforms", e)
                    emptyList<LegacyTransform>()
                }
            } else {
                emptyList<LegacyTransform>()
            }
        } else {
            emptyList<LegacyTransform>()
        }

        // NOTE: the caller (PurSceneCodec.readFromFile or SceneImageDecoder)
        // is responsible for calling bindSourceFile(file) after this returns
        // if they need lazy byte access to the source file. We no longer
        // open a second RAF here — the old code opened purRaf and assigned
        // it to dbFile, but that created a double-RAF lifecycle issue when
        // the caller also held its own RAF. Now parseFromFile's inputRaf
        // is the only RAF during parsing, and lazy-access RAFs are managed
        // by the caller.
        //
        // For probing image metadata we still need to seek through the file,
        // but we reuse the [raf] that parseFromFile passed in.
        val probeRaf = raf

        val images = mutableListOf<SceneImage>()
        for ((i, entry) in indexEntries.withIndex()) {
            val dataLength = (entry.end - entry.start).toInt()

            try {
                probeRaf.seek(entry.start)
                val probeSize = minOf(dataLength, 65536)
                val probeData = ByteArray(probeSize)
                probeRaf.readFully(probeData)

                val (width, height) = probeImageSize(probeData)
                val mimeType = when {
                    probeData.size >= 2 && probeData[0] == 0xFF.toByte() && probeData[1] == 0xD8.toByte() -> "image/jpeg"
                    probeData.size >= 4 && probeData[0] == 0x89.toByte() && String(probeData, 1, 3) == "PNG" -> "image/png"
                    probeData.size >= 12 && probeData.copyOfRange(0, 4).contentEquals(
                        byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()),
                    ) -> "image/webp"
                    else -> "image/jpeg"
                }

                val transform = transforms.firstOrNull { it.id == entry.id }

                val center: Offset
                val displaySize: Size2D
                val rotationDegrees: Float
                val flipX: Boolean
                val flipY: Boolean
                val zIndex: Int
                val colorAdjust: ColorAdjust
                val name: String

                if (transform != null) {
                    center = Offset(transform.x.toFloat(), transform.y.toFloat())
                    val dw = hypot(transform.matrix[0], transform.matrix[2]) * width
                    val dh = hypot(transform.matrix[1], transform.matrix[3]) * height
                    displaySize = Size2D(dw.toFloat(), dh.toFloat())
                    rotationDegrees = Math.toDegrees(atan2(transform.matrix[2], transform.matrix[0])).toFloat()
                    flipX = transform.matrix[0] < 0
                    flipY = transform.matrix[3] < 0
                    zIndex = transform.zLayer.toInt()
                    colorAdjust = transform.colorAdjust
                    name = transform.name.ifBlank { transform.source.ifBlank { "image_${entry.id}" } }
                } else {
                    center = Offset.Zero
                    displaySize = Size2D(width.toFloat(), height.toFloat())
                    rotationDegrees = 0f
                    flipX = false
                    flipY = false
                    zIndex = i
                    colorAdjust = ColorAdjust()
                    name = "image_${entry.id}"
                }

                images += SceneImage(
                    id = entry.id.toString(),
                    name = name,
                    mimeType = mimeType,
                    imageBytes = ByteArray(0),
                    imageByteOffset = entry.start.toInt(),
                    imageByteLength = dataLength,
                    intrinsicWidth = width,
                    intrinsicHeight = height,
                    center = center,
                    displaySize = displaySize,
                    colorAdjust = colorAdjust,
                    rotationDegrees = rotationDegrees,
                    scale = 1f,
                    flipX = flipX,
                    flipY = flipY,
                    zIndex = zIndex,
                )

            } catch (e: Exception) {
                Log.w(TAG, "V1: failed to parse image $i at offset ${entry.start}", e)
            }
        }

        return PockRefScene(
            version = 1,
            // [name=""] — V1 .pur has no name field; the call
            // site in [PureRefViewModelScene.loadScene] falls
            // back to the file's display name via
            // [SceneNaming.fromPurFileName] when the parser
            // returns blank. Previously we hardcoded the
            // "PockRef Import" placeholder here, which won
            // over the file-name fallback and pinned every
            // opened scene's title to "PockRef Import" — see
            // the file-too-small branch above for the full
            // comment.
            name = "",
            backgroundColor = Color(0xFF111111),
            images = images.sortedBy { it.zIndex },
        )
    }

    private data class LegacyTransform(
        val id: Int,
        val source: String,
        val name: String,
        val matrix: List<Double>,
        val x: Double,
        val y: Double,
        val zLayer: Double,
        val colorAdjust: ColorAdjust,
    )

    private fun parseLegacyTransforms(data: ByteArray, imageCount: Int, baseOffset: Long): List<LegacyTransform> {
        val transforms = mutableListOf<LegacyTransform>()
        var cursor = 0

        repeat(imageCount) {
            if (cursor + 12 > data.size) {
                Log.w(TAG, "parseLegacyTransforms: cursor=$cursor out of bounds at transform $it")
                return transforms
            }

            val endAddress = readInt64BE(data, cursor)
            cursor += 8
            val type = readInt32BE(data, cursor)
            cursor += 4

            if (type < 1 || type > 1000) {
                Log.w(TAG, "parseLegacyTransforms: invalid type=$type at transform $it")
                return transforms
            }

            cursor += type

            if (cursor + 4 > data.size) return transforms
            val source = readStringUtf16BE(data, cursor)
            cursor += 4 + source.byteLengthUtf16BE()

            if (cursor + 4 > data.size) return transforms
            val name = readStringUtf16BE(data, cursor)
            cursor += 4 + name.byteLengthUtf16BE()

            cursor += 8

            val m00 = readDoubleBE(data, cursor)
            val m01 = readDoubleBE(data, cursor + 8)
            val m10 = readDoubleBE(data, cursor + 24)
            val m11 = readDoubleBE(data, cursor + 32)
            val matrix = listOf(m00, m01, m10, m11)
            cursor += 48

            val x = readDoubleBE(data, cursor)
            cursor += 8
            val y = readDoubleBE(data, cursor)
            cursor += 8

            cursor += 8

            val id = readInt32BE(data, cursor)
            cursor += 4

            val zLayer = readDoubleBE(data, cursor)
            cursor += 8

            cursor += 48

            cursor += 8
            cursor += 8
            cursor += 8

            val pointCount = readInt32BE(data, cursor)
            cursor += 4
            repeat(pointCount) {
                cursor += 4
                cursor += 8
                cursor += 8
            }

            val colorAdjust = if (cursor + 28 <= (endAddress - baseOffset).toInt() && readInt32BE(data, cursor) == COLOR_ADJUST_SENTINEL) {
                ColorAdjust(
                    brightness = readDoubleBE(data, cursor + 4).toFloat(),
                    contrast = readDoubleBE(data, cursor + 12).toFloat(),
                    saturation = readDoubleBE(data, cursor + 20).toFloat(),
                )
            } else {
                ColorAdjust()
            }

            cursor += 8 + 4 + 1 + 8 + 4
            cursor = (endAddress - baseOffset).toInt()

            transforms += LegacyTransform(
                id = id,
                source = source,
                name = name,
                matrix = matrix,
                x = x,
                y = y,
                zLayer = zLayer,
                colorAdjust = colorAdjust,
            )
        }

        return transforms
    }

    private fun readStringUtf16BE(bytes: ByteArray, offset: Int): String {
        if (offset + 4 > bytes.size) return ""
        val byteLen = readInt32BE(bytes, offset)
        if (byteLen <= 0 || offset + 4 + byteLen > bytes.size) return ""
        return String(bytes, offset + 4, byteLen, Charsets.UTF_16BE).trimEnd('\u0000')
    }

    private fun String.byteLengthUtf16BE(): Int {
        val encoded = this.toByteArray(Charsets.UTF_16BE)
        return encoded.size
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun readInt64BE(bytes: ByteArray, offset: Int): Long {
        return ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long
    }

    private fun probeImageSize(data: ByteArray?): Pair<Int, Int> {
        if (data == null || data.isEmpty()) return 0 to 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        return bounds.outWidth to bounds.outHeight
    }

    private fun readUInt32BE(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt64BE(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 56) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 48) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 40) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 32) or
            ((bytes[offset + 4].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 5].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 6].toLong() and 0xFF) shl 8) or
            (bytes[offset + 7].toLong() and 0xFF)
    }

    private fun readUInt16BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun decodeUtf16BE(bytes: ByteArray, offset: Int, byteLen: Int): String {
        if (byteLen <= 0 || offset + byteLen > bytes.size) return ""
        return String(bytes, offset, byteLen, Charsets.UTF_16BE).trimEnd('\u0000')
    }

    companion object {
        private const val TAG = "PurFileParser"
        private const val HEADER_MIN_SIZE = 0x6C
        // ASCII "PRAC" (PockRef Android Coloradjust) in big-endian. Marks
        // the start of a colorAdjust block inside an Android-written
        // legacy .pur transform; desktop .pur files end their transform
        // block with a different 25-byte trailer (double 0.0 + int 1 +
        // byte 0 + long -1 + int 0) and the first 4 of those bytes never
        // match this value, so the reader falls back to ColorAdjust()
        // instead of misreading trailer bytes as brightness/contrast/
        // saturation (which can yield saturation = -1 → grayscale).
        // Must stay in sync with PurSceneCodec.COLOR_ADJUST_SENTINEL.
        private const val COLOR_ADJUST_SENTINEL = 0x50524341
    }
}

private class RandomAccessFileRegionInputStream(
    private val raf: RandomAccessFile,
    private val startOffset: Long,
    private var remaining: Long,
) : InputStream() {
    private var position = 0L

    override fun read(): Int {
        val buffer = ByteArray(1)
        return if (read(buffer, 0, 1) == 1) buffer[0].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0L) return -1
        val toRead = minOf(len.toLong(), remaining).toInt()
        return synchronized(raf) {
            raf.seek(startOffset + position)
            val read = raf.read(b, off, toRead)
            if (read > 0) {
                position += read
                remaining -= read
            }
            read
        }
    }
}
