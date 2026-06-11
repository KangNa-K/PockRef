package com.pockref.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.CropRect
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class PurSceneCodec {
    fun write(scene: PockRefScene, output: OutputStream) {
        output.write(MAGIC)
        output.writeIntLe(scene.version)
        output.writeStringUtf8(scene.name)
        output.writeLongLe(scene.backgroundColor.value.toLong())
        output.writeIntLe(scene.images.size)
        scene.images.sortedBy { it.zIndex }.forEach { image ->
            val imgBytes = resolveImageBytes(image)
            output.writeStringUtf8(image.id)
            output.writeStringUtf8(image.name)
            output.writeStringUtf8(image.mimeType)
            output.writeIntLe(imgBytes.size)
            output.write(imgBytes)
            output.writeIntLe(image.intrinsicWidth)
            output.writeIntLe(image.intrinsicHeight)
            output.writeFloatLe(image.center.x)
            output.writeFloatLe(image.center.y)
            output.writeFloatLe(image.displaySize.width)
            output.writeFloatLe(image.displaySize.height)
            output.writeFloatLe(image.colorAdjust.brightness)
            output.writeFloatLe(image.colorAdjust.contrast)
            output.writeFloatLe(image.colorAdjust.saturation)
            output.writeFloatLe(image.rotationDegrees)
            output.writeFloatLe(image.scale)
            output.writeBooleanByte(image.flipX)
            output.writeBooleanByte(image.flipY)
            output.writeIntLe(image.zIndex)
            // V2 fields: locked + cropRect
            output.writeBooleanByte(image.locked)
            val crop = image.cropRect
            output.writeBooleanByte(crop != null)
            if (crop != null) {
                output.writeFloatLe(crop.left)
                output.writeFloatLe(crop.top)
                output.writeFloatLe(crop.right)
                output.writeFloatLe(crop.bottom)
            }
        }
    }

    fun writeLegacy(scene: PockRefScene, output: OutputStream) {
        val images = scene.images.sortedBy { it.zIndex }
        val legacy = ByteArrayOutputStream()
        legacy.write(ByteArray(224))

        val imageRecords = images.mapIndexed { index, image ->
            val pngBytes = ensurePngBytes(resolveImageBytes(image))
            require(pngBytes.copyOfRange(0, minOf(pngBytes.size, PNG_SIGNATURE.size)).contentEquals(PNG_SIGNATURE)) {
                "Legacy PockRef writer requires PNG image bytes"
            }
            val start = legacy.size()
            legacy.write(pngBytes)
            val end = legacy.size()
            LegacyImageRecord(
                start = start.toLong(),
                end = end.toLong(),
                transform = LegacyImageTransform(
                    id = index,
                    source = image.name,
                    name = image.name,
                    matrix = matrixForLegacy(image),
                    x = image.center.x.toDouble(),
                    y = image.center.y.toDouble(),
                    zLayer = image.zIndex.toDouble(),
                    matrixBeforeCrop = LEGACY_IDENTITY_MATRIX,
                    xCrop = -(image.intrinsicWidth / 2.0),
                    yCrop = -(image.intrinsicHeight / 2.0),
                    scaleCrop = 1.0,
                    pointX = legacyDefaultPointX(image.intrinsicWidth),
                    pointY = legacyDefaultPointY(image.intrinsicHeight),
                    colorAdjust = image.colorAdjust,
                )
            )
        }

        imageRecords.forEach { record ->
            writeLegacyImageTransform(legacy, record.transform)
        }

        legacy.writeStringUtf16Be(scene.name)
        val fileLength = legacy.size()

        imageRecords.forEach { record ->
            legacy.writeIntBe(record.transform.id)
            legacy.writeLongBe(record.start)
            legacy.writeLongBe(record.end)
        }

        val withRefs = legacy.toByteArray()
        val canvas = legacyCanvasBounds(images)
        ByteBuffer.wrap(withRefs, 0, 4).order(ByteOrder.BIG_ENDIAN).putInt(8)
        "1.10".toByteArray(Charsets.UTF_16BE).copyInto(withRefs, destinationOffset = 4)
        ByteBuffer.wrap(withRefs, 12, 2).order(ByteOrder.BIG_ENDIAN).putShort(images.size.toShort())
        ByteBuffer.wrap(withRefs, 14, 2).order(ByteOrder.BIG_ENDIAN).putShort(images.size.toShort())
        ByteBuffer.wrap(withRefs, 16, 8).order(ByteOrder.BIG_ENDIAN).putLong(fileLength.toLong())
        ByteBuffer.wrap(withRefs, 24, 4).order(ByteOrder.BIG_ENDIAN).putInt(12)
        ByteBuffer.wrap(withRefs, 40, 4).order(ByteOrder.BIG_ENDIAN).putInt(64)
        ByteBuffer.wrap(withRefs, 108, 4).order(ByteOrder.BIG_ENDIAN).putInt(images.size)
        ByteBuffer.wrap(withRefs, 112, 8).order(ByteOrder.BIG_ENDIAN).putDouble(canvas[0])
        ByteBuffer.wrap(withRefs, 120, 8).order(ByteOrder.BIG_ENDIAN).putDouble(canvas[1])
        ByteBuffer.wrap(withRefs, 128, 8).order(ByteOrder.BIG_ENDIAN).putDouble(canvas[2])
        ByteBuffer.wrap(withRefs, 136, 8).order(ByteOrder.BIG_ENDIAN).putDouble(canvas[3])
        ByteBuffer.wrap(withRefs, 144, 8).order(ByteOrder.BIG_ENDIAN).putDouble(1.0)
        ByteBuffer.wrap(withRefs, 176, 8).order(ByteOrder.BIG_ENDIAN).putDouble(1.0)
        ByteBuffer.wrap(withRefs, 208, 8).order(ByteOrder.BIG_ENDIAN).putDouble(1.0)
        ByteBuffer.wrap(withRefs, 216, 4).order(ByteOrder.BIG_ENDIAN).putInt(0)
        ByteBuffer.wrap(withRefs, 220, 4).order(ByteOrder.BIG_ENDIAN).putInt(0)
        val md5 = MessageDigest.getInstance("MD5").digest(withRefs.copyOfRange(108, withRefs.size))
        val md5Hex = md5.joinToString("") { "%02x".format(it) }
        val md5Bytes = md5Hex.toByteArray(Charsets.UTF_16BE)
        System.arraycopy(md5Bytes, 0, withRefs, 44, md5Bytes.size.coerceAtMost(64))
        output.write(withRefs)
    }

    fun writeLegacyStreaming(scene: PockRefScene, output: OutputStream, workDir: File) {
        val images = scene.images.sortedBy { it.zIndex }
        workDir.mkdirs()
        val tempFile = File.createTempFile("pockref_legacy_stream_", ".pur", workDir)
        val imageRecords = mutableListOf<LegacyImageRecord>()

        try {
            RandomAccessFile(tempFile, "rw").use { raf ->
                raf.channel.truncate(0)
                raf.write(ByteArray(224))
                val rafOutput = RandomAccessFileOutputStream(raf)
                val bufferedOutput = java.io.BufferedOutputStream(rafOutput, 256 * 1024)

                images.forEachIndexed { index, image ->
                    val start = raf.filePointer
                    writeLegacyImageBytesStreaming(image, bufferedOutput)
                    bufferedOutput.flush()
                    val end = raf.filePointer
                    val matrix = matrixForLegacy(image)
                    imageRecords += LegacyImageRecord(
                        start = start,
                        end = end,
                        transform = LegacyImageTransform(
                            id = index,
                            source = image.name,
                            name = image.name,
                            matrix = matrix,
                            x = image.center.x.toDouble(),
                            y = image.center.y.toDouble(),
                            zLayer = image.zIndex.toDouble(),
                            matrixBeforeCrop = LEGACY_IDENTITY_MATRIX,
                            xCrop = -(image.intrinsicWidth / 2.0),
                            yCrop = -(image.intrinsicHeight / 2.0),
                            scaleCrop = 1.0,
                            pointX = legacyDefaultPointX(image.intrinsicWidth),
                            pointY = legacyDefaultPointY(image.intrinsicHeight),
                            colorAdjust = image.colorAdjust,
                        )
                    )
                }

                imageRecords.forEach { record ->
                    writeLegacyImageTransform(raf, record.transform)
                }

                raf.writeStringUtf16Be(scene.name)
                // Scene-level metadata block (PockRef-Android extension):
                //   [length:4 BE int] [spacingX:8 BE double] [spacingY:8 BE double] [alignment:4 BE int]
                // Length is the byte count of the payload after the length
                // prefix, so 0 means "no metadata" (legacy desktop .pur
                // files). Readers that see a length > MAX_METADATA_BYTES
                // treat it as garbage and skip; this keeps us safe against
                // misaligned positions when reading files written before
                // this extension.
                val metadataPayload = ByteArray(SCENE_METADATA_PAYLOAD_SIZE)
                val payloadView = java.nio.ByteBuffer.wrap(metadataPayload).order(java.nio.ByteOrder.BIG_ENDIAN)
                payloadView.putDouble(scene.defaultSpacing.width.toDouble())
                payloadView.putDouble(scene.defaultSpacing.height.toDouble())
                payloadView.putInt(scene.defaultAlignment.ordinal)
                raf.writeIntBe(metadataPayload.size)
                raf.write(metadataPayload)

                val fileLength = raf.filePointer

                imageRecords.forEach { record ->
                    raf.writeIntBe(record.transform.id)
                    raf.writeLongBe(record.start)
                    raf.writeLongBe(record.end)
                }

                patchLegacyHeader(raf, images, fileLength)
            }

            tempFile.inputStream().use { input ->
                val bufferedInput = java.io.BufferedInputStream(input, 256 * 1024)
                bufferedInput.copyTo(output, 256 * 1024)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun writeLegacyImageBytesStreaming(image: SceneImage, output: OutputStream) {
        val bytes = resolveImageBytes(image)
        if (bytes.isEmpty()) error("No image data for ${image.name}")

        val isPng = bytes.size >= PNG_SIGNATURE.size &&
            bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
        val isJpeg = !isPng && bytes.size >= JPEG_SIGNATURE.size &&
            bytes.copyOfRange(0, JPEG_SIGNATURE.size).contentEquals(JPEG_SIGNATURE)
        val isWebp = !isPng && !isJpeg && bytes.size >= 4 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()

        if (isPng || isJpeg || isWebp) {
            output.write(bytes)
        } else {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Unable to decode ${image.name}")
            try {
                require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Unable to encode ${image.name} as PNG"
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun openImageInputStream(image: SceneImage): InputStream {
        if (image.imageBytes.isNotEmpty()) return ByteArrayInputStream(image.imageBytes)
        if (image.imageByteOffset >= 0 && image.imageByteLength > 0) {
            return purFileParser.openImageInputStream(image.imageByteOffset, image.imageByteLength)
                ?: error("Unable to read image data for ${image.name}")
        }
        error("No image data available for ${image.name}")
    }

    private fun patchLegacyHeader(raf: RandomAccessFile, images: List<SceneImage>, fileLength: Long) {
        val canvas = legacyCanvasBounds(images)
        raf.seek(0)
        raf.writeIntBe(8)
        raf.seek(4)
        raf.write("1.10".toByteArray(Charsets.UTF_16BE))
        raf.seek(12)
        raf.writeShortBe(images.size)
        raf.writeShortBe(images.size)
        raf.writeLongBe(fileLength)
        raf.writeIntBe(12)
        raf.seek(40)
        raf.writeIntBe(64)
        raf.seek(108)
        raf.writeIntBe(images.size)
        raf.writeDoubleBe(canvas[0])
        raf.writeDoubleBe(canvas[1])
        raf.writeDoubleBe(canvas[2])
        raf.writeDoubleBe(canvas[3])
        raf.writeDoubleBe(1.0)
        raf.seek(176)
        raf.writeDoubleBe(1.0)
        raf.seek(208)
        raf.writeDoubleBe(1.0)
        raf.writeIntBe(0)
        raf.writeIntBe(0)

        val md5 = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(65536)
        raf.seek(108)
        var remaining = raf.length() - 108
        while (remaining > 0) {
            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
            raf.readFully(buffer, 0, toRead)
            md5.update(buffer, 0, toRead)
            remaining -= toRead
        }
        val md5Hex = md5.digest().joinToString("") { "%02x".format(it) }
        val md5Bytes = md5Hex.toByteArray(Charsets.UTF_16BE)
        raf.seek(44)
        raf.write(md5Bytes, 0, md5Bytes.size.coerceAtMost(64))
    }

    private val purFileParser = PurFileParser()

    val imageDecoder = SceneImageDecoder(null /* cacheDir set lazily via initCacheDir */)

    private var _cacheDir: File? = null

    fun initCacheDir(cacheDir: File) {
        _cacheDir = cacheDir
        imageDecoder.setCacheDir(cacheDir)
    }

    val imageLazyLoader: PurFileParser get() = purFileParser

    private fun resolveImageBytes(image: SceneImage): ByteArray {
        if (image.imageBytes.isNotEmpty()) return image.imageBytes
        if (image.imageByteOffset >= 0 && image.imageByteLength > 0) {
            // Prefer the SceneImageDecoder's RAF (same handle, same lifecycle)
            imageDecoder.readImageBytes(image.imageByteOffset, image.imageByteLength)?.let { return it }
            return purFileParser.readImageBytes(image.imageByteOffset, image.imageByteLength)
                ?: error("Unable to read image data for ${image.name}")
        }
        error("No image data available for ${image.name}")
    }

    fun read(input: InputStream): PockRefScene {
        val tempFile = File.createTempFile("pockref_input_", ".tmp")
        tempFile.deleteOnExit()
        try {
            val copiedBytes = tempFile.outputStream().use { out -> input.copyTo(out, 65536) }
            require(copiedBytes > 0L) { "Input stream produced 0 bytes" }
            return readFromFile(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    fun readFromFile(file: File): PockRefScene {
        val raf = RandomAccessFile(file, "r")
        try {
            val fileLen = raf.length()
            require(fileLen > 0L) { "Input file is empty" }
            val headerSize = minOf(fileLen.toInt(), 108)
            val header = ByteArray(headerSize)
            raf.readFully(header)

            if (header.hasPrefix(MAGIC)) {
                raf.seek(0)
                val bytes = readAllBytes(raf, fileLen)
                return readCustomScene(bytes)
            }

            if (looksLikeDesktopPur(raf, header)) {
                try {
                    val scene = purFileParser.parseFromFile(file)
                    purFileParser.bindSourceFile(file)
                    imageDecoder.setSourceFile(file)
                    return scene
                } catch (e: Exception) {
                    Log.w(TAG, "Structured desktop .pur parser failed, trying embedded image fallback", e)
                    tryReadEmbeddedImagesFallback(file, fileLen)?.let { return it }
                    throw e
                }
            }

            if (looksLikeLegacyPur(header, fileLen)) {
                // For large files, skip the readAllBytes path entirely —
                // it causes OOM. Go straight to the streaming parser.
                if (fileLen > 100_000_000L) {
                    Log.i(TAG, "Large legacy file ($fileLen bytes), using streaming parser")
                    return try {
                        val scene = purFileParser.parseFromFile(file)
                        purFileParser.bindSourceFile(file)
                        imageDecoder.setSourceFile(file)
                        scene
                    } catch (e2: Exception) {
                        Log.w(TAG, "Desktop parser failed for large legacy file", e2)
                        tryReadEmbeddedImagesFallback(file, fileLen)?.let { return it }
                        throw e2
                    }
                }
                raf.seek(0)
                val allBytes = try {
                    readAllBytes(raf, fileLen)
                } catch (oom: OutOfMemoryError) {
                    Log.w(TAG, "OOM reading $fileLen bytes for legacy parser; falling back", oom)
                    return try {
                        val scene = purFileParser.parseFromFile(file)
                        purFileParser.bindSourceFile(file)
                        imageDecoder.setSourceFile(file)
                        scene
                    } catch (e2: Exception) {
                        Log.w(TAG, "Desktop parser also failed for large file", e2)
                        throw oom
                    }
                }
                try {
                    return readLegacyPurScene(allBytes)
                } catch (e: Exception) {
                    Log.w(TAG, "Legacy parser failed, trying desktop parser", e)
                    try {
                        val scene = purFileParser.parseFromFile(file)
                        purFileParser.bindSourceFile(file)
                        imageDecoder.setSourceFile(file)
                        return scene
                    } catch (e2: Exception) {
                        Log.w(TAG, "Desktop parser also failed", e2)
                        throw e
                    }
                }
            }

            Log.w(TAG, "All format detection methods failed, trying parseFromFile fallback")
            try {
                val scene = purFileParser.parseFromFile(file)
                purFileParser.bindSourceFile(file)
                imageDecoder.setSourceFile(file)
                return scene
            } catch (e: Exception) {
                Log.e(TAG, "parseFromFile fallback also failed", e)
                tryReadEmbeddedImagesFallback(file, fileLen)?.let { return it }
                val hexDump = header.take(32).joinToString(" ") { "%02x".format(it) }
                error("Unsupported file header: $hexDump (file size: $fileLen bytes)")
            }
        } finally {
            raf.close()
        }
    }

    private fun readAllBytes(raf: RandomAccessFile, fileLen: Long): ByteArray {
        require(fileLen <= Int.MAX_VALUE) { "File too large to read eagerly: $fileLen bytes" }
        val bytes = ByteArray(fileLen.toInt())
        raf.seek(0)
        raf.readFully(bytes)
        return bytes
    }

    private fun tryReadEmbeddedImagesFallback(file: File, fileLen: Long): PockRefScene? {
        if (fileLen <= 0L || fileLen > EMBEDDED_IMAGE_FALLBACK_MAX_BYTES) return null
        return runCatching {
            val bytes = file.readBytes()
            readEmbeddedImagesScene(bytes).takeIf { it.images.isNotEmpty() }
        }.onFailure {
            Log.w(TAG, "Embedded image fallback failed", it)
        }.getOrNull()
    }

    private fun looksLikeDesktopPur(raf: RandomAccessFile, header: ByteArray): Boolean {
        if (header.size < HEADER_MIN_SIZE) return false
        try {
            val parsedHeader = purFileParser.parseHeader(header, raf.length())
            if (parsedHeader.numImages > 0
                && parsedHeader.sqliteOffset > 0 && parsedHeader.sqliteOffset < raf.length()
            ) {
                val indexOffset = parsedHeader.sqliteOffset
                val indexSize = (raf.length() - indexOffset).toInt()
                val desktopEntrySize = 12
                val legacyEntrySize = 20
                val desktopEntries = indexSize / desktopEntrySize
                val legacyEntries = indexSize / legacyEntrySize
                val isLikelyDesktopV1 = if (desktopEntries > 0 && desktopEntries == parsedHeader.numImages) {
                    try {
                        raf.seek(indexOffset)
                        val firstEntry = ByteArray(desktopEntrySize)
                        raf.readFully(firstEntry)
                        val startOffset = readIntBe(firstEntry, 4)
                        val endOffset = readIntBe(firstEntry, 8)
                        startOffset > 0 && endOffset > startOffset && endOffset <= indexOffset
                    } catch (_: Exception) { false }
                } else false
                val isLikelyLegacyV1 = if (legacyEntries > 0 && legacyEntries == parsedHeader.numImages) {
                    try {
                        raf.seek(indexOffset)
                        val firstEntry = ByteArray(legacyEntrySize)
                        raf.readFully(firstEntry)
                        val startOffset = readLongBe(firstEntry, 4)
                        val endOffset = readLongBe(firstEntry, 12)
                        startOffset > 0 && endOffset > startOffset && endOffset <= indexOffset
                    } catch (_: Exception) { false }
                } else false
                if (isLikelyDesktopV1 || isLikelyLegacyV1) {
                    return true
                }
            }
        } catch (e: Exception) {
        }
        return false
    }

    private fun readCustomScene(bytes: ByteArray): PockRefScene {
        val data = DataInputStream(ByteArrayInputStream(bytes, MAGIC.size, bytes.size - MAGIC.size))
        val version = data.readIntLe()
        val name = data.readStringUtf8()
        val backgroundColor = Color(data.readLongLe().toULong())
        val imageCount = data.readIntLe()
        val images = buildList(imageCount) {
            repeat(imageCount) {
                val base = SceneImage(
                    id = data.readStringUtf8(),
                    name = data.readStringUtf8(),
                    mimeType = data.readStringUtf8(),
                    imageBytes = data.readByteArray(),
                    intrinsicWidth = data.readIntLe(),
                    intrinsicHeight = data.readIntLe(),
                    center = Offset(data.readFloatLe(), data.readFloatLe()),
                    displaySize = Size2D(data.readFloatLe(), data.readFloatLe()),
                    colorAdjust = ColorAdjust(
                        brightness = data.readFloatLe(),
                        contrast = data.readFloatLe(),
                        saturation = data.readFloatLe(),
                    ),
                    rotationDegrees = data.readFloatLe(),
                    scale = data.readFloatLe(),
                    flipX = data.readBooleanByte(),
                    flipY = data.readBooleanByte(),
                    zIndex = data.readIntLe(),
                )
                // V2 fields: locked + cropRect. Version 1 files
                // don't have these — they default to false / null.
                val img = if (version >= 2) {
                    val locked = data.readBooleanByte()
                    val hasCrop = data.readBooleanByte()
                    val cropRect = if (hasCrop) {
                        CropRect(
                            left = data.readFloatLe(),
                            top = data.readFloatLe(),
                            right = data.readFloatLe(),
                            bottom = data.readFloatLe(),
                        )
                    } else null
                    base.copy(locked = locked, cropRect = cropRect)
                } else {
                    base
                }
                add(img)
            }
        }
        return PockRefScene(version = version, name = name, backgroundColor = backgroundColor, images = images)
    }

    private fun readLegacyPurScene(bytes: ByteArray): PockRefScene {
        require(bytes.size >= 224) { "Invalid legacy PockRef file" }
        val fileType = readIntBe(bytes, 0)
        require(fileType == 8) { "Not a legacy PockRef file" }
        val imageCount = readUnsignedShortBe(bytes, 14)
        val transformIdCount = readIntBe(bytes, 108)
        val indexOffset = readLongBe(bytes, 16)

        val indexEntrySize = 20
        val indexEntries = mutableListOf<Triple<Int, Long, Long>>()
        if (indexOffset > 0 && indexOffset + imageCount * indexEntrySize <= bytes.size) {
            var idxCursor = indexOffset.toInt()
            repeat(imageCount) {
                val id = readIntBe(bytes, idxCursor)
                val start = readLongBe(bytes, idxCursor + 4)
                val end = readLongBe(bytes, idxCursor + 12)
                indexEntries.add(Triple(id, start, end))
                idxCursor += indexEntrySize
            }
        } else {
            Log.w(TAG, "readLegacyPurScene: invalid indexOffset=$indexOffset, falling back to signature detection")
        }

        val imageBlobs = mutableListOf<LegacyImageBlob>()
        if (indexEntries.size == imageCount) {
            for ((id, start, end) in indexEntries) {
                if (start >= 0 && end > start && end <= bytes.size) {
                    val blob = bytes.copyOfRange(start.toInt(), end.toInt())
                    imageBlobs += LegacyImageBlob(start, end, blob)
                } else {
                    Log.w(TAG, "readLegacyPurScene: invalid blob range start=$start end=$end")
                }
            }
        } else {
            var cursor = 224
            repeat(imageCount) {
                val start = cursor
                val pngEnd = findPngEnd(bytes, cursor)
                val jpegEnd = findJpegEnd(bytes, cursor)
                val webpEnd = findWebpEnd(bytes, cursor)
                val end = when {
                    pngEnd != null && jpegEnd != null && webpEnd != null -> minOf(pngEnd, jpegEnd, webpEnd)
                    pngEnd != null && jpegEnd != null -> minOf(pngEnd, jpegEnd)
                    pngEnd != null && webpEnd != null -> minOf(pngEnd, webpEnd)
                    jpegEnd != null && webpEnd != null -> minOf(jpegEnd, webpEnd)
                    pngEnd != null -> pngEnd
                    jpegEnd != null -> jpegEnd
                    webpEnd != null -> webpEnd
                    else -> error("Missing image data at offset $cursor")
                }
                val imageBytes = bytes.copyOfRange(start, end)
                imageBlobs += LegacyImageBlob(start.toLong(), end.toLong(), imageBytes)
                cursor = end
            }
        }

        var cursor = if (imageBlobs.isNotEmpty()) {
            imageBlobs.maxOf { it.end.toInt() }
        } else {
            224
        }

        val transforms = mutableListOf<LegacyImageTransform>()
        run loop@{
            repeat(imageCount) { idx ->
                if (cursor + 12 > bytes.size) {
                    Log.e(TAG, "readLegacyPurScene: cursor=$cursor out of bounds at transform $idx (size=${bytes.size})")
                    return@loop
                }
                val endAddress = readLongBe(bytes, cursor)
                cursor += 8
                val type = readIntBe(bytes, cursor)
                cursor += 4
                if (type !in 1..1000) {
                    Log.e(TAG, "readLegacyPurScene: invalid type=$type at transform $idx cursor=$cursor, aborting")
                    return@loop
                }
                val standardTextLength = type
                cursor += standardTextLength
                val source = readStringUtf16Be(bytes, cursor).also { cursor += 4 + it.byteLengthUtf16Be() }
                val name = readStringUtf16Be(bytes, cursor).also { cursor += 4 + it.byteLengthUtf16Be() }
                cursor += 8
                val matrix = readLegacyMatrix(bytes, cursor)
                cursor += 48
                val x = readDoubleBe(bytes, cursor)
                cursor += 8
                val y = readDoubleBe(bytes, cursor)
                cursor += 8
                cursor += 8
                val id = readIntBe(bytes, cursor)
                cursor += 4
                val zLayer = readDoubleBe(bytes, cursor)
                cursor += 8
                val matrixBeforeCrop = readLegacyMatrix(bytes, cursor)
                cursor += 48
                val xCrop = readDoubleBe(bytes, cursor)
                cursor += 8
                val yCrop = readDoubleBe(bytes, cursor)
                cursor += 8
                val scaleCrop = readDoubleBe(bytes, cursor)
                cursor += 8
                val pointCount = readIntBe(bytes, cursor)
                cursor += 4
                val pointX = mutableListOf<Double>()
                val pointY = mutableListOf<Double>()
                repeat(pointCount) {
                    cursor += 4
                    pointX += readDoubleBe(bytes, cursor)
                    cursor += 8
                    pointY += readDoubleBe(bytes, cursor)
                    cursor += 8
                }
                val colorAdjust = if (cursor + 28 <= endAddress && readIntBe(bytes, cursor) == COLOR_ADJUST_SENTINEL) {
                    com.pockref.app.model.ColorAdjust(
                        brightness = readDoubleBe(bytes, cursor + 4).toFloat(),
                        contrast = readDoubleBe(bytes, cursor + 12).toFloat(),
                        saturation = readDoubleBe(bytes, cursor + 20).toFloat(),
                    )
                } else {
                    com.pockref.app.model.ColorAdjust()
                }
                cursor += 8 + 4 + 1 + 8 + 4
                cursor = endAddress.toInt()
                transforms += LegacyImageTransform(
                    id = id,
                    source = source,
                    name = name,
                    matrix = matrix,
                    x = x,
                    y = y,
                    zLayer = zLayer,
                    matrixBeforeCrop = matrixBeforeCrop,
                    xCrop = xCrop,
                    yCrop = yCrop,
                    scaleCrop = scaleCrop,
                    pointX = pointX,
                    pointY = pointY,
                    colorAdjust = colorAdjust,
                )
            }
        }

        val folderLocation = readStringUtf16Be(bytes, cursor).also { cursor += 4 + it.byteLengthUtf16Be() }

        // Scene-level metadata block (PockRef-Android extension). The
        // length prefix is bounded — anything > MAX_SCENE_METADATA_BYTES
        // is treated as garbage (a legacy desktop file has nothing here,
        // so the bytes at this position are UTF-16 scene-name tail
        // fragments, not a real length). In that case we keep the
        // default spacing/alignment and rewind the cursor so the index
        // area is read from the position the file actually uses.
        var defaultSpacing = com.pockref.app.model.Size2D(8f, 8f)
        var defaultAlignment = com.pockref.app.model.ImageAlignment.CENTER
        if (cursor + 4 <= bytes.size) {
            val metadataLen = readIntBe(bytes, cursor)
            if (metadataLen in 1..MAX_SCENE_METADATA_BYTES && cursor + 4 + metadataLen <= bytes.size) {
                val payloadStart = cursor + 4
                if (metadataLen == SCENE_METADATA_PAYLOAD_SIZE) {
                    val payloadBuf = java.nio.ByteBuffer.wrap(bytes, payloadStart, metadataLen)
                        .order(java.nio.ByteOrder.BIG_ENDIAN)
                    defaultSpacing = com.pockref.app.model.Size2D(
                        width = payloadBuf.getDouble().toFloat(),
                        height = payloadBuf.getDouble().toFloat(),
                    )
                    val alignmentOrdinal = payloadBuf.getInt()
                    val candidates = com.pockref.app.model.ImageAlignment.entries
                    defaultAlignment = candidates.getOrNull(alignmentOrdinal) ?: defaultAlignment
                }
                cursor = payloadStart + metadataLen
            }
        }

        val images = mutableListOf<SceneImage>()
        repeat(transformIdCount) {
            val id = readIntBe(bytes, cursor)
            cursor += 4
            val start = readLongBe(bytes, cursor)
            cursor += 8
            val end = readLongBe(bytes, cursor)
            cursor += 8
            val blob = imageBlobs.firstOrNull { it.start == start && it.end == end } ?: return@repeat
            val transform = transforms.firstOrNull { it.id == id } ?: return@repeat
            val size = probeRasterSize(blob.bytes)
            val isPng = blob.bytes.size >= PNG_SIGNATURE.size &&
                blob.bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)
            val isWebp = !isPng && blob.bytes.size >= WEBP_RIFF_SIGNATURE.size + 4 &&
                blob.bytes.copyOfRange(0, WEBP_RIFF_SIGNATURE.size).contentEquals(WEBP_RIFF_SIGNATURE) &&
                blob.bytes.copyOfRange(8, 12).contentEquals(WEBP_MARKER)
            val display = displayFromMatrix(transform.matrix, size.first, size.second)
            if (images.size < 3) {
            }
            // Diagnostic: also log for images 3..7 so a typical 8-image scene
            // shows full coverage. Comment out after debugging.
            if (images.size in 3..7) {
            }
            images += SceneImage(
                id = id.toString(),
                name = transform.name.ifBlank { transform.source.ifBlank { "image_$id" } },
                mimeType = when {
                    isPng -> "image/png"
                    isWebp -> "image/webp"
                    else -> "image/jpeg"
                },
                imageBytes = blob.bytes,
                intrinsicWidth = size.first,
                intrinsicHeight = size.second,
                center = Offset(transform.x.toFloat(), transform.y.toFloat()),
                displaySize = Size2D(display.first, display.second),
                colorAdjust = transform.colorAdjust,
                rotationDegrees = rotationFromMatrix(transform.matrix),
                scale = 1f,
                flipX = transform.matrix[0] < 0,
                flipY = transform.matrix[3] < 0,
                zIndex = transform.zLayer.toInt(),
            )
        }

        return PockRefScene(
            version = 1,
            // [folderLocation] is the V1 .pur's "where this file
            // was exported from" metadata, NOT a user-set scene
            // name. The legacy desktop format never had a real
            // scene-name field, so most real-world .pur files
            // have a blank or stale folderLocation. Previously
            // we fell back to the "Desktop PockRef Import"
            // placeholder, which won over the call site's
            // [SceneNaming.fromPurFileName] file-name fallback
            // and pinned the title to the placeholder. Returning
            // blank here lets the [loadScene] call site use the
            // file's display name (e.g. "MyProject" for
            // "MyProject.pur") — matching user expectation that
            // the title reflects the file they opened.
            name = folderLocation.ifBlank { "" },
            backgroundColor = Color(0xFF111111),
            images = images.sortedBy { it.zIndex },
            defaultSpacing = defaultSpacing,
            defaultAlignment = defaultAlignment,
        )
    }

    private fun readEmbeddedImagesScene(bytes: ByteArray): PockRefScene {
        val images = mutableListOf<SceneImage>()
        var cursor = 0
        while (cursor < bytes.size) {
            val pngStart = bytes.indexOfSubArray(PNG_SIGNATURE, cursor)
            val jpegStart = bytes.indexOfSubArray(JPEG_SIGNATURE, cursor)
            val nextStart = when {
                pngStart == null -> jpegStart
                jpegStart == null -> pngStart
                else -> minOf(pngStart, jpegStart)
            } ?: break

            val isPng = pngStart == nextStart
            val end = if (isPng) {
                findPngEnd(bytes, nextStart)
            } else {
                findJpegEnd(bytes, nextStart)
            } ?: break

            val imageBytes = bytes.copyOfRange(nextStart, end)
            val size = probeRasterSize(imageBytes)
            val displayWidth = size.first.toFloat().coerceAtMost(720f)
            val displayHeight = size.second.toFloat() * (displayWidth / size.first.toFloat().coerceAtLeast(1f))
            val index = images.size
            images += SceneImage(
                id = index.toString(),
                name = "image_$index",
                mimeType = if (isPng) "image/png" else "image/jpeg",
                imageBytes = imageBytes,
                intrinsicWidth = size.first,
                intrinsicHeight = size.second,
                center = Offset(160f + index * 80f, 160f + index * 60f),
                displaySize = Size2D(displayWidth, displayHeight.coerceAtLeast(64f)),
                colorAdjust = ColorAdjust(),
                zIndex = index,
            )
            cursor = end
        }

        return PockRefScene(
            version = 1,
            // [name=""] — this is the "file is so malformed
            // we could only pull out the embedded images"
            // fallback path ([tryReadEmbeddedImagesFallback]).
            // Returning blank lets the [loadScene] call site
            // use the file's display name as the title via
            // [SceneNaming.fromPurFileName] (matched the user's
            // "title should show the opened .pur file's name"
            // bug report). The old "Desktop PockRef Import"
            // placeholder was misleading — the user picked
            // this file, the title should reflect the file.
            name = "",
            backgroundColor = Color(0xFF111111),
            images = images,
        )
    }

    private fun looksLikeDesktopPur(bytes: ByteArray): Boolean {
        if (bytes.size < HEADER_MIN_SIZE) return false
        return false
    }

    private fun looksLikeLegacyPur(bytes: ByteArray, fileLength: Long = Long.MAX_VALUE): Boolean {
        if (bytes.size < 14 || readIntBe(bytes, 0) != 8) return false
        val imageCount = readUnsignedShortBe(bytes, 12)
        if (imageCount == 0 || imageCount > 10000) return false
        if (bytes.size >= 12) {
            val versionBe = decodeUtf16Be(bytes.copyOfRange(4, 12)).trim('\u0000')
            if (!versionBe.matches(Regex("""\d+\.\d+.*"""))) return false
        }
        return true
    }

    private fun probePngSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 24) return null
        if (!bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return null
        val width = readIntBe(bytes, 16)
        val height = readIntBe(bytes, 20)
        return if (width > 0 && height > 0) width to height else null
    }

    private fun findPngEnd(bytes: ByteArray, fromIndex: Int): Int? {
        val footerIndex = bytes.indexOfSubArray(PNG_FOOTER, fromIndex)
        return footerIndex?.plus(PNG_FOOTER.size)
    }

    private fun findJpegEnd(bytes: ByteArray, fromIndex: Int): Int? {
        val footerIndex = bytes.indexOfSubArray(JPEG_FOOTER, fromIndex + JPEG_SIGNATURE.size)
        return footerIndex?.plus(JPEG_FOOTER.size)
    }

    private fun findWebpEnd(bytes: ByteArray, fromIndex: Int): Int? {
        if (fromIndex + 12 > bytes.size) return null
        if (!bytes.copyOfRange(fromIndex, fromIndex + 4).contentEquals(WEBP_RIFF_SIGNATURE)) return null
        if (!bytes.copyOfRange(fromIndex + 8, fromIndex + 12).contentEquals(WEBP_MARKER)) return null
        val riffSize = ((bytes[fromIndex + 4].toInt() and 0xFF)) or
            ((bytes[fromIndex + 5].toInt() and 0xFF) shl 8) or
            ((bytes[fromIndex + 6].toInt() and 0xFF) shl 16) or
            ((bytes[fromIndex + 7].toInt() and 0xFF) shl 24)
        val totalSize = 8 + riffSize
        val endIndex = fromIndex + totalSize
        if (endIndex > bytes.size) return bytes.size
        return endIndex
    }

    private fun probeRasterSize(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            return options.outWidth to options.outHeight
        }
        return probePngSize(bytes) ?: probeJpegSize(bytes) ?: (1024 to 1024)
    }

    private fun probeJpegSize(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 4 || !bytes.copyOfRange(0, 2).contentEquals(JPEG_SIGNATURE)) return null
        var offset = 2
        while (offset + 4 < bytes.size) {
            while (offset < bytes.size && bytes[offset] != 0xFF.toByte()) offset++
            while (offset < bytes.size && bytes[offset] == 0xFF.toByte()) offset++
            if (offset >= bytes.size) return null
            val marker = bytes[offset].toInt() and 0xFF
            offset++

            if (marker == 0xD9 || marker == 0xDA) return null
            if (marker == 0x01 || marker in 0xD0..0xD7) continue
            if (offset + 2 > bytes.size) return null

            val segmentLength = readUnsignedShortBe(bytes, offset)
            if (segmentLength < 2 || offset + segmentLength > bytes.size) return null

            if (marker in JPEG_SIZE_MARKERS && offset + 7 <= bytes.size) {
                val height = readUnsignedShortBe(bytes, offset + 3)
                val width = readUnsignedShortBe(bytes, offset + 5)
                if (width > 0 && height > 0) return width to height
            }
            offset += segmentLength
        }
        return null
    }

    @androidx.annotation.VisibleForTesting
    internal fun displayFromMatrixForTest(matrix: List<Double>, intrinsicWidth: Int, intrinsicHeight: Int): Pair<Float, Float> =
        displayFromMatrix(matrix, intrinsicWidth, intrinsicHeight)

    private fun displayFromMatrix(matrix: List<Double>, intrinsicWidth: Int, intrinsicHeight: Int): Pair<Float, Float> {
        val scaleX = hypot(matrix[0], matrix[2]) * intrinsicWidth
        val scaleY = hypot(matrix[1], matrix[3]) * intrinsicHeight
        return scaleX.toFloat() to scaleY.toFloat()
    }

    private fun rotationFromMatrix(matrix: List<Double>): Float {
        return Math.toDegrees(atan2(matrix[2], matrix[0])).toFloat()
    }

    @androidx.annotation.VisibleForTesting
    internal fun matrixForLegacyForTest(image: SceneImage): List<Double> =
        matrixForLegacy(image)

    private fun matrixForLegacy(image: SceneImage): List<Double> {
        val baseScaleX = image.displaySize.width * image.scale / image.intrinsicWidth.toFloat().coerceAtLeast(1f)
        val baseScaleY = image.displaySize.height * image.scale / image.intrinsicHeight.toFloat().coerceAtLeast(1f)
        val theta = Math.toRadians(image.rotationDegrees.toDouble())
        val cosTheta = cos(theta)
        val sinTheta = sin(theta)
        val flipX = if (image.flipX) -1.0 else 1.0
        val flipY = if (image.flipY) -1.0 else 1.0
        return listOf(
            baseScaleX.toDouble() * cosTheta * flipX,
            -baseScaleY.toDouble() * sinTheta * flipY,
            baseScaleX.toDouble() * sinTheta * flipX,
            baseScaleY.toDouble() * cosTheta * flipY,
        )
    }

    private fun legacyCanvasBounds(images: List<SceneImage>): List<Double> {
        if (images.isEmpty()) return listOf(-10000.0, -10000.0, 10000.0, 10000.0)
        val left = images.minOf { it.center.x - (it.displaySize.width * it.scale) / 2f }.toDouble() - 1000.0
        val top = images.minOf { it.center.y - (it.displaySize.height * it.scale) / 2f }.toDouble() - 1000.0
        val right = images.maxOf { it.center.x + (it.displaySize.width * it.scale) / 2f }.toDouble() + 1000.0
        val bottom = images.maxOf { it.center.y + (it.displaySize.height * it.scale) / 2f }.toDouble() + 1000.0
        return listOf(left, top, right, bottom)
    }

    private fun legacyDefaultPointX(width: Int): List<Double> {
        val halfWidth = width / 2.0
        return listOf(-halfWidth, halfWidth, halfWidth, -halfWidth, -halfWidth)
    }

    private fun legacyDefaultPointY(height: Int): List<Double> {
        val halfHeight = height / 2.0
        return listOf(-halfHeight, -halfHeight, halfHeight, halfHeight, -halfHeight)
    }

    private fun writeLegacyImageTransform(output: ByteArrayOutputStream, transform: LegacyImageTransform) {
        val transformEnd = output.size()
        output.writeLongBe(0L)
        output.writeIntBe(34)
        output.write("GraphicsImageItem".toByteArray(Charsets.UTF_16BE))
        output.writeStringUtf16Be(transform.source)
        output.writeStringUtf16Be(transform.name)
        output.writeDoubleBe(1.0)
        output.writeLegacyMatrix(transform.matrix)
        output.writeDoubleBe(transform.x)
        output.writeDoubleBe(transform.y)
        output.writeDoubleBe(1.0)
        output.writeIntBe(transform.id)
        output.writeDoubleBe(transform.zLayer)
        output.writeLegacyMatrix(transform.matrixBeforeCrop)
        output.writeDoubleBe(transform.xCrop)
        output.writeDoubleBe(transform.yCrop)
        output.writeDoubleBe(transform.scaleCrop)
        output.writeIntBe(transform.pointX.size)
        transform.pointX.indices.forEach { index ->
            output.writeIntBe(if (index == 0) 0 else 1)
            output.writeDoubleBe(transform.pointX[index])
            output.writeDoubleBe(transform.pointY[index])
        }
        // colorAdjust is not in the original desktop .pur format but is a
        // real user-tweakable field on SceneImage (brightness / contrast /
        // saturation). We append it just before the trailer bytes; readers
        // skip over the unknown bytes via the endAddress stored at the start
        // of each transform block, so legacy desktop readers ignore it
        // cleanly while Android rehydrates it.
        //
        // The 4-byte COLOR_ADJUST_SENTINEL ("PRAC" — PockRef Android
        // Coloradjust) before the three doubles lets the reader distinguish
        // an Android-written transform from a desktop file: desktop's last
        // 25 bytes are a writeDoubleBe(0.0) + writeIntBe(1) + write(0) +
        // writeLongBe(-1L) + writeIntBe(0) trailer, and the first 4 of
        // those (the 0.0 double) will never match 0x50524341, so the
        // reader falls back to ColorAdjust() instead of deserialising
        // misaligned brightness / contrast / saturation doubles that
        // could yield saturation = -1 and turn the image grayscale.
        output.writeIntBe(COLOR_ADJUST_SENTINEL)
        output.writeDoubleBe(transform.colorAdjust.brightness.toDouble())
        output.writeDoubleBe(transform.colorAdjust.contrast.toDouble())
        output.writeDoubleBe(transform.colorAdjust.saturation.toDouble())
        output.writeDoubleBe(0.0)
        output.writeIntBe(1)
        output.write(0)
        output.writeLongBe(-1L)
        output.writeIntBe(0)
        val end = output.size()
        val bytes = output.toByteArray()
        ByteBuffer.wrap(bytes, transformEnd, 8).order(ByteOrder.BIG_ENDIAN).putLong(end.toLong())
        output.reset()
        output.write(bytes)
    }

    private fun ByteArrayOutputStream.writeLegacyMatrix(matrix: List<Double>) {
        writeDoubleBe(matrix[0])
        writeDoubleBe(matrix[1])
        writeDoubleBe(0.0)
        writeDoubleBe(matrix[2])
        writeDoubleBe(matrix[3])
        writeDoubleBe(0.0)
    }

    private fun ensurePngBytes(bytes: ByteArray): ByteArray {
        if (bytes.copyOfRange(0, minOf(bytes.size, 8)).contentEquals(PNG_SIGNATURE)) return bytes
        return bytes
    }



    private fun decodeUtf16Be(bytes: ByteArray): String {
        val chars = CharArray(bytes.size / 2)
        var charIndex = 0
        var index = 0
        while (index < bytes.size - 1) {
            val codePoint = ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
            chars[charIndex++] = codePoint.toChar()
            index += 2
        }
        return String(chars, 0, charIndex)
    }

    private fun DataInputStream.readByteArray(): ByteArray {
        val size = readIntLe()
        if (size < 0) {
            throw EOFException("Negative byte array size")
        }
        return ByteArray(size).also(::readFully)
    }

    private fun DataInputStream.readStringUtf8(): String {
        val size = readIntLe()
        if (size < 0) {
            throw EOFException("Negative string size")
        }
        val bytes = ByteArray(size)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataInputStream.readIntLe(): Int {
        val bytes = ByteArray(4)
        readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun DataInputStream.readLongLe(): Long {
        val bytes = ByteArray(8)
        readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun DataInputStream.readFloatLe(): Float {
        val bytes = ByteArray(4)
        readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float
    }

    private fun DataInputStream.readBooleanByte(): Boolean = readUnsignedByte() == 1

    private fun OutputStream.writeStringUtf8(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeIntLe(bytes.size)
        write(bytes)
    }

    private fun OutputStream.writeIntLe(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun OutputStream.writeLongLe(value: Long) {
        write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array())
    }

    private fun OutputStream.writeFloatLe(value: Float) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array())
    }

    private fun OutputStream.writeBooleanByte(value: Boolean) {
        write(if (value) 1 else 0)
    }

    private fun OutputStream.writeDoubleBe(value: Double) {
        write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(value).array())
    }

    private fun OutputStream.writeShortBe(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array())
    }

    private fun OutputStream.writeStringUtf16Be(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_16BE)
        writeIntBe(bytes.size)
        write(bytes)
    }

    private fun OutputStream.writeLegacyMatrix(matrix: List<Double>) {
        writeDoubleBe(matrix[0])
        writeDoubleBe(matrix[1])
        writeDoubleBe(0.0)
        writeDoubleBe(matrix[2])
        writeDoubleBe(matrix[3])
        writeDoubleBe(0.0)
    }

    private fun OutputStream.writeIntBe(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
    }

    private fun OutputStream.writeLongBe(value: Long) {
        write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array())
    }

    companion object {
        private const val TAG = "PurSceneCodec"
        private const val HEADER_MIN_SIZE = 0x6C
        private const val EMBEDDED_IMAGE_FALLBACK_MAX_BYTES = 64L * 1024L * 1024L

        // Scene-level metadata payload size (spacingX + spacingY + alignment).
        private const val SCENE_METADATA_PAYLOAD_SIZE = 8 + 8 + 4
        // Sanity cap: a length prefix larger than this is treated as
        // garbage (likely scene-name UTF-16 tail bytes misread as a
        // length). Keeps legacy desktop .pur files readable.
        private const val MAX_SCENE_METADATA_BYTES = 256
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'R'.code.toByte(), 'F'.code.toByte(), 'A'.code.toByte())
        private val LEGACY_IDENTITY_MATRIX = listOf(1.0, 0.0, 0.0, 1.0)
        // ASCII "PRAC" (PockRef Android Coloradjust) in big-endian is
        // declared at file scope (above the class) so the top-level
        // writeLegacyImageTransform(RandomAccessFile) overload can
        // also emit the sentinel. The class-internal readers and the
        // ByteArrayOutputStream writer both reference the same
        // file-level const.
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val PNG_FOOTER = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte())
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        private val JPEG_FOOTER = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        private val WEBP_RIFF_SIGNATURE = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
        private val WEBP_MARKER = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
        private val JPEG_SIZE_MARKERS = setOf(
            0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
            0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF,
        )
    }

    /**
     * Release all resources held by this codec: the image decoder's RAF,
     * the parser's RAF, and the disk cache. Call when closing the scene.
     */
    fun release() {
        imageDecoder.release()
        purFileParser.release()
    }
}

