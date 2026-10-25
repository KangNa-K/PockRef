package com.pockref.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.pockref.app.data.SceneImageDecoder
import com.pockref.app.model.PockRefUiState
import com.pockref.app.model.SceneImage
import com.pockref.app.model.isImageLocked
import com.pockref.app.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scene layer navigator sharing the settings popup footprint. */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.CanvasLayersChrome(
    state: PockRefUiState,
    onAction: (PockRefAction) -> Unit,
    imageDecoder: SceneImageDecoder?,
    panelWidth: Dp = 360.dp,
    topInset: Dp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() } + Dimens.CornerInset,
    bottomInset: Dp = Dimens.CornerInset,
    expandedOverride: Boolean? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    showLauncher: Boolean = true,
) {
    if (state.images.isEmpty()) return

    var localExpanded by remember { mutableStateOf(false) }
    val expanded = expandedOverride ?: localExpanded
    val setExpanded: (Boolean) -> Unit = {
        localExpanded = it
        onExpandedChange(it)
    }
    val orderedImages = remember(state.images) {
        state.images.sortedByDescending(SceneImage::zIndex)
    }
    LaunchedEffect(state.sceneName) { setExpanded(false) }
    BackHandler(enabled = expanded) { setExpanded(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CanvasSidePopup(
            visible = expanded,
            panelWidth = panelWidth,
            topInset = topInset,
            bottomInset = bottomInset,
            title = "图层窗口",
        ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "图层",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                        Text(
                            text = "${state.images.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        itemsIndexed(
                            items = orderedImages,
                            key = { _, image -> image.id },
                        ) { index, image ->
                            CanvasLayerRow(
                                image = image,
                                order = index + 1,
                                groupName = state.groups.firstOrNull { image.id in it.imageIds }?.name,
                                selected = image.id in state.selectedImageIds,
                                locked = state.isImageLocked(image.id),
                                imageDecoder = imageDecoder,
                                onClick = { onAction(PockRefAction.SelectImage(image.id)) },
                                onDoubleClick = {
                                    onAction(PockRefAction.SelectImage(image.id))
                                    // List navigation always focuses; it must not toggle
                                    // an already-focused image back to the previous view.
                                    if (state.fittedImageId != image.id) {
                                        onAction(PockRefAction.FitImageToScreen(image.id))
                                    }
                                },
                            )
                        }
                    }
                }
        }

        AnimatedVisibility(
            visible = showLauncher,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.CornerInset, bottom = bottomInset),
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(90)),
        ) {
            GlassSurface(
                modifier = Modifier
                    .size(Dimens.BottomPanelChromeSize)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                    )
                    .semantics { contentDescription = "打开图层窗口" },
                onClick = { setExpanded(!expanded) },
                shape = RoundedCornerShape(Dimens.ChromeCornerRadius),
                color = MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
                contentColor = MaterialTheme.colorScheme.primary,
                tonalElevation = Dimens.FabElevation,
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CanvasLayerRow(
    image: SceneImage,
    order: Int,
    groupName: String?,
    selected: Boolean,
    locked: Boolean,
    imageDecoder: SceneImageDecoder?,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(
        initialValue = null,
        image.id,
        image.imageBytes.size,
        image.imageByteOffset,
        image.imageByteLength,
        imageDecoder,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { imageDecoder?.decodeThumbnail(image)?.bitmap }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(Dimens.ChipCornerRadius)
    val rowBackground = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(rowBackground)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onDoubleClick = onDoubleClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "图层 $order：${image.name}"
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = image.name.ifBlank { "未命名图层 $order" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = groupName?.let { "组：$it · 层级 $order" } ?: "层级 $order",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (locked) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "已锁定",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
