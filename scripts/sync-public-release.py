#!/usr/bin/env python3
"""Mirror the verified updater release and keep the 2.2.6 update route alive."""

import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import urllib.request


SOURCE = "KangNa-K/PockRef"
UPDATES = "KangNa-K/PockRef-updates"
MANIFEST = "update/version.json"
ROOT = Path(__file__).resolve().parents[1]


def gh(*args, data=None):
    result = subprocess.run(
        ["gh", *args], input=json.dumps(data) if data is not None else None,
        text=True, capture_output=True, check=True,
    )
    return result.stdout


def api(path, data=None):
    args = ["api", path]
    if data is not None:
        args += ["--method", "PUT", "--input", "-"]
    return json.loads(gh(*args, data=data))


def content(repo):
    try:
        result = api(f"repos/{repo}/contents/{MANIFEST}?ref=main")
    except subprocess.CalledProcessError as error:
        if "HTTP 404" in error.stderr:
            return None, None
        raise
    return json.loads(base64.b64decode(result["content"])), result["sha"]


def download(url, destination, manifest):
    # Deliberately anonymous: verify the same public path used by the app.
    subprocess.run([
        "curl", "--fail", "--silent", "--show-error", "--location",
        "--retry", "2", "--connect-timeout", "15", "--max-time", "300",
        "--header", "Accept: application/octet-stream", url,
        "--output", str(destination),
    ], check=True)
    verify(destination, manifest)


def verify(path, manifest):
    if path.stat().st_size != manifest["size"]:
        raise ValueError("APK size differs from the canonical manifest")
    if hashlib.sha256(path.read_bytes()).hexdigest() != manifest["sha256"]:
        raise ValueError("APK SHA-256 differs from the canonical manifest")


def main():
    manifest, _ = content(UPDATES)
    if not manifest:
        raise ValueError("Canonical update manifest is missing")
    version = manifest["versionName"]
    code = manifest["versionCode"]
    if not re.fullmatch(r"\d+\.\d+\.\d+", version) or not isinstance(code, int) or code <= 0:
        raise ValueError("Invalid canonical version")
    if not isinstance(manifest["size"], int) or not 0 < manifest["size"] <= 250 * 1024 * 1024:
        raise ValueError("Invalid canonical APK size")
    if not re.fullmatch(r"[0-9a-f]{64}", manifest["sha256"]):
        raise ValueError("Invalid canonical APK hash")
    if not re.fullmatch(rf"https://api.github.com/repos/{UPDATES}/releases/assets/\d+", manifest["apkUrl"]):
        raise ValueError("Unexpected canonical APK URL")
    gradle = (ROOT / "app/build.gradle.kts").read_text()
    if re.search(r'versionName\s*=\s*"([^"]+)"', gradle)[1] != version or int(
        re.search(r"versionCode\s*=\s*(\d+)", gradle)[1]
    ) != code:
        raise ValueError("Push matching source version before mirroring the release")

    canonical = api(f"repos/{UPDATES}/releases/latest")
    tag = f"release-{version}"
    name = f"pockref-{version}.apk"
    if canonical["tag_name"] != tag or canonical["draft"] or canonical["prerelease"]:
        raise ValueError("Canonical stable release and manifest disagree")
    assets = [a for a in canonical["assets"] if a["name"] == name and a["url"] == manifest["apkUrl"]]
    if len(assets) != 1 or assets[0]["size"] != manifest["size"]:
        raise ValueError("Canonical release asset and manifest disagree")

    previous, previous_sha = content(SOURCE)
    if previous and previous["versionCode"] > code:
        raise ValueError("Refusing to roll back the compatibility manifest")
    latest = api(f"repos/{SOURCE}/releases/latest")
    # All project stable tags use release-X.Y.Z. Never replace a newer Latest.
    if tuple(map(int, latest["tag_name"].removeprefix("release-").split("."))) > tuple(map(int, version.split("."))):
        raise ValueError("Source repository already has a newer release")
    releases = api(f"repos/{SOURCE}/releases?per_page=100")
    release = next((r for r in releases if r["tag_name"] == tag), None)

    with tempfile.TemporaryDirectory(prefix="pockref-release-sync-") as directory:
        apk = Path(directory) / name
        download(manifest["apkUrl"], apk, manifest)
        notes = Path(directory) / "notes.md"
        notes.write_text(canonical["body"] + f"\n\n同步自 [{UPDATES}]({canonical['html_url']})；APK 与更新渠道完全一致。\n")
        if release is None:
            target = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
            gh("release", "create", tag, str(apk), "--repo", SOURCE, "--target", target,
               "--title", canonical["name"], "--notes-file", str(notes), "--draft")
            # A draft may not have a Git tag yet, so /releases/tags/TAG can
            # return 404. Authenticated release listing includes drafts.
            releases = api(f"repos/{SOURCE}/releases?per_page=100")
            release = next(r for r in releases if r["tag_name"] == tag)
        mirrored = [a for a in release["assets"] if a["name"] == name]
        if not mirrored and release["draft"]:
            gh("release", "upload", tag, str(apk), "--repo", SOURCE)
            release = api(f"repos/{SOURCE}/releases/{release['id']}")
            mirrored = [a for a in release["assets"] if a["name"] == name]
        if len(mirrored) != 1 or len(release["assets"]) != 1 or release["prerelease"]:
            raise ValueError("Conflicting mirror release; refusing to replace assets")
        asset = mirrored[0]
        if asset["size"] != manifest["size"]:
            raise ValueError("Conflicting mirror APK size")
        # Verify uploaded draft bytes before promotion, including resumed runs.
        if release["draft"]:
            staged = Path(directory) / "staged.apk"
            with staged.open("wb") as stream:
                subprocess.run(["gh", "api", asset["url"], "-H", "Accept: application/octet-stream"],
                               stdout=stream, check=True)
            verify(staged, manifest)
            gh("release", "edit", tag, "--repo", SOURCE, "--draft=false", "--latest")
        download(asset["url"], Path(directory) / "public.apk", manifest)
        if latest["tag_name"] != tag:
            gh("release", "edit", tag, "--repo", SOURCE, "--latest")

    # Read both manifests again after network work; never publish stale metadata.
    current, _ = content(UPDATES)
    if current != manifest:
        raise ValueError("Canonical manifest changed during synchronization; rerun")
    mirror = dict(manifest, apkUrl=asset["url"], releaseUrl=release["html_url"])
    current, sha = content(SOURCE)
    if current != previous or sha != previous_sha:
        raise ValueError("Compatibility manifest changed during synchronization; rerun")
    if current != mirror:
        payload = {
            "message": f"fix(update): sync public release and legacy updater to {version}",
            "branch": "main",
            "content": base64.b64encode((json.dumps(mirror, ensure_ascii=False, indent=2) + "\n").encode()).decode(),
        }
        if sha:
            payload["sha"] = sha
        api(f"repos/{SOURCE}/contents/{MANIFEST}", payload)
    raw = f"https://raw.githubusercontent.com/{SOURCE}/main/{MANIFEST}"
    request = urllib.request.Request(raw, headers={"Cache-Control": "no-cache"})
    with urllib.request.urlopen(request, timeout=30) as response:
        public = json.load(response)
    if public != mirror:
        raise ValueError("Public manifest readback is stale; rerun after propagation")
    if api(f"repos/{SOURCE}/releases/latest")["tag_name"] != tag:
        raise ValueError("Source repository Latest does not match the update channel")
    print(json.dumps({"version": version, "versionCode": code, "release": release["html_url"],
                      "sha256": mirror["sha256"], "size": mirror["size"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
