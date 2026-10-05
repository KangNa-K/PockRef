package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockref.app.update.AppUpdateController

@Composable
internal fun AppUpdateNotice(controller: AppUpdateController, compact: Boolean, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    var details by remember { mutableStateOf(false) }
    val manifest = state.manifest ?: return
    if (state.noticeVisible) {
        if (compact) {
            FilledTonalIconButton(onClick = { details = true }, modifier = modifier.size(40.dp)) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = "发现新版本 ${manifest.versionName}")
            }
        } else {
            TextButton(onClick = { details = true }, modifier = modifier) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("发现新版本 ${manifest.versionName}")
            }
        }
    }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text("新版本 ${manifest.versionName}") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${"%.1f".format(manifest.size / 1048576.0)} MB")
                    Text(manifest.notes)
                    state.message?.let { Text(it) }
                    if (state.downloading) {
                        LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${state.progress}%")
                        TextButton(onClick = controller::cancelDownload) { Text("取消下载") }
                    }
                    TextButton(onClick = { controller.ignoreCurrentVersion(); details = false }) {
                        Text("忽略此版本")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.checking && !state.downloading && !state.preparingInstall,
                    onClick = {
                        if (state.readyApk != null) controller.installReady() else controller.downloadAndInstall()
                    },
                ) { Text(if (state.readyApk != null) "继续安装" else "立即更新") }
            },
            dismissButton = {
                TextButton(onClick = { controller.later(); details = false }) { Text("稍后提醒") }
            },
        )
    }
}
