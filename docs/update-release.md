# GitHub APK updates

The Android client reads `update/version.json` from the `main` branch. A
Release asset named `pockref-X.Y.Z.apk` contains the signed APK. Publish the
asset before pointing the manifest to it. Do not replace an asset after
publishing its ID in the manifest: GitHub assigns a new ID on reupload.

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`, update
   `CHANGELOG_X.Y.Z.md`, and build a signed APK with
   `./gradlew :app:assembleRelease`.
2. Verify that `apksigner verify --print-certs` reports the same signing
   certificate as the installed release. Never upload the keystore or
   `keystore.properties`.
3. Commit and push the implementation to `main`, copy the exact built APK to
   `pockref-X.Y.Z.apk`, then publish it with
   `gh release create release-X.Y.Z /path/to/pockref-X.Y.Z.apk --repo KangNa-K/PockRef --target main --title "PockRef X.Y.Z" --notes-file CHANGELOG_X.Y.Z.md`.
   The `#label` suffix accepted by `gh release create` does **not** rename the
   file; use a correctly named copy.
4. Obtain the asset ID from `gh release view release-X.Y.Z --json assets`.
   Download `https://api.github.com/repos/KangNa-K/PockRef/releases/assets/ID`
   anonymously with `Accept: application/octet-stream`; compare `shasum -a
   256` and byte size against the local APK, including from a target device.
   For the first release, the normal browser URL initially returned 404,
   then became available after propagation. A later tablet download from
   that URL disconnected mid-transfer. The public asset API URL downloaded
   and verified on the tablet; keep testing both paths for later releases.
5. **Last**, update `update/version.json` on `main` with `versionCode`,
   `versionName`, the tested asset API `apkUrl`, `sha256`, `size`, `notes`,
   and `releaseUrl`.
   Never publish an unverified APK URL or a hash placeholder.

The first update-capable version still needs to be installed by existing
users through the usual side-loading route. Future updates can use the
in-app button in Settings > General.
