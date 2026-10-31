package com.pockref.app.update

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject

data class AppUpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val manifest: AppUpdateManifest? = null,
    val readyApk: File? = null,
    val awaitingPermission: Boolean = false,
    val message: String? = null,
    val noticeVisible: Boolean = false,
    val preparingInstall: Boolean = false,
)

internal interface UpdateBackend {
    suspend fun check(): UpdateCheckResult
    suspend fun cached(manifest: AppUpdateManifest): File?
    suspend fun download(manifest: AppUpdateManifest, progress: (Int) -> Unit): File
    fun newerThanInstalled(code: Int): Boolean
    fun canInstall(): Boolean
    fun openPermission()
    fun install(apk: File): UpdateInstallResult
}

private class AndroidUpdateBackend(private val context: Context) : UpdateBackend {
    override suspend fun check() = GithubAppUpdater.checkForUpdate(context)
    override suspend fun cached(manifest: AppUpdateManifest) = GithubAppUpdater.cachedApk(context, manifest)
    override suspend fun download(manifest: AppUpdateManifest, progress: (Int) -> Unit) =
        GithubAppUpdater.download(context, manifest, progress)
    override fun newerThanInstalled(code: Int) = GithubAppUpdater.isInstalledVersionOlder(context, code)
    override fun canInstall() = GithubAppUpdater.canInstall(context)
    override fun openPermission() = GithubAppUpdater.openInstallPermission(context)
    override fun install(apk: File) = GithubAppUpdater.install(context, apk)
}

