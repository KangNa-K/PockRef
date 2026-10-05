# GitHub APK updates

The Android client reads `update/version.json` from the public
`KangNa-K/PockRef-updates` repository's `main` branch. A Release asset named
`pockref-X.Y.Z.apk` contains the signed APK. The source repository `PockRef`
is private; never push its Git history, source files, or signing keys to
`PockRef-updates`. Publish the asset before pointing the manifest to it. Do
not replace an asset after publishing its ID in the manifest: GitHub assigns
a new ID on reupload.

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`, update
   `CHANGELOG_X.Y.Z.md`, and build a signed APK with
   `./gradlew :app:assembleRelease`.
2. Verify that `apksigner verify --print-certs` reports the same signing
   certificate as the installed release. Never upload the keystore or
   `keystore.properties`.
3. Commit the implementation to the **private** source repository, copy the
   exact built APK to `pockref-X.Y.Z.apk`, then publish it with
   `gh release create release-X.Y.Z /path/to/pockref-X.Y.Z.apk --repo KangNa-K/PockRef-updates --target main --title "PockRef X.Y.Z" --notes-file CHANGELOG_X.Y.Z.md`.
   The `#label` suffix accepted by `gh release create` does **not** rename the
   file; use a correctly named copy.
4. Obtain the asset ID from `gh release view release-X.Y.Z --repo KangNa-K/PockRef-updates --json assets`.
   Download `https://api.github.com/repos/KangNa-K/PockRef-updates/releases/assets/ID`
   anonymously with `Accept: application/octet-stream`; compare `shasum -a
   256` and byte size against the local APK, including from a target device.
   For the first release, the normal browser URL initially returned 404,
   then became available after propagation. A later tablet download from
   that URL disconnected mid-transfer. The public asset API URL downloaded
   and verified on the tablet; keep testing both paths for later releases.
5. **Last**, update `update/version.json` on the public repository's `main`
   branch with `versionCode`,
   `versionName`, the tested asset API `apkUrl`, `sha256`, `size`, `notes`,
   and `releaseUrl`.
   Never publish an unverified APK URL or a hash placeholder.

Versions up to and including 2.2.6 still point to the now-private source
repository, so users must manually install the 2.2.7 migration APK. Future
updates can use the in-app button in Settings > General.

## Foreground update flow

The shared ViewModel controller checks when the main activity resumes, at most
once every six hours (including failed attempts). Settings checks bypass this
interval. Automatic failures are silent. Reminders can be snoozed for 24 hours
or ignored for one version; manual checks still expose ignored releases.

APK downloads require a user action. Closing Settings does not cancel them.
Verified completed APKs and release metadata can be reused after restarting;
partial downloads restart rather than resume byte ranges. Background download
completion never opens the installer. Floating-window users return to the
main activity and tap Continue Installation.

Before opening the installer, the app creates a separate internal recovery
checkpoint even when autosave is disabled, without overwriting the original
document. Active scene operations or checkpoint failures block installation.
After a version-code increase, the next ordinary launch restores this checkpoint.
External document-open requests retain priority. Android's installation
confirmation is still required.

Local verification covers reminder policy and controller behavior with fake
downloads/installers. Before publishing, verify a real signed version-code
upgrade on device, permission return, background completion, download cancellation,
and recovery with autosave disabled. Do not treat a same-version reinstall as
an end-to-end update test.
