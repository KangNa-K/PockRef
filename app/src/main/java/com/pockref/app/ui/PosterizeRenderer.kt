package com.pockref.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.graphics.createBitmap
import com.pockref.app.model.ColorAdjust
import com.pockref.app.model.SceneImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PosterizeRequest(
    val levels: Int,
    val colorAdjust: ColorAdjust,
)

/** Inputs that must distinguish CPU posterized bitmap results. */
internal data class PosterizeCacheKey(
    val imageId: String,
    val sourceToken: String,
    val decodeLevel: Int,
    val tileToken: String?,
    val levels: Int,
    val brightnessBits: Int,
    val contrastBits: Int,
    val saturationBits: Int,
)

internal data class PosterizeSourceMapping(
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float,
)

/**
 * Return the forward source-to-destination transform used by BitmapShader's
 * local matrix. Skia inverts a shader local matrix while sampling, so passing
 * the inverse transform here would make a full image sample only a cropped
 * portion of the source bitmap.
 */
internal fun posterizeSourceToCanvasMapping(
    sourceLeft: Float,
    sourceTop: Float,
    sourceWidth: Float,
    sourceHeight: Float,
    destinationLeft: Float,
    destinationTop: Float,
    destinationWidth: Float,
    destinationHeight: Float,
): PosterizeSourceMapping {
    require(sourceWidth > 0f && sourceHeight > 0f)
    require(destinationWidth > 0f && destinationHeight > 0f)
    val scaleX = destinationWidth / sourceWidth
    val scaleY = destinationHeight / sourceHeight
    return PosterizeSourceMapping(
        scaleX = scaleX,
        scaleY = scaleY,
        translateX = destinationLeft - sourceLeft * scaleX,
        translateY = destinationTop - sourceTop * scaleY,
    )
}

internal fun posterizeSourceToken(image: SceneImage): String =
    if (image.imageBytes.isNotEmpty()) {
        "inline:${image.imageBytes.contentHashCode()}:${image.imageBytes.size}:${image.intrinsicWidth}x${image.intrinsicHeight}"
    } else {
        "disk:${image.imageByteOffset}:${image.imageByteLength}:${image.intrinsicWidth}x${image.intrinsicHeight}"
    }

internal fun posterizeCacheKeyForImage(
    image: SceneImage,
    sourceToken: String,
    decodeLevel: Int,
    levels: Int,
): PosterizeCacheKey = PosterizeCacheKey(
    imageId = image.id,
    sourceToken = sourceToken,
    decodeLevel = decodeLevel,
    tileToken = null,
    levels = levels,
    brightnessBits = image.colorAdjust.brightness.toBits(),
    contrastBits = image.colorAdjust.contrast.toBits(),
    saturationBits = image.colorAdjust.saturation.toBits(),
)

internal fun posterizeCacheKeyForTile(
    image: SceneImage,
    sourceToken: String,
    tileToken: String,
    levels: Int,
): PosterizeCacheKey = PosterizeCacheKey(
    imageId = image.id,
    sourceToken = sourceToken,
    decodeLevel = 0,
    tileToken = tileToken,
    levels = levels,
    brightnessBits = image.colorAdjust.brightness.toBits(),
    contrastBits = image.colorAdjust.contrast.toBits(),
    saturationBits = image.colorAdjust.saturation.toBits(),
)

/**
 * Owns only generated posterize outputs. Source bitmaps remain owned by the
 * existing decode/tile caches and are never recycled here.
 */
