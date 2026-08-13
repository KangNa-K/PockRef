package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.pockref.app.model.CropRect
import com.pockref.app.model.SceneImage
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

internal data class ImageTileKey(
    val imageId: String,
    val column: Int,
    val row: Int,
)

internal data class ImageTileRequest(
    val key: ImageTileKey,
    val sourceLeft: Int,
    val sourceTop: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

internal object ImageTilePlanner {
    const val TILE_EDGE_PX = 1024

    fun visibleTiles(
        image: SceneImage,
        viewport: Rect,
    ): List<ImageTileRequest> {
        val sourceBounds = visibleSourceBounds(image, viewport) ?: return emptyList()
        val firstColumn = sourceBounds.left / TILE_EDGE_PX
        val lastColumn = ((sourceBounds.right - 1) / TILE_EDGE_PX).coerceAtLeast(0)
        val firstRow = sourceBounds.top / TILE_EDGE_PX
        val lastRow = ((sourceBounds.bottom - 1) / TILE_EDGE_PX).coerceAtLeast(0)

        val sourceCenterX = (sourceBounds.left + sourceBounds.right) / 2f
        val sourceCenterY = (sourceBounds.top + sourceBounds.bottom) / 2f
        return buildList {
            for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) {
                    val sourceLeft = column * TILE_EDGE_PX
                    val sourceTop = row * TILE_EDGE_PX
                    add(
                        ImageTileRequest(
                            key = ImageTileKey(image.id, column, row),
                            sourceLeft = sourceLeft,
                            sourceTop = sourceTop,
                            sourceWidth = minOf(TILE_EDGE_PX, image.intrinsicWidth - sourceLeft),
                            sourceHeight = minOf(TILE_EDGE_PX, image.intrinsicHeight - sourceTop),
                        ),
                    )
                }
            }
        }.sortedBy { request ->
            val tileCenterX = request.sourceLeft + request.sourceWidth / 2f
            val tileCenterY = request.sourceTop + request.sourceHeight / 2f
            val dx = tileCenterX - sourceCenterX
            val dy = tileCenterY - sourceCenterY
            dx * dx + dy * dy
        }
    }

    private fun visibleSourceBounds(image: SceneImage, viewport: Rect): SourceBounds? {
        val displayWidth = image.displaySize.width * image.scale
        val displayHeight = image.displaySize.height * image.scale
        if (displayWidth <= 0f || displayHeight <= 0f ||
            image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0
        ) {
            return null
        }

        val crop = image.cropRect ?: CropRect()
        val corners = listOf(
            Offset(viewport.left, viewport.top),
            Offset(viewport.right, viewport.top),
            Offset(viewport.left, viewport.bottom),
            Offset(viewport.right, viewport.bottom),
        )
        val sourcePoints = corners.map { worldPoint ->
            val local = worldToImageLocal(
                point = worldPoint,
                imageCenter = image.center,
                imageRotationDegrees = image.rotationDegrees,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
            )
            val cropX = crop.left + (local.x / displayWidth) * crop.cropWidth
            val cropY = crop.top + (local.y / displayHeight) * crop.cropHeight
            Offset(
                x = cropX * image.intrinsicWidth,
                y = cropY * image.intrinsicHeight,
            )
        }
        val left = sourcePoints.minOf { it.x }.coerceIn(0f, image.intrinsicWidth.toFloat())
        val right = sourcePoints.maxOf { it.x }.coerceIn(0f, image.intrinsicWidth.toFloat())
        val top = sourcePoints.minOf { it.y }.coerceIn(0f, image.intrinsicHeight.toFloat())
        val bottom = sourcePoints.maxOf { it.y }.coerceIn(0f, image.intrinsicHeight.toFloat())
        if (right <= left || bottom <= top) return null

        return SourceBounds(
            left = floor(left).toInt().coerceIn(0, image.intrinsicWidth - 1),
            top = floor(top).toInt().coerceIn(0, image.intrinsicHeight - 1),
            right = ceil(right).toInt().coerceIn(1, image.intrinsicWidth),
            bottom = ceil(bottom).toInt().coerceIn(1, image.intrinsicHeight),
        )
    }

    private fun worldToImageLocal(
        point: Offset,
        imageCenter: Offset,
        imageRotationDegrees: Float,
        displayWidth: Float,
        displayHeight: Float,
    ): Offset {
        val radians = Math.toRadians(imageRotationDegrees.toDouble())
        val cosTheta = cos(radians).toFloat()
        val sinTheta = sin(radians).toFloat()
        val dx = point.x - imageCenter.x
        val dy = point.y - imageCenter.y
        return Offset(
            x = cosTheta * dx + sinTheta * dy + displayWidth / 2f,
            y = -sinTheta * dx + cosTheta * dy + displayHeight / 2f,
        )
    }

    private data class SourceBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