private data class LegacyImageBlob(
    val start: Long,
    val end: Long,
    val bytes: ByteArray,
)

private data class LegacyImageTransform(
    val id: Int,
    val source: String,
    val name: String,
    val matrix: List<Double>,
    val x: Double,
    val y: Double,
    val zLayer: Double,
    val matrixBeforeCrop: List<Double>,
    val xCrop: Double,
    val yCrop: Double,
    val scaleCrop: Double,
    val pointX: List<Double>,
    val pointY: List<Double>,
    val colorAdjust: com.pockref.app.model.ColorAdjust = com.pockref.app.model.ColorAdjust(),
)

private data class LegacyImageRecord(
    val start: Long,
    val end: Long,
    val transform: LegacyImageTransform,
)

private class RandomAccessFileOutputStream(
    private val raf: RandomAccessFile,
) : OutputStream() {
    override fun write(b: Int) {
        raf.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        raf.write(b, off, len)
    }
}

private fun ByteArray.indexOfSubArray(pattern: ByteArray, fromIndex: Int = 0): Int? {
    if (pattern.isEmpty() || size < pattern.size) return null
    outer@ for (i in fromIndex.coerceAtLeast(0)..(size - pattern.size)) {
        for (j in pattern.indices) {
            if (this[i + j] != pattern[j]) continue@outer
        }
        return i
    }
    return null
}

