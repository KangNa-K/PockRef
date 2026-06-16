package com.pockref.app.ui.logs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pockref.app.R
import com.pockref.app.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * V_NOW (2026-06-16, "启动页导出运行日志" feature):
 * The bottom-right FAB on the start page. Mounted only when
 * [com.pockref.app.model.PockRefUiState.images] is empty (the
 * caller filters).
 *
 * Visual: 48dp chrome (Dimens.BottomPanelChromeSize), semitransparent
 * surface, chrome corner radius, FabElevation shadow, Material
 * `Description` icon, permanent `labelSmall` label below
 * ("导出运行日志").
 *
 * Lifecycle (state machine on local `remember`):
 *   Idle ──(click)──▶ Loading ──(VM returns text)──▶ Idle
 *                            │
 *                            └──(VM throws)──▶ Error(msg) ──(1.5s)──▶ Idle
 *
 * The composable owns the [rememberLauncherForActivityResult] for
 * `ACTION_CREATE_DOCUMENT("text/plain")`; the VM hands back the
 * assembled text via [onStartLogExport]'s `onReady` closure,
 * never via StateFlow.
 *
 * @param exportInProgress A hot StateFlow that mirrors the VM's
 *                         `logExportInProgress` — true while a
 *                         build coroutine is in flight. Owned by
 *                         the caller so we don't depend on a VM
 *                         ref here (PockRefApp doesn't take one).
 * @param onStartLogExport Click → invoke this with the same
 *                         3-tuple signature as
 *                         [com.pockref.app.ui.PockRefViewModel.startLogExport].
 */
@Composable
fun LogExportFab(
    exportInProgress: StateFlow<Boolean>,
    onStartLogExport: (
        appLabel: String,
        onReady: (suggestedName: String, content: String) -> Unit,
        onError: (reason: String) -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appLabel = remember(context) {
        try {
            context.applicationInfo.loadLabel(context.packageManager).toString()
                .ifBlank { "PockRef" }
        } catch (_: Throwable) {
            "PockRef"
        }
    }

    // Captured text waiting for SAF callback to write.
    var pendingContent by remember { mutableStateOf<String?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<LogExportState>(LogExportState.Idle) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val name = pendingName
        val content = pendingContent
        pendingContent = null
        pendingName = null
        if (uri == null) {
            // User cancelled. No toast, no state change.
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write((content ?: "").toByteArray(Charsets.UTF_8))
            } ?: error("openOutputStream returned null")
        }.fold(
            onSuccess = {
                Toast.makeText(
                    context,
                    context.getString(R.string.log_export_toast_success, name ?: ""),
                    Toast.LENGTH_LONG,
                ).show()
            },
            onFailure = { t ->
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.log_export_toast_write_failed,
                        t.message ?: t::class.java.simpleName,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }

    // VM in-progress → disable button + show progress.
    val inProgress by exportInProgress.collectAsState()
    val clickEnabled = !inProgress && state !is LogExportState.Loading

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.BottomPanelChromeSize)
                .shadow(Dimens.FabElevation, RoundedCornerShape(Dimens.ChromeCornerRadius))
                .clip(RoundedCornerShape(Dimens.ChromeCornerRadius))
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = Dimens.ChromeSurfaceAlpha),
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(Dimens.ChromeCornerRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = {
                    if (!clickEnabled) return@IconButton
                    state = LogExportState.Loading
                    onStartLogExport(
                        appLabel,
                        { name, content ->
                            pendingName = name
                            pendingContent = content
                            state = LogExportState.Idle
                            launcher.launch(name)
                        },
                        { reason ->
                            state = LogExportState.Error(reason)
                        },
                    )
                },
                enabled = clickEnabled,
            ) {
                if (state is LogExportState.Loading || inProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = stringResource(R.string.log_export_button_label),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.log_export_button_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Error → show toast + auto-reset to Idle after 1.5s.
    LaunchedEffect(state) {
        if (state is LogExportState.Error) {
            val msg = (state as LogExportState.Error).message
            Toast.makeText(
                context,
                context.getString(R.string.log_export_toast_build_failed, msg),
                Toast.LENGTH_LONG,
            ).show()
            delay(1500L)
            state = LogExportState.Idle
        }
    }
}

