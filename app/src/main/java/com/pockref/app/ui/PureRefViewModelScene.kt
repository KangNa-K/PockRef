package com.pockref.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.pockref.app.data.ImageOptimizer
import com.pockref.app.model.CropAspect
import com.pockref.app.model.CropRect
import com.pockref.app.model.ExportFormat
import com.pockref.app.model.PockRefScene
import com.pockref.app.model.SceneImage
import com.pockref.app.model.SceneNaming
import com.pockref.app.model.Size2D
import com.pockref.app.model.toScene
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update

class PockRefViewModelScene(private val mainVm: PockRefViewModel) {

    private val supportedImageMimes = setOf(
        "image/jpeg", "image/png", "image/webp", "image/gif",
    )
    private val supportedExtensions = setOf("jpg", "jpeg", "png", "webp", "gif")

    /**
     * Compute a placement origin for new images that avoids
     * overlapping with existing images. Returns the top-left
     * corner (in scene coordinates) where new images should
     * start being placed. Uses the AABB of existing images
     * and places new content below them with a gap.
     */
    private fun computePlacementOrigin(existingImages: List<SceneImage>, gap: Float = 8f): Offset {
        if (existingImages.isEmpty()) return Offset.Zero
        val bounds = imagesBounds(existingImages) ?: return Offset.Zero
        // Place new images below the existing images' bounding box.
        return Offset(bounds.left, bounds.bottom + gap)
    }