private fun readIntBe(bytes: ByteArray, index: Int): Int =
    ByteBuffer.wrap(bytes, index, 4).order(ByteOrder.BIG_ENDIAN).int

private fun readUnsignedShortBe(bytes: ByteArray, index: Int): Int =
    ByteBuffer.wrap(bytes, index, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF

private fun readLongBe(bytes: ByteArray, index: Int): Long =
    ByteBuffer.wrap(bytes, index, 8).order(ByteOrder.BIG_ENDIAN).long

private fun readDoubleBe(bytes: ByteArray, index: Int): Double =
    ByteBuffer.wrap(bytes, index, 8).order(ByteOrder.BIG_ENDIAN).double

// ASCII "PRAC" (PockRef Android Coloradjust) in big-endian. Marks the
// start of a colorAdjust block inside an Android-written legacy .pur
// transform; desktop .pur files end their transform block with a
// different 25-byte trailer (double 0.0 + int 1 + byte 0 + long -1 +
// int 0) and the first 4 of those bytes never match this value, so the
// reader falls back to ColorAdjust() instead of misreading trailer
// bytes as brightness/contrast/saturation (which can yield
// saturation = -1 → grayscale on desktop imports). Must stay in sync
// with PurFileParser.COLOR_ADJUST_SENTINEL.
private const val COLOR_ADJUST_SENTINEL = 0x50524341

private fun readStringUtf16Be(bytes: ByteArray, index: Int): String {
    val length = readIntBe(bytes, index)
    val textBytes = bytes.copyOfRange(index + 4, index + 4 + length)
    return String(textBytes, Charsets.UTF_16BE)
}

private fun String.byteLengthUtf16Be(): Int = toByteArray(Charsets.UTF_16BE).size

private fun readLegacyMatrix(bytes: ByteArray, index: Int): List<Double> = listOf(
    readDoubleBe(bytes, index),
    readDoubleBe(bytes, index + 8),
    readDoubleBe(bytes, index + 24),
    readDoubleBe(bytes, index + 32),
)

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
    size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

private fun RandomAccessFile.writeIntBe(value: Int) {
    write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
}

private fun RandomAccessFile.writeLongBe(value: Long) {
    write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array())
}

