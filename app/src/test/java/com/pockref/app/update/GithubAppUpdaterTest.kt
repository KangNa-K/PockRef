package com.pockref.app.update

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubAppUpdaterTest {
    private fun manifest(sha: String, size: Long) = AppUpdateManifest(
        versionCode = 32,
        versionName = "2.2.6",
        apkUrl = "https://api.github.com/repos/KangNa-K/PockRef-updates/releases/assets/611312842",
        sha256 = sha,
        size = size,
        notes = "",
        releaseUrl = null,
    )

    @Test fun comparesVersionCodeNotVersionName() {
        assertTrue(GithubAppUpdater.isNewer(32, 31))
        assertFalse(GithubAppUpdater.isNewer(31, 31))
        assertFalse(GithubAppUpdater.isNewer(30, 31))
    }

    @Test fun currentVersionManifestDoesNotRequireApkFields() {
        val current = GithubAppUpdater.parseManifest(
            """{"versionCode":31,"versionName":"2.2.5","notes":"当前版本"}""",
        )
        assertFalse(GithubAppUpdater.isNewer(current.versionCode, 31))
        assertTrue(current.apkUrl.isEmpty())
    }

    @Test fun validatesDownloadSourceDigestAndSize() {
        val valid = manifest("ab".repeat(32), 100)
        GithubAppUpdater.validateDownload(valid)
        listOf(
            valid.copy(size = 0),
            valid.copy(size = 251L * 1024 * 1024),
            valid.copy(sha256 = "invalid"),
            valid.copy(apkUrl = "https://example.org/update.apk"),
            valid.copy(apkUrl = valid.apkUrl + "/../../../../../../other"),
            valid.copy(apkUrl = valid.apkUrl + "?redirect=https://example.org"),
            valid.copy(apkUrl = valid.apkUrl.replace("611312842", "not-an-asset")),
        ).forEach { invalid ->
            assertTrue(runCatching { GithubAppUpdater.validateDownload(invalid) }.isFailure)
        }
    }

    @Test fun rejectsTruncatedOrTamperedApk() {
        val file = File.createTempFile("pockref-update", ".apk")
        try {
            val bytes = "signed apk bytes".toByteArray()
            file.writeBytes(bytes)
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val valid = manifest(sha, bytes.size.toLong())
            assertTrue(GithubAppUpdater.verifyFile(file, valid))
            assertFalse(GithubAppUpdater.verifyFile(file, valid.copy(size = bytes.size.toLong() + 1)))
            assertFalse(GithubAppUpdater.verifyFile(file, valid.copy(sha256 = "00".repeat(32))))
        } finally {
            file.delete()
        }
    }

    @Test fun cacheWithInvalidPackageIsDiscardedForRetry() {
        val file = File.createTempFile("pockref-update", ".apk")
        try {
            val bytes = "apk bytes".toByteArray()
            file.writeBytes(bytes)
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val valid = manifest(sha, bytes.size.toLong())
            assertTrue(GithubAppUpdater.validatedCachedApk(file, valid) {} == file)
            assertTrue(GithubAppUpdater.validatedCachedApk(file, valid) { error("signature mismatch") } == null)
            assertFalse(file.exists())
        } finally {
            file.delete()
        }
    }
}
