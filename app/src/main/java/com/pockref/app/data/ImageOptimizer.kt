package com.pockref.app.data

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File

class ImageOptimizer(private val context: Context) {

    data class OptimizeResult(
        val imageId: String = "",
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val mimeType: String,
        val originalSize: Int,
        val optimizedSize: Int,
    )

    data class OptimizeConfig(
        val maxLongSide: Int = MAX_LONG_SIDE,
        val targetSizeRatio: ClosedFloatingPointRange<Float> = TARGET_SIZE_RATIO,
        val webpQuality: Int = WEBP_QUALITY,
        val jpegQuality: Int = JPEG_QUALITY,
        val preferWebP: Boolean = true,
        val minCompressThreshold: Int = MIN_COMPRESS_THRESHOLD,
        val maxCompressTarget: Int = MAX_COMPRESS_TARGET,
        val qualityRange: ClosedFloatingPointRange<Float> = QUALITY_RANGE,
    )

    data class BatchTask(
        val imageId: String,
        val imageName: String,
        val imageBytes: ByteArray,
        val mimeType: String,
    )

    data class LazyBatchTask(
        val imageId: String,
        val imageName: String,
        val estimatedSize: Int,
        val mimeType: String,
        val bytesLoader: () -> ByteArray,
    )

    data class BatchProgress(
        val total: Int = 0,
        val completed: Int = 0,
        val failed: Int = 0,
        val skipped: Int = 0,
        val currentImageName: String = "",
        val totalOriginalBytes: Long = 0,
        val totalOptimizedBytes: Long = 0,
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val isCancelled: Boolean = false,
        val failedIds: Set<String> = emptySet(),
        val failedReasons: Map<String, String> = emptyMap(),
    ) {
        val progress: Float get() = if (total == 0) 0f else (completed + failed + skipped).toFloat() / total
        val savedBytes: Long get() = totalOriginalBytes - totalOptimizedBytes
    }

    private enum class OutputFormat {
        WEBP_LOSSY,
        WEBP_LOSSLESS,
        JPEG,
        PNG,
    }

    private enum class ImageType {
        PHOTO,
        ICON_LIKE,
        FLAT_COLOR,
    }

    private val batchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batchJob: Job? = null
    private val _batchProgress = MutableStateFlow(BatchProgress())
    val batchProgress: StateFlow<BatchProgress> = _batchProgress.asStateFlow()