class AppUpdateController internal constructor(
    private val backend: UpdateBackend,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val prepareInstall: suspend () -> Unit,
) {
    constructor(context: Context, scope: CoroutineScope, prepareInstall: suspend () -> Unit) : this(
        AndroidUpdateBackend(context.applicationContext),
        context.applicationContext.getSharedPreferences("pockref_updates", Context.MODE_PRIVATE),
        scope, prepareInstall,
    )
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state = _state.asStateFlow()
    private var job: kotlinx.coroutines.Job? = null
    private var foreground = false

    init {
        val saved = prefs.getString("manifest", null)
        if (saved != null) {
            _state.update { it.copy(checking = true) }
            job = scope.launch {
                try {
                    val manifest = GithubAppUpdater.parseManifest(saved)
                    if (backend.newerThanInstalled(manifest.versionCode)) {
                        val apk = backend.cached(manifest)
                        _state.update {
                            it.copy(manifest = manifest, readyApk = apk,
                                noticeVisible = shouldNotify(manifest))
                        }
                    } else {
                        prefs.edit { remove("manifest") }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    prefs.edit { remove("manifest") }
                } finally {
                    _state.update { it.copy(checking = false) }
                }
                if (foreground) check(false)
            }
        }
    }

    private fun shouldNotify(manifest: AppUpdateManifest) = UpdateReminderPolicy.shouldNotify(
        manifest.versionCode, prefs.getInt("ignored_code", -1),
        prefs.getInt("snoozed_code", -1), prefs.getLong("snoozed_until", 0),
        System.currentTimeMillis(),
    )

    fun check(manual: Boolean = true) {
        if (_state.value.checking || _state.value.downloading || _state.value.preparingInstall) return
        val now = System.currentTimeMillis()
        if (!manual && !UpdateReminderPolicy.shouldCheck(now, prefs.getLong("last_check", 0))) {
            _state.update { it.copy(noticeVisible = it.manifest?.let(::shouldNotify) == true) }
            return
        }
        prefs.edit { putLong("last_check", now) }
        _state.update { it.copy(checking = true, message = null) }
        job = scope.launch {
            try {
                when (val result = backend.check()) {
                    UpdateCheckResult.UpToDate -> {
                        prefs.edit { remove("manifest") }
                        _state.value = AppUpdateUiState(message = if (manual) "已是最新版本" else null)
                    }
                    is UpdateCheckResult.Available -> {
                        val manifest = result.manifest
                        val json = JSONObject().put("versionCode", manifest.versionCode)
                            .put("versionName", manifest.versionName).put("apkUrl", manifest.apkUrl)
                            .put("sha256", manifest.sha256).put("size", manifest.size)
                            .put("notes", manifest.notes).put("releaseUrl", manifest.releaseUrl)
                        prefs.edit { putString("manifest", json.toString()) }
                        val apk = backend.cached(result.manifest)
                        _state.value = _state.value.copy(
                            checking = false,
                            manifest = result.manifest,
                            readyApk = apk,
                            noticeVisible = shouldNotify(result.manifest),
                        )
                    }
                }
                prefs.edit { putLong("last_check", System.currentTimeMillis()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        checking = false,
                        message = if (manual) "检查失败：${error.localizedMessage ?: "网络不可用"}" else null,
                    )
                }
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    fun onForeground() {
        foreground = true
        if (_state.value.awaitingPermission) {
            _state.update { it.copy(awaitingPermission = false) }
            if (backend.canInstall()) {
                installReady()
                return
            }
        }
        check(false)
    }

    fun onBackground() { foreground = false }

    fun downloadAndInstall() {
        val manifest = _state.value.manifest ?: return
        if (_state.value.downloading || _state.value.checking || _state.value.preparingInstall) return
        _state.update { it.copy(downloading = true, progress = 0, message = "正在下载 APK…") }
        job = scope.launch {
            try {
                val apk = backend.download(manifest) { value ->
                    _state.update { it.copy(progress = value) }
                }
                _state.update { it.copy(downloading = false, readyApk = apk, message = "下载完成，等待安装") }
                if (foreground) installReady()
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(downloading = false, message = "下载已取消") }
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(downloading = false, message = "更新失败：${error.localizedMessage ?: "请重试"}")
                }
            }
        }
    }

    fun cancelDownload() {
        if (_state.value.downloading) job?.cancel()
    }

    fun installReady() {
        if (_state.value.checking || _state.value.downloading || _state.value.preparingInstall) return
        val manifest = _state.value.manifest ?: return
        if (!foreground) {
            _state.update { it.copy(message = "请返回主应用继续安装") }
            return
        }
        _state.update { it.copy(preparingInstall = true, message = "正在准备安装…") }
        job = scope.launch {
            try {
                val apk = backend.cached(manifest)
                if (apk == null) {
                    _state.update { it.copy(readyApk = null) }
                    error("安装包已失效，请重新下载")
                }
                if (!foreground) return@launch
                if (!backend.canInstall()) {
                    _state.update { it.copy(awaitingPermission = true, message = "授权后返回继续安装") }
                    backend.openPermission()
                    return@launch
                }
                prepareInstall()
                if (!foreground) {
                    _state.update { it.copy(message = "下载完成，等待安装") }
                    return@launch
                }
                when (backend.install(apk)) {
                    UpdateInstallResult.Started ->
                        _state.update { it.copy(awaitingPermission = false, message = "请在系统界面确认安装") }
                    UpdateInstallResult.PermissionRequired -> error("请允许安装应用后重试")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(message = "安装未开始：${error.localizedMessage}") }
            } finally {
                _state.update { it.copy(preparingInstall = false) }
            }
        }
    }

    fun ignoreCurrentVersion() {
        _state.value.manifest?.let { prefs.edit { putInt("ignored_code", it.versionCode) } }
        _state.update { it.copy(noticeVisible = false) }
    }

    fun later() {
        _state.value.manifest?.let {
            prefs.edit {
                putInt("snoozed_code", it.versionCode)
                putLong("snoozed_until", System.currentTimeMillis() + UpdateReminderPolicy.SNOOZE_MS)
            }
        }
        _state.update { it.copy(noticeVisible = false) }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}

internal object UpdateReminderPolicy {
    const val SNOOZE_MS = 24L * 60 * 60 * 1000
    fun shouldCheck(now: Long, last: Long) =
        last == 0L || now < last || now - last >= AppUpdateController.CHECK_INTERVAL_MS
    fun shouldNotify(code: Int, ignored: Int, snoozed: Int, until: Long, now: Long) =
        code != ignored && (code != snoozed || now >= until)
}
