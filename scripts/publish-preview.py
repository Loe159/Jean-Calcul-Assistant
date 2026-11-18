#!/usr/bin/env python3
"""Publish an APK and an integrity manifest to the PR preview channel.

Uses the workflow token through gh; no credentials are embedded in the APK.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile


def gh(*args, check=True):
    return subprocess.run(["gh", *args], check=check, capture_output=True, text=True)


def main():
    channel = os.environ["UPDATE_CHANNEL"]
    code = int(os.environ["PREVIEW_VERSION_CODE"])
    repository = os.environ["GITHUB_REPOSITORY"]
    commit = os.environ.get("APK_SOURCE_COMMIT") or os.environ["GITHUB_SHA"]
    apk, = Path("app/build/outputs/apk/debug").glob("*.apk")
    # A rerun of an older build must never replace a newer preview.
    existing = gh("release", "download", channel, "--repo", repository,
                  "--pattern", "update.json", "--output", "-", check=False)
    if existing.returncode == 0:
        previous = json.loads(existing.stdout)
        if previous["versionCode"] > code:
            print("A newer preview is already published; skipping.")
            return
        if previous["versionCode"] == code:
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            if previous["sha256"] != digest:
                raise RuntimeError("Refusing to replace a different APK with the same versionCode")
            if previous.get("commit") == commit:
                print("Identical APK and source commit already published; skipping.")
                return
    release = gh("release", "view", channel, "--repo", repository, check=False)
    if release.returncode != 0:
        gh("release", "create", channel, "--repo", repository, "--prerelease", "--target", commit,
           "--title", "Jean-Calcul development previews", "--notes",
           "Test builds from GitHub Actions. Development signing key; not a production release.")
    with tempfile.TemporaryDirectory() as directory:
        asset = Path(directory) / f"JeanCalcul-{code}.apk"
        asset.write_bytes(apk.read_bytes())
        manifest = {
            "applicationId": "fr.loevan.jeancalcul.v2", "channel": channel,
            "versionCode": code, "versionName": f"2.0.0-preview.{code}-debug",
            "apkUrl": f"https://github.com/{repository}/releases/download/{channel}/{asset.name}",
            "sha256": hashlib.sha256(asset.read_bytes()).hexdigest(),
            "size": asset.stat().st_size, "commit": commit,
            "branch": os.environ.get("GITHUB_REF_NAME", ""),
        }
        path = Path(directory) / "update.json"
        path.write_text(json.dumps(manifest, indent=2) + "\n")
        # Immutable versioned APK first; replace the manifest only after upload succeeds.
        gh("release", "upload", channel, str(asset), "--repo", repository, "--clobber")
        gh("release", "upload", channel, str(path), "--repo", repository, "--clobber")
        print(f"Published {asset.name} ({manifest['sha256']})")


if __name__ == "__main__":
    main()