private fun RandomAccessFile.writeDoubleBe(value: Double) {
    write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(value).array())
}

private fun RandomAccessFile.writeShortBe(value: Int) {
    write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array())
}

private fun RandomAccessFile.writeStringUtf16Be(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_16BE)
    writeIntBe(bytes.size)
    write(bytes)
}

private fun RandomAccessFile.writeLegacyMatrix(matrix: List<Double>) {
    writeDoubleBe(matrix[0])
    writeDoubleBe(matrix[1])
    writeDoubleBe(0.0)
    writeDoubleBe(matrix[2])
    writeDoubleBe(matrix[3])
    writeDoubleBe(0.0)
}

private fun writeLegacyImageTransform(output: RandomAccessFile, transform: LegacyImageTransform) {
    val transformEnd = output.filePointer
    output.writeLongBe(0L)
    output.writeIntBe(34)
    output.write("GraphicsImageItem".toByteArray(Charsets.UTF_16BE))
    output.writeStringUtf16Be(transform.source)
    output.writeStringUtf16Be(transform.name)
    output.writeDoubleBe(1.0)
    output.writeLegacyMatrix(transform.matrix)
    output.writeDoubleBe(transform.x)
    output.writeDoubleBe(transform.y)
    output.writeDoubleBe(1.0)
    output.writeIntBe(transform.id)
    output.writeDoubleBe(transform.zLayer)
    output.writeLegacyMatrix(transform.matrixBeforeCrop)
    output.writeDoubleBe(transform.xCrop)
    output.writeDoubleBe(transform.yCrop)
    output.writeDoubleBe(transform.scaleCrop)
    output.writeIntBe(transform.pointX.size)
    transform.pointX.indices.forEach { index ->
        output.writeIntBe(if (index == 0) 0 else 1)
        output.writeDoubleBe(transform.pointX[index])
        output.writeDoubleBe(transform.pointY[index])
    }
    // colorAdjust is not in the original desktop .pur format but is a
    // real user-tweakable field on SceneImage (brightness / contrast /
    // saturation). We append it just before the trailer bytes; readers
    // skip over the unknown bytes via the endAddress stored at the start
    // of each transform block, so legacy desktop readers ignore it
    // cleanly while Android rehydrates it.
    //
    // The 4-byte COLOR_ADJUST_SENTINEL ("PRAC" — PockRef Android
    // Coloradjust) before the three doubles lets the reader distinguish
    // an Android-written transform from a desktop file (see the matching
    // block in writeLegacyImageTransform(ByteArrayOutputStream) for the
    // full reasoning).
    output.writeIntBe(COLOR_ADJUST_SENTINEL)
    output.writeDoubleBe(transform.colorAdjust.brightness.toDouble())
    output.writeDoubleBe(transform.colorAdjust.contrast.toDouble())
    output.writeDoubleBe(transform.colorAdjust.saturation.toDouble())
    output.writeDoubleBe(0.0)
    output.writeIntBe(1)
    output.write(0)
    output.writeLongBe(-1L)
    output.writeIntBe(0)
    val end = output.filePointer
    output.seek(transformEnd)
    output.writeLongBe(end)
    output.seek(end)
}
