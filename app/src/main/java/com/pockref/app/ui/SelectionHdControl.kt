package com.pockref.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class OriginalResolutionSelectionState {
    OFF,
    MIXED,
    ON,
}

internal fun originalResolutionStateFor(values: List<Boolean>): OriginalResolutionSelectionState = when {
    values.isEmpty() || values.none { it } -> OriginalResolutionSelectionState.OFF
    values.all { it } -> OriginalResolutionSelectionState.ON
    else -> OriginalResolutionSelectionState.MIXED
}

internal fun targetOriginalResolutionEnabled(values: List<Boolean>): Boolean =
    values.isNotEmpty() && !values.all { it }

internal fun originalResolutionControlPosition(
    frame: Rect,
    controlWidthPx: Float = CONTROL_WIDTH_PX,
    controlHeightPx: Float = CONTROL_HEIGHT_PX,
    insetPx: Float = CONTROL_INSET_PX,
): Offset {
    val x = if (frame.width >= controlWidthPx + insetPx * 2f) {
        frame.right - insetPx - controlWidthPx / 2f
    } else {
        frame.center.x
    }
    val y = if (frame.height >= controlHeightPx + insetPx * 2f) {
        frame.bottom - insetPx - controlHeightPx / 2f
    } else {
        frame.center.y
    }
    return Offset(x, y)
}

@Composable
internal fun SelectionHdControl(
    state: OriginalResolutionSelectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state != OriginalResolutionSelectionState.OFF
    val backgroundColor = if (selected) {
        Color(0xFF1976D2)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    }
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val stateLabel = when (state) {
        OriginalResolutionSelectionState.OFF -> "关闭"
        OriginalResolutionSelectionState.MIXED -> "部分开启"
        OriginalResolutionSelectionState.ON -> "开启"
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width = 52.dp, height = 44.dp)
            .semantics {
                contentDescription = "高清显示"
                stateDescription = stateLabel
            },
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "HD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state == OriginalResolutionSelectionState.MIXED) {
                Text(
                    text = "-",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val CONTROL_WIDTH_PX = 52f
private const val CONTROL_HEIGHT_PX = 44f
private const val CONTROL_INSET_PX = 8f
