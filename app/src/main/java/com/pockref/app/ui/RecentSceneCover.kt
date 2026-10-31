package com.pockref.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.net.toUri
import com.pockref.app.data.PurSceneCodec
import com.pockref.app.model.pickCoverTarget
import java.io.File

internal fun loadRecentSceneCover(context: Context, uri: String, coverImageId: String?): Bitmap? {
    return try {
        val tempFile = File.createTempFile("pockref_cover_", ".pur", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri.toUri()) ?: return null
            input.use { source -> tempFile.outputStream().use { source.copyTo(it) } }
            val codec = PurSceneCodec()
            try {
                val scene = codec.readFromFile(tempFile)
                val image = pickCoverTarget(scene.images, coverImageId) ?: return null
                // The codec binds the correct lazy source, including V2's extracted image file.
                val decoded = codec.imageDecoder.decodeThumbnail(image)?.bitmap ?: return null
                val crop = image.cropRect ?: return decoded
                val x = (crop.left * decoded.width).toInt().coerceIn(0, decoded.width - 1)
                val y = (crop.top * decoded.height).toInt().coerceIn(0, decoded.height - 1)
                val width = ((crop.right - crop.left) * decoded.width).toInt().coerceIn(1, decoded.width - x)
                val height = ((crop.bottom - crop.top) * decoded.height).toInt().coerceIn(1, decoded.height - y)
                Bitmap.createBitmap(decoded, x, y, width, height)
            } finally {
                codec.release()
            }
        } finally {
            tempFile.delete()
        }
    } catch (error: Exception) {
        Log.w("RecentSceneCover", "Failed to load recent scene cover", error)
        null
    }
}
