# GitHub APK updates

From 2.5.3 (code 49), `KangNa-K/PockRef` is the single canonical repository
for source, APK Releases and `update/version.json`. The Android client reads
`https://raw.githubusercontent.com/KangNa-K/PockRef/main/update/version.json`
and only accepts APK asset API URLs under `KangNa-K/PockRef`.

## Publish a version

1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`, prepare
   release notes, run relevant checks and `./gradlew :app:assembleRelease`.
   Keep signing keys/properties and R8 diagnostics private.
2. Verify package/version and `apksigner verify --print-certs`; compare the
   certificate with the installed release. Record final SHA-256 and byte size.
3. Commit and push the matching source to `KangNa-K/PockRef`. Copy the exact
   verified APK to `pockref-X.Y.Z.apk`, then create a draft with
   `gh release create release-X.Y.Z /path/to/pockref-X.Y.Z.apk --repo KangNa-K/PockRef --target SOURCE_COMMIT --draft --title "PockRef X.Y.Z" --notes-file /path/to/notes.md`.
   Use `release-*` tags, never `v*` tags.
4. Download the draft asset via the authenticated GitHub asset API and compare
   size/hash/signature. Publish the verified draft as the stable Latest release.
5. Download anonymously from the final asset API URL with
   `Accept: application/octet-stream`; verify bytes/hash/package/signature.
   Do not replace published assets or advertise an unverified download.
6. **Last**, update `update/version.json` on `main` with `versionCode`,
   `versionName`, the immutable asset API `apkUrl`, `sha256`, `size`, `notes`
   and `releaseUrl`. Commit the exact local manifest and push; read back the
   public raw endpoint used by the client. Never publish placeholders.

For interrupted publication, inspect the existing release, assets and manifest
before retrying. Reuse matching artifacts; refuse conflicting bytes or rollback.
Future releases only require publishing in `KangNa-K/PockRef`.

## Migration from the former updates repository

Versions 2.2.7 through 2.5.2 read `PockRef-updates` and validate that APK URLs
belong to that repository. Merely redirecting their manifest to a `PockRef`
asset would break downloads. The last compatibility release in the old
repository is therefore 2.5.3, containing the exact same signed APK as the
canonical release but using the old repository's own asset API URL in its
manifest. After installing it, clients use `PockRef` for all future checks.
Versions up to 2.2.6 already read `PockRef` and can update directly.

Historical APKs and release notes are retained in `PockRef` where the matching
source revision can be established. The old repository remains a read-only
compatibility archive for existing installations; do not delete its manifest
or APKs. Its README directs users to `PockRef`. The previous reverse-mirroring
script and hourly workflow have been removed to prevent overwriting the new
canonical manifest with old metadata.

## Device validation

Use a signed version-code upgrade and preserve app data. Check launch and
relevant records, manual check/download/install, permission return, background
completion, cancellation and recovery with autosave disabled when those paths
are in scope. An ADB installation does not prove the in-app updater flow.
