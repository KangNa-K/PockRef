package com.pockref.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GithubUpdateRepositoryTest {
    private fun manifest(url: String) = AppUpdateManifest(
        versionCode = 49,
        versionName = "2.5.3",
        apkUrl = url,
        sha256 = "a".repeat(64),
        size = 1024,
        notes = "",
        releaseUrl = null,
    )

    @Test fun checksCanonicalSourceRepository() {
        assertEquals(
            "https://raw.githubusercontent.com/KangNa-K/PockRef/main/update/version.json",
            GithubUpdateConfig.manifestUrl,
        )
        GithubAppUpdater.validateDownload(manifest(
            "https://api.github.com/repos/KangNa-K/PockRef/releases/assets/12345",
        ))
    }

    @Test fun rejectsLegacyRepositoryAndLookalikeDownloadUrls() {
        listOf(
            "https://api.github.com/repos/KangNa-K/PockRef-updates/releases/assets/12345",
            "https://api.github.com/repos/another-owner/PockRef/releases/assets/12345",
            "https://api.github.com.evil.example/repos/KangNa-K/PockRef/releases/assets/12345",
            "http://api.github.com/repos/KangNa-K/PockRef/releases/assets/12345",
            "https://api.github.com/repos/KangNa-K/PockRef/releases/assets/12345/extra",
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                GithubAppUpdater.validateDownload(manifest(url))
            }
        }
    }

    @Test fun retainsHashSizeAndVersionChecks() {
        val valid = manifest("https://api.github.com/repos/KangNa-K/PockRef/releases/assets/12345")
        listOf(valid.copy(sha256 = "bad"), valid.copy(size = 0), valid.copy(versionCode = 0))
            .forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) {
                    GithubAppUpdater.validateDownload(invalid)
                }
            }
    }
}
