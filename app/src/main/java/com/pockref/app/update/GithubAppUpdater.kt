package com.pockref.app.update

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/**
 * GitHub single-source updater.
 *
 * Keep this URL stable after the repository is created. The JSON should live
 * on the default branch; the APK itself should be an immutable Release asset.
 * Use the public asset API URL: the browser download path may lag publication
 * or fail in networks where the asset API still downloads successfully.
 */
object GithubUpdateConfig {
    const val manifestUrl =
        "https://raw.githubusercontent.com/KangNa-K/PockRef-updates/main/update/version.json"
}

data class AppUpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val notes: String,
    val releaseUrl: String?,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val manifest: AppUpdateManifest) : UpdateCheckResult
}

sealed interface UpdateInstallResult {
    data object Started : UpdateInstallResult
    data object PermissionRequired : UpdateInstallResult
}

object GithubAppUpdater {
    suspend fun checkForUpdate(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val manifest = parseManifest(readUrl(GithubUpdateConfig.manifestUrl))
        require(manifest.versionCode > 0 && manifest.versionName.isNotBlank()) {
            "Invalid update version"
        }
        val current = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            current.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            current.versionCode.toLong()
        }
        if (isNewer(manifest.versionCode, currentCode)) {
            validateDownload(manifest)
            UpdateCheckResult.Available(manifest)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    suspend fun download(
        context: Context,
        manifest: AppUpdateManifest,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        validateDownload(manifest)
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updatesDir, "pockref-${manifest.versionCode}.apk")
        if (!verifyFile(apk, manifest)) {
            downloadFile(manifest, apk, onProgress)
        }
        validatePackage(context, apk, manifest)
        apk
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun install(context: Context, apk: File): UpdateInstallResult {
        if (!canInstall(context)) return UpdateInstallResult.PermissionRequired
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .apply { clipData = ClipData.newRawUri("PockRef update", uri) }
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return UpdateInstallResult.Started
    }

    internal fun validateDownload(manifest: AppUpdateManifest) {
        require(manifest.versionCode > 0 && manifest.size in 1..MAX_APK_SIZE) {
            "Invalid APK size or version"
        }
        require(
            manifest.apkUrl.startsWith(
                "https://api.github.com/repos/KangNa-K/PockRef-updates/releases/assets/",
            ),
        ) { "APK must be a PockRef GitHub Release asset" }
        require(manifest.sha256.matches(Regex("[A-Fa-f0-9]{64}"))) {
            "Invalid APK SHA-256"
        }
    }

    internal fun isNewer(latestCode: Int, currentCode: Long): Boolean =
        latestCode.toLong() > currentCode

    private const val MAX_APK_SIZE = 250L * 1024 * 1024

    private fun readUrl(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return connection.run {
            try {
                if (responseCode !in 200..299) error("HTTP $responseCode")
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                disconnect()
            }
        }
    }

    internal fun parseManifest(json: String): AppUpdateManifest {
        val objectJson = JSONObject(json)
        return AppUpdateManifest(
            versionCode = objectJson.getInt("versionCode"),
            versionName = objectJson.getString("versionName"),
            apkUrl = objectJson.optString("apkUrl"),
            sha256 = objectJson.optString("sha256"),
            size = objectJson.optLong("size", 0L),
            notes = objectJson.optString("notes"),
            releaseUrl = objectJson.optString("releaseUrl").ifBlank { null },
        )
    }

    internal fun verifyFile(file: File, manifest: AppUpdateManifest): Boolean {
        if (!file.isFile || file.length() != manifest.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
            .equals(manifest.sha256, ignoreCase = true)
    }

    private suspend fun downloadFile(
        manifest: AppUpdateManifest,
        destination: File,
        onProgress: (Int) -> Unit,
    ) {
        val connection = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val temporary = File(destination.parentFile, "${destination.name}.part")
        try {
            if (connection.responseCode !in 200..299) error("APK HTTP ${connection.responseCode}")
            require(connection.url.protocol == "https") { "APK redirect must use HTTPS" }
            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (downloaded > manifest.size) error("APK exceeds expected size")
                        onProgress(((downloaded * 100) / manifest.size).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (downloaded != manifest.size) error("APK size mismatch")
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualSha.equals(manifest.sha256, ignoreCase = true)) {
                error("APK SHA-256 mismatch")
            }
            check(temporary.renameTo(destination)) { "Unable to finalize APK" }
            onProgress(100)
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun validatePackage(context: Context, apk: File, manifest: AppUpdateManifest) {
        @Suppress("DEPRECATION")
        val signingFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val manager = context.packageManager
        val info: PackageInfo = manager.getPackageArchiveInfo(apk.absolutePath, signingFlag)
            ?: error("Downloaded file is not an APK")
        require(info.packageName == context.packageName) { "APK package mismatch" }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        require(code == manifest.versionCode.toLong()) { "APK version mismatch" }
        @Suppress("DEPRECATION")
        val installed = manager.getPackageInfo(context.packageName, signingFlag)
        @Suppress("DEPRECATION")
        val installedSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }
        } else {
            installed.signatures?.map { it.toCharsString() }
        }
        @Suppress("DEPRECATION")
        val apkSigners = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.map { it.toCharsString() }
        } else {
            info.signatures?.map { it.toCharsString() }
        }
        require(!installedSigners.isNullOrEmpty() && installedSigners == apkSigners) {
            "APK signing certificate mismatch"
        }
    }
}