internal class PosterizeBitmapCache(
    private val scope: CoroutineScope,
) {
    private val results = mutableStateMapOf<PosterizeCacheKey, Bitmap>()
    private val pending = mutableSetOf<PosterizeCacheKey>()
    private var generation = 0L

    fun get(key: PosterizeCacheKey): Bitmap? = results[key]?.takeUnless { it.isRecycled }

    fun getOrRequest(
        key: PosterizeCacheKey,
        source: Bitmap,
        request: PosterizeRequest,
        snapshotSource: Boolean = false,
    ): Bitmap? {
        get(key)?.let { return it }
        if (!PosterizeRenderer.isSupported(request.levels) || key in pending) return null

        val sourceForTask = if (snapshotSource) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        } else {
            source
        }
        val requestGeneration = generation
        pending += key
        scope.launch(Dispatchers.Default) {
            val generated = try {
                createPosterizedBitmap(sourceForTask, request)
            } catch (_: Throwable) {
                null
            } finally {
                if (snapshotSource && sourceForTask !== source && !sourceForTask.isRecycled) {
                    sourceForTask.recycle()
                }
            }
            withContext(Dispatchers.Main.immediate) {
                pending -= key
                if (generated == null) return@withContext
                if (requestGeneration != generation) {
                    if (!generated.isRecycled) generated.recycle()
                    return@withContext
                }
                results.remove(key)?.let { old ->
                    if (!old.isRecycled) old.recycle()
                }
                results[key] = generated
            }
        }
        return null
    }

    fun removeImage(imageId: String) {
        val keys = results.keys.filter { it.imageId == imageId }
        val pendingKeys = pending.filter { it.imageId == imageId }
        if (keys.isEmpty() && pendingKeys.isEmpty()) return
        generation += 1L
        keys.forEach { key ->
            results.remove(key)?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        pending.removeAll(pendingKeys.toSet())
    }

    /**
     * Keep only the current GIF frame result for [imageId]. GIF playback
     * changes frames continuously, so retaining every frame would turn a
     * temporary preview cache into an unbounded bitmap store on API < 33.
     */
    fun retainGifFrame(imageId: String, frameIndex: Int) {
        val currentToken = "gif-frame:$frameIndex"
        val staleKeys = results.keys.filter { key ->
            key.imageId == imageId &&
                key.tileToken?.startsWith("gif-frame:") == true &&
                key.tileToken != currentToken
        }
        val stalePending = pending.filter { key ->
            key.imageId == imageId &&
                key.tileToken?.startsWith("gif-frame:") == true &&
                key.tileToken != currentToken
        }
        if (staleKeys.isEmpty() && stalePending.isEmpty()) return

        generation += 1L
        staleKeys.forEach { key ->
            results.remove(key)?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
        pending.removeAll(stalePending.toSet())
    }

    fun removeImagesNotIn(imageIds: Set<String>) {
        val idsToRemove = results.keys
            .asSequence()
            .map { it.imageId }
            .filterNot { it in imageIds }
            .toSet()
        idsToRemove.forEach(::removeImage)
    }

    fun clear() {
        generation += 1L
        pending.clear()
        results.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        results.clear()
    }
}

/** Reusable frame buffers for the GIF posterize path. */
internal class GifFrameBitmapCache {
    private val buffers = mutableMapOf<String, Bitmap>()

    fun obtain(imageId: String, width: Int, height: Int): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val current = buffers[imageId]
        if (current != null && current.width == safeWidth && current.height == safeHeight && !current.isRecycled) {
            return current
        }
        current?.takeUnless { it.isRecycled }?.recycle()
        return createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also {
            buffers[imageId] = it
        }
    }

    fun removeImage(imageId: String) {
        buffers.remove(imageId)?.let { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun removeImagesNotIn(imageIds: Set<String>) {
        buffers.keys.filterNot { it in imageIds }.toList().forEach(::removeImage)
    }

    fun clear() {
        buffers.values.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        buffers.clear()
    }
}

internal object PosterizeRenderer {
    private const val AGSL = """
        uniform shader inputImage;
        uniform float levels;
        uniform float brightness;
        uniform float contrast;
        uniform float saturation;

        vec4 main(vec2 canvasCoordinates) {
            vec4 source = inputImage.eval(canvasCoordinates);

            float saturationScale = max(0.0, 1.0 + saturation);
            float sourceLuminance = dot(source.rgb, vec3(0.213, 0.715, 0.072));
            vec3 saturated = mix(vec3(sourceLuminance), source.rgb, saturationScale);

            float contrastScale = max(0.0, 1.0 + contrast);
            float contrastTranslate = 0.5 * (1.0 - contrastScale);
            vec3 adjusted = clamp(
                saturated * contrastScale + vec3(contrastTranslate + brightness),
                0.0,
                1.0
            );

            float adjustedLuminance = dot(adjusted, vec3(0.213, 0.715, 0.072));
            float levelScale = max(1.0, levels - 1.0);
            float quantized = floor(
                clamp(adjustedLuminance, 0.0, 1.0) * levelScale + 0.5
            ) / levelScale;
            return vec4(vec3(quantized), source.a);
        }
    """

    internal fun isSupported(levels: Int): Boolean =
        levels in SUPPORTED_POSTERIZE_LEVELS

    /**
     * Draw a posterized source rectangle. On API 33+ the operation stays on the
     * GPU; older devices require a precomputed [cpuFallback] bitmap.
     */
    internal fun drawBitmap(
        canvas: Canvas,
        source: Bitmap,
        sourceRect: Rect,
        destinationRect: RectF,
        request: PosterizeRequest,
        cpuFallback: Bitmap?,
    ): Boolean {
        if (!isSupported(request.levels)) return false
        if (sourceRect.width() <= 0 || sourceRect.height() <= 0) return false
        if (destinationRect.width() <= 0f || destinationRect.height() <= 0f) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            drawWithRuntimeShader(canvas, source, sourceRect, destinationRect, request)
            return true
        }

        if (cpuFallback == null) return false
        canvas.drawBitmap(
            cpuFallback,
            sourceRect,
            destinationRect,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        return true
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawWithRuntimeShader(
        canvas: Canvas,
        source: Bitmap,
        sourceRect: Rect,
        destinationRect: RectF,
        request: PosterizeRequest,
    ) {
        // Skia inverts a Shader local matrix before sampling. Pass the forward
        // source-to-destination transform here; passing destination-to-source
        // would invert it again and crop the visible image.
        val bitmapShader = BitmapShader(
            source,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(sourceToCanvasMatrix(sourceRect, destinationRect))
        }
        val shader = RuntimeShader(AGSL).apply {
            setInputShader("inputImage", bitmapShader)
            setFloatUniform("levels", request.levels.toFloat())
            setFloatUniform(
                "brightness",
                request.colorAdjust.brightness.coerceIn(-1f, 1f),
            )
            setFloatUniform(
                "contrast",
                request.colorAdjust.contrast.coerceIn(-1f, 1f),
            )
            setFloatUniform(
                "saturation",
                request.colorAdjust.saturation.coerceIn(-1f, 1f),
            )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRect(destinationRect, paint)
    }

    private fun sourceToCanvasMatrix(
        sourceRect: Rect,
        destinationRect: RectF,
    ): Matrix = Matrix().apply {
        val mapping = posterizeSourceToCanvasMapping(
            sourceLeft = sourceRect.left.toFloat(),
            sourceTop = sourceRect.top.toFloat(),
            sourceWidth = sourceRect.width().toFloat(),
            sourceHeight = sourceRect.height().toFloat(),
            destinationLeft = destinationRect.left,
            destinationTop = destinationRect.top,
            destinationWidth = destinationRect.width(),
            destinationHeight = destinationRect.height(),
        )
        setScale(mapping.scaleX, mapping.scaleY)
        postTranslate(mapping.translateX, mapping.translateY)
    }
}

/** Create an ARGB_8888 posterized copy for API < 33 background rendering. */
internal fun createPosterizedBitmap(
    source: Bitmap,
    request: PosterizeRequest,
): Bitmap {
    require(PosterizeRenderer.isSupported(request.levels))

    val output = createBitmap(
        source.width.coerceAtLeast(1),
        source.height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val colorCanvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = buildColorMatrix(request.colorAdjust)?.let {
            ColorMatrixColorFilter(ColorMatrix(it))
        }
    }
    colorCanvas.drawBitmap(source, 0f, 0f, paint)

    val pixels = IntArray(output.width * output.height)
    output.getPixels(
        pixels,
        0,
        output.width,
        0,
        0,
        output.width,
        output.height,
    )
    posterizeArgbPixels(pixels, request.levels)
    output.setPixels(
        pixels,
        0,
        output.width,
        0,
        0,
        output.width,
        output.height,
    )
    return output
}
