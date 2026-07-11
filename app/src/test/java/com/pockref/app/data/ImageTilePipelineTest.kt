package com.pockref.app.data

import androidx.compose.ui.geometry.Offset
import com.pockref.app.model.SceneImage
import com.pockref.app.model.Size2D
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImageTilePipelineTest {

    @Test
    fun decode_publishesEachCompletedTileBeforeTheBatchFinishes() = runBlocking {
        val firstTilePublished = CompletableDeferred<Unit>()
        val allowSecondTile = CompletableDeferred<Unit>()
        var batchFinished = false
        val pipeline = ImageTilePipeline<Int>(
            decodeTile = { _, request ->
                if (request.key.column == 0) {
                    1
                } else {
                    allowSecondTile.await()
                    2
                }
            },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val requests = listOf(
            request(column = 0),
            request(column = 1),
        )

        val batch = async {
            pipeline.decode(
                image = SceneImage(
                    id = "image",
                    name = "image.jpg",
                    mimeType = "image/jpeg",
                    intrinsicWidth = 1,
                    intrinsicHeight = 1,
                    center = Offset.Zero,
                    displaySize = Size2D(1f, 1f),
                ),
                requests = requests,
            ) { tile ->
                if (tile.request.key.column == 0) {
                    firstTilePublished.complete(Unit)
                }
            }.also {
                batchFinished = true
            }
        }

        firstTilePublished.await()

        assertFalse(batchFinished)
        allowSecondTile.complete(Unit)
        assertEquals(2, batch.await().size)
    }

    private fun request(column: Int) = ImageTileRequest(
        key = ImageTileKey("image", column, row = 0),
        sourceLeft = column * ImageTilePlanner.TILE_EDGE_PX,
        sourceTop = 0,
        sourceWidth = ImageTilePlanner.TILE_EDGE_PX,
        sourceHeight = ImageTilePlanner.TILE_EDGE_PX,
    )
}
