package com.pockref.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.pockref.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

/**
 * GitHub single-source updater.
 *
 * Keep this URL stable after the repository is created. The JSON should live
 * on the default branch; the APK itself should be an immutable Release asset.
 */
object GithubUpdateConfig {
    const val manifestUrl =
        "https://raw.githubusercontent.com/OWNER/REPOSITORY/main/update/version.json"
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
    suspend fun checkForUpdate(): UpdateCheckResult {
        val json = readUrl(GithubUpdateConfig.manifestUrl)
        val manifest = parseManifest(json)
        require(manifest.versionCode > 0) { "versionCode must be positive" }
        require(manifest.apkUrl.startsWith("https://")) { "apkUrl must use HTTPS" }
        require(manifest.sha256.matches(Regex("[A-Fa-f0-9]{64}"))) {
            "sha256 must be a SHA-256 hex digest"
        }
        return if (manifest.versionCode > BuildConfig.VERSION_CODE) {
            UpdateCheckResult.Available(manifest)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    fun downloadAndInstall(
        context: Context,
        manifest: AppUpdateManifest,
        onProgress: (Int) -> Unit,
    ): UpdateInstallResult {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(updatesDir, "pockref-${manifest.versionCode}.apk")
        download(manifest, apk, onProgress)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return UpdateInstallResult.PermissionRequired
        }

        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return UpdateInstallResult.Started
    }

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

    private fun parseManifest(json: String): AppUpdateManifest {
        val objectJson = JSONObject(json)
        return AppUpdateManifest(
            versionCode = objectJson.getInt("versionCode"),
            versionName = objectJson.getString("versionName"),
            apkUrl = objectJson.getString("apkUrl"),
            sha256 = objectJson.getString("sha256"),
            size = objectJson.optLong("size", 0L),
            notes = objectJson.optString("notes"),
            releaseUrl = objectJson.optString("releaseUrl").ifBlank { null },
        )
    }

    private fun download(
        manifest: AppUpdateManifest,
        destination: File,
        onProgress: (Int) -> Unit,
    ) {
        val connection = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        val temporary = File(destination.parentFile, "${destination.name}.part")
        try {
            if (connection.responseCode !in 200..299) error("APK HTTP ${connection.responseCode}")
            val expectedLength = if (manifest.size > 0) manifest.size else connection.contentLengthLong
            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (expectedLength > 0) {
                            onProgress(((downloaded * 100) / expectedLength).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (manifest.size > 0 && downloaded != manifest.size) {
                error("APK size mismatch")
            }
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
}