    // Read on Dispatchers.IO worker threads inside the in-flight optimize
    // loop, written from the caller thread by pause/cancel. Without
    // @Volatile the IO workers may never observe the change — pause and
    // cancel would silently no-op.
    @Volatile private var isPaused = false
    @Volatile private var isCancelled = false

    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    private fun availableMemoryMb(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    private fun isLowMemory(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.lowMemory
    }

    private fun computeConcurrency(tasks: List<BatchTask>, requested: Int): Int {
        if (isLowMemory()) return 1
        val availMb = availableMemoryMb()
        val avgTaskSizeMb = if (tasks.isEmpty()) 0.0 else tasks.map { it.imageBytes.size }.average() / (1024.0 * 1024.0)
        if (avgTaskSizeMb <= 0) return requested.coerceIn(1, 4)
        val bitmapOverheadMb = avgTaskSizeMb * 5
        val maxConcurrent = (availMb / bitmapOverheadMb).toInt().coerceIn(1, requested)
        return maxConcurrent
    }

    private fun computeLazyConcurrency(tasks: List<LazyBatchTask>, requested: Int): Int {
        if (isLowMemory()) return 1
        val availMb = availableMemoryMb()
        val avgTaskSizeMb = if (tasks.isEmpty()) 0.0 else tasks.map { it.estimatedSize }.average() / (1024.0 * 1024.0)
        if (avgTaskSizeMb <= 0) return requested.coerceIn(1, 4)
        val bitmapOverheadMb = avgTaskSizeMb * 5
        val maxConcurrent = (availMb / bitmapOverheadMb).toInt().coerceIn(1, requested)
        return maxConcurrent
    }

    fun startBatchOptimize(
        tasks: List<BatchTask>,
        config: OptimizeConfig = saveOptimizeConfig(),
        concurrency: Int = DEFAULT_CONCURRENCY,
        onItemComplete: (OptimizeResult) -> Unit = {},
        onProgress: (BatchProgress) -> Unit = {},
        onComplete: (BatchProgress) -> Unit = {},
    ) {
        val lazyTasks = tasks.map { task ->
            LazyBatchTask(
                imageId = task.imageId,
                imageName = task.imageName,
                estimatedSize = task.imageBytes.size,
                mimeType = task.mimeType,
                bytesLoader = { task.imageBytes },
            )
        }
        startLazyBatchOptimize(lazyTasks, config, concurrency, onItemComplete, onProgress, onComplete)
    }

    fun startLazyBatchOptimize(
        tasks: List<LazyBatchTask>,
        config: OptimizeConfig = saveOptimizeConfig(),
        concurrency: Int = DEFAULT_CONCURRENCY,
        onItemComplete: (OptimizeResult) -> Unit = {},
        onProgress: (BatchProgress) -> Unit = {},
        onComplete: (BatchProgress) -> Unit = {},
    ) {
        cancelBatchOptimize()

        isPaused = false
        isCancelled = false

        _batchProgress.value = BatchProgress(
            total = tasks.size,
            isRunning = true,
        )

        val actualConcurrency = computeLazyConcurrency(tasks, concurrency)

        batchJob = batchScope.launch {
            var completed = 0
            var failed = 0
            var skipped = 0
            var totalOriginal = 0L
            var totalOptimized = 0L
            val failedIds = mutableSetOf<String>()
            val failedReasons = mutableMapOf<String, String>()
            val semaphore = Semaphore(actualConcurrency)

            val deferredList = tasks.map { task ->
                async {
                    if (isCancelled) return@async

                    semaphore.withPermit {
                        if (isCancelled) return@withPermit

                        while (isPaused && !isCancelled) {
                            yield()
                            kotlinx.coroutines.delay(200)
                        }

                        if (isCancelled) return@withPermit

                        _batchProgress.value = _batchProgress.value.copy(
                            currentImageName = task.imageName,
                        )
                        onProgress(_batchProgress.value)

                        try {
                            if (isLowMemory()) {
                                System.gc()
                                Thread.sleep(200)
                            }

                            val imageBytes = task.bytesLoader()
                            val result = optimizeForSave(imageBytes, task.mimeType, config)
                            val wasOptimized = result.optimizedSize < result.originalSize

                            if (wasOptimized) {
                                onItemComplete(OptimizeResult(
                                    imageId = task.imageId,
                                    bytes = result.bytes,
                                    width = result.width,
                                    height = result.height,
                                    mimeType = result.mimeType,
                                    originalSize = result.originalSize,
                                    optimizedSize = result.optimizedSize,
                                ))
                                synchronized(this) {
                                    completed++
                                    totalOriginal += result.originalSize
                                    totalOptimized += result.optimizedSize
                                }
                            } else {
                                synchronized(this) {
                                    skipped++
                                    totalOriginal += result.originalSize
                                    totalOptimized += result.originalSize
                                }
                            }
                        } catch (oom: OutOfMemoryError) {
                            Log.w(TAG, "OOM optimizing ${task.imageName}, marking as failed")
                            System.gc()
                            synchronized(this) {
                                failed++
                                failedIds.add(task.imageId)
                                failedReasons[task.imageId] = "内存不足"
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Error optimizing ${task.imageName}: ${e.message}")
                            synchronized(this) {
                                failed++
                                failedIds.add(task.imageId)
                                failedReasons[task.imageId] = e.message ?: "未知错误"
                            }
                        }

                        val updated = _batchProgress.value.copy(
                            completed = completed,
                            failed = failed,
                            skipped = skipped,
                            totalOriginalBytes = totalOriginal,
                            totalOptimizedBytes = totalOptimized,
                            failedIds = failedIds.toSet(),
                            failedReasons = failedReasons.toMap(),
                        )
                        _batchProgress.value = updated
                        onProgress(updated)
                    }
                }
            }

            deferredList.awaitAll()

            val finalProgress = _batchProgress.value.copy(
                isRunning = false,
                isCancelled = isCancelled,
                currentImageName = "",
            )
            _batchProgress.value = finalProgress
            onComplete(finalProgress)
        }
    }

    fun retryFailedLazy(
        originalTasks: List<LazyBatchTask>,
        config: OptimizeConfig = saveOptimizeConfig(),
        concurrency: Int = 1,
        onItemComplete: (OptimizeResult) -> Unit = {},
        onProgress: (BatchProgress) -> Unit = {},
        onComplete: (BatchProgress) -> Unit = {},
    ) {
        val currentProgress = _batchProgress.value
        val failedIds = currentProgress.failedIds
        val failedTasks = originalTasks.filter { it.imageId in failedIds }
        if (failedTasks.isEmpty()) return

        isPaused = false
        isCancelled = false

        _batchProgress.value = _batchProgress.value.copy(
            failed = 0,
            isRunning = true,
            isCancelled = false,
            failedIds = emptySet(),
            failedReasons = emptyMap(),
        )

        batchJob = batchScope.launch {
            var completed = currentProgress.completed
            var failed = 0
            var skipped = currentProgress.skipped
            var totalOriginal = currentProgress.totalOriginalBytes
            var totalOptimized = currentProgress.totalOptimizedBytes
            val newFailedIds = mutableSetOf<String>()
            val newFailedReasons = mutableMapOf<String, String>()
            val semaphore = Semaphore(concurrency)

            failedTasks.map { task ->
                async {
                    if (isCancelled) return@async

                    semaphore.withPermit {
                        if (isCancelled) return@withPermit

                        try {
                            if (isLowMemory()) {
                                System.gc()
                                Thread.sleep(200)
                            }

                            val imageBytes = task.bytesLoader()
                            val result = optimizeForSave(imageBytes, task.mimeType, config)
                            val wasOptimized = result.optimizedSize < result.originalSize

                            if (wasOptimized) {
                                onItemComplete(OptimizeResult(
                                    imageId = task.imageId,
                                    bytes = result.bytes,
                                    width = result.width,
                                    height = result.height,
                                    mimeType = result.mimeType,
                                    originalSize = result.originalSize,
                                    optimizedSize = result.optimizedSize,
                                ))
                                synchronized(this) {
                                    completed++
                                    totalOriginal += result.originalSize
                                    totalOptimized += result.optimizedSize
                                }
                            } else {
                                synchronized(this) { skipped++ }
                            }
                        } catch (oom: OutOfMemoryError) {
                            System.gc()
                            synchronized(this) {
                                failed++
                                newFailedIds.add(task.imageId)
                                newFailedReasons[task.imageId] = "内存不足"
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            synchronized(this) {
                                failed++
                                newFailedIds.add(task.imageId)
                                newFailedReasons[task.imageId] = e.message ?: "未知错误"
                            }
                        }

                        val updated = _batchProgress.value.copy(
                            completed = completed,
                            failed = failed,
                            skipped = skipped,
                            totalOriginalBytes = totalOriginal,
                            totalOptimizedBytes = totalOptimized,
                            failedIds = newFailedIds.toSet(),
                            failedReasons = newFailedReasons.toMap(),
                        )
                        _batchProgress.value = updated
                        onProgress(updated)
                    }
                }
            }.awaitAll()

            val finalProgress = _batchProgress.value.copy(
                isRunning = false,
                isCancelled = isCancelled,
                currentImageName = "",
            )
            _batchProgress.value = finalProgress
            onComplete(finalProgress)
        }
    }

    fun pauseBatchOptimize() {
        isPaused = true
        _batchProgress.value = _batchProgress.value.copy(isPaused = true)
    }

    fun resumeBatchOptimize() {
        isPaused = false
        _batchProgress.value = _batchProgress.value.copy(isPaused = false)
    }

    fun cancelBatchOptimize() {
        isCancelled = true
        isPaused = false
        batchJob?.cancel()
        batchJob = null
        _batchProgress.value = _batchProgress.value.copy(
            isRunning = false,
            isCancelled = true,
            isPaused = false,
        )
    }

    fun retryFailed(
        originalTasks: List<BatchTask>,
        config: OptimizeConfig = saveOptimizeConfig(),
        onItemComplete: (OptimizeResult) -> Unit = {},
        onProgress: (BatchProgress) -> Unit = {},
        onComplete: (BatchProgress) -> Unit = {},
    ) {
        val currentProgress = _batchProgress.value
        val failedIds = currentProgress.failedIds
        val failedTasks = originalTasks.filter { it.imageId in failedIds }
        if (failedTasks.isEmpty()) return

        isPaused = false
        isCancelled = false

        _batchProgress.value = _batchProgress.value.copy(
            failed = 0,
            isRunning = true,
            isCancelled = false,
            failedIds = emptySet(),
            failedReasons = emptyMap(),
        )

        batchJob = batchScope.launch {
            var completed = currentProgress.completed
            var failed = 0
            var skipped = currentProgress.skipped
            var totalOriginal = currentProgress.totalOriginalBytes
            var totalOptimized = currentProgress.totalOptimizedBytes
            val newFailedIds = mutableSetOf<String>()
            val newFailedReasons = mutableMapOf<String, String>()

            failedTasks.map { task ->
                async {
                    if (isCancelled) return@async

                    try {
                        if (isLowMemory()) {
                            System.gc()
                            Thread.sleep(200)
                        }

                        val result = optimizeForSave(task.imageBytes, task.mimeType, config)
                        val wasOptimized = result.optimizedSize < result.originalSize

                        if (wasOptimized) {
                            onItemComplete(OptimizeResult(
                                imageId = task.imageId,
                                bytes = result.bytes,
                                width = result.width,
                                height = result.height,
                                mimeType = result.mimeType,
                                originalSize = result.originalSize,
                                optimizedSize = result.optimizedSize,
                            ))
                            synchronized(this) {
                                completed++
                                totalOriginal += result.originalSize
                                totalOptimized += result.optimizedSize
                            }
                        } else {
                            synchronized(this) { skipped++ }
                        }
                    } catch (oom: OutOfMemoryError) {
                        System.gc()
                        synchronized(this) {
                            failed++
                            newFailedIds.add(task.imageId)
                            newFailedReasons[task.imageId] = "内存不足"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        synchronized(this) {
                            failed++
                            newFailedIds.add(task.imageId)
                            newFailedReasons[task.imageId] = e.message ?: "未知错误"
                        }
                    }

                    val updated = _batchProgress.value.copy(
                        completed = completed,
                        failed = failed,
                        skipped = skipped,
                        totalOriginalBytes = totalOriginal,
                        totalOptimizedBytes = totalOptimized,
                        failedIds = newFailedIds.toSet(),
                        failedReasons = newFailedReasons.toMap(),
                    )
                    _batchProgress.value = updated
                    onProgress(updated)
                }
            }.awaitAll()

            val finalProgress = _batchProgress.value.copy(
                isRunning = false,
                isCancelled = isCancelled,
                currentImageName = "",
            )
            _batchProgress.value = finalProgress
            onComplete(finalProgress)
        }
    }

    fun optimize(imageBytes: ByteArray, mimeType: String, config: OptimizeConfig = OptimizeConfig()): OptimizeResult {
        val originalSize = imageBytes.size

        if (originalSize < config.minCompressThreshold) {
            // Small-file passthrough: still report real dimensions so callers that
            // gate on width/height > 0 (e.g. importImages) don't silently drop the
            // image. Bug fix: previously width/height were hard-coded to 0 here.
            val passthroughBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, passthroughBounds)
            return OptimizeResult(
                bytes = imageBytes,
                width = passthroughBounds.outWidth,
                height = passthroughBounds.outHeight,
                mimeType = mimeType,
                originalSize = originalSize,
                optimizedSize = originalSize,
            )
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return OptimizeResult(
                bytes = imageBytes,
                width = bounds.outWidth,
                height = bounds.outHeight,
                mimeType = mimeType,
                originalSize = originalSize,
                optimizedSize = originalSize,
            )
        }

        val needsDownscale = bounds.outWidth > config.maxLongSide || bounds.outHeight > config.maxLongSide
        val sampleSize = if (needsDownscale) {
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longSide / (sample * 2) >= config.maxLongSide) {
                sample *= 2
            }
            sample
        } else 1

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
            ?: return OptimizeResult(
                bytes = imageBytes,
                width = bounds.outWidth,
                height = bounds.outHeight,
                mimeType = mimeType,
                originalSize = originalSize,
                optimizedSize = originalSize,
            )

        val scaled = if (needsDownscale) {
            val scale = config.maxLongSide.toFloat() / maxOf(decoded.width, decoded.height).toFloat()
            val newW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val newH = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, newW, newH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        val imageType = classifyImage(scaled, mimeType)
        val hasAlpha = hasTransparency(scaled)
        val outputFormat = chooseOutputFormat(imageType, config, hasAlpha)
        val quality = chooseQuality(imageType, outputFormat, config)

        val compressed = compressBitmap(scaled, outputFormat, quality)

        val finalBytes = if (compressed.size in (originalSize * config.targetSizeRatio.start).toInt()..(originalSize * config.targetSizeRatio.endInclusive).toInt()) {
            compressed
        } else if (compressed.size > (originalSize * config.targetSizeRatio.endInclusive).toInt()) {
            recompressToTarget(scaled, outputFormat, originalSize, config.targetSizeRatio, quality, imageType)
        } else {
            compressed
        }

        val result = OptimizeResult(
            bytes = finalBytes,
            width = scaled.width,
            height = scaled.height,
            mimeType = formatToMime(outputFormat),
            originalSize = originalSize,
            optimizedSize = finalBytes.size,
        )

        scaled.recycle()


        return result
    }

    fun optimizeForSave(
        imageBytes: ByteArray,
        mimeType: String,
        config: OptimizeConfig = saveOptimizeConfig(),
    ): OptimizeResult {
        val originalSize = imageBytes.size

        if (originalSize < config.minCompressThreshold) {
            // Small-file passthrough: still report real dimensions so callers that
            // gate on width/height > 0 (e.g. importImages) don't silently drop the
            // image. Bug fix: previously width/height were hard-coded to 0 here.
            val passthroughBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, passthroughBounds)
            return OptimizeResult(
                bytes = imageBytes,
                width = passthroughBounds.outWidth,
                height = passthroughBounds.outHeight,
                mimeType = mimeType,
                originalSize = originalSize,
                optimizedSize = originalSize,
            )
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return OptimizeResult(
                bytes = imageBytes,
                width = bounds.outWidth,
                height = bounds.outHeight,
                mimeType = mimeType,
                originalSize = originalSize,
                optimizedSize = originalSize,
            )
        }

        val decoded = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return OptimizeResult(
            bytes = imageBytes,
            width = bounds.outWidth,
            height = bounds.outHeight,
            mimeType = mimeType,
            originalSize = originalSize,
            optimizedSize = originalSize,
        )

        val imageType = classifyImage(decoded, mimeType)
        val hasAlpha = hasTransparency(decoded)
        val outputFormat = chooseOutputFormat(imageType, config, hasAlpha)
        val quality = chooseQuality(imageType, outputFormat, config)

        val compressed = compressBitmap(decoded, outputFormat, quality)

        val finalBytes = if (compressed.size >= originalSize) {
            if (originalSize > config.maxCompressTarget) {
                recompressToTarget(decoded, outputFormat, originalSize, config.targetSizeRatio, quality, imageType)
            } else {
                imageBytes
            }
        } else {
            compressed
        }

        val result = OptimizeResult(
            bytes = finalBytes,
            width = decoded.width,
            height = decoded.height,
            mimeType = if (finalBytes === imageBytes) mimeType else formatToMime(outputFormat),
            originalSize = originalSize,
            optimizedSize = finalBytes.size,
        )

        decoded.recycle()

        return result
    }

    private fun classifyImage(bitmap: Bitmap, originalMime: String): ImageType {
        if (originalMime.contains("png", ignoreCase = true) || originalMime.contains("gif", ignoreCase = true)) {
            if (hasTransparency(bitmap)) return ImageType.ICON_LIKE
        }

        val width = bitmap.width
        val height = bitmap.height
        val sampleStep = maxOf(1, width / SAMPLE_GRID)
        if (sampleStep >= width && sampleStep >= height) return ImageType.PHOTO

        // Single JNI hop instead of one per sampled pixel. For a 2048×2048
        // image at SAMPLE_GRID=16 that's 1 call vs ~16k.
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val uniqueColors = mutableSetOf<Int>()
        var totalSamples = 0
        sampleLoop@ for (x in 0 until width step sampleStep) {
            for (y in 0 until height step sampleStep) {
                val pixel = pixels[y * width + x]
                uniqueColors.add(pixel ushr 16)
                uniqueColors.add(pixel and 0xFFFF)
                totalSamples++
                if (uniqueColors.size > COLOR_COMPLEXITY_THRESHOLD) break@sampleLoop
            }
        }

        val complexity = uniqueColors.size.toFloat() / totalSamples.coerceAtLeast(1)
        return if (complexity < LOW_COMPLEXITY_THRESHOLD) ImageType.FLAT_COLOR else ImageType.PHOTO
    }

    private fun hasTransparency(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val step = maxOf(1, minOf(width, height) / TRANSPARENCY_SAMPLE_COUNT)
        if (step >= width && step >= height) {
            // Sample size >= image: just probe one corner pixel via the
            // batch path; calling getPixels for a 1x1 lookup is wasted work.
            return Color.alpha(bitmap.getPixel(0, 0)) < 255
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                // Alpha is the high 8 bits of an ARGB int.
                if ((pixels[y * width + x] ushr 24) and 0xFF < 255) return true
            }
        }
        return false
    }

    private fun chooseOutputFormat(imageType: ImageType, config: OptimizeConfig, hasAlpha: Boolean = false): OutputFormat {
        if (imageType == ImageType.ICON_LIKE) {
            if (config.preferWebP && supportsWebP()) return OutputFormat.WEBP_LOSSLESS
            return OutputFormat.PNG
        }
        if (imageType == ImageType.FLAT_COLOR) {
            if (hasAlpha) {
                if (config.preferWebP && supportsWebP()) return OutputFormat.WEBP_LOSSLESS
                return OutputFormat.PNG
            }
            if (config.preferWebP && supportsWebP()) return OutputFormat.WEBP_LOSSY
            return OutputFormat.JPEG
        }
        if (config.preferWebP && supportsWebP()) return OutputFormat.WEBP_LOSSY
        return OutputFormat.JPEG
    }

    private fun chooseQuality(imageType: ImageType, format: OutputFormat, config: OptimizeConfig): Int {
        return when {
            imageType == ImageType.ICON_LIKE || imageType == ImageType.FLAT_COLOR -> {
                if (format == OutputFormat.WEBP_LOSSLESS) WEBP_LOSSLESS_QUALITY else 100
            }
            format == OutputFormat.WEBP_LOSSY -> config.webpQuality
            format == OutputFormat.JPEG -> config.jpegQuality
            else -> 100
        }
    }

    private fun compressBitmap(bitmap: Bitmap, format: OutputFormat, quality: Int): ByteArray {
        // Start the buffer at ~25% of the raw bitmap's allocation. Compressed
        // JPEG/WebP is almost always << raw ARGB (and PNG usually too, unless
        // the image is noise). For a 4096² image this is ~16 MB instead of
        // 64 MB. The stream grows 2× as needed so a too-small initial size
        // is fine, just costs one reallocation.
        val estimatedSize = (bitmap.allocationByteCount / 4).coerceAtLeast(1024)
        val stream = ByteArrayOutputStream(estimatedSize)
        when {
            format == OutputFormat.WEBP_LOSSY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, stream)
            }
            format == OutputFormat.WEBP_LOSSLESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, quality, stream)
            }
            format == OutputFormat.WEBP_LOSSY || format == OutputFormat.WEBP_LOSSLESS -> {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality, stream)
            }
            format == OutputFormat.JPEG -> {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            format == OutputFormat.PNG -> {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
        return stream.toByteArray()
    }

    private fun recompressToTarget(
        bitmap: Bitmap,
        format: OutputFormat,
        originalSize: Int,
        targetRatio: ClosedFloatingPointRange<Float>,
        startQuality: Int,
        imageType: ImageType,
    ): ByteArray {
        if (imageType == ImageType.ICON_LIKE || imageType == ImageType.FLAT_COLOR) {
            return compressBitmap(bitmap, format, if (format == OutputFormat.WEBP_LOSSLESS) WEBP_LOSSLESS_QUALITY else 100)
        }

        val targetMax = (originalSize * targetRatio.endInclusive).toInt()
        val targetMin = (originalSize * targetRatio.start).toInt()

        var low = 10
        var high = startQuality
        var bestResult = compressBitmap(bitmap, format, high)

        while (low <= high) {
            val mid = (low + high) / 2
            val result = compressBitmap(bitmap, format, mid)
            if (result.size <= targetMax) {
                bestResult = result
                if (result.size >= targetMin) break
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        return bestResult
    }

    private fun supportsWebP(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    private fun formatToMime(format: OutputFormat): String = when (format) {
        OutputFormat.WEBP_LOSSY, OutputFormat.WEBP_LOSSLESS -> "image/webp"
        OutputFormat.JPEG -> "image/jpeg"
        OutputFormat.PNG -> "image/png"
    }

    /**
     * Persists a one-shot summary of a finished batch optimize run to
     * `<filesDir>/optimize_logs/batch_<timestamp>.log` and emits a `Log.i`
     * line. Previously the name `writeBatchOptimizeLog` covered a body
     * that *deleted* the log directory — leaving the caller with no log
     * file at all. The summary text below is the minimum useful content
     * (totals, counts, percentage saved) and matches the on-screen
     * "完成 — 节省 N%" message.
     */
    fun writeBatchOptimizeLog(progress: BatchProgress) {
        Log.i(
            TAG,
            "Batch optimize: total=${progress.total} completed=${progress.completed} " +
                "failed=${progress.failed} skipped=${progress.skipped} " +
                "size=${formatSize(progress.totalOriginalBytes)} -> ${formatSize(progress.totalOptimizedBytes)} " +
                "saved=${formatSize(progress.savedBytes)}",
        )
        val logDir = File(context.filesDir, "optimize_logs").apply { mkdirs() }
        val logFile = File(logDir, "batch_${System.currentTimeMillis()}.log")
        val pct = if (progress.totalOriginalBytes > 0) {
            (progress.totalOptimizedBytes.toFloat() / progress.totalOriginalBytes * 100).toInt()
        } else 100
        val savedBytes = (progress.totalOriginalBytes - progress.totalOptimizedBytes).coerceAtLeast(0L)
        logFile.writeText(
            buildString {
                appendLine("=== Batch Optimize Summary ===")
                appendLine("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("Total: ${progress.total}  Completed: ${progress.completed}  Failed: ${progress.failed}  Skipped: ${progress.skipped}")
                appendLine("Size: ${formatSize(progress.totalOriginalBytes)} -> ${formatSize(progress.totalOptimizedBytes)} (${pct}%)")
                appendLine("Saved: ${formatSize(savedBytes)}")
            },
        )
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes.toFloat() / (1024 * 1024))} MB"
    }

    companion object {
        private const val TAG = "ImageOptimizer"
        private const val MAX_LONG_SIDE = 2048
        private val TARGET_SIZE_RATIO = 0.50f..0.70f
        private const val WEBP_QUALITY = 75
        private const val JPEG_QUALITY = 80
        private const val WEBP_LOSSLESS_QUALITY = 100
        private const val SAMPLE_GRID = 16
        private const val COLOR_COMPLEXITY_THRESHOLD = 256
        private const val LOW_COMPLEXITY_THRESHOLD = 2.0f
        private const val TRANSPARENCY_SAMPLE_COUNT = 64
        const val MIN_COMPRESS_THRESHOLD = 50 * 1024
        const val MAX_COMPRESS_TARGET = 500 * 1024
        val QUALITY_RANGE = 0.70f..0.80f
        const val DEFAULT_CONCURRENCY = 4
        const val MAX_QUEUE_SIZE = 20

        fun saveOptimizeConfig() = OptimizeConfig(
            maxLongSide = 4096,
            targetSizeRatio = QUALITY_RANGE,
            webpQuality = 75,
            jpegQuality = 80,
            preferWebP = true,
            minCompressThreshold = MIN_COMPRESS_THRESHOLD,
            maxCompressTarget = MAX_COMPRESS_TARGET,
        )
    }
}
