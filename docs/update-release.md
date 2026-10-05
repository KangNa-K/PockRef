# GitHub APK updates

The Android client reads `update/version.json` from the `main` branch. A
Release asset named `pockref-X.Y.Z.apk` contains the signed APK. Publish the
asset before pointing the manifest to it.

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`, update
   `CHANGELOG_X.Y.Z.md`, and build a signed APK with
   `./gradlew :app:assembleRelease`.
2. Verify that `apksigner verify --print-certs` reports the same signing
   certificate as the installed release. Never upload the keystore or
   `keystore.properties`.
3. Commit and push the implementation to `main`, then publish the exact
   built APK as a GitHub Release asset:
   `gh release create release-X.Y.Z app/build/outputs/apk/release/app-release.apk#pockref-X.Y.Z.apk --repo KangNa-K/PockRef --target main --title "PockRef X.Y.Z" --notes-file CHANGELOG_X.Y.Z.md`
4. Download that public URL without authentication, compare `shasum -a 256`
   and byte size against the local APK, and test the URL from a target device.
5. **Last**, update `update/version.json` on `main` with `versionCode`,
   `versionName`, `apkUrl`, `sha256`, `size`, `notes`, and `releaseUrl`.
   Never publish an unverified APK URL or a hash placeholder.

The first update-capable version still needs to be installed by existing
users through the usual side-loading route. Future updates can use the
in-app button in Settings > General.