    /**
     * V_NOW (2026-06-12 "剪贴板多张导入" extension): inner
     * helper that iterates every [ClipData.Item] in the
     * clipboard and decodes it as an image. Returns the
     * successfully-decoded [SceneImage]s plus a per-item
     * failure count. Must be called from
     * [Dispatchers.IO] (the [importFromClipboard] caller
     * is already inside a `withContext(Dispatchers.IO)`
     * block).
     *
     * Per-item failure modes (no URI, unsupported scheme,
     * empty bytes, decode error, etc.) are caught and
     * counted in [ClipboardImportResult.failedCount] —
     * a single bad item never aborts the rest of the
     * batch. The caller surfaces a partial-success
     * snackbar when [ClipboardImportResult.failedCount]
     * is non-zero.
     *
     * Items that aren't images (text clips, URL labels,
     * etc.) are silently skipped — the previous
     * single-item code only checked `description.mimeType`
     * for the whole clip; for multi-item clips we check
     * each item's MIME individually.
     */
    private fun importAllFromClipboard(): ClipboardImportResult {
        val app = mainVm.getApplication<Application>()
        val clipboard = app.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as? android.content.ClipboardManager
            ?: return ClipboardImportResult(emptyList(), 0)
        val clip = clipboard.primaryClip ?: return ClipboardImportResult(emptyList(), 0)
        if (clip.itemCount == 0) return ClipboardImportResult(emptyList(), 0)
        val existingImages = mainVm._uiState.value.images
        val existingCount = existingImages.size
        val imageSpacing = 8f
        val placementOrigin = computePlacementOrigin(existingImages, imageSpacing)

        val imported = mutableListOf<SceneImage>()
        var failedCount = 0
        // V_NOW (2026-06-12 "剪贴板多张导入" extension):
        // cascadeOrigin tracks the placement Y for the
        // next image — each new image drops below the
        // previous one with [imageSpacing] of breathing
        // room. Stacking starts at [placementOrigin]
        // (below the existing canvas) so the new images
        // never overlap the user's existing layout.
        var cascadeY = placementOrigin.y
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            // Try to get the URI from the item — Android
            // stores clipboard image data in (a) a
            // content URI via item.getUri(), (b) an
            // inline URI on API 31+, or (c) as a text
            // representation that happens to be a URI
            // string. The coerceToText fallback catches
            // case (c) and the very rare "browser copied
            // an image address as text" case.
            val uri = item.uri
                ?: try {
                    Uri.parse(item.coerceToText(app)?.toString()?.trim() ?: "")
                } catch (_: Exception) { null }
            if (uri == null) {
                failedCount++
                continue
            }
            val scheme = uri.scheme
            if (scheme !in listOf("content", "file", "android.resource")) {
                failedCount++
                continue
            }
            // Per-item try/catch so a single bad URI
            // doesn't abort the whole batch (e.g. one
            // content URI's provider has been revoked
            // while the rest are still readable).
            val decoded = try {
                decodeClipboardItem(uri)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.w(
                    "PockRefClipboard",
                    "importAll: ${e.javaClass.simpleName} for $uri: ${e.message}",
                )
                null
            }
            if (decoded == null) {
                failedCount++
                continue
            }
            val imgIndex = imported.size
            val baseWidth = decoded.width.toFloat().coerceAtMost(720f)
            val scale = baseWidth / decoded.width.toFloat()
            val baseHeight = decoded.height * scale
            imported.add(
                SceneImage(
                    name = "剪贴板图片_${existingCount + imgIndex + 1}",
                    mimeType = decoded.mimeType,
                    imageBytes = decoded.bytes,
                    intrinsicWidth = decoded.width,
                    intrinsicHeight = decoded.height,
                    center = Offset(
                        placementOrigin.x + baseWidth / 2f,
                        cascadeY + baseHeight / 2f,
                    ),
                    displaySize = Size2D(baseWidth, baseHeight),
                    zIndex = existingCount + imgIndex,
                )
            )
            // Advance the cascade for the next image.
            cascadeY += baseHeight + imageSpacing
        }
        return ClipboardImportResult(imported, failedCount)
    }

    /**
     * Decode a single clipboard item's URI into
     * (bytes, mimeType, width, height). Returns null
     * on any decode failure (open fails, optimizer
     * produces 0×0 output, etc.). Caller decides
     * what to do with nulls.
     */
    private fun decodeClipboardItem(uri: Uri): ClipboardDecodedImage? {
        val rawBytes = mainVm.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (rawBytes.isEmpty()) return null
        var mime = mainVm.contentResolver.getType(uri)
        if (mime == null) {
            val uriStr = uri.toString()
            mime = when {
                uriStr.endsWith(".png", ignoreCase = true) -> "image/png"
                uriStr.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }
        }
        val result = mainVm.imageOptimizer.optimize(rawBytes, mime)
        if (result.width <= 0 || result.height <= 0) return null
        return ClipboardDecodedImage(
            bytes = result.bytes,
            mimeType = result.mimeType,
            width = result.width,
            height = result.height,
        )
    }

    /**
     * V_NOW (2026-06-12 "剪贴板多张导入" extension): a
     * single decoded clipboard image. Internal DTO so
     * [importAllFromClipboard] and [decodeClipboardItem]
     * can communicate without dragging Kotlin's stdlib
     * tuple types into the public surface.
     */
    private data class ClipboardDecodedImage(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val height: Int,
    )

    /**
     * V_NOW (2026-06-12 "剪贴板多张导入" extension):
     * the result of a multi-item clipboard import.
     * [imported] is the list of successfully-decoded
     * [SceneImage]s; [failedCount] is the number of
     * items that were skipped (no URI, bad scheme,
     * decode failure, etc.). The caller turns
     * [failedCount] into a partial-success snackbar.
     */
    private data class ClipboardImportResult(
        val imported: List<SceneImage>,
        val failedCount: Int,
    )

    fun dropImages(uris: List<Uri>, dragPermission: android.view.DragAndDropPermissions? = null) {
        if (uris.isEmpty()) return
        mainVm.viewModelScope.launch {
            mainVm.setBusy(true)
            val imported = try {
                withContext(Dispatchers.IO) {
                    val filtered = uris.filter { uri ->
                        val mime = mainVm.contentResolver.getType(uri) ?: ""
                        val name = mainVm.queryDisplayName(uri) ?: ""
                        val ext = name.substringAfterLast('.', "").lowercase()
                        mime in supportedImageMimes || ext in supportedExtensions
                    }
                    if (filtered.isEmpty()) return@withContext emptyList<SceneImage>()

                    val existingImages = mainVm._uiState.value.images
                    val existingCount = existingImages.size
                    val viewport = mainVm._uiState.value.canvasViewportSize
                    val containerPadding = 16f
                    val imageSpacing = 8f
                    val minColumnWidth = 120f
                    val maxColumns = 4
                    val availableWidth = viewport.width - containerPadding * 2
                    val columns = if (availableWidth <= 0f) maxColumns
                        else ((availableWidth + imageSpacing) / (minColumnWidth + imageSpacing))
                            .toInt().coerceIn(1, maxColumns)
                    val columnWidth = if (availableWidth <= 0f) minColumnWidth
                        else (availableWidth - imageSpacing * (columns - 1)) / columns

                    filtered.mapIndexedNotNull { index, uri ->
                        val rawBytes = mainVm.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@mapIndexedNotNull null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@mapIndexedNotNull null

                        val maxSide = 512
                        val scale = if (maxOf(bounds.outWidth, bounds.outHeight) > maxSide) {
                            maxSide.toFloat() / maxOf(bounds.outWidth, bounds.outHeight)
                        } else 1f
                        val targetW = (bounds.outWidth * scale).toInt().coerceAtLeast(1)
                        val targetH = (bounds.outHeight * scale).toInt().coerceAtLeast(1)

                        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH)
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                            ?: return@mapIndexedNotNull null
                        val scaled = if (decoded.width != targetW || decoded.height != targetH) {
                            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                                if (it !== decoded) decoded.recycle()
                            }
                        } else decoded

                        val pngStream = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, pngStream)
                        val pngBytes = pngStream.toByteArray()
                        scaled.recycle()

                        val displayScale = columnWidth / targetW.toFloat()
                        val displayW = columnWidth
                        val displayH = targetH * displayScale

                        val placementOrigin = computePlacementOrigin(existingImages, imageSpacing)
                        val totalIndex = existingCount + index
                        val col = totalIndex % columns
                        val row = totalIndex / columns
                        val centerX = placementOrigin.x + col * (columnWidth + imageSpacing) + columnWidth / 2f
                        val centerY = placementOrigin.y + row * (displayH + imageSpacing) + displayH / 2f

                        SceneImage(
                            name = mainVm.queryDisplayName(uri) ?: "image_${totalIndex + 1}",
                            mimeType = "image/png",
                            imageBytes = pngBytes,
                            intrinsicWidth = targetW,
                            intrinsicHeight = targetH,
                            center = Offset(centerX, centerY),
                            displaySize = Size2D(displayW, displayH),
                            zIndex = totalIndex,
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                dragPermission?.release()
                throw e
            } catch (error: Throwable) {
                dragPermission?.release()
                mainVm.reportError("拖拽导入图片失败: ${error.message ?: error.javaClass.simpleName}", error)
                mainVm.setBusy(false)
                return@launch
            }

            dragPermission?.release()

            if (imported.isEmpty()) {
                mainVm.reportError("没有可导入的图片文件")
                mainVm.setBusy(false)
                return@launch
            }
            mainVm.recordUndoState()
            // Snapshot the pre-import image list so the
            // auto-arrange path below can decide between
            // [executeAutoLayout] (empty → nice grid for the new
            // images) and [fitToScreen] (populated → preserve
            // the user's manual layout, just zoom out so the
            // new ones are visible).
            val preImportImages = mainVm._uiState.value.images
            val hasOpenPur = mainVm.currentSceneUri != null && mainVm.isImportedPurFile
            if (!hasOpenPur) {
                mainVm.untitledCounter++
                // Use the project's [SceneNaming] convention so the
                // stored name never carries the unsaved marker and
                // matches whatever the rest of the app produces for
                // first-time scene names ("Untitled N", not
                // "untitled_N").
                val newName = SceneNaming.nextUntitled(mainVm.untitledCounter)
                mainVm._uiState.update { state ->
                    state.copy(
                        sceneName = newName,
                        images = state.images + imported,
                        selectedImageId = imported.lastOrNull()?.id ?: state.selectedImageId,
                        selectedImageIds = imported.mapTo(linkedSetOf()) { it.id },
                        isDragOver = false,
                    ).normalizeSelection().syncHistoryFlags(mainVm.undoStackSize, mainVm.redoStackSize)
                }
            } else {
                mainVm._uiState.update { state ->
                    state.copy(
                        images = state.images + imported,
                        selectedImageId = imported.lastOrNull()?.id ?: state.selectedImageId,
                        selectedImageIds = imported.mapTo(linkedSetOf()) { it.id },
                        isDragOver = false,
                    ).normalizeSelection().syncHistoryFlags(mainVm.undoStackSize, mainVm.redoStackSize)
                }
            }
            mainVm.notifyContentChanged()
            // First-import auto-arrange: when the scene was empty
            // before this import, run [executeAutoLayout] so the
            // new images land in a sensible grid instead of
            // overlapping at their import offsets. For subsequent
            // imports into a populated scene, preserve the user's
            // existing layout and just call [fitToScreen] to zoom
            // the canvas so the new images are visible (the
            // pre-existing behavior).
            if (preImportImages.isEmpty()) {
                mainVm.batchController.executeAutoLayout()
            } else {
                mainVm.batchController.fitToScreen()
            }
            mainVm.setBusy(false)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var sampleSize = 1
        val halfW = width / 2
        val halfH = height / 2
        // V_NOW (2026-06-08 user feedback "导出图不清晰 /
        // 没有原图画质"): the original loop aimed for
        // source ≥ reqW (any smallest), which meant the
        // first-pass decode could be 2x larger than the
        // exact target. The exact resize stage then
        // downscaled 2x via Bitmap.createBitmap with
        // filter=false (nearest-neighbor), which is the
        // main reason the export looked "fuzzy / pixelated"
        // — nearest-neighbor drops pixels in blocks. We
        // now aim for source ≥ 2x reqW so the exact
        // resize can use bilinear (filter=true) at ≤0.5x
        // shrink, which is the sweet spot for preserving
        // detail. We also raise the cap from 8 to 16 so
        // huge source images (8K+) still get sampled down
        // enough that the exact resize doesn't blow the
        // heap on a 32MB ARGB_8888 intermediate.
        while (halfW / sampleSize >= reqW * 2 && halfH / sampleSize >= reqH * 2) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtMost(16)
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        mainVm.viewModelScope.launch {
            mainVm.setBusy(true)
            val imported = try {
                withContext(Dispatchers.IO) {
                    val existingImages = mainVm._uiState.value.images
                    val existingCount = existingImages.size
                    val imageSpacing = 8f
                    val placementOrigin = computePlacementOrigin(existingImages, imageSpacing)
                    uris.mapIndexedNotNull { index, uri ->
                        val rawBytes = mainVm.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapIndexedNotNull null
                        val originalMime = mainVm.contentResolver.getType(uri) ?: "application/octet-stream"
                        val result = mainVm.imageOptimizer.optimize(rawBytes, originalMime)
                        if (result.width <= 0 || result.height <= 0) return@mapIndexedNotNull null
                        val baseWidth = result.width.toFloat().coerceAtMost(720f)
                        val scale = baseWidth / result.width.toFloat()
                        val baseHeight = result.height * scale
                        // Grid layout: 4 columns max, placed below
                        // existing images.
                        val maxColumns = 4
                        val col = index % maxColumns
                        val row = index / maxColumns
                        val centerX = placementOrigin.x + col * (baseWidth + imageSpacing) + baseWidth / 2f
                        val centerY = placementOrigin.y + row * (baseHeight + imageSpacing) + baseHeight / 2f
                        SceneImage(
                            name = mainVm.queryDisplayName(uri) ?: "image_${existingCount + index + 1}",
                            mimeType = result.mimeType,
                            imageBytes = result.bytes,
                            intrinsicWidth = result.width,
                            intrinsicHeight = result.height,
                            center = Offset(centerX, centerY),
                            displaySize = Size2D(baseWidth, baseHeight),
                            zIndex = existingCount + index,
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                mainVm.reportError("导入图片失败: ${error.message ?: error.javaClass.simpleName}", error)
                mainVm.setBusy(false)
                return@launch
            }
            mainVm.recordUndoState()
            mainVm._uiState.update { state ->
                val newSelection = imported.mapTo(linkedSetOf()) { it.id }
                state.copy(
                    images = state.images + imported,
                    selectedImageId = imported.lastOrNull()?.id ?: state.selectedImageId,
                    selectedImageIds = newSelection.ifEmpty { state.selectedImageIds },
                ).normalizeSelection().syncHistoryFlags(mainVm.undoStackSize, mainVm.redoStackSize)
            }
            mainVm.batchController.fitToScreen()
            mainVm.setBusy(false)
        }
    }

    /**
     * Import images from the system clipboard. Reads
     * [ClipboardManager] in an IO coroutine, decodes each
     * image's bytes, and adds one or more [SceneImage]s to
     * the scene. Supports the multi-item clipboard format
     * — when the user copies multiple images in some
     * gallery apps / browsers, the [ClipData] has
     * `itemCount > 1` and each item carries its own URI.
     *
     * Per-item failures are isolated: a single item that
     * can't be decoded / opened does NOT abort the rest
     * of the batch. The snackbar reports the per-item
     * failure count when the batch is partial-success.
     *
     * Surfaces errors via [PockRefViewModel.reportError].
     */
    fun importFromClipboard() {
        mainVm.viewModelScope.launch {
            mainVm.setBusy(true)
            // V_NOW (2026-06-12 "剪贴板多张导入" extension):
            // result is a (imported, failedCount) pair so
            // partial successes can be reported in the
            // snackbar (e.g. "已导入 3 张剪贴板图片,2 张失败").
            val batchResult = try {
                withContext(Dispatchers.IO) {
                    importAllFromClipboard()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                mainVm.reportError("从剪贴板导入失败: ${error.message ?: error.javaClass.simpleName}", error)
                mainVm.setBusy(false)
                return@launch
            }
            val imported = batchResult.imported
            val failedCount = batchResult.failedCount
            if (imported.isEmpty()) {
                mainVm.reportError("剪贴板中没有可用的图片", null)
                mainVm.setBusy(false)
                return@launch
            }
            // V_NOW (2026-06-12 "剪贴板多张导入" extension):
            // report partial-success when some items
            // failed. Full success uses the same concise
            // message as before; full failure is already
            // caught by the imported.isEmpty() branch above.
            if (failedCount > 0) {
                mainVm.reportError("已导入 ${imported.size} 张剪贴板图片,${failedCount} 张失败")
            }
            mainVm.recordUndoState()
            mainVm._uiState.update { state ->
                val newSelection = imported.mapTo(linkedSetOf()) { it.id }
                state.copy(
                    images = state.images + imported,
                    selectedImageId = imported.lastOrNull()?.id ?: state.selectedImageId,
                    selectedImageIds = newSelection.ifEmpty { state.selectedImageIds },
                ).normalizeSelection().syncHistoryFlags(mainVm.undoStackSize, mainVm.redoStackSize)
            }
            mainVm.batchController.fitToScreen()
            mainVm.setBusy(false)
        }
    }
    fun loadScene(uri: Uri) {
        mainVm.currentSceneUri = uri
        val fileName = mainVm.queryDisplayNameSync(uri) ?: ""
        mainVm.isImportedPurFile = fileName.endsWith(".pur", ignoreCase = true)
        mainVm.viewModelScope.launch {
            mainVm.setBusy(true)
            val scene = try {
                withContext(Dispatchers.IO) {
                    mainVm.takePersistablePermission(uri, read = true, write = false)
                    mainVm.openSceneFile(uri).use { sceneFile ->
                        mainVm.codec.readFromFile(sceneFile.file)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                mainVm.reportError("打开场景失败: ${error.message ?: error.javaClass.simpleName}", error)
                mainVm.setBusy(false)
                return@launch
            }
            mainVm.undoStack.clear()
            mainVm.redoStack.clear()
            mainVm.sceneGestureActive = false
            mainVm.gestureUndoRecorded = false
            mainVm.rememberRecentScene(uri, scene.name.ifBlank { mainVm.queryDisplayNameSync(uri) ?: "Scene" }, "Opened")
            mainVm._uiState.update {
                val bg = if (mainVm.isImportedPurFile) mainVm.appPrefs.defaultBackgroundPreset.color else scene.backgroundColor
                // Derive the display name through [SceneNaming] so
                // the stored value follows the same convention as
                // freshly-imported scenes. If the embedded .pur has
                // its own name we keep it; if it's blank (some .pur
                // files don't store a name) we fall back to the
                // file's display name.
                val displaySceneName = scene.name.ifBlank {
                    SceneNaming.fromPurFileName(mainVm.queryDisplayNameSync(uri) ?: "Scene")
                }
                it.copy(
                    sceneName = SceneNaming.sanitizeForStorage(displaySceneName),
                    backgroundColor = bg,
                    backgroundCustomColor = bg,
                    images = scene.images,
                    // Newly opened scene starts with nothing selected —
                    // the user picks by tapping. The previous behavior
                    // (auto-select the last image) made the "edit
                    // happens to whatever happens to be last in the .pur
                    // file" footgun very easy to hit, especially when the
                    // .pur has 50+ images.
                    selectedImageId = null,
                    selectedImageIds = emptySet(),
                    multiSelectMode = false,
                    canUndo = false,
                    canRedo = false,
                    canvasOffset = Offset.Zero,
                    canvasScale = 1f,
                    fittedImageId = null,
                    transientDragImageIds = emptySet(),
                    transientDragDelta = Offset.Zero,
                )
            }
            mainVm.lastSaveSnapshotHash = mainVm.computeQuickHash(mainVm._uiState.value.toScene())
            mainVm.syncDirectSaveState()
            mainVm.batchController.fitToScreen()
            mainVm.setBusy(false)
        }
    }

    fun saveScene(uri: Uri) {
        mainVm.currentSceneUri = uri
        mainVm.viewModelScope.launch {
            val success = performSave(uri, truncate = false)
            if (success) {
                mainVm.isImportedPurFile = true
                if (mainVm._uiState.value.pendingCloseAfterSave) {
                    mainVm._uiState.update { it.copy(pendingCloseAfterSave = false) }
                    closeScene()
                }
            }
        }
    }

    fun saveSceneDirect() {
        val uri = mainVm.currentSceneUri ?: return
        if (!mainVm.hasUnsavedChanges) return
        mainVm.viewModelScope.launch {
            performSave(uri, truncate = true)
        }
    }

    private suspend fun performSave(uri: Uri, truncate: Boolean): Boolean {
        val scene = mainVm._uiState.value.toScene()
        val config = ImageOptimizer.saveOptimizeConfig()

        val lazyTasks = scene.images.mapNotNull { image ->
            val estimatedSize = when {
                image.imageBytes.isNotEmpty() -> image.imageBytes.size
                image.imageByteLength > 0 -> image.imageByteLength
                else -> 0
            }
            if (estimatedSize < config.minCompressThreshold) return@mapNotNull null
            ImageOptimizer.LazyBatchTask(
                imageId = image.id,
                imageName = image.name,
                estimatedSize = estimatedSize,
                mimeType = image.mimeType,
                bytesLoader = {
                    mainVm.resolveRawImageBytes(image)
                },
            )
        }

        if (lazyTasks.isEmpty()) {
            return writeSceneToFile(uri, truncate, scene.images)
        }

        mainVm.pendingBatchTasks = null
        mainVm.pendingLazyTasks = lazyTasks
        mainVm.pendingSaveUri = uri
        mainVm.pendingSaveTruncate = truncate

        mainVm._uiState.update {
            it.copy(
                showBatchOptimizeConfirm = true,
            )
        }
        return false
    }

    fun confirmBatchOptimize() {
        val lazyTasks = mainVm.pendingLazyTasks ?: return
        val uri = mainVm.pendingSaveUri ?: return
        val truncate = mainVm.pendingSaveTruncate

        mainVm._uiState.update {
            it.copy(
                showBatchOptimizeConfirm = false,
                isSaving = true,
            )
        }
        mainVm.setBusy(true)

        val appContext = mainVm.getApplication<Application>()
        val bDir = File(appContext.cacheDir, "optimize_backup_${System.currentTimeMillis()}").also { it.mkdirs() }
        mainVm.backupDir = bDir

        mainVm.imageOptimizer.startLazyBatchOptimize(
            tasks = lazyTasks,
            config = ImageOptimizer.saveOptimizeConfig(),
            concurrency = ImageOptimizer.DEFAULT_CONCURRENCY,
            onItemComplete = { result ->
                val backupFile = File(bDir, "${result.imageId}.dat")
                if (!backupFile.exists()) {
                    val currentImage = mainVm._uiState.value.images.find { it.id == result.imageId }
                    val originalBytes = currentImage?.let { mainVm.resolveRawImageBytes(it) }
                    if (originalBytes != null && originalBytes.isNotEmpty()) {
                        backupFile.writeBytes(originalBytes)
                        val metaFile = File(bDir, "${result.imageId}.mime")
                        metaFile.writeText(currentImage.mimeType)
                    }
                }
                mainVm._uiState.update { state ->
                    val updatedImages = state.images.map { image ->
                        if (image.id == result.imageId) {
                            val newWidth = if (result.width > 0) result.width else image.intrinsicWidth
                            val newHeight = if (result.height > 0) result.height else image.intrinsicHeight
                            image.copy(
                                imageBytes = result.bytes,
                                mimeType = result.mimeType,
                                intrinsicWidth = newWidth,
                                intrinsicHeight = newHeight,
                                imageByteOffset = -1,
                                imageByteLength = 0,
                            )
                        } else image
                    }
                    state.copy(images = updatedImages)
                }
            },
            onProgress = { progress ->
                mainVm.viewModelScope.launch(Dispatchers.Main) {
                    mainVm._uiState.update { state ->
                        state.copy(
                            batchOptimizeState = com.pockref.app.model.BatchOptimizeState(
                                status = if (progress.isPaused) com.pockref.app.model.BatchOptimizeStatus.PAUSED else com.pockref.app.model.BatchOptimizeStatus.RUNNING,
                                total = progress.total,
                                completed = progress.completed,
                                failed = progress.failed,
                                skipped = progress.skipped,
                                totalOriginalBytes = progress.totalOriginalBytes,
                                totalOptimizedBytes = progress.totalOptimizedBytes,
                                currentImageName = progress.currentImageName,
                                isPaused = progress.isPaused,
                            ),
                        )
                    }
                }
            },
            onComplete = { progress ->
                mainVm.imageOptimizer.writeBatchOptimizeLog(progress)
                mainVm.viewModelScope.launch(Dispatchers.Main) {
                    applyBatchResultsAndSave(uri, truncate, progress)
                }
            },
        )
    }

    fun skipBatchOptimize() {
        val uri = mainVm.pendingSaveUri ?: return
        val truncate = mainVm.pendingSaveTruncate
        val scene = mainVm._uiState.value.toScene()

        mainVm._uiState.update {
            it.copy(
                showBatchOptimizeConfirm = false,
                isSaving = true,
            )
        }

        mainVm.viewModelScope.launch {
            writeSceneToFile(uri, truncate, scene.images)
        }
    }

    fun cancelBatchOptimize() {
        mainVm.imageOptimizer.cancelBatchOptimize()
        mainVm._uiState.update {
            it.copy(
                batchOptimizeState = com.pockref.app.model.BatchOptimizeState(
                    status = com.pockref.app.model.BatchOptimizeStatus.CANCELLED,
                ),
                isSaving = false,
            )
        }
        mainVm.setBusy(false)
    }

    fun pauseBatchOptimize() {
        mainVm.imageOptimizer.pauseBatchOptimize()
        mainVm._uiState.update {
            it.copy(
                batchOptimizeState = it.batchOptimizeState.copy(
                    status = com.pockref.app.model.BatchOptimizeStatus.PAUSED,
                    isPaused = true,
                ),
            )
        }
    }

    fun resumeBatchOptimize() {
        mainVm.imageOptimizer.resumeBatchOptimize()
        mainVm._uiState.update {
            it.copy(
                batchOptimizeState = it.batchOptimizeState.copy(
                    status = com.pockref.app.model.BatchOptimizeStatus.RUNNING,
                    isPaused = false,
                ),
            )
        }
    }

    fun retryFailedOptimization() {
        val lazyTasks = mainVm.pendingLazyTasks ?: return
        val bDir = mainVm.backupDir
        mainVm._uiState.update {
            it.copy(
                batchOptimizeState = it.batchOptimizeState.copy(
                    status = com.pockref.app.model.BatchOptimizeStatus.RUNNING,
                ),
            )
        }
        mainVm.imageOptimizer.retryFailedLazy(
            originalTasks = lazyTasks,
            config = ImageOptimizer.saveOptimizeConfig(),
            concurrency = 1,
            onItemComplete = { result ->
                mainVm._uiState.update { state ->
                    val updatedImages = state.images.map { image ->
                        if (image.id == result.imageId) {
                            val newWidth = if (result.width > 0) result.width else image.intrinsicWidth
                            val newHeight = if (result.height > 0) result.height else image.intrinsicHeight
                            image.copy(
                                imageBytes = result.bytes,
                                mimeType = result.mimeType,
                                intrinsicWidth = newWidth,
                                intrinsicHeight = newHeight,
                                imageByteOffset = -1,
                                imageByteLength = 0,
                            )
                        } else image
                    }
                    state.copy(images = updatedImages)
                }
            },
            onProgress = { progress ->
                mainVm.viewModelScope.launch(Dispatchers.Main) {
                    mainVm._uiState.update { state ->
                        state.copy(
                            batchOptimizeState = com.pockref.app.model.BatchOptimizeState(
                                status = com.pockref.app.model.BatchOptimizeStatus.RUNNING,
                                total = progress.total,
                                completed = progress.completed,
                                failed = progress.failed,
                                skipped = progress.skipped,
                                totalOriginalBytes = progress.totalOriginalBytes,
                                totalOptimizedBytes = progress.totalOptimizedBytes,
                                currentImageName = progress.currentImageName,
                            ),
                        )
                    }
                }
            },
            onComplete = { progress ->
                mainVm.imageOptimizer.writeBatchOptimizeLog(progress)
                mainVm.viewModelScope.launch(Dispatchers.Main) {
                    val uri = mainVm.pendingSaveUri ?: return@launch
                    val truncate = mainVm.pendingSaveTruncate
                    applyBatchResultsAndSave(uri, truncate, progress)
                }
            },
        )
    }

    private suspend fun applyBatchResultsAndSave(
        uri: Uri,
        truncate: Boolean,
        progress: ImageOptimizer.BatchProgress,
    ) {
        val scene = mainVm._uiState.value.toScene()

        val saveSuccess = writeSceneToFile(uri, truncate, scene.images)

        mainVm._uiState.update { state ->
            state.copy(
                batchOptimizeState = com.pockref.app.model.BatchOptimizeState(
                    status = com.pockref.app.model.BatchOptimizeStatus.COMPLETED,
                    total = progress.total,
                    completed = progress.completed,
                    failed = progress.failed,
                    skipped = progress.skipped,
                    totalOriginalBytes = progress.totalOriginalBytes,
                    totalOptimizedBytes = progress.totalOptimizedBytes,
                ),
            )
        }

        if (saveSuccess) {
            mainVm._uiState.update {
                it.copy(
                    canUndoOptimization = mainVm.backupDir != null && mainVm.backupDir!!.exists(),
                )
            }
        }
    }

    private suspend fun writeSceneToFile(uri: Uri, truncate: Boolean, images: List<SceneImage>): Boolean {
        mainVm.setBusy(true)
        var success = false
        try {
            withContext(Dispatchers.IO) {
                val scene = mainVm._uiState.value.toScene().copy(images = images)

                val t0 = System.nanoTime()
                val cacheDir = mainVm.getApplication<Application>().cacheDir
                val tempOutput = File.createTempFile("pockref_save_", ".pur", cacheDir)
                try {
                    val bufferedOut = java.io.BufferedOutputStream(FileOutputStream(tempOutput), 256 * 1024)
                    bufferedOut.use { tempFileOutput ->
                        // V_NOW (2026-06-11): use the custom format
                        // (write) instead of legacy format
                        // (writeLegacyStreaming). The custom format
                        // persists cropRect and locked fields that
                        // the legacy format silently drops. When a
                        // cropped image is saved in legacy format
                        // and reloaded, cropRect is lost (defaults
                        // to null) but displaySize retains the
                        // cropped dimensions, so drawSceneImage
                        // renders the full uncropped bitmap into the
                        // cropped-aspect box, producing a stretched
                        // image.
                        mainVm.codec.write(scene, tempFileOutput)
                    }

                    val t1 = System.nanoTime()
                    Log.d("PockRefSave", "Scene written to temp file in ${(t1 - t0) / 1_000_000}ms")

                    mainVm.takePersistablePermission(uri, read = true, write = true)
                    val mode = if (truncate) "wt" else null
                    val stream = if (mode != null) {
                        mainVm.contentResolver.openOutputStream(uri, mode)
                    } else {
                        mainVm.contentResolver.openOutputStream(uri)
                    }
                    stream?.use { output ->
                        val bufferedOutput = java.io.BufferedOutputStream(output, 256 * 1024)
                        tempOutput.inputStream().use { input ->
                            val bufferedInput = java.io.BufferedInputStream(input, 256 * 1024)
                            bufferedInput.copyTo(bufferedOutput, 256 * 1024)
                            bufferedOutput.flush()
                        }
                    } ?: error("Unable to open destination")

                    val t2 = System.nanoTime()
                    Log.d("PockRefSave", "Copied to destination in ${(t2 - t1) / 1_000_000}ms, total ${(t2 - t0) / 1_000_000}ms")
                } finally {
                    tempOutput.delete()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (error: Throwable) {
            Log.e("PockRefSave", "writeSceneToFile failed", error)
            mainVm.reportError("保存场景失败: ${error.message ?: error.javaClass.simpleName}", error)
            mainVm.setBusy(false)
            mainVm._uiState.update { it.copy(isSaving = false) }
            return false
        }
        mainVm.rememberRecentScene(uri, mainVm._uiState.value.sceneName.ifBlank { mainVm.queryDisplayNameSync(uri) ?: "Scene" }, "Saved")
        mainVm._uiState.update { it.copy(images = images) }
        mainVm.lastSaveSnapshotHash = mainVm.computeQuickHash(mainVm._uiState.value.toScene())
        mainVm.syncDirectSaveState()
        success = true
        mainVm.setBusy(false)
        mainVm._uiState.update { it.copy(isSaving = false) }
        return success
    }

    fun undoLastOptimization() {
        val bDir = mainVm.backupDir ?: return
        if (!bDir.exists()) return

        mainVm.viewModelScope.launch(Dispatchers.IO) {
            val restored = mainVm._uiState.value.images.map { image ->
                val datFile = File(bDir, "${image.id}.dat")
                val mimeFile = File(bDir, "${image.id}.mime")
                if (datFile.exists()) {
                    val bytes = datFile.readBytes()
                    val mime = if (mimeFile.exists()) mimeFile.readText() else image.mimeType
                    image.copy(imageBytes = bytes, mimeType = mime)
                } else image
            }

            withContext(Dispatchers.Main) {
                mainVm._uiState.update { state ->
                    state.copy(
                        images = restored,
                        canUndoOptimization = false,
                    )
                }
            }
            mainVm.notifyContentChanged()
        }
    }

    fun enterCropMode() {
        val state = mainVm._uiState.value
        val selectedId = state.selectedImageId ?: return
        // V_NOW: locked images can't be cropped. Without this
        // guard the crop overlay would render and commit a
        // pixel-level change despite the user's lock intent.
        val selectedImage = state.images.firstOrNull { it.id == selectedId }
        if (selectedImage?.locked == true) return
        // Restore the image's existing crop rect (if any)
        // so the user can adjust a previous crop. If no
        // crop exists, start with the full-image default.
        val existingCrop = selectedImage?.cropRect ?: CropRect()
        mainVm._uiState.update { it.copy(cropImageId = selectedId, cropRect = existingCrop, cropAspect = CropAspect.FREE, cropRotation = 0f) }
    }

    fun cancelCrop() {
        mainVm._uiState.update { it.copy(cropImageId = null, cropRect = CropRect(), cropAspect = CropAspect.FREE, cropRotation = 0f) }
    }

    fun updateCropRect(cropRect: CropRect) {
        val state = mainVm._uiState.value
        val aspect = state.cropAspect
        if (aspect != CropAspect.FREE) {
            val image = state.images.find { it.id == state.cropImageId } ?: return
            val constrained = constrainToAspect(cropRect, aspect, image.intrinsicWidth, image.intrinsicHeight)
            mainVm._uiState.update { it.copy(cropRect = constrained) }
        } else {
            mainVm._uiState.update { it.copy(cropRect = cropRect) }
        }
    }

    private fun constrainToAspect(rect: CropRect, aspect: CropAspect, imgW: Int, imgH: Int): CropRect {
        // V_NOW: math fix (user feedback "调整好选区,点击
        // 应用后,图片没有变化"). The previous formula had
        // two bugs:
        //
        //   1. The branch condition compared
        //      `currentW / currentH > targetAspect` — this
        //      is the rect's NORMALIZED aspect, not its
        //      PIXEL aspect. Since the [CropRect] coords are
        //      normalized over [0, 1] in image space, the
        //      pixel aspect of the selection is
        //      `(currentW / currentH) * imgAspect`, NOT
        //      `currentW / currentH`. Comparing the wrong
        //      metric made the "too wide / too tall" branch
        //      decision incorrect for any image where
        //      [imgAspect] != 1.0.
        //
        //   2. The `newW` / `newH` math multiplied by
        //      `imgH / imgW` or `imgW / imgH` to keep the
        //      PIXEL aspect at `targetAspect`, but the
        //      result was `newW = currentH * (targetAspect /
        //      imgAspect)` and `newH = currentW * (imgAspect
        //      / targetAspect)`. The `coerceIn(0f, 1f -
        //      newW)` then failed silently when `newW > 1f`
        //      (range became [0, negative], clamping
        //      produced `left = 0, right = newW > 1f` —
        //      out-of-bounds rect). The downstream
        //      [commitCrop] then hit its `isValid()` /
        //      `cropPixelW <= 0` early return and silently
        //      called [cancelCrop] — the user saw
        //      "nothing happened" on Apply.
        //
        // Fixed formula: keep the newH branch's "freeze H,
        // scale W to hit targetAspect" direction; pick the
        // branch by comparing the actual PIXEL aspect
        // (`currentW / currentH) * imgAspect`) against
        // `targetAspect`. The `coerceIn` lower bound uses
        // `(1f - newW).coerceAtLeast(0f)` to defend against
        // the `newW > 1f` clamp-reversal case
        // (see [clampOffsetToScene] for the same pattern).
        val targetAspect = aspect.ratioW / aspect.ratioH
        val imgAspect = imgW.toFloat() / imgH.toFloat()
        val currentW = rect.cropWidth
        val currentH = rect.cropHeight
        val currentPxAspect = if (currentH > 0f) (currentW / currentH) * imgAspect else targetAspect
        val newW: Float
        val newH: Float
        if (currentPxAspect > targetAspect) {
            // Selection is too wide in pixels: freeze H,
            // scale W down to hit targetAspect.
            newH = currentH
            newW = currentH * (targetAspect / imgAspect)
        } else {
            // Selection is too tall in pixels: freeze W,
            // scale H down to hit targetAspect.
            newW = currentW
            newH = currentW * (imgAspect / targetAspect)
        }
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        val cl = (cx - newW / 2f).coerceIn(0f, (1f - newW).coerceAtLeast(0f))
        val ct = (cy - newH / 2f).coerceIn(0f, (1f - newH).coerceAtLeast(0f))
        return CropRect(left = cl, top = ct, right = cl + newW, bottom = ct + newH)
    }

    fun setCropAspect(aspect: CropAspect) {
        val state = mainVm._uiState.value
        val image = state.images.find { it.id == state.cropImageId } ?: return
        val newRect = state.cropRect.withAspect(aspect, image.intrinsicWidth, image.intrinsicHeight)
        mainVm._uiState.update { it.copy(cropAspect = aspect, cropRect = newRect) }
    }

    fun setCropRotation(degrees: Float) {
        // V_NOW: accumulate, not overwrite. The crop UI's two
        // rotate buttons (PureRefApp.kt crop overlay) dispatch
        // SetCropRotation(-90f) / SetCropRotation(90f) — the
        // user expects each tap to rotate by another 90° from
        // the current value (e.g. tap -90° twice → -180°, tap
        // -90° four times → 0° mod 360). The previous code set
        // `cropRotation = degrees` directly, so successive taps
        // were idempotent and the second + third + fourth taps
        // had no visible effect. Normalize to (-180, 180] to
        // keep the displayed "X°" label compact.
        mainVm._uiState.update { current ->
            val raw = current.cropRotation + degrees
            val normalized = ((raw % 360f) + 360f) % 360f
            val signed = if (normalized > 180f) normalized - 360f else normalized
            current.copy(cropRotation = signed)
        }
    }

    /**
     * Render the current selection (or the single selected
     * image) and write it to [uri] as [format]. Cancels silently
     * when nothing is selected or the image is in the middle of
     * a crop (the in-progress crop's image is a stale state).
     *
     * The render path applies the image's full transform —
     * scale, rotation, and both flip axes — in that order, so
     * the exported file is "what the user sees" rather than the
     * raw decoded bytes. Decoding, transform, encode, and
     * write all run on [Dispatchers.IO] to keep the main
     * thread free; the surrounding [mainVm.setBusy] /
     * [setBusy] pair drives the progress indicator.
     */
    fun exportSelected(uri: Uri, format: ExportFormat) {
        val state = mainVm._uiState.value
        val targets = if (state.selectedImageIds.isNotEmpty()) {
            state.images.filter { it.id in state.selectedImageIds }
        } else if (state.selectedImageId != null) {
            state.images.filter { it.id == state.selectedImageId }
        } else {
            emptyList()
        }
        if (targets.isEmpty()) return
        // For multi-select we still want a single output. The
        // user picked one save location; we write the FIRST
        // selected image to it. The chooser dialog explicitly
        // tells the user "导出当前选中的图" (singular) for this
        // reason — single-image export is the supported
        // workflow. (Batch export to a folder would need a
        // separate action and UI.)
        val image = targets.first()
        if (image == state.images.find { it.id == state.cropImageId }) {
            // Don't allow exporting an image that's mid-crop
            // (its display state is stale).
            return
        }
        mainVm.viewModelScope.launch {
            mainVm.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    val bytes = mainVm.resolveImageBytes(image)
                    var source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: error("无法解码图片数据")
                    // Apply non-destructive crop before transforms.
                    // The crop rect is stored as 0..1 normalized
                    // coordinates on the original image.
                    val crop = image.cropRect
                    val croppedSource = if (crop != null) {
                        val cx = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
                        val cy = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
                        val cw = ((crop.right - crop.left) * source.width).toInt().coerceIn(1, source.width - cx)
                        val ch = ((crop.bottom - crop.top) * source.height).toInt().coerceIn(1, source.height - cy)
                        Bitmap.createBitmap(source, cx, cy, cw, ch)
                            .also { if (it !== source) source.recycle() }
                    } else {
                        source
                    }
                    // Apply transforms in canvas-render order:
                    // scale first, then rotation, then both
                    // flips. The matrix's pre-/post- semantics
                    // mean we build the matrix incrementally
                    // and pass the whole thing to createBitmap.
                    val matrix = android.graphics.Matrix()
                    matrix.postScale(image.scale, image.scale)
                    if (image.rotationDegrees != 0f) {
                        matrix.postRotate(
                            image.rotationDegrees,
                            croppedSource.width / 2f,
                            croppedSource.height / 2f,
                        )
                    }
                    if (image.flipX || image.flipY) {
                        val sx = if (image.flipX) -1f else 1f
                        val sy = if (image.flipY) -1f else 1f
                        matrix.postScale(sx, sy, croppedSource.width / 2f, croppedSource.height / 2f)
                    }
                    val rendered = if (matrix.isIdentity) croppedSource
                    else Bitmap.createBitmap(croppedSource, 0, 0, croppedSource.width, croppedSource.height, matrix, true)
                        .also { if (it !== croppedSource) croppedSource.recycle() }
                    try {
                        val resolver = mainVm.contentResolver
                        val outputStream = resolver.openOutputStream(uri)
                            ?: error("无法打开目标 URI 的输出流")
                        outputStream.use { stream ->
                            val compressFormat = when (format) {
                                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                            }
                            // Quality 95 matches the crop path —
                            // visually lossless for JPEG and
                            // ignored by PNG.
                            val ok = rendered.compress(compressFormat, 95, stream)
                            if (!ok) error("图片编码失败")
                        }
                    } finally {
                        if (rendered !== source) rendered.recycle()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                mainVm.reportError("导出图片失败: ${error.message ?: error.javaClass.simpleName}", error)
            } finally {
                mainVm.setBusy(false)
            }
        }
    }

    /**
     * Batch export: write every selected image as a sibling file inside
     * the folder the user picked via ACTION_OPEN_DOCUMENT_TREE. Mirrors
     * [exportSelected]'s render path (scale → rotation → flips → encode)
     * but creates a new DocumentFile per image via
     * [DocumentsContract.createDocument] so name collisions are handled
     * by the Storage Access Framework (it appends " (N)" for duplicates).
     *
     * Selected images whose name collides with an existing file in the
     * tree are still written, but under the SAF-disambiguated name —
     * i.e. we don't silently skip them. The single-image export path
     * (the per-image CreateDocument flow) gives the user the chance to
     * overwrite or rename manually; the batch path deliberately does
     * not, because the use case is "export the whole selection" and
     * silent skips would surprise the user.
     */
    fun exportBatchToTree(treeUri: Uri, format: ExportFormat) {
        val state = mainVm._uiState.value
        val targets = if (state.selectedImageIds.isNotEmpty()) {
            state.images.filter { it.id in state.selectedImageIds }
        } else if (state.selectedImageId != null) {
            state.images.filter { it.id == state.selectedImageId }
        } else {
            emptyList()
        }
        if (targets.isEmpty()) return
        // Don't allow exporting an image that's mid-crop
        // (its display state is stale). If ANY target is in-crop,
        // we bail — partial-batch export would leave the user with
        // a folder of mixed-state images, which is more confusing
        // than just saying "finish the crop first".
        val cropId = state.cropImageId
        if (cropId != null && targets.any { it.id == cropId }) {
            return
        }
        val mime = when (format) {
            ExportFormat.PNG -> "image/png"
            ExportFormat.JPEG -> "image/jpeg"
        }
        val extension = when (format) {
            ExportFormat.PNG -> "png"
            ExportFormat.JPEG -> "jpg"
        }
        // ACTION_OPEN_DOCUMENT_TREE returns a tree URI like
        //   content://com.android.externalstorage.documents/tree/primary%3ADownload
        // but DocumentsContract.createDocument() requires a
        // document URI like
        //   content://.../tree/primary%3ADownload/document/primary%3ADownload
        // Passing the raw tree URI causes createDocument to
        // return null on Samsung devices (the provider can't
        // resolve the document ID from a tree-only path).
        // Convert tree URI → document URI once, then use it
        // for all createDocument calls.
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        mainVm.viewModelScope.launch {
            mainVm.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    val resolver = mainVm.contentResolver
                    // V_NOW: pre-flight check. The user's "multi-
                    // select batch export always fails" symptom
                    // was traced to the picked folder's
                    // DocumentsProvider silently returning null
                    // from [DocumentsContract.createDocument] for
                    // every image — common with cloud-sync
                    // providers (Samsung Cloud, OneDrive, Google
                    // Drive under some configurations), with
                    // read-only share targets, and with some
                    // Samsung fork paths under /storage/emulated/0
                    // that grant read but not write. The
                    // per-image try/catch would have masked the
                    // cause and surfaced it as the unhelpful
                    // "批量导出完成: 0 张成功, N 张失败" — the
                    // user couldn't tell whether it was a bug,
                    // a permission issue, or a wrong folder.
                    //
                    // Doing ONE createDocument with a unique
                    // temp name before the loop costs ~1ms and
                    // converts the silent-fail into a clear
                    // "this folder doesn't support image
                    // export" message naming the provider. The
                    // temp file is deleted immediately after
                    // creation so the user's folder stays clean
                    // (cleanup failure is logged but non-fatal
                    // — the file is hidden by leading
                    // underscores and easy to filter).
                    // V_NOW2: dual-preflight to differentiate the
                    // two real reasons the user's multi-select
                    // batch export fails:
                    //
                    //   (A) folder is read-only / provider doesn't
                    //       support createDocument at all
                    //       (Samsung Cloud, some OneDrive shares,
                    //       OEM fork paths under /storage/emulated/0
                    //       that grant read but not write)
                    //   (B) folder is fine, but provider has a
                    //       MIME whitelist that REJECTS the user's
                    //       chosen format (Google Drive + image/png
                    //       or image/jpeg; OneDrive has the same
                    //       pattern)
                    //
                    // ONE preflight with the user's MIME couldn't
                    // tell (A) from (B) — both surface as "uri =
                    // null". Doing TWO tests (user's MIME +
                    // application/octet-stream) lets us branch the
                    // error into actionable advice. The temp
                    // files are deleted after each preflight so
                    // the user's folder stays clean (cleanup
                    // failure is logged but non-fatal — the
                    // files are hidden by leading underscores).
                    suspend fun tryCreateDocument(
                        testMime: String,
                        testName: String,
                    ): android.net.Uri? {
                        return try {
                            DocumentsContract.createDocument(
                                resolver,
                                docUri,
                                testMime,
                                testName,
                            )
                        } catch (e: Throwable) {
                            android.util.Log.w(
                                "PockRefExport",
                                "preflight createDocument threw " +
                                    "(mime=$testMime): " +
                                    "${e.javaClass.simpleName}: ${e.message}",
                            )
                            null
                        }
                    }
                    suspend fun deleteIfPresent(uri: android.net.Uri?) {
                        if (uri == null) return
                        try {
                            DocumentsContract.deleteDocument(resolver, uri)
                        } catch (e: Throwable) {
                            android.util.Log.w(
                                "PockRefExport",
                                "preflight cleanup failed: ${e.message}",
                            )
                        }
                    }
                    val preflightStamp = System.currentTimeMillis()
                    val preflightUriUser = tryCreateDocument(
                        testMime = mime,
                        testName = "__pockref_preflight_test__" +
                            "${preflightStamp}__.$extension",
                    )
                    val preflightUriOctet = tryCreateDocument(
                        testMime = "application/octet-stream",
                        testName = "__pockref_preflight_test_octet__" +
                            "${preflightStamp}__.bin",
                    )
                    val userMimeFailed = preflightUriUser == null
                    val octetFailed = preflightUriOctet == null
                    if (userMimeFailed) deleteIfPresent(preflightUriOctet)
                    if (!userMimeFailed) deleteIfPresent(preflightUriUser)
                    if (userMimeFailed && octetFailed) {
                        // Case (A): the folder rejects BOTH image
                        // MIME and octet-stream → the provider
                        // either doesn't allow file creation at
                        // all (read-only share / cloud-sync
                        // without write permission) or doesn't
                        // implement createDocument for this tree
                        // (some Samsung fork paths under
                        // /storage/emulated/0). Old advice still
                        // applies: try a different folder.
                        val auth = treeUri.authority
                        android.util.Log.w(
                            "PockRefExport",
                            "preflight failed: BOTH mime rejected " +
                                "(authority=$auth, mime=$mime, " +
                                "octet also failed) — folder is read-only " +
                                "or provider doesn't support createDocument",
                        )
                        mainVm.reportError(
                            "无法在此文件夹创建文件 (provider=$auth)。" +
                                "请尝试选择 Download 或 Documents 这样的本地文件夹重试。",
                        )
                        return@withContext
                    }
                    if (userMimeFailed) {
                        // Case (B): folder accepts file creation
                        // (octet-stream worked) but the chosen
                        // image MIME is on the provider's
                        // blacklist. Tell the user to try the
                        // OTHER format (PNG ↔ JPG) — most of
                        // these providers reject exactly one
                        // of the two — or pick a different
                        // folder.
                        val auth = treeUri.authority
                        val formatName = if (mime == "image/png") "PNG" else "JPG"
                        val otherFormat = if (mime == "image/png") "JPG" else "PNG"
                        android.util.Log.w(
                            "PockRefExport",
                            "preflight partial: user-mime=$mime rejected, " +
                                "octet-stream accepted (authority=$auth). " +
                                "Provider has a MIME whitelist. " +
                                "Suggest retry with $otherFormat.",
                        )
                        mainVm.reportError(
                            "此文件夹拒绝 $formatName 格式 (provider=$auth)。" +
                                "可以尝试切换为 $otherFormat 格式,或选择其他文件夹。",
                        )
                        return@withContext
                    }
                    // Case (C): both preflights succeeded. The
                    // folder is healthy; the real export below
                    // will use the user's chosen MIME.
                    var successCount = 0
                    var failCount = 0
                    // V3 diagnostic: log the tree URI's
                    // authority + the MIME we're about to
                    // create with. The "无法使用此文件夹"
                    // failure mode the user reported can
                    // mean several things:
                    //   (a) the picked folder's DocumentsProvider
                    //       doesn't support createDocument for
                    //       image/png (some cloud providers
                    //       whitelist MIME types)
                    //   (b) the per-task write grant was
                    //       revoked before this ran
                    //   (c) the persistable grant succeeded
                    //       but the URI was malformed
                    //   (d) some other provider-specific bug
                    // Capturing the authority + the first
                    // createDocument return (URI or null)
                    // narrows this to (a)/(b)/(c)/(d) in
                    // one logcat line.
                    val treeAuthority = treeUri.authority
                    android.util.Log.i(
                        "PockRefExport",
                        "batch start: tree=$treeUri authority=$treeAuthority " +
                            "mime=$mime targetCount=${targets.size}",
                    )
                    for (image in targets) {
                        try {
                            val baseName = image.name.ifBlank { "Untitled" }
                            val displayName = "$baseName.$extension"
                            // DocumentsContract.createDocument handles
                            // collision-disambiguation on the SAF side,
                            // so we pass the desired name verbatim.
                            val documentUri = try {
                                DocumentsContract.createDocument(
                                    resolver,
                                    docUri,
                                    mime,
                                    displayName,
                                )
                            } catch (innerCreate: Throwable) {
                                // Some providers throw rather than
                                // returning null on permission /
                                // capability errors. Surface the
                                // exception class + message in the
                                // log so the user can read it off
                                // logcat for diagnosis.
                                android.util.Log.w(
                                    "PockRefExport",
                                    "createDocument threw for $displayName: " +
                                        "${innerCreate.javaClass.simpleName}: ${innerCreate.message}",
                                )
                                null
                            }
                            if (documentUri == null) {
                                android.util.Log.w(
                                    "PockRefExport",
                                    "createDocument returned null for $displayName " +
                                        "(authority=$treeAuthority mime=$mime)",
                                )
                                failCount++
                                continue
                            }
                            val bytes = mainVm.resolveImageBytes(image)
                            var source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                ?: error("无法解码图片数据")
                            // Apply non-destructive crop before transforms.
                            val crop = image.cropRect
                            val croppedSource = if (crop != null) {
                                val cx = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
                                val cy = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
                                val cw = ((crop.right - crop.left) * source.width).toInt().coerceIn(1, source.width - cx)
                                val ch = ((crop.bottom - crop.top) * source.height).toInt().coerceIn(1, source.height - cy)
                                Bitmap.createBitmap(source, cx, cy, cw, ch)
                                    .also { if (it !== source) source.recycle() }
                            } else {
                                source
                            }
                            val matrix = android.graphics.Matrix()
                            matrix.postScale(image.scale, image.scale)
                            if (image.rotationDegrees != 0f) {
                                matrix.postRotate(
                                    image.rotationDegrees,
                                    croppedSource.width / 2f,
                                    croppedSource.height / 2f,
                                )
                            }
                            if (image.flipX || image.flipY) {
                                val sx = if (image.flipX) -1f else 1f
                                val sy = if (image.flipY) -1f else 1f
                                matrix.postScale(sx, sy, croppedSource.width / 2f, croppedSource.height / 2f)
                            }
                            val rendered = if (matrix.isIdentity) croppedSource
                            else Bitmap.createBitmap(croppedSource, 0, 0, croppedSource.width, croppedSource.height, matrix, true)
                                .also { if (it !== croppedSource) croppedSource.recycle() }
                            try {
                                val outputStream = resolver.openOutputStream(documentUri)
                                    ?: error("无法打开目标 URI 的输出流")
                                outputStream.use { stream ->
                                    val compressFormat = when (format) {
                                        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                                        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                                    }
                                    val ok = rendered.compress(compressFormat, 95, stream)
                                    if (!ok) error("图片编码失败")
                                }
                            } finally {
                                if (rendered !== source) rendered.recycle()
                            }
                            successCount++
                        } catch (innerError: Throwable) {
                            // One bad image shouldn't kill the whole
                            // batch — log and continue. The final
                            // error message below summarizes how
                            // many landed and how many failed.
                            Log.w(
                                "PockRef",
                                "exportBatch: failed to write ${image.name}: ${innerError.message}",
                            )
                            failCount++
                        }
                    }
                    if (failCount > 0) {
                        mainVm.reportError("批量导出完成: ${successCount} 张成功, ${failCount} 张失败")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                // V3: include the authority in the error message
                // so the user can tell which provider failed
                // (com.android.providers.media for /Documents,
                // com.google.android.apps.docs.storage for Drive,
                // etc.) without having to dig through logcat.
                val auth = treeUri.authority
                mainVm.reportError(
                    "批量导出失败 [provider=$auth]: " +
                        "${error.message ?: error.javaClass.simpleName}",
                    error,
                )
                android.util.Log.w(
                    "PockRefExport",
                    "batch top-level error: ${error.javaClass.simpleName}: ${error.message}",
                    error,
                )
            } finally {
                mainVm.setBusy(false)
            }
        }
    }

    fun commitCrop() {
        val state = mainVm._uiState.value
        val cropId = state.cropImageId ?: return
        val cropRect = state.cropRect
        if (!cropRect.isValid()) { cancelCrop(); return }
        val image = state.images.find { it.id == cropId } ?: return

        val cropPixelW = (cropRect.cropWidth * image.intrinsicWidth).toInt()
        val cropPixelH = (cropRect.cropHeight * image.intrinsicHeight).toInt()
        if (cropPixelW <= 0 || cropPixelH <= 0) { cancelCrop(); return }

        // Non-destructive crop: instead of modifying the
        // bitmap pixels, store the crop rect on the
        // SceneImage. The original pixel data is preserved
        // so the user can re-adjust or remove the crop
        // later. The display renderer (drawSceneImage)
        // applies the crop rect as a clip/mask at draw
        // time. The display size is adjusted to match the
        // cropped aspect ratio.
        val oldDisplayW = image.displaySize.width
        val oldDisplayH = image.displaySize.height
        // V_NOW (2026-06-11): use the PIXEL-SPACE aspect
        // ratio of the crop, not the normalized
        // cropWidth/cropHeight ratio. [cropWidth] and
        // [cropHeight] are in 0..1 normalized space, so
        // their ratio is NOT the visual aspect ratio of
        // the cropped pixels. For a 1:1 crop on a 16:9
        // image, [withAspect] returns a normalized rect
        // whose cropWidth/cropHeight ratio is 0.5625 (a
        // vertical letterbox column) — the pixel-space
        // aspect is 1.0. Using 0.5625 here caused the
        // new displaySize to be 0.5625:1 instead of 1:1,
        // so [drawSceneImage] would non-uniformly stretch
        // the square crop into a tall narrow box.
        val imgAspect = if (image.intrinsicHeight > 0)
            image.intrinsicWidth.toFloat() / image.intrinsicHeight.toFloat()
        else 1f
        val cropAspect = (cropRect.cropWidth / cropRect.cropHeight) * imgAspect
        val newDisplayW: Float
        val newDisplayH: Float
        if (oldDisplayW / oldDisplayH > cropAspect) {
            newDisplayH = oldDisplayH
            newDisplayW = oldDisplayH * cropAspect
        } else {
            newDisplayW = oldDisplayW
            newDisplayH = oldDisplayW / cropAspect
        }
        // If cropRect covers the full image (0,0,1,1),
        // store null to indicate "no crop" — this lets
        // the user remove a previous crop by dragging
        // the handles back to the edges.
        val storedCrop = if (cropRect.left <= 0.001f && cropRect.top <= 0.001f &&
            cropRect.right >= 0.999f && cropRect.bottom >= 0.999f) null else cropRect
        mainVm.mutateScene {
            copy(
                images = images.map { img ->
                    if (img.id != cropId) img else img.copy(
                        cropRect = storedCrop,
                        displaySize = Size2D(newDisplayW, newDisplayH),
                    )
                },
                cropImageId = null,
                cropRect = CropRect(),
                cropAspect = CropAspect.FREE,
                cropRotation = 0f,
            )
        }
    }

    fun onCloseScene() {
        if (mainVm._uiState.value.isSaving) return
        if (mainVm.hasUnsavedChanges) {
            mainVm._uiState.update { it.copy(showUnsavedChangesDialog = true) }
        } else {
            closeScene()
        }
    }

    fun onUnsavedDialogSave() {
        mainVm._uiState.update { it.copy(showUnsavedChangesDialog = false) }
        if (mainVm.currentSceneUri == null) {
            mainVm._uiState.update { it.copy(triggerSaveAsFromDialog = true, pendingCloseAfterSave = true) }
            return
        }
        mainVm.viewModelScope.launch {
            val success = if (mainVm.canDirectSave) {
                performSave(mainVm.currentSceneUri!!, truncate = true)
            } else {
                performSave(mainVm.currentSceneUri!!, truncate = false).also { saved ->
                    if (saved) mainVm.isImportedPurFile = true
                }
            }
            if (success) {
                closeScene()
            } else if (!mainVm._uiState.value.showBatchOptimizeConfirm) {
                // V_NOW (user bug report — "保存完成后未保存的更改弹窗
                // 一直显示"): [performSave] returns `false` in TWO
                // distinct cases and the old code treated both as
                // "save failed, re-show the unsaved-changes dialog":
                //
                //   (a) Real failure — disk full, write threw, etc.
                //       Re-showing the dialog is correct: the user's
                //       data is unsaved, they need to know.
                //
                //   (b) Pending the batch-optimize-confirm prompt.
                //       When [ImageOptimizer.saveOptimizeConfig] finds
                //       images that exceed [minCompressThreshold],
                //       [performSave] flips
                //       `showBatchOptimizeConfirm = true` and returns
                //       false WITHOUT actually writing — it's a
                //       "save is queued, waiting for the user's
                //       yes/no on the optimize dialog" state, not a
                //       failure. Re-showing the unsaved-changes
                //       dialog on top of (or before the user even sees)
                //       the optimize-confirm dialog is what produced
                //       the "stuck on the unsaved-changes dialog"
                //       symptom the user reported.
                //
                // Differentiate via [showBatchOptimizeConfirm]: if
                // it's set, we're in case (b) — leave the
                // unsaved-changes dialog hidden, the user will
                // resolve the optimize-confirm flow instead.
                // Otherwise we're in case (a) — re-show the
                // dialog as before.
                mainVm._uiState.update { it.copy(showUnsavedChangesDialog = true) }
            }
        }
    }

    fun onUnsavedDialogDiscard() {
        mainVm._uiState.update { it.copy(showUnsavedChangesDialog = false) }
        closeScene()
    }

    fun onUnsavedDialogCancel() {
        mainVm._uiState.update { it.copy(showUnsavedChangesDialog = false) }
    }

    private fun closeScene() {
        mainVm.undoStack.clear()
        mainVm.redoStack.clear()
        mainVm.currentSceneUri = null
        mainVm.autoSaveManager.clearAutoSaveState()
        // V_NOW (follow-up after "1 min never fires"
        // bug): the previous version of this function
        // also reset `mainVm.lastAutoSaveSnapshotHash =
        // ""` here. That in-memory field was the
        // auto-save loop's `hasContentChanged` source.
        // It's been removed — the auto-save content-
        // change decision now lives in
        // [AutoSaveManager.autoSave]'s SHA-256 hash
        // compare, which reads the live [uiState.value]
        // on every tick and doesn't depend on a per-
        // [uiState.update] notification path.
        // [AutoSaveManager.clearAutoSaveState] (above)
        // already wipes the durable
        // [KEY_LAST_CONTENT_HASH] + [KEY_LAST_SAVE_TS],
        // which is what the next-loop-tick SHA-256
        // compare uses as its baseline.
        mainVm.isImportedPurFile = false
        mainVm.lastSaveSnapshotHash = ""
        // Reuse the last preset the user picked, so closing one scene and
        // starting the next keeps the canvas on the same colour.
        val defaultBg = mainVm.appPrefs.defaultBackgroundPreset.color
        mainVm._uiState.update {
            it.copy(
                // Closing the scene leaves the canvas empty but the
                // counter-1 still represents the last auto-name we
                // handed out; +1 is the natural "next" name when the
                // user imports the next batch of images.
                sceneName = SceneNaming.nextUntitled(mainVm.untitledCounter + 1),
                backgroundColor = defaultBg,
                backgroundCustomColor = defaultBg,
                showBackgroundPicker = false,
                images = emptyList(),
                selectedImageId = null,
                selectedImageIds = emptySet(),
                canUndo = false,
                canRedo = false,
                canvasScale = 1f,
                canvasOffset = Offset.Zero,
                fittedImageId = null,
                cropImageId = null,
            )
        }
    }

    fun clearSaveAsTrigger() {
        mainVm._uiState.update { it.copy(triggerSaveAsFromDialog = false) }
    }

    fun updateCanvasViewport(width: Float, height: Float) {
        val safeWidth = width.coerceAtLeast(0f)
        val safeHeight = height.coerceAtLeast(0f)
        mainVm._uiState.update { state ->
            val current = state.canvasViewportSize
            if (current.width == safeWidth && current.height == safeHeight) {
                state
            } else {
                state.copy(canvasViewportSize = Size2D(safeWidth, safeHeight))
            }
        }
    }

    fun setSceneName(value: String) {
        mainVm._uiState.update { it.copy(sceneName = value) }
    }

    private fun PockRefScene.toLegacyPngScene() = copy(
        images = images.map { image ->
            val imgBytes = mainVm.resolveImageBytes(image)
            val pngBytes = if (image.mimeType == "image/png") {
                imgBytes
            } else {
                val bitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                    ?: error("Unable to decode ${image.name}")
                ByteArrayOutputStream().use { buffer ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
                    bitmap.recycle()
                    buffer.toByteArray()
                }
            }
            image.copy(imageBytes = pngBytes, mimeType = "image/png")
        }
    )

    /**
     * Export the entire canvas as a single PNG image with transparent
     * background. Computes the bounding box of all images, creates a
     * bitmap of that size, and renders every image at its canvas
     * position with full transform support (scale, rotation, flips).
     */
    fun exportCanvas(uri: Uri) {
        val state = mainVm._uiState.value
        val images = state.images
        if (images.isEmpty()) return

        mainVm.viewModelScope.launch {
            mainVm.setBusy(true)
            try {
                withContext(Dispatchers.IO) {
                    // V_NOW (2026-06-08 user feedback "13MB 文件导出后
                    // 只有 791KB 且图片不完整"): the previous
                    // implementation called BitmapFactory.decodeByteArray
                    // on every image in a first pass WITHOUT
                    // inSampleSize, then held all decoded bitmaps in
                    // a list while creating an 8192px-target canvas.
                    // For a 13MB .pur with 10+ high-res references
                    // (each 4K ~64MB ARGB_8888 after decode) plus a
                    // 8192² ARGB_8888 canvas (~256MB), the total
                    // exceeded the Android heap and decode threw
                    // OutOfMemoryError — but the per-image try/catch
                    // in the render pass silently swallowed it, so
                    // the user saw a partial export.
                    //
                    // The fix is a two-pass design:
                    //   Pass 1 — inJustDecodeBounds only (no pixel
                    //            allocation), compute the bounding
                    //            box in canvas space.
                    //   Pass 2 — stream-decode each image with
                    //            inSampleSize tuned to its
                    //            target render size, draw it, then
                    //            immediately recycle the bitmap so
                    //            at most one source bitmap is live
                    //            at any moment. The export canvas
                    //            is also capped at 4096px (down
                    //            from 8192) so the canvas itself
                    //            stays under ~64MB ARGB_8888.

                    // -----------------------------------------------------------------
                    // Pass 1: probe sizes, compute bounding box. No pixel decode.
                    // -----------------------------------------------------------------
                    data class ProbeInfo(
                        val image: SceneImage,
                        val byteSize: Int,
                        val intrinsicW: Int,
                        val intrinsicH: Int,
                    )

                    val probes = mutableListOf<ProbeInfo>()
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE

                    for (image in images) {
                        val bytes = mainVm.resolveImageBytes(image)
                        val bounds = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) continue
                        probes += ProbeInfo(
                            image = image,
                            byteSize = bytes.size,
                            intrinsicW = bounds.outWidth,
                            intrinsicH = bounds.outHeight,
                        )

                        // V_NOW (2026-06-09 bug fix "导出画布图片
                        // 尺寸不对 + 不是原始分辨率"):
                        // Pass 1's bbox math runs in **DISPLAY
                        // coordinates** (= on-screen layout
                        // positions and sizes). A uniform
                        // exportDensity factor (computed after
                        // this loop) scales the entire canvas
                        // up to accommodate intrinsic resolution
                        // rendering. This ensures layout
                        // positions match the screen exactly
                        // (no per-image coordinate conversion)
                        // while each image is rendered at its
                        // original photo resolution.
                        //
                        // Previous approaches and why they failed:
                        // 1. Display coords only: canvas =
                        //    screen layout size, but images
                        //    rendered at displaySize resolution
                        //    → "分辨率被压缩"
                        // 2. Intrinsic coords: images at
                        //    original resolution, but
                        //    center.x / displayScaleX uses a
                        //    PER-IMAGE scale factor for position
                        //    conversion. When images have
                        //    different displayScales, relative
                        //    positions are distorted →
                        //    "图片尺寸不对 / 布局错乱"
                        //
                        // The correct approach: display coords
                        // for ALL positions (center, bbox) +
                        // uniform exportDensity that scales the
                        // ENTIRE canvas up. This is like
                        // rendering a webpage at 2×/3× for a
                        // Retina display — the LOGICAL layout
                        // is identical to the screen, but the
                        // PIXEL dimensions are larger.
                        //
                        // exportDensity = max(intrinsicWidth_i
                        //   / displaySize.width_i) across all
                        // images. This ensures the most-
                        // scaled-down image gets its full
                        // intrinsic resolution. Other images
                        // are rendered at
                        //   displaySize × exportDensity
                        // which is ≥ their intrinsicWidth,
                        // so they also get full resolution.
                        val w = image.displaySize.width * image.scale
                        val h = image.displaySize.height * image.scale
                        val cx = image.center.x
                        val cy = image.center.y
                        val corners = floatArrayOf(
                            -w / 2f, -h / 2f,
                             w / 2f, -h / 2f,
                             w / 2f,  h / 2f,
                            -w / 2f,  h / 2f,
                        )
                        // Apply rotation
                        val rad = Math.toRadians(image.rotationDegrees.toDouble()).toFloat()
                        val cos = kotlin.math.cos(rad)
                        val sin = kotlin.math.sin(rad)
                        for (i in 0 until 4) {
                            val x = corners[i * 2]
                            val y = corners[i * 2 + 1]
                            val rx = x * cos - y * sin + cx
                            val ry = x * sin + y * cos + cy
                            if (rx < minX) minX = rx
                            if (ry < minY) minY = ry
                            if (rx > maxX) maxX = rx
                            if (ry > maxY) maxY = ry
                        }
                    }

                    if (probes.isEmpty()) {
                        Log.w("ExportCanvas", "no images decoded; aborting")
                        return@withContext
                    }

                    // V_NOW (2026-06-08 user feedback "导出的画布图片
                    // 显示不全"): add a 4px safety padding (was 1px)
                    // so images that sit flush against the bbox edge
                    // are not clipped when the canvas integer-size
                    // round-down happens below. 1px is mathematically
                    // enough but leaves zero room for any rounding
                    // error in the rotation math; 4px is a robust
                    // margin that still looks tight.
                    val padding = 4f
                    minX -= padding
                    minY -= padding
                    maxX += padding
                    maxY += padding

                    // V_NOW (2026-06-08 user feedback "第 10 张图
                    // 缺失 / 显示不全"): use roundToInt() instead of
                    // toInt(). The previous floor-truncation could
                    // lose up to 1 row of pixels for the bottommost
                    // (or rightmost) image — i.e. the user saw the
                    // last image half-cut because the canvas was
                    // sized 1px shorter than the bbox.
                    val canvasWidth = (maxX - minX).roundToInt().coerceAtLeast(1)
                    val canvasHeight = (maxY - minY).roundToInt().coerceAtLeast(1)

                    // V_NOW (2026-06-09 bug fix "导出画布图片
                    // 尺寸不对 + 不是原始分辨率"):
                    // Compute a UNIFORM exportDensity that scales
                    // the display-coordinate canvas up so each
                    // image can be rendered at its original photo
                    // resolution. exportDensity = max ratio of
                    // intrinsic-to-display across all images.
                    // This is like a Retina/HiDPI scale factor —
                    // the LOGICAL layout (in points) matches the
                    // screen exactly, but the PIXEL dimensions
                    // are larger to accommodate full-resolution
                    // rendering.
                    var exportDensity = 1f
                    for (probe in probes) {
                        val img = probe.image
                        if (img.displaySize.width > 0f && img.displaySize.height > 0f) {
                            val ratioX = img.intrinsicWidth.toFloat() / img.displaySize.width
                            val ratioY = img.intrinsicHeight.toFloat() / img.displaySize.height
                            exportDensity = maxOf(exportDensity, ratioX, ratioY)
                        }
                    }

                    // V_NOW (2026-06-08 user feedback "导出图片
                    // 清晰度不足"): raise the canvas cap from 4096
                    // to 8192 so the export keeps close to the
                    // source resolution for typical reference
                    // boards. 8192² ARGB_8888 = 256MB, but the
                    // two-pass stream-decode design below keeps
                    // peak heap at ~1 source bitmap + the canvas
                    // (~128MB worst case) — fits the standard
                    // 256MB Android heap. The cap only kicks in
                    // when the scene's actual bbox exceeds 8192,
                    // in which case we scale down uniformly.
                    // V_NOW (2026-06-09 user spec "导出画布选项 —
                    // 无尺寸上限"): the cap is now user-toggleable
                    // via the canvas-export dialog. `null` =
                    // unlimited — canvas sizes to actual bbox
                    // regardless of size (can OOM on small-heap
                    // devices; the dialog warns before letting
                    // the user pick this). Default8192 preserves
                    // the pre-feature behaviour for callers that
                    // don't set [PockRefUiState.exportCanvasMaxDim].
                    val maxDim = state.exportCanvasMaxDim
                    // V_NOW (2026-06-09): scale accounts for
                    // exportDensity. The canvas pixel dimensions
                    // = displayBbox × exportDensity × scale.
                    // exportDensity scales the canvas up from
                    // display coords to accommodate intrinsic
                    // resolution; scale optionally shrinks it
                    // back down if maxDim is exceeded.
                    val scale = when {
                        maxDim == null -> 1f
                        canvasWidth * exportDensity > maxDim || canvasHeight * exportDensity > maxDim ->
                        maxDim.toFloat() / maxOf(canvasWidth * exportDensity, canvasHeight * exportDensity)
                        else -> 1f
                    }

                    // V_NOW (2026-06-08 user feedback "导出图不清
                    // 晰 / 没有原图画质"): the previous cap
                    // let a single image with intrinsic > 8192
                    // get decoded at full intrinsic (e.g. 8000²
                    // ARGB_8888 = 256MB), which OOM'd on phones
                    // with a 256MB heap. Cap per-image target at
                    // 4096 — visually identical to the original
                    // for desktop / reference-board viewing, and
                    // keeps the per-image source bitmap at ≤64MB
                    // so we stay safely under the heap (canvas
                    // 64MB + source 64MB = 128MB peak).
                    val maxImageDim = if (maxDim == null) Int.MAX_VALUE else 4096

                    // V_NOW (2026-06-09): canvas pixel size =
                    // display bbox × exportDensity × scale.
                    // exportDensity scales the canvas up from
                    // display coords to accommodate intrinsic
                    // resolution; scale optionally shrinks it
                    // back down if maxDim is exceeded.
                    val bitmap = Bitmap.createBitmap(
                        (canvasWidth * exportDensity * scale).toInt().coerceAtLeast(1),
                        (canvasHeight * exportDensity * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    val canvas = android.graphics.Canvas(bitmap)

                    // -----------------------------------------------------------------
                    // Pass 2: stream-decode with inSampleSize, draw, recycle.
                    // -----------------------------------------------------------------
                    for (probe in probes) {
                        val image = probe.image
                        // V_NOW (2026-06-09 bug fix "导出画布图片
                        // 不是原始分辨率"): decode at intrinsic
                        // resolution so the export preserves
                        // original photo quality. The canvas
                        // is in display coords × exportDensity,
                        // so each image's render size is
                        // [displaySize.width * image.scale
                        //   * exportDensity * scale].
                        // Decoding at intrinsic resolution
                        // ensures the source has enough pixels
                        // for the bilinear drawBitmap to
                        // produce a sharp result.
                        val targetW = probe.intrinsicW
                            .coerceAtMost(maxImageDim).coerceAtLeast(1)
                        val targetH = probe.intrinsicH
                            .coerceAtMost(maxImageDim).coerceAtLeast(1)
                        val sampleSize = calculateSampleSize(
                            probe.intrinsicW, probe.intrinsicH, targetW, targetH,
                        )

                        // Re-read bytes — resolveImageBytes may go through
                        // a RandomAccessFile when imageBytes is empty, so
                        // we don't want to hold every image's bytes
                        // resident across the whole pass.
                        val bytes = mainVm.resolveImageBytes(image)

                        // V_NOW (2026-06-08 user feedback "导出图
                        // 不如原图清晰"): the previous pipeline
                        // did inSampleSize → nearest-neighbor
                        // createBitmap(..., filter=false). The
                        // nearest-neighbor was the main reason
                        // the export looked "fuzzy / pixelated"
                        // — it drops pixels in blocks, producing
                        // visible jaggies on every edge. We now:
                        //   (1) target intrinsicW/H (the original
                        //       photo resolution, capped at
                        //       maxImageDim=4096) so we have
                        //       full detail to draw from,
                        //   (2) use inSampleSize only as a coarse
                        //       shrink to keep the decoded
                        //       bitmap ≤2x the target (see
                        //       calculateSampleSize), and
                        //   (3) use bilinear (filter=true) for
                        //       the exact resize — at ≤0.5x
                        //       shrink this is the sweet spot
                        //       for preserving detail without
                        //       aliasing. The final drawBitmap
                        //       (in renderImageToCanvas) takes
                        //       the high-res source and bilinearly
                        //       scales it down to dstRect
                        //       (displaySize in canvas pixels)
                        //       for the on-screen-equivalent
                        //       placement.
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val source = try {
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                        } catch (oom: OutOfMemoryError) {
                            Log.e(
                                "ExportCanvas",
                                "OOM decoding id=${image.id} name=${image.name} " +
                                    "intrinsic=${probe.intrinsicW}x${probe.intrinsicH} " +
                                    "sampleSize=$sampleSize",
                            )
                            null
                        } ?: continue

                        val scaled = if (source.width != targetW || source.height != targetH) {
                            try {
                                val sx = targetW.toFloat() / source.width.toFloat()
                                val sy = targetH.toFloat() / source.height.toFloat()
                                val m = android.graphics.Matrix().apply { setScale(sx, sy) }
                                val exact = Bitmap.createBitmap(
                                    source, 0, 0, source.width, source.height, m, true,
                                )
                                if (exact !== source && !source.isRecycled) source.recycle()
                                exact
                            } catch (oom: OutOfMemoryError) {
                                Log.e(
                                    "ExportCanvas",
                                    "OOM scaling id=${image.id} name=${image.name} " +
                                        "src=${source.width}x${source.height} " +
                                        "target=${targetW}x${targetH}",
                                )
                                if (!source.isRecycled) source.recycle()
                                continue
                            }
                        } else source

                        try {
                            renderImageToCanvas(
                                canvas = canvas,
                                image = image,
                                source = scaled,
                                minX = minX,
                                minY = minY,
                                scale = scale,
                                exportDensity = exportDensity,
                            )
                        } catch (t: Throwable) {
                            if (t is kotlinx.coroutines.CancellationException) throw t
                            Log.e(
                                "ExportCanvas",
                                "skip img id=${image.id} name=${image.name}: " +
                                    "${t.javaClass.simpleName}: ${t.message}",
                                t,
                            )
                        } finally {
                            if (!scaled.isRecycled) scaled.recycle()
                        }
                    }

                    // Write PNG to URI
                    val resolver = mainVm.contentResolver
                    resolver.openOutputStream(uri)?.use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    } ?: error("无法打开目标 URI 的输出流")

                    // Cleanup
                    bitmap.recycle()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Throwable) {
                mainVm.reportError("导出画布失败: ${error.message ?: error.javaClass.simpleName}", error)
            } finally {
                mainVm.setBusy(false)
            }
        }
    }

    /**
     * Render a single [image] into [canvas] as part of
     * [exportCanvas]. Extracted from the export loop so the
     * caller can `try { ... } catch (Throwable) { skip }`
     * per image — a single misconfigured image (NaN matrix
     * from a bad `image.scale` / `displaySize`, etc.) used
     * to abort the whole export, leaving the rest of the
     * scene unrendered. See [exportCanvas] for context.
     *
     * Post* in Android is right-multiplication, so the post*
     * code order here is the REVERSE of the source-space
     * order. The desired source-space order is
     * `T_c → S(scale) → S(flip) → R(rot) → T` (= center,
     * scale, flip, rotate, translate) to match the
     * on-screen pipeline in [drawSceneImage]. See
     * [exportCanvas] for the full rationale.
     */
    private fun renderImageToCanvas(
        canvas: android.graphics.Canvas,
        image: SceneImage,
        source: android.graphics.Bitmap,
        minX: Float,
        minY: Float,
        scale: Float,
        exportDensity: Float,
    ) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            // Apply colorAdjust (brightness/contrast/saturation) to
            // match the on-screen rendering in drawSceneImage which
            // uses buildColorFilter(image.colorAdjust).
            val matrix = buildColorMatrix(image.colorAdjust)
            if (matrix != null) {
                colorFilter = android.graphics.ColorMatrixColorFilter(
                    android.graphics.ColorMatrix(matrix),
                )
            }
        }

        // V_NOW (2026-06-09 bug fix "导出画布图片尺寸不对 +
        // 不是原始分辨率"):
        // The export pipeline uses display coordinates for layout
        // positions and a uniform exportDensity to scale the canvas
        // up to accommodate intrinsic resolution rendering.
        //
        // [minX/minY] are in display coordinates (Pass 1 uses
        // image.center.x directly, no per-image displayScaleX
        // conversion). [exportDensity] is a UNIFORM factor
        // (= max intrinsicWidth/displaySize.width across all
        // images) that scales the entire canvas up from display
        // coords to pixel coords, like a Retina/HiDPI factor.
        //
        // [dstW/dstH] = displaySize × image.scale × exportDensity.
        // For the image with the highest intrinsic/display ratio,
        // dstW = intrinsicWidth × image.scale — full original
        // resolution. For other images, dstW ≥ intrinsicWidth
        // (the source is at intrinsic resolution, so any extra
        // upscale beyond intrinsicWidth just bilinearly expands
        // without adding detail, but doesn't lose any either).
        val canvasCenterX = (image.center.x - minX) * exportDensity * scale
        val canvasCenterY = (image.center.y - minY) * exportDensity * scale
        val dstW = image.displaySize.width * image.scale * exportDensity * scale
        val dstH = image.displaySize.height * image.scale * exportDensity * scale
        // Apply non-destructive crop: only draw the cropped
        // region of the source bitmap, not the full image.
        val crop = image.cropRect
        val srcRect = if (crop != null) {
            val cx = (crop.left * source.width).toInt().coerceIn(0, source.width - 1)
            val cy = (crop.top * source.height).toInt().coerceIn(0, source.height - 1)
            val cw = ((crop.right - crop.left) * source.width).toInt().coerceIn(1, source.width - cx)
            val ch = ((crop.bottom - crop.top) * source.height).toInt().coerceIn(1, source.height - cy)
            android.graphics.Rect(cx, cy, cx + cw, cy + ch)
        } else {
            android.graphics.Rect(0, 0, source.width, source.height)
        }
        val dstRect = android.graphics.RectF(
            -dstW / 2f, -dstH / 2f, dstW / 2f, dstH / 2f,
        )

        canvas.save()
        try {
            canvas.translate(canvasCenterX, canvasCenterY)
            canvas.rotate(image.rotationDegrees)
            canvas.scale(
                if (image.flipX) -1f else 1f,
                if (image.flipY) -1f else 1f,
            )
            canvas.drawBitmap(source, srcRect, dstRect, paint)
        } finally {
            canvas.restore()
        }
    }
}
