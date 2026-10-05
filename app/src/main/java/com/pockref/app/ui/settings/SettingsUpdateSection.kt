package com.pockref.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pockref.app.update.AppUpdateManifest
import com.pockref.app.update.GithubAppUpdater
import com.pockref.app.update.UpdateCheckResult
import com.pockref.app.update.UpdateInstallResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsUpdateSection() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var manifest by remember { mutableStateOf<AppUpdateManifest?>(null) }
    var readyApk by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var awaitingPermission by remember { mutableStateOf(false) }

    // Unknown-source permission is granted on a system screen; resume the
    // pending install when the user returns, without downloading again.
    DisposableEffect(lifecycleOwner, readyApk, awaitingPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingPermission &&
                GithubAppUpdater.canInstall(context)
            ) {
                awaitingPermission = false
                readyApk?.let { apk ->
                    runCatching { GithubAppUpdater.install(context, apk) }
                        .onFailure { message = "无法打开系统安装界面：${it.localizedMessage}" }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("应用更新", style = MaterialTheme.typography.titleSmall)
        val version = remember(context) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知"
        }
        Text("当前版本 $version", style = MaterialTheme.typography.bodySmall)
        if (manifest != null) {
            Text("新版本 ${manifest!!.versionName}", style = MaterialTheme.typography.bodyMedium)
            if (manifest!!.notes.isNotBlank()) {
                Text(manifest!!.notes, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
        if (busy && manifest != null) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FilledTonalButton(
            enabled = !busy,
            onClick = {
                val available = manifest
                if (available == null) {
                    scope.launch {
                        busy = true
                        message = "正在检查更新…"
                        try {
                            when (val result = GithubAppUpdater.checkForUpdate(context)) {
                                UpdateCheckResult.UpToDate -> message = "已是最新版本"
                                is UpdateCheckResult.Available -> {
                                    manifest = result.manifest
                                    message = ""
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            message = "检查失败：${error.localizedMessage ?: "网络不可用"}"
                        } finally {
                            busy = false
                        }
                    }
                } else {
                    scope.launch {
                        busy = true
                        progress = 0
                        message = "正在下载 APK…"
                        try {
                            val apk = GithubAppUpdater.download(context, available) { value ->
                                progress = value
                            }
                            readyApk = apk
                            when (GithubAppUpdater.install(context, apk)) {
                                UpdateInstallResult.Started -> message = "请在系统界面确认安装"
                                UpdateInstallResult.PermissionRequired -> {
                                    awaitingPermission = true
                                    message = "请允许 PockRef 安装应用，然后返回继续"
                                    GithubAppUpdater.openInstallPermission(context)
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            message = "更新失败：${error.localizedMessage ?: "请重试"}"
                        } finally {
                            busy = false
                        }
                    }
                }
            },
        ) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (manifest == null) "检查更新" else "下载并安装")
        }
        if (awaitingPermission && readyApk != null) {
            FilledTonalButton(onClick = {
                if (GithubAppUpdater.canInstall(context)) {
                    runCatching { GithubAppUpdater.install(context, readyApk!!) }
                        .onSuccess { awaitingPermission = false }
                        .onFailure { message = "无法打开系统安装界面：${it.localizedMessage}" }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    GithubAppUpdater.openInstallPermission(context)
                }
            }) {
                Text("继续安装")
            }
        }
        if (manifest != null && !busy) {
            OutlinedButton(onClick = {
                scope.launch {
                    busy = true
                    try {
                        when (val result = GithubAppUpdater.checkForUpdate(context)) {
                            UpdateCheckResult.UpToDate -> {
                                manifest = null
                                readyApk = null
                                message = "已是最新版本"
                            }
                            is UpdateCheckResult.Available -> {
                                manifest = result.manifest
                                readyApk = null
                                message = ""
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        message = "检查失败：${error.localizedMessage ?: "网络不可用"}"
                    } finally {
                        busy = false
                    }
                }
            }) {
                Text("重新检查")
            }
        }
    }
}
