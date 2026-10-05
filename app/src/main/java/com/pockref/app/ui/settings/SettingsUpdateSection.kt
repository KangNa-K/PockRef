package com.pockref.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockref.app.update.AppUpdateController

internal val LocalAppUpdateController = staticCompositionLocalOf<AppUpdateController?> { null }

@Composable
internal fun SettingsUpdateSection() {
    val controller = LocalAppUpdateController.current ?: return
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val busy = state.checking || state.downloading || state.preparingInstall
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("应用更新", style = MaterialTheme.typography.titleSmall)
        Text("当前版本 $version", style = MaterialTheme.typography.bodySmall)
        state.manifest?.let {
            Text("新版本 ${it.versionName} · ${"%.1f".format(it.size / 1048576.0)} MB")
            if (it.notes.isNotBlank()) Text(it.notes, style = MaterialTheme.typography.bodySmall)
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (state.downloading) {
            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("${state.progress}%")
            TextButton(onClick = controller::cancelDownload) { Text("取消下载") }
        }
        if (state.manifest != null) {
            FilledTonalButton(
                enabled = !busy,
                onClick = {
                    if (state.readyApk != null) controller.installReady() else controller.downloadAndInstall()
                },
            ) { Text(if (state.readyApk != null) "继续安装" else "下载并安装") }
        }
        TextButton(enabled = !busy, onClick = { controller.check() }) {
            Text(if (state.checking) "正在检查…" else "检查更新")
        }
    }
}
