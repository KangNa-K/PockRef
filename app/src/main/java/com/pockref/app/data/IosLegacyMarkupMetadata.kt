package com.pockref.app.data

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** Read only the metadata region, never search image payloads for a coincidental marker. */
internal fun readIosLegacyMarkupMetadata(file: File, parser: PurFileParser): JSONObject? =
    RandomAccessFile(file, "r").use { input ->
        if (input.length() < 224L) return@use null
        val headerBytes = ByteArray(224).also(input::readFully)
        val header = parser.parseHeader(headerBytes)
        if (!header.formatVersion.startsWith("1.")) return@use null
        val index = header.sqliteOffset
        require(index in 224L..input.length()) { "旧版场景索引损坏" }
        var start = 224L
        if (header.numImages > 0) {
            val indexSize = input.length() - index
            val entrySize = when (indexSize) {
                header.numImages * 20L -> 20
                header.numImages * 12L -> 12
                else -> return@use null
            }
            input.seek(index)
            repeat(header.numImages) {
                input.readInt()
                val imageStart = if (entrySize == 20) input.readLong() else input.readInt().toLong() and 0xffffffffL
                val imageEnd = if (entrySize == 20) input.readLong() else input.readInt().toLong() and 0xffffffffL
                require(imageStart in 224L..imageEnd && imageEnd <= index) { "旧版场景图片索引损坏" }
                start = maxOf(start, imageEnd)
            }
        }
        val marker = "POCKREF_META_V1".toByteArray(Charsets.US_ASCII)
        val buffer = ByteArray(64 * 1024)
        var offset = start
        while (offset + marker.size + 4 <= index) {
            input.seek(offset)
            val length = minOf(buffer.size.toLong(), index - offset).toInt()
            input.readFully(buffer, 0, length)
            for (position in 0..length - marker.size) {
                if (!marker.indices.all { buffer[position + it] == marker[it] }) continue
                val payloadStart = offset + position + marker.size + 4
                require(payloadStart <= index) { "旧版标记元数据已截断" }
                input.seek(payloadStart - 4)
                val payloadSize = input.readInt()
                require(payloadSize in 1..32 * 1024 * 1024 && payloadStart + payloadSize <= index) {
                    "旧版标记元数据长度无效"
                }
                val payload = ByteArray(payloadSize).also(input::readFully)
                return@use JSONObject(String(payload, Charsets.UTF_8))
            }
            if (offset + length >= index) break
            offset += length - marker.size + 1
        }
        null
    }
