package com.pockref.app.data

import kotlinx.coroutines.CoroutineDispatcher
import com.pockref.app.model.SceneImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal data class DecodedImageTile<T : Any>(
    val request: ImageTileRequest,
    val bitmap: T,
)

internal class ImageTilePipeline<T : Any>(
    private val decodeTile: suspend (SceneImage, ImageTileRequest) -> T?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val decodePermit = Semaphore(1)

    suspend fun decode(
        image: SceneImage,
        requests: List<ImageTileRequest>,
        onTileDecoded: (DecodedImageTile<T>) -> Unit = {},
    ): List<DecodedImageTile<T>> = coroutineScope {
        requests.map { request ->
            async {
                val decoded = decodePermit.withPermit {
                    withContext(ioDispatcher) {
                        decodeTile(image, request)
                    }
                }?.let { bitmap ->
                    DecodedImageTile(request, bitmap)
                }
                decoded?.also(onTileDecoded)
            }
        }.awaitAll().filterNotNull()
    }
}
