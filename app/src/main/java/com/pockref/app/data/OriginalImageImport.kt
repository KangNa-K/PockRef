package com.pockref.app.data

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D

internal data class ImageBounds(
    val width: Int,
    val height: Int,
    val mimeType: String?,
)

internal data class OriginalImageImport(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
)

internal fun sceneImageFromOriginalImport(
    name: String,
    imported: OriginalImageImport,
    center: Offset,
    displayWidth: Float,
    zIndex: Int,
): SceneImage {
    val safeDisplayWidth = displayWidth.coerceAtLeast(1f)
    val displayHeight = imported.height.toFloat() * safeDisplayWidth / imported.width.coerceAtLeast(1)
    return SceneImage(
        name = name,
        mimeType = imported.mimeType,
        imageBytes = imported.bytes,
        intrinsicWidth = imported.width,
        intrinsicHeight = imported.height,
        center = center,
        displaySize = Size2D(safeDisplayWidth, displayHeight),
        zIndex = zIndex,
    )
}

internal fun decodeOriginalImage(
    imageBytes: ByteArray,
    declaredMimeType: String,
    boundsReader: (ByteArray) -> ImageBounds? = ::readImageBounds,
): OriginalImageImport? {
    if (imageBytes.isEmpty()) return null
    val bounds = boundsReader(imageBytes) ?: return null
    if (bounds.width <= 0 || bounds.height <= 0) return null
    val resolvedMimeType = if (declaredMimeType.startsWith("image/", ignoreCase = true)) {
        declaredMimeType
    } else {
        bounds.mimeType?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: declaredMimeType
    }
    return OriginalImageImport(
        bytes = imageBytes,
        width = bounds.width,
        height = bounds.height,
        mimeType = resolvedMimeType,
    )
}

private fun readImageBounds(imageBytes: ByteArray): ImageBounds {
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    return ImageBounds(
        width = options.outWidth,
        height = options.outHeight,
        mimeType = options.outMimeType,
    )
}
