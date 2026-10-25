package com.pockref.app.data

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pockref.app.model.CanvasMarkupText
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.CropRect
import com.pockref.app.model.ImageAlignment
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.Size2D
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal fun pureRefNotePlainText(html: String): String {
    val withoutStyle = html
        .replace(Regex("(?is)<style[^>]*>.*?</style>"), "\n")
        .replace(Regex("(?is)<head[^>]*>.*?</head>"), "\n")
    val withBreaks = withoutStyle
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p\\s*>"), "\n")
        .replace(Regex("(?i)</li\\s*>"), "\n")
    return withBreaks
        .replace(Regex("(?is)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

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

    @Volatile
    private var imageTempFile: File? = null

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

            return if (header.formatVersion.startsWith("2.")) {
                parseV2Format(inputRaf, file, header)
            } else {
                parseV1Format(inputRaf, file, header)
            }
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
        try { dbTempFile?.delete() } catch (_: Exception) {}
        dbTempFile = null
        try { imageTempFile?.delete() } catch (_: Exception) {}
        imageTempFile = null
    }

    /**
     * The currently-bound source RAF. Exposed so SceneImageDecoder can
     * use the same file handle for BitmapRegionDecoder creation.
     */
    val sourceRaf: RandomAccessFile? get() = dbFile

    /** The rotated SQLite file used by V2 lazy image records, if any. */
    val lazySourceFile: File? get() = imageTempFile ?: dbTempFile

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

    private data class V2Transform(
        val m11: Double = 1.0,
        val m12: Double = 0.0,
        val m21: Double = 0.0,
        val m22: Double = 1.0,
        val dx: Double = 0.0,
        val dy: Double = 0.0,
    ) {
        fun applying(other: V2Transform): V2Transform = V2Transform(
            m11 = m11 * other.m11 + m21 * other.m12,
            m12 = m12 * other.m11 + m22 * other.m12,
            m21 = m11 * other.m21 + m21 * other.m22,
            m22 = m12 * other.m21 + m22 * other.m22,
            dx = m11 * other.dx + m21 * other.dy + dx,
            dy = m12 * other.dx + m22 * other.dy + dy,
        )

        fun applyingTo(x: Double, y: Double): Offset = Offset(
            (m11 * x + m21 * y + dx).toFloat(),
            (m12 * x + m22 * y + dy).toFloat(),
        )
    }

    private data class V2Size(val width: Double, val height: Double)

    private data class V2RasterTarget(
        val rasterWidth: Int,
        val rasterHeight: Int,
        val scale: Float,
    )

    private data class V2RenderedDrawing(
        val logicalBounds: RectF,
        val rasterWidth: Int,
        val rasterHeight: Int,
        val bytes: ByteArray,
    )

    private fun parseV2Format(raf: RandomAccessFile, file: File, header: PurHeader): PockRefScene {
        val sqliteOffset = findV2SQLiteHeaderOffset(raf, file.length(), header.sqliteOffset)
            ?: error("PureRef v2 SQLite header not found")
        val dbFile = prepareV2SQLiteDatabase(file, sqliteOffset)
        dbTempFile = dbFile
        val imageFile = File.createTempFile("pockref_v2_images_", ".bin")
        imageFile.deleteOnExit()
        imageTempFile = imageFile
        try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            try {
                val privateMetadata = readV2PockRefMetadata(db)
                val images = FileOutputStream(imageFile).use { output ->
                    readV2SceneImages(db, output, privateMetadata)
                }
                val shared = privateMetadata?.let(SharedMarkupCodec::decode)
                val nativeIDs = privateMetadata?.optJSONArray("sharedMarkupNativeItemIDs")?.let { ids ->
                    (0 until ids.length()).map { ids.getLong(it).toString() }.toSet()
                } ?: emptySet()
                val notes = readV2Notes(db).filterNot { shared != null && shared.warning == null && it.id.removePrefix("pur-v2-note-") in nativeIDs }
                val drawings = readV2DrawingImages(db).filterNot { shared != null && shared.warning == null && it.id.removePrefix("pur-v2-drawing-") in nativeIDs }
                val groupRead = readV2SceneGroups(db)
                val groups = mergeV2Groups(groupRead.groups, privateMetadata)
                val name = privateMetadata?.optString("displayName")?.takeIf { it.isNotBlank() }
                    ?: readV2DisplayName(db)
                return PockRefScene(
                    version = 2,
                    name = name,
                    backgroundColor = readV2BackgroundColor(privateMetadata),
                    backgroundColorIsPersisted = privateMetadata?.has("backgroundColor") == true,
                    images = (images + drawings).sortedBy { it.zIndex },
                    groups = groups,
                    requiresGroupFlattening = groupRead.requiresFlattening,
                    markupTexts = (shared?.texts ?: emptyList()) + notes,
                    markupStrokes = shared?.strokes ?: emptyList(),
                    markupSourceMetadata = privateMetadata?.toString(),
                    markupCompatibilityWarning = shared?.warning,
                    defaultSpacing = readV2DefaultSpacing(privateMetadata),
                    defaultAlignment = readV2DefaultAlignment(privateMetadata),
                )
            } finally {
                db.close()
            }
        } catch (error: Throwable) {
            dbTempFile = null
            try { dbFile.delete() } catch (_: Exception) {}
            imageTempFile = null
            try { imageFile.delete() } catch (_: Exception) {}
            throw error
        }
    }

    private data class V2GroupReadResult(
        val groups: List<SceneGroup>,
        val requiresFlattening: Boolean,
    )

    private fun readV2PockRefMetadata(db: SQLiteDatabase): JSONObject? {
        val exists = db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='pockref_metadata'", null)
            .use { it.moveToFirst() }
        if (!exists) return null
        return db.rawQuery("SELECT json FROM pockref_metadata ORDER BY id LIMIT 1", emptyArray()).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) return@use null
            val bytes = if (cursor.getType(0) == android.database.Cursor.FIELD_TYPE_BLOB) {
                cursor.getBlob(0)
            } else {
                cursor.getString(0).toByteArray(Charsets.UTF_8)
            }
            require(bytes.size <= 32 * 1024 * 1024) { "标记元数据超出安全限制" }
            JSONObject(String(bytes, Charsets.UTF_8))
        }
    }

    private fun readV2BackgroundColor(metadata: JSONObject?): Color {
        val value = metadata?.optJSONObject("backgroundColor") ?: return Color(0xFF111111)
        val red = value.optInt("r", 17).coerceIn(0, 255)
        val green = value.optInt("g", 17).coerceIn(0, 255)
        val blue = value.optInt("b", 17).coerceIn(0, 255)
        val alpha = value.optInt("a", 255).coerceIn(0, 255)
        return Color(android.graphics.Color.argb(alpha, red, green, blue))
    }

    private fun readV2DefaultSpacing(metadata: JSONObject?): Size2D {
        val value = metadata?.optJSONObject("androidDefaultSpacing") ?: return Size2D(8f, 8f)
        return Size2D(
            value.optDouble("width", 8.0).toFloat().coerceAtLeast(0f),
            value.optDouble("height", 8.0).toFloat().coerceAtLeast(0f),
        )
    }

    private fun readV2DefaultAlignment(metadata: JSONObject?): ImageAlignment {
        val ordinal = metadata?.optInt("androidDefaultAlignment", ImageAlignment.CENTER.ordinal)
            ?: ImageAlignment.CENTER.ordinal
        return ImageAlignment.values().getOrElse(ordinal) { ImageAlignment.CENTER }
    }

    private fun mergeV2Groups(nativeGroups: List<SceneGroup>, metadata: JSONObject?): List<SceneGroup> {
        val savedGroups = metadata?.optJSONArray("groups") ?: return nativeGroups
        if (nativeGroups.isEmpty()) return emptyList()
        return nativeGroups.map { native ->
            val saved = (0 until savedGroups.length())
                .mapNotNull { savedGroups.optJSONObject(it) }
                .firstOrNull { savedGroup ->
                    val savedIds = savedGroup.optJSONArray("imageIds").toStringSet()
                    savedIds.isNotEmpty() && savedIds == native.imageIds.toSet()
                } ?: return@map native
            val colorJson = saved.optJSONObject("backgroundColor")
            val savedColor = colorJson?.let {
                Color(android.graphics.Color.argb(
                    it.optInt("a", 255).coerceIn(0, 255),
                    it.optInt("r", 0).coerceIn(0, 255),
                    it.optInt("g", 0).coerceIn(0, 255),
                    it.optInt("b", 0).coerceIn(0, 255),
                ))
            }
            val savedOpacity = saved.optDouble("backgroundOpacity", 0.0).toFloat().coerceIn(0f, 1f)
            val metadataVersion = metadata.optInt("version", 1)
            val savedMovesAsUnit = if (saved.has("movesAsUnit")) {
                saved.optBoolean("movesAsUnit", native.movesAsUnit)
            } else {
                // Version 1 used one `locked` bit for both native group mode
                // and PockRef's positional lock.
                true
            }
            val savedPositionLocked = if (saved.has("positionLocked")) {
                saved.optBoolean("positionLocked", native.positionLocked)
            } else {
                saved.optBoolean("locked", native.positionLocked)
            }
            val nativeArgb = native.backgroundColor.toArgb()
            val savedArgb = savedColor?.toArgb()
            val expectedAlpha = savedArgb?.let {
                (android.graphics.Color.alpha(it) * savedOpacity).roundToInt().coerceIn(0, 255)
            }
            var mergedMovesAsUnit = native.movesAsUnit
            if (metadataVersion < 2 &&
                native.movesAsUnit == savedPositionLocked &&
                native.positionLocked == savedPositionLocked
            ) {
                mergedMovesAsUnit = true
            }
            val nativeMatchesSaved = native.name == saved.optString("name", native.name) &&
                mergedMovesAsUnit == savedMovesAsUnit &&
                native.positionLocked == savedPositionLocked &&
                savedArgb != null &&
                android.graphics.Color.red(nativeArgb) == android.graphics.Color.red(savedArgb) &&
                android.graphics.Color.green(nativeArgb) == android.graphics.Color.green(savedArgb) &&
                android.graphics.Color.blue(nativeArgb) == android.graphics.Color.blue(savedArgb) &&
                (android.graphics.Color.alpha(nativeArgb) * native.backgroundOpacity).roundToInt() == expectedAlpha
            native.copy(
                id = saved.optString("id", native.id).ifBlank { native.id },
                backgroundColor = if (nativeMatchesSaved) savedColor!! else native.backgroundColor,
                backgroundOpacity = if (nativeMatchesSaved) savedOpacity else native.backgroundOpacity,
                movesAsUnit = mergedMovesAsUnit,
                positionLocked = native.positionLocked,
            )
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> = this?.let { array ->
        buildSet { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }.orEmpty()

    private fun readV2SceneGroups(db: SQLiteDatabase): V2GroupReadResult {
        val hasGroupTable = runCatching {
            db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'items_groups'",
                emptyArray(),
            ).use { it.moveToFirst() }
        }.getOrDefault(false)
        if (!hasGroupTable) return V2GroupReadResult(emptyList(), false)

        return runCatching {
            data class GroupRow(
                val id: Long,
                val name: String,
                val movesAsUnit: Boolean,
                val positionLocked: Boolean,
                val backgroundColor: String?,
                val parentId: Long,
            )

            val rows = buildList {
                db.rawQuery(
                    "SELECT g.id, i.name, g.lock_mode, g.background_color, i.parent, i.locked " +
                        "FROM items_groups g JOIN items i ON i.id = g.id ORDER BY i.z, i.id",
                    emptyArray(),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        add(
                            GroupRow(
                                id = cursor.getLong(0),
                                name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "组",
                                movesAsUnit = cursor.getInt(2) != 0,
                                positionLocked = cursor.getInt(5) != 0,
                                backgroundColor = if (cursor.isNull(3)) null else cursor.getString(3),
                                parentId = cursor.getLong(4),
                            ),
                        )
                    }
                }
            }
            val groupIds = rows.mapTo(mutableSetOf()) { it.id }
            val imageItemIds = mutableSetOf<Long>()
            db.rawQuery("SELECT id FROM items_images", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) imageItemIds += cursor.getLong(0)
            }
            var nested = rows.any { it.parentId >= 0L && it.parentId in groupIds }
            val parentByItem = mutableMapOf<Long, Long>()
            db.rawQuery("SELECT id, parent FROM items WHERE parent >= 0", emptyArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val childId = cursor.getLong(0)
                    val parentId = cursor.getLong(1)
                    parentByItem[childId] = parentId
                    if (childId in groupIds && parentId in groupIds) nested = true
                }
            }
            val unsupportedGroupContents = parentByItem.any { (childId, parentId) ->
                parentId in groupIds && childId !in imageItemIds && childId !in groupIds
            }

            val groups = rows.mapNotNull { row ->
                val memberIds = buildList {
                    db.rawQuery(
                        "SELECT id FROM items WHERE parent = ? ORDER BY z, id",
                        arrayOf(row.id.toString()),
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            val memberId = cursor.getLong(0)
                            if (memberId in imageItemIds) add("pur-v2-image-$memberId")
                        }
                    }
                }
                if (memberIds.isEmpty()) return@mapNotNull null
                val storedArgb = row.backgroundColor?.let {
                    runCatching { android.graphics.Color.parseColor(it) }.getOrNull()
                }
                val opacity = if (storedArgb != null) android.graphics.Color.alpha(storedArgb) / 255f else 0f
                val opaqueColor = storedArgb?.let {
                    Color(android.graphics.Color.rgb(
                        android.graphics.Color.red(it),
                        android.graphics.Color.green(it),
                        android.graphics.Color.blue(it),
                    ))
                } ?: Color.Black
                SceneGroup(
                    id = "pur-v2-group-${row.id}",
                    name = row.name,
                    imageIds = memberIds,
                    backgroundColor = opaqueColor,
                    backgroundOpacity = opacity,
                    movesAsUnit = row.movesAsUnit,
                    positionLocked = row.positionLocked,
                )
            }
            V2GroupReadResult(groups, nested || unsupportedGroupContents)
        }.getOrElse {
            Log.w(TAG, "PureRef v2 group metadata could not be read", it)
            V2GroupReadResult(emptyList(), true)
        }
    }

    private fun findV2SQLiteHeaderOffset(raf: RandomAccessFile, fileLength: Long, hint: Long): Long? {
        if (hint in 0 until fileLength && hint + V2_SQLITE_HEADER.size <= fileLength) {
            val bytes = ByteArray(V2_SQLITE_HEADER.size)
            raf.seek(hint)
            raf.readFully(bytes)
            if (bytes.contentEquals(V2_SQLITE_HEADER)) return hint
        }

        val buffer = ByteArray(1024 * 1024)
        var filePos = 0L
        var overlap = ByteArray(0)
        raf.seek(0)
        while (filePos < fileLength) {
            val read = raf.read(buffer)
            if (read <= 0) break
            val chunk = ByteArray(overlap.size + read)
            System.arraycopy(overlap, 0, chunk, 0, overlap.size)
            System.arraycopy(buffer, 0, chunk, overlap.size, read)
            val index = chunk.indexOfSubArray(V2_SQLITE_HEADER)
            if (index >= 0) return filePos - overlap.size + index
            val keep = minOf(V2_SQLITE_HEADER.size - 1, chunk.size)
            overlap = chunk.copyOfRange(chunk.size - keep, chunk.size)
            filePos += read
        }
        return null
    }

    private fun prepareV2SQLiteDatabase(file: File, sqliteHeaderOffset: Long): File {
        val prefixLength = file.length() - sqliteHeaderOffset
        require(prefixLength > 0 && prefixLength < sqliteHeaderOffset) {
            "Invalid PureRef v2 SQLite rotation: prefix=$prefixLength sqliteOffset=$sqliteHeaderOffset"
        }
        val outFile = File.createTempFile("pockref_v2_", ".sqlite")
        outFile.deleteOnExit()
        RandomAccessFile(file, "r").use { input ->
            FileOutputStream(outFile).use { output ->
                copyRegion(input, output, sqliteHeaderOffset, prefixLength)
                copyRegion(input, output, prefixLength, sqliteHeaderOffset - prefixLength)
            }
        }
        return outFile
    }

    private fun copyRegion(input: RandomAccessFile, output: FileOutputStream, start: Long, length: Long) {
        val buffer = ByteArray(1024 * 1024)
        var remaining = length
        input.seek(start)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun readV2SceneImages(
        db: SQLiteDatabase,
        imageOutput: FileOutputStream,
        privateMetadata: JSONObject?,
    ): List<SceneImage> {
        val columns = db.rawQuery("PRAGMA table_info(items)",null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
        val nameColumn = if ("name" in columns) "it.name" else "NULL"
        val parentTransform = if ("parent" in columns) "parent_group.transform" else "NULL"
        val parentJoin = if ("parent" in columns) "LEFT JOIN items parent_group ON parent_group.id = it.parent" else ""
        val sql = """
            SELECT ii.id, ii.image, im.width, im.height, im.format, it.opacity, it.z, it.locked,
                   it.transform, ii.image_transform, $parentTransform, $nameColumn
            FROM items_images ii
            JOIN images im ON im.id = ii.image
            JOIN items it ON it.id = ii.id
            $parentJoin
            WHERE im.data IS NOT NULL
            ORDER BY it.z, ii.id
        """.trimIndent()

        val images = mutableListOf<SceneImage>()
        db.rawQuery(sql, emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val itemId = cursor.getLong(0)
                val imageId = cursor.getLong(1)
                val width = cursor.getInt(2).coerceAtLeast(1)
                val height = cursor.getInt(3).coerceAtLeast(1)
                val format = if (cursor.isNull(4)) "" else cursor.getString(4).orEmpty()
                val z = cursor.getDouble(6)
                val locked = cursor.getInt(7) != 0
                val itemTransform = v2TransformFrom(sqliteTextPayload(cursor, 8)) ?: V2Transform()
                val imageTransform = v2TransformFrom(sqliteTextPayload(cursor, 9))
                    ?: centeredImageTransform(width, height)
                val groupTransform = v2TransformFrom(sqliteTextPayload(cursor, 10)) ?: V2Transform()
                val blob = copyV2ImageBlob(db, imageId, imageOutput) ?: continue
                val combined = groupTransform.applying(itemTransform).applying(imageTransform)
                val center = combined.applyingTo(width / 2.0, height / 2.0)
                val displayWidth = maxOf(1.0, hypot(combined.m11, combined.m12) * width)
                val displayHeight = maxOf(1.0, hypot(combined.m21, combined.m22) * height)
                val stableId = "pur-v2-image-$itemId"
                val metadata = privateMetadata?.optJSONObject("images")?.optJSONObject(stableId)
                val cropJson = metadata?.optJSONObject("cropRect")
                val crop = cropJson?.takeIf { metadata.optBoolean("androidHasCrop", true) }?.let {
                    val left = it.optDouble("x", 0.0).toFloat()
                    val top = it.optDouble("y", 0.0).toFloat()
                    val right = (left + it.optDouble("width", 1.0)).toFloat()
                    val bottom = (top + it.optDouble("height", 1.0)).toFloat()
                    CropRect(left, top, right, bottom)
                }
                val adjustJson = metadata?.optJSONObject("colorAdjust")
                images += SceneImage(
                    id = stableId,
                    name = cursor.getString(11)?.takeIf { it.isNotBlank() }
                        ?: metadata?.optString("originalFilename")?.takeIf { it.isNotBlank() }
                        ?: "image_$itemId",
                    mimeType = formatMimeType(format),
                    imageBytes = ByteArray(0),
                    imageByteOffset = blob.offset.toInt(),
                    imageByteLength = blob.length,
                    intrinsicWidth = width,
                    intrinsicHeight = height,
                    center = center,
                    displaySize = Size2D(displayWidth.toFloat(), displayHeight.toFloat()),
                    rotationDegrees = Math.toDegrees(atan2(combined.m12, combined.m11)).toFloat(),
                    flipX = false,
                    flipY = combined.m11 * combined.m22 - combined.m12 * combined.m21 < 0,
                    zIndex = z.toInt(),
                    locked = locked,
                    colorAdjust = ColorAdjust(
                        brightness = adjustJson?.optDouble("brightness", 0.0)?.toFloat() ?: 0f,
                        contrast = adjustJson?.optDouble("contrast", 0.0)?.toFloat() ?: 0f,
                        saturation = adjustJson?.optDouble("saturation", 0.0)?.toFloat() ?: 0f,
                    ),
                    cropRect = crop,
                    showsOriginalResolution = metadata?.optBoolean("showsOriginalResolution", false) ?: false,
                    posterizeLevels = metadata?.optInt("posterizeLevels", 0)?.let(::supportedPosterizeLevel),
                )
            }
        }
        return images
    }

    private fun readV2Notes(db: SQLiteDatabase): List<CanvasMarkupText> {
        val sql = """
            SELECT n.id, n.text, n.fixed_size, n.background_color, it.opacity, it.z,
                   it.locked, it.transform
            FROM items_notes n
            JOIN items it ON it.id = n.id
            ORDER BY it.z, n.id
        """.trimIndent()
        val notes = mutableListOf<CanvasMarkupText>()
        db.rawQuery(sql, emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val itemId = cursor.getLong(0)
                val html = if (cursor.isNull(1)) "" else cursor.getString(1).orEmpty()
                val text = pureRefNotePlainText(html)
                if (text.isBlank()) continue
                val fixedSize = v2SizeFrom(sqliteTextPayload(cursor, 2))
                val transform = v2TransformFrom(sqliteTextPayload(cursor, 7)) ?: V2Transform()
                notes += CanvasMarkupText(
                    id = "pur-v2-note-$itemId",
                    text = text,
                    position = Offset(transform.dx.toFloat(), transform.dy.toFloat()),
                    fontSize = ((fixedSize?.height?.toFloat() ?: 320f) / text.lines().size.coerceAtLeast(1))
                        .coerceIn(24f, 240f),
                )
            }
        }
        return notes
    }

    private fun readV2DrawingImages(db: SQLiteDatabase): List<SceneImage> {
        val sql = """
            SELECT d.id, d.strokes, it.transform, it.z, it.locked
            FROM items_drawings d
            JOIN items it ON it.id = d.id
            ORDER BY it.z, d.id
        """.trimIndent()
        val drawings = mutableListOf<SceneImage>()
        db.rawQuery(sql, emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val itemId = cursor.getLong(0)
                val payload = sqliteTextPayload(cursor, 1) ?: continue
                val transform = v2TransformFrom(sqliteTextPayload(cursor, 2)) ?: V2Transform()
                val z = cursor.getDouble(3)
                val locked = cursor.getInt(4) != 0
                val rendered = renderV2DrawingPng(payload, transform) ?: continue
                drawings += SceneImage(
                    id = "pur-v2-drawing-$itemId",
                    name = "drawing_$itemId",
                    mimeType = "image/png",
                    imageBytes = rendered.bytes,
                    intrinsicWidth = rendered.rasterWidth,
                    intrinsicHeight = rendered.rasterHeight,
                    center = Offset(rendered.logicalBounds.centerX(), rendered.logicalBounds.centerY()),
                    displaySize = Size2D(rendered.logicalBounds.width(), rendered.logicalBounds.height()),
                    zIndex = z.toInt(),
                    locked = locked,
                )
            }
        }
        return drawings
    }

    private fun readV2DisplayName(db: SQLiteDatabase): String {
        return runCatching {
            db.rawQuery("SELECT last_load_path FROM metadata LIMIT 1", emptyArray()).use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) return ""
                File(cursor.getString(0)).nameWithoutExtension
            }
        }.getOrDefault("")
    }

    private data class V2BlobReference(val offset: Long, val length: Int)

    /** Copy one blob at a time using bounded SQLite substr reads. */
    private fun copyV2ImageBlob(
        db: SQLiteDatabase,
        imageId: Long,
        output: FileOutputStream,
    ): V2BlobReference? = runCatching {
            val length = db.rawQuery(
                "SELECT length(data) FROM images WHERE id = ?",
                arrayOf(imageId.toString()),
            ).use { cursor ->
                if (!cursor.moveToFirst() || cursor.isNull(0)) return@runCatching null
                cursor.getLong(0)
            }
            if (length <= 0L || length > Int.MAX_VALUE) return@runCatching null
            val offset = output.channel.position()
            var copied = 0L
            while (copied < length) {
                val requested = minOf(V2_BLOB_CHUNK_SIZE.toLong(), length - copied).toInt()
                val chunk = db.rawQuery(
                    "SELECT substr(data, ?, ?) FROM images WHERE id = ?",
                    arrayOf((copied + 1L).toString(), requested.toString(), imageId.toString()),
                ).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.isNull(0)) return@runCatching null
                    cursor.getBlob(0)
                }
                if (chunk.isEmpty()) return@runCatching null
                output.write(chunk)
                copied += chunk.size
            }
            if (copied == length) V2BlobReference(offset, copied.toInt()) else null
        }.onFailure { Log.w(TAG, "V2 blob copy failed for image $imageId", it) }.getOrNull()

    private fun formatMimeType(format: String): String = when (format.trim().lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        else -> "image/jpeg"
    }

    private fun supportedPosterizeLevel(value: Int): Int? = when (value) {
        2, 4, 8, 16 -> value
        else -> null
    }

    private fun v2TransformFrom(payload: ByteArray?): V2Transform? {
        if (payload == null || payload.size < 77) return null
        val start = 5
        fun f(index: Int): Double = readDoubleBE(payload, start + index * 8)
        return V2Transform(
            m11 = f(0),
            m12 = f(1),
            m21 = f(3),
            m22 = f(4),
            dx = f(6),
            dy = f(7),
        )
    }

    private fun centeredImageTransform(width: Int, height: Int): V2Transform = V2Transform(
        dx = -width / 2.0,
        dy = -height / 2.0,
    )

    private fun v2SizeFrom(payload: ByteArray?): V2Size? {
        if (payload == null || payload.size < 21) return null
        val width = readDoubleBE(payload, 5)
        val height = readDoubleBE(payload, 13)
        return if (width.isFinite() && height.isFinite() && width > 0 && height > 0) {
            V2Size(width, height)
        } else {
            null
        }
    }

    private fun sqliteTextPayload(cursor: android.database.Cursor, index: Int): ByteArray? {
        if (cursor.isNull(index)) return null
        if (cursor.getType(index) == android.database.Cursor.FIELD_TYPE_BLOB) {
            return runCatching { cursor.getBlob(index) }.getOrNull()
        }
        val text = runCatching { cursor.getString(index) }.getOrNull()
        if (text != null) {
            return text.mapNotNull { ch -> ch.code.takeIf { it <= 0xFF }?.toByte() }.toByteArray()
        }
        return runCatching {
            cursor.getBlob(index)
        }.getOrNull()
    }

    private fun renderV2DrawingPng(payload: ByteArray, transform: V2Transform): V2RenderedDrawing? {
        val typeName = "QList<GraphicsDrawItem::Stroke>".toByteArray(Charsets.UTF_8)
        val typeStart = payload.indexOfSubArray(typeName)
        if (typeStart < 0) return null
        var cursor = typeStart + typeName.size + 5
        val strokeCountOffset = typeStart + typeName.size + 4
        if (strokeCountOffset >= payload.size || cursor > payload.size) return null
        val strokeCount = payload[strokeCountOffset].toInt() and 0xFF
        if (strokeCount <= 0 || strokeCount >= 10_000) return null

        data class Stroke(val width: Float, val points: List<Offset>)
        val strokes = mutableListOf<Stroke>()
        val bounds = RectF()
        var hasBounds = false
        repeat(strokeCount) {
            if (cursor + 24 > payload.size) return@repeat
            val lineWidth = readDoubleBE(payload, cursor + 12).takeIf { it.isFinite() && it > 0 } ?: 1.0
            val pointCount = readUInt32BE(payload, cursor + 20).toInt()
            if (pointCount < 2 || pointCount >= 100_000) return@repeat
            cursor += 24
            if (cursor + pointCount * 20 > payload.size) return@repeat
            val points = mutableListOf<Offset>()
            repeat(pointCount) {
                cursor += 4
                val p = transform.applyingTo(readDoubleBE(payload, cursor), readDoubleBE(payload, cursor + 8))
                cursor += 16
                if (p.x.isFinite() && p.y.isFinite()) {
                    points += p
                    if (!hasBounds) {
                        bounds.set(p.x, p.y, p.x, p.y)
                        hasBounds = true
                    } else {
                        bounds.union(p.x, p.y)
                    }
                }
            }
            if (points.size >= 2) strokes += Stroke(lineWidth.toFloat(), points)
            if (cursor + 28 <= payload.size) cursor += 28 else return@repeat
        }
        if (!hasBounds || strokes.isEmpty()) return null

        val pad = 16f
        val logicalWidth = maxOf(1.0, (bounds.width() + pad * 2f).toDouble())
        val logicalHeight = maxOf(1.0, (bounds.height() + pad * 2f).toDouble())
        val target = limitV2GeneratedRaster(logicalWidth, logicalHeight) ?: return null
        val bitmap = Bitmap.createBitmap(target.rasterWidth, target.rasterHeight, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.RED
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val out = ByteArrayOutputStream()
        try {
            val canvas = Canvas(bitmap)
            for (stroke in strokes) {
                paint.strokeWidth = maxOf(1f, stroke.width * target.scale)
                for (i in 0 until stroke.points.lastIndex) {
                    val a = stroke.points[i]
                    val b = stroke.points[i + 1]
                    canvas.drawLine(
                        (a.x - bounds.left + pad) * target.scale,
                        (a.y - bounds.top + pad) * target.scale,
                        (b.x - bounds.left + pad) * target.scale,
                        (b.y - bounds.top + pad) * target.scale,
                        paint,
                    )
                }
            }
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            bitmap.recycle()
        }
        val imageBounds = RectF(bounds.left - pad, bounds.top - pad, bounds.right + pad, bounds.bottom + pad)
        return V2RenderedDrawing(
            logicalBounds = imageBounds,
            rasterWidth = target.rasterWidth,
            rasterHeight = target.rasterHeight,
            bytes = out.toByteArray(),
        )
    }

    private fun limitV2GeneratedRaster(logicalWidth: Double, logicalHeight: Double): V2RasterTarget? {
        if (!logicalWidth.isFinite() || !logicalHeight.isFinite()) return null
        val width = logicalWidth.coerceAtLeast(1.0)
        val height = logicalHeight.coerceAtLeast(1.0)
        val longSideScale = MAX_V2_GENERATED_RASTER_LONG_SIDE / maxOf(width, height)
        val pixelScale = sqrt(MAX_V2_GENERATED_RASTER_PIXELS / (width * height))
        val scale = minOf(1.0, longSideScale, pixelScale).toFloat()
        val rasterWidth = (width * scale).toInt().coerceIn(1, MAX_V2_GENERATED_RASTER_LONG_SIDE.toInt())
        val rasterHeight = (height * scale).toInt().coerceIn(1, MAX_V2_GENERATED_RASTER_LONG_SIDE.toInt())
        return V2RasterTarget(rasterWidth, rasterHeight, scale)
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

                val (width, height) = probeImageSize(probeData).let { size ->
                    if (size.first > 0 && size.second > 0) size else {
                        // EXIF/XMP may put the JPEG frame header beyond the 64 KiB
                        // prefix. Scan only this image's bounded stream, not pixels.
                        val stream = RandomAccessFileRegionInputStream(
                            probeRaf, entry.start, dataLength.toLong(),
                        )
                        stream.buffered().use { probeJpegSize(it) } ?: size
                    }
                }
                val mimeType = when {
                    probeData.size >= 6 &&
                        (String(probeData, 0, 6) == "GIF87a" || String(probeData, 0, 6) == "GIF89a") -> "image/gif"
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
        if (bounds.outWidth > 0 && bounds.outHeight > 0) return bounds.outWidth to bounds.outHeight
        return probePngSize(data) ?: probeGifSize(data) ?: probeJpegSize(data) ?: (0 to 0)
    }

    private fun probeGifSize(data: ByteArray): Pair<Int, Int>? {
        if (data.size < 10) return null
        val isGif = String(data, 0, 6) == "GIF87a" || String(data, 0, 6) == "GIF89a"
        if (!isGif) return null
        val width = (data[6].toInt() and 0xFF) or ((data[7].toInt() and 0xFF) shl 8)
        val height = (data[8].toInt() and 0xFF) or ((data[9].toInt() and 0xFF) shl 8)
        return if (width > 0 && height > 0) width to height else null
    }

    private fun probePngSize(data: ByteArray): Pair<Int, Int>? {
        if (data.size < 24) return null
        val isPng = data[0] == 0x89.toByte() &&
            data[1] == 'P'.code.toByte() &&
            data[2] == 'N'.code.toByte() &&
            data[3] == 'G'.code.toByte()
        if (!isPng) return null
        val width = readInt32BE(data, 16)
        val height = readInt32BE(data, 20)
        return if (width > 0 && height > 0) width to height else null
    }

    private fun probeJpegSize(data: ByteArray): Pair<Int, Int>? =
        probeJpegSize(ByteArrayInputStream(data))

    internal fun probeJpegSize(input: InputStream): Pair<Int, Int>? {
        val data = DataInputStream(input)
        return try {
            if (data.readUnsignedShort() != 0xFFD8) return null
            while (true) {
                if (data.readUnsignedByte() != 0xFF) return null
                var marker = data.readUnsignedByte()
                while (marker == 0xFF) marker = data.readUnsignedByte()
                if (marker == 0xD9 || marker == 0xDA || marker == 0) return null
                if (marker == 0x01 || marker in 0xD0..0xD7) continue
                val segmentLength = data.readUnsignedShort()
                if (segmentLength < 2) return null
                if (marker in JPEG_SIZE_MARKERS) {
                    if (segmentLength < 8) return null
                    data.readUnsignedByte() // sample precision
                    val height = data.readUnsignedShort()
                    val width = data.readUnsignedShort()
                    return if (width > 0 && height > 0) width to height else null
                }
                // Skip entire metadata segments so an EXIF thumbnail's SOF
                // cannot be mistaken for the original image's dimensions.
                var remaining = segmentLength - 2
                while (remaining > 0) {
                    val skipped = data.skipBytes(remaining)
                    if (skipped > 0) remaining -= skipped else {
                        data.readUnsignedByte()
                        remaining--
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } catch (_: IOException) {
            null
        }
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
        private const val V2_BLOB_CHUNK_SIZE = 256 * 1024
        private val V2_SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.UTF_8)
        private const val MAX_V2_GENERATED_RASTER_LONG_SIDE = 4096.0
        private const val MAX_V2_GENERATED_RASTER_PIXELS = 8_000_000.0
        private const val HEADER_MIN_SIZE = 0x6C
        private val JPEG_SIZE_MARKERS = setOf(
            0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF,
        )
        // ASCII "PRCA" (PockRef ColorAdjust) in big-endian. Marks
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

private fun ByteArray.indexOfSubArray(needle: ByteArray): Int {
    if (needle.isEmpty() || needle.size > size) return -1
    for (i in 0..size - needle.size) {
        var matches = true
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) {
                matches = false
                break
            }
        }
        if (matches) return i
    }
    return -1
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
