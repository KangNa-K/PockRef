package com.pockref.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.database.sqlite.transaction
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.pockref.app.model.CanvasMarkupStroke
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneGroup
import com.pockref.app.model.SceneImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Writes the native PureRef 2.x SQLite container used by the iOS group-preserving saver. */
internal class PurV2SceneWriter(
    private val workDir: File,
    private val resolveImageBytes: (SceneImage) -> ByteArray,
) {
    fun write(scene: PockRefScene, output: OutputStream) {
        require(!scene.requiresGroupFlattening) { "无法保存包含不支持嵌套组的场景" }
        check(workDir.isDirectory || workDir.mkdirs()) { "Unable to create V2 save directory" }

        val images = scene.images.sortedBy { it.zIndex }
        require(scene.groups.map { it.id }.distinct().size == scene.groups.size) { "组 ID 重复，无法保存原生 V2 文件" }
        val memberships = scene.groups.flatMap { group -> group.imageIds.map { it to group.id } }
        require(memberships.map { it.first }.distinct().size == memberships.size) { "图片不能同时属于多个组" }
        images.forEach { image ->
            // Fail before creating/truncating a destination when an in-memory source is malformed.
            if (image.imageBytes.isNotEmpty()) {
                require(isRecognizedImage(image.imageBytes)) { "无法保存无法识别的图片: ${image.name}" }
            }
        }
        val dbFile = File.createTempFile("pockref-v2-db-", ".sqlite", workDir)
        val wrappedFile = File.createTempFile("pockref-v2-scene-", ".pur", workDir)
        try {
            val thumbnail = thumbnailData(images)
            writeDatabase(scene, images, thumbnail, dbFile)
            wrapDatabase(dbFile, wrappedFile, images.size, thumbnail)
            wrappedFile.inputStream().buffered(BUFFER_SIZE).use { it.copyTo(output, BUFFER_SIZE) }
        } finally {
            dbFile.delete()
            wrappedFile.delete()
        }
    }

    private fun writeDatabase(
        scene: PockRefScene,
        images: List<SceneImage>,
        thumbnail: ByteArray,
        dbFile: File,
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.rawQuery("PRAGMA journal_mode=OFF", null).use { }
            db.execSQL("PRAGMA synchronous=OFF")
            createSchema(db)
            db.transaction { insertScene(db, scene, images, thumbnail) }
        } finally {
            db.close()
        }
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE images (width INTEGER,source_type INTEGER,source TEXT,checksum TEXT,origin TEXT,data BLOB,format TEXT,height INTEGER,id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE items (comment INTEGER,opacity REAL,z REAL,locked INTEGER,sort_order BLOB,parent INTEGER,transform BLOB,name TEXT,id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE items_drawings (strokes BLOB,id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE items_groups (lock_mode INTEGER,background_color TEXT,id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE items_images (playback_frame INTEGER,playback_state INTEGER,flags INTEGER,image_bounds BLOB,playback_speed REAL,image_transform BLOB,image INTEGER,id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE items_notes (fixed_size TEXT,background_color TEXT,text TEXT,style INTEGER,text_color TEXT,id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE metadata (last_save_path TEXT,last_load_checksum TEXT,thumbnail BLOB,saved INTEGER,vertical_scroll INTEGER,last_load_path TEXT,horizontal_scroll INTEGER,scene_rect TEXT,view_transform TEXT,application_version TEXT,id INTEGER PRIMARY KEY)")
        db.execSQL("CREATE TABLE pockref_metadata (json BLOB,id INTEGER PRIMARY KEY)")
    }

    private fun insertScene(
        db: SQLiteDatabase,
        scene: PockRefScene,
        images: List<SceneImage>,
        thumbnail: ByteArray,
    ) {
        val displayName = scene.name.ifBlank { "Untitled" }
        val pseudoPath = "/PockRef/$displayName.pur"
        db.insertOrThrow("metadata", null, ContentValues().apply {
            put("last_save_path", "/PockRef")
            put("last_load_checksum", md5(pseudoPath.toByteArray(Charsets.UTF_8)))
            put("thumbnail", thumbnail)
            put("saved", 0)
            put("vertical_scroll", 0)
            put("last_load_path", pseudoPath)
            put("horizontal_scroll", 0)
            put("scene_rect", "")
            put("view_transform", transformPayload(1.0, 0.0, 0.0, 1.0, 0.0, 0.0))
            put("application_version", "2.1.2")
            put("id", 0L)
        })

        val groupRows = scene.groups.mapNotNull { group ->
            val members = group.imageIds.filter { id -> images.any { it.id == id } }.distinct()
            if (members.isEmpty()) null else group.copy(imageIds = members)
        }
        val groupItemIds = groupRows.mapIndexed { index, group -> group.id to images.size + index + 1L }.toMap()
        val imageItemIds = images.mapIndexed { index, image -> image.id to index + 1L }.toMap()

        groupRows.forEach { group ->
            val groupId = groupItemIds.getValue(group.id)
            val maxZ = group.imageIds.mapNotNull { member -> images.firstOrNull { it.id == member }?.zIndex }.maxOrNull() ?: 0
            insertItem(
                db = db,
                id = groupId,
                name = group.name,
                z = maxZ.toDouble(),
                locked = group.positionLocked,
                parent = -1L,
                transform = identityTransform(),
            )
            db.insertOrThrow("items_groups", null, ContentValues().apply {
                // PureRef's lock_mode is the native "move as one" flag;
                // the item's locked column carries PockRef's position lock.
                put("lock_mode", if (group.movesAsUnit) 1 else 0)
                put("background_color", groupColorString(group))
                put("id", groupId)
            })
        }

        images.forEachIndexed { index, image ->
            val id = index + 1L
            val bytes = resolveImageBytes(image)
            require(bytes.isNotEmpty()) { "图片数据为空: ${image.name}" }
            val pureRefBytes = normalizeImageBytes(image, bytes)
            val width = image.intrinsicWidth.coerceAtLeast(1)
            val height = image.intrinsicHeight.coerceAtLeast(1)
            val format = formatName(image, pureRefBytes)
            db.insertOrThrow("images", null, ContentValues().apply {
                put("width", width)
                put("source_type", 1)
                putNull("source")
                put("checksum", md5(pureRefBytes))
                putNull("origin")
                put("data", pureRefBytes)
                put("format", format)
                put("height", height)
                put("id", id)
            })

            val parent = groupRows.firstNotNullOfOrNull { group ->
                groupItemIds[group.id]?.takeIf { image.id in group.imageIds }
            } ?: -1L
            insertItem(
                db = db,
                id = id,
                name = image.name,
                z = image.zIndex.toDouble(),
                locked = image.locked,
                parent = parent,
                transform = imageTransform(image, width, height),
            )
            db.insertOrThrow("items_images", null, ContentValues().apply {
                put("playback_frame", 0)
                put("playback_state", if (format.equals("gif", ignoreCase = true)) 3 else 0)
                put("flags", 1)
                put("image_bounds", painterPath(width.toDouble(), height.toDouble()))
                put("playback_speed", 1.0)
                put("image_transform", transformPayload(1.0, 0.0, 0.0, 1.0, -width / 2.0, -height / 2.0))
            put("image", id)
                put("id", id)
            })
        }

        insertMarkupTexts(db, scene, groupRows.size)
        insertMarkupDrawing(db, scene, images.size + groupRows.size + scene.markupTexts.size + 1L)
        insertPockRefMetadata(db, scene, images, groupRows, imageItemIds)
    }

    private fun insertItem(
        db: SQLiteDatabase,
        id: Long,
        name: String,
        z: Double,
        locked: Boolean,
        parent: Long,
        transform: ByteArray,
    ) {
        db.insertOrThrow("items", null, ContentValues().apply {
            putNull("comment")
            put("opacity", 1.0)
            put("z", z)
            put("locked", if (locked) 1 else 0)
            put("sort_order", sortOrderPayload(id))
            put("parent", parent)
            put("transform", transform)
            put("name", name)
            put("id", id)
        })
    }

    private fun insertMarkupTexts(db: SQLiteDatabase, scene: PockRefScene, groupCount: Int) {
        val idStart = scene.images.size + groupCount + 1L
        scene.markupTexts.forEachIndexed { index, note ->
            val id = idStart + index
            val z = (scene.images.maxOfOrNull { it.zIndex } ?: 0) + index + 1.0
            insertItem(db, id, "", z, note.locked, -1L,
                transformPayload(cos(note.rotation.toDouble()), sin(note.rotation.toDouble()), -sin(note.rotation.toDouble()), cos(note.rotation.toDouble()), note.position.x.toDouble(), note.position.y.toDouble()))
            db.insertOrThrow("items_notes", null, ContentValues().apply {
                val longestLine = note.text.lineSequence().map { it.length }.maxOrNull() ?: 0
                put("fixed_size", sizePayload(
                    max(note.boxSize?.width ?: (note.fontSize * longestLine), 1f).toDouble(),
                    max(note.boxSize?.height ?: (note.fontSize * note.text.lines().size.coerceAtLeast(1)), 1f).toDouble(),
                ))
                putNull("background_color")
                put("text", "<p style=\"font-size:${note.fontSize}px;color:#${"%06x".format(note.color.toArgb() and 0xFFFFFF)}\">${escapeHtml(note.text).replace("\n","<br/>")}</p>")
                put("style", 1)
                put("text_color", "#%08x".format(note.color.toArgb()))
                put("id", id)
            })
        }
    }

    private fun insertMarkupDrawing(db: SQLiteDatabase, scene: PockRefScene, id: Long) {
        val strokes = scene.markupStrokes.flatMap { stroke ->
            com.pockref.app.ui.markupPolylines(stroke).map { stroke.copy(points=it, rotation=0f, kind="pen") }
        }.filter { it.points.size >= 2 }
        if (strokes.isEmpty()) return
        val payload = drawingPayload(strokes)
        val transform = transformPayload(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        insertItem(db, id, "PockRef Markup", (scene.images.maxOfOrNull { it.zIndex } ?: 0) + 1.0, false, -1L, transform)
        db.insertOrThrow("items_drawings", null, ContentValues().apply {
            put("strokes", payload)
            put("id", id)
        })
    }

    private fun insertPockRefMetadata(
        db: SQLiteDatabase,
        scene: PockRefScene,
        images: List<SceneImage>,
        groups: List<SceneGroup>,
        imageIds: Map<String, Long>,
    ) {
        val imageMetadata = JSONObject()
        images.forEach { image ->
            val itemId = imageIds[image.id] ?: return@forEach
            val crop = image.cropRect
            imageMetadata.put("pur-v2-image-$itemId", JSONObject().apply {
                put("opacity", 1.0)
                put("locked", image.locked)
                put("isFavorite", false)
                put("originalFilename", image.name)
                put("showsOriginalResolution", image.showsOriginalResolution)
                put("androidHasCrop", crop != null)
                put("cropRect", JSONObject().apply {
                    put("x", crop?.left ?: 0f)
                    put("y", crop?.top ?: 0f)
                    put("width", crop?.cropWidth ?: 1f)
                    put("height", crop?.cropHeight ?: 1f)
                })
                put("colorAdjust", JSONObject().apply {
                    put("brightness", image.colorAdjust.brightness)
                    put("contrast", image.colorAdjust.contrast)
                    put("saturation", image.colorAdjust.saturation)
                })
                put("posterizeLevels", image.posterizeLevels ?: 0)
            })
        }
        val groupMetadata = JSONArray()
        groups.forEach { group ->
            groupMetadata.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("imageIds", JSONArray(group.imageIds.mapNotNull { imageIds[it]?.let { id -> "pur-v2-image-$id" } }))
                put("backgroundColor", groupColorJson(group.backgroundColor))
                put("backgroundOpacity", group.backgroundOpacity.coerceIn(0f, 1f))
                put("movesAsUnit", group.movesAsUnit)
                put("positionLocked", group.positionLocked)
            })
        }
        val argb = scene.backgroundColor.toArgb()
        val metadata = SharedMarkupCodec.metadata(scene.markupStrokes, scene.markupTexts, scene.markupSourceMetadata).apply {
            put("version", 2)
            put("displayName", scene.name)
            if (!has("canvasSize")) put("canvasSize", org.json.JSONArray().put(4096).put(4096))
            put("backgroundColor", JSONObject().apply {
                put("r", AndroidColor.red(argb)); put("g", AndroidColor.green(argb))
                put("b", AndroidColor.blue(argb)); put("a", AndroidColor.alpha(argb))
            })
            put("backgroundFollowsSystem", false)
            put("androidDefaultSpacing", JSONObject().apply {
                put("width", scene.defaultSpacing.width)
                put("height", scene.defaultSpacing.height)
            })
            put("androidDefaultAlignment", scene.defaultAlignment.ordinal)
            put("annotationsVisible", true)
            put("markupsHidden", false)
            put("images", imageMetadata)
            put("groups", groupMetadata)
            put("sharedMarkupNativeItemIDs", org.json.JSONArray().apply {
                scene.markupTexts.indices.forEach { put(images.size + groups.size + 1L + it) }
                if (scene.markupStrokes.isNotEmpty()) put(images.size + groups.size + scene.markupTexts.size + 1L)
            })
        }
        db.insertOrThrow("pockref_metadata", null, ContentValues().apply {
            put("json", metadata.toString().toByteArray(Charsets.UTF_8))
            put("id", 1L)
        })
    }

    private fun wrapDatabase(dbFile: File, outputFile: File, imageCount: Int, thumbnail: ByteArray) {
        val sqliteLength = dbFile.length()
        val prefix = v2Header(imageCount, sqliteLength, thumbnail)
        require(prefix.size < sqliteLength) { "V2 SQLite database is too small to wrap" }
        RandomAccessFile(dbFile, "r").use { db ->
            RandomAccessFile(outputFile, "rw").use { out ->
                out.setLength(0L)
                out.write(prefix)
                copyRange(db, out, prefix.size.toLong(), sqliteLength - prefix.size)
                copyRange(db, out, 0L, prefix.size.toLong())
                out.seek(104L)
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(BUFFER_SIZE)
                var remaining = out.length() - 104L
                while (remaining > 0) {
                    val count = minOf(buffer.size.toLong(), remaining).toInt()
                    out.readFully(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    remaining -= count
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                out.seek(40L)
                out.write(hex.toByteArray(Charsets.UTF_16BE).copyOf(64))
                out.fd.sync()
            }
        }
    }

    private fun copyRange(input: RandomAccessFile, output: RandomAccessFile, start: Long, length: Long) {
        input.seek(start)
        var remaining = length
        val buffer = ByteArray(BUFFER_SIZE)
        while (remaining > 0) {
            val count = minOf(buffer.size.toLong(), remaining).toInt()
            input.readFully(buffer, 0, count)
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun v2Header(imageCount: Int, sqliteLength: Long, thumbnail: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
        header.putInt(0, 6)
        "2.1".toByteArray(Charsets.UTF_16BE).copyInto(header.array(), 4)
        header.putShort(10, imageCount.coerceAtMost(0xFFFF).toShort())
        header.putLong(14, sqliteLength)
        header.putShort(24, 10)
        "2.1.2".toByteArray(Charsets.UTF_16BE).copyInto(header.array(), 26)
        header.putInt(36, 64)
        header.putInt(104, thumbnail.size)
        return header.array() + thumbnail
    }

    /** PureRef stores a small opaque JPEG thumbnail between its header and SQLite payload. */
    private fun thumbnailData(images: List<SceneImage>): ByteArray {
        // PockRefScene does not carry the recent-file cover selection; use
        // the first persisted image, matching the deterministic V2 fallback.
        val source = images.firstOrNull()
            ?: return fallbackThumbnail()
        return runCatching {
            val bytes = resolveImageBytes(source)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching fallbackThumbnail()
            val sample = generateSequence(1) { it * 2 }
                .takeWhile { maxOf(bounds.outWidth, bounds.outHeight) / it > 96 }
                .lastOrNull() ?: 1
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@runCatching fallbackThumbnail()
            val scale = minOf(1f, 96f / maxOf(bitmap.width, bitmap.height).toFloat())
            val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = if (width == bitmap.width && height == bitmap.height) bitmap
            else bitmap.scale(width, height, filter = true)
            try {
                ByteArrayOutputStream().use { output ->
                    val canvasBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(canvasBitmap)
                    canvas.drawColor(AndroidColor.WHITE)
                    canvas.drawBitmap(scaled, 0f, 0f, null)
                    check(canvasBitmap.compress(Bitmap.CompressFormat.JPEG, 72, output))
                    canvasBitmap.recycle()
                    output.toByteArray()
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.getOrElse { fallbackThumbnail() }
    }

    private fun fallbackThumbnail(): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.WHITE)
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 72, output))
        bitmap.recycle()
        output.toByteArray()
    }

    private fun imageTransform(image: SceneImage, width: Int, height: Int): ByteArray {
        val scaleX = image.displaySize.width * image.scale / width.coerceAtLeast(1) * if (image.flipX) -1.0 else 1.0
        val scaleY = image.displaySize.height * image.scale / height.coerceAtLeast(1) * if (image.flipY) -1.0 else 1.0
        val angle = Math.toRadians(image.rotationDegrees.toDouble())
        val c = cos(angle)
        val s = sin(angle)
        return transformPayload(
            scaleX * c,
            scaleX * s,
            -scaleY * s,
            scaleY * c,
            image.center.x.toDouble(),
            image.center.y.toDouble(),
        )
    }

    private fun transformPayload(m11: Double, m12: Double, m21: Double, m22: Double, dx: Double, dy: Double): ByteArray =
        ByteBuffer.allocate(77).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x50); put(0)
            putDouble(m11); putDouble(m12); putDouble(0.0)
            putDouble(m21); putDouble(m22); putDouble(0.0)
            putDouble(dx); putDouble(dy); putDouble(1.0)
        }.array()

    private fun identityTransform() = transformPayload(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

    private fun sizePayload(width: Double, height: Double): ByteArray =
        ByteBuffer.allocate(22).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x16); put(0); putDouble(width); putDouble(height); put(0)
        }.array()

    private fun painterPath(width: Double, height: Double): ByteArray {
        val points = listOf(
            Triple(0, -width / 2, -height / 2), Triple(1, width / 2, -height / 2),
            Triple(1, width / 2, height / 2), Triple(1, -width / 2, height / 2),
            Triple(0, -width / 2, -height / 2),
        )
        val type = "QPainterPath".toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(4 + 4 + 1 + type.size + 4 + 1 + points.size * 20).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x400); putInt(0); put((type.size + 1).toByte()); put(type); putInt(0); put(points.size.toByte())
            points.forEach { (kind, x, y) -> putInt(kind); putDouble(x); putDouble(y) }
        }.array()
    }

    private fun sortOrderPayload(id: Long): ByteArray {
        val type = "BigRational".toByteArray(Charsets.UTF_8)
        val value = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0).putInt(1).putInt(0).putInt(1).putInt(id.coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
            .putInt(1).putInt(0).putInt(1).array()
        return ByteBuffer.allocate(9 + type.size + value.size + 1).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0x400); putInt(0); put((type.size + 1).toByte()); put(type); put(value); put(1)
        }.array()
    }

    private fun drawingPayload(strokes: List<CanvasMarkupStroke>): ByteArray {
        val type = "QList<GraphicsDrawItem::Stroke>".toByteArray(Charsets.UTF_8)
        val renderable = strokes.take(255)
        val buffer = ByteBuffer.allocate(
            14 + type.size + renderable.sumOf { 12 + 8 + 4 + it.points.size * 20 + 28 },
        ).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0x400); buffer.putInt(0); buffer.put((type.size + 1).toByte()); buffer.put(type); buffer.putInt(0)
        buffer.put(renderable.size.toByte())
        renderable.forEach { stroke ->
            val argb = stroke.color.toArgb()
            buffer.put(0x64.toByte()); buffer.put(1.toByte())
            buffer.putShort(((argb ushr 24) * stroke.opacity * 257).roundToInt().coerceIn(0,65535).toShort())
            buffer.putShort(((argb ushr 16 and 255)*257).toShort())
            buffer.putShort(((argb ushr 8 and 255)*257).toShort())
            buffer.putShort(((argb and 255)*257).toShort()); buffer.putShort(0)
            buffer.putDouble(max(stroke.lineWidth, 1f).toDouble())
            buffer.putInt(stroke.points.size)
            stroke.points.forEachIndexed { index, point ->
                buffer.putInt(if (index == 0) 0 else 1)
                buffer.putDouble(point.x.toDouble()); buffer.putDouble(point.y.toDouble())
            }
            buffer.putDouble(0.0)
            buffer.putDouble(stroke.points.first().x.toDouble()); buffer.putDouble(stroke.points.first().y.toDouble())
            buffer.putInt(0)
        }
        return buffer.array().copyOf(buffer.position())
    }

    private fun groupColorString(group: SceneGroup): String {
        val argb = group.backgroundColor.toArgb()
        val alpha = (AndroidColor.alpha(argb) * group.backgroundOpacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return "#%02x%02x%02x%02x".format(
            alpha, AndroidColor.red(argb), AndroidColor.green(argb), AndroidColor.blue(argb),
        )
    }

    private fun groupColorJson(color: Color): JSONObject {
        val argb = color.toArgb()
        return JSONObject().apply {
            put("r", AndroidColor.red(argb)); put("g", AndroidColor.green(argb))
            put("b", AndroidColor.blue(argb)); put("a", AndroidColor.alpha(argb))
        }
    }

    private fun formatName(image: SceneImage, bytes: ByteArray): String = when {
        bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) -> "PNG"
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) -> "JPEG"
        bytes.startsWith("GIF87a".toByteArray()) || bytes.startsWith("GIF89a".toByteArray()) -> "gif"
        bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "webp"
        else -> image.mimeType.substringAfter("/", "").substringBefore(';').ifBlank {
            image.name.substringAfterLast('.', "")
        }.ifBlank { "" }
    }

    private fun isRecognizedImage(bytes: ByteArray): Boolean =
        bytes.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) ||
            bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) ||
            bytes.startsWith("GIF87a".toByteArray()) || bytes.startsWith("GIF89a".toByteArray()) ||
            (bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) ||
            (bytes.size >= 12 && bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray()) &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII).startsWith("hei"))

    private fun normalizeImageBytes(image: SceneImage, bytes: ByteArray): ByteArray {
        if (isRecognizedImage(bytes) && !isHeif(bytes)) return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("无法解码 PureRef 不支持的图片格式: ${image.name}")
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "无法将图片转换为 PureRef 支持的 PNG: ${image.name}"
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun isHeif(bytes: ByteArray): Boolean = bytes.size >= 12 &&
        bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray()) &&
        bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII).startsWith("hei")

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val HEADER_SIZE = 108
        const val BUFFER_SIZE = 256 * 1024
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
