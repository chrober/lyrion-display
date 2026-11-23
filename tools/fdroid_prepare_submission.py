#!/usr/bin/env python3
"""Create a pinned F-Droid submission recipe without publishing anything.

The historical disabled v0.3.0 metadata stays untouched. The generated recipe
is used only after an explicitly chosen, already-published GitHub release.
"""
from __future__ import annotations

import argparse
import copy
from pathlib import Path
import re
import subprocess

import yaml

APP_ID = "com.chrober.lmsmaterial.nowplaying"
AUTHOR = "Christoph O'Bermair"
SIGNING_CERT = "af624ba3a730a5bf4a9d0b77e2bed2ec557744705362d4a4445e6dcb125f1dfb"
BINARY_URL = "https://github.com/chrober/lyrion-display/releases/download/v%v/lyrion-display-v%v.apk"
REPO_URL = "https://github.com/chrober/lyrion-display.git"
VERSION_RE = re.compile(r"^v([0-9]+)\.([0-9]+)\.([0-9]+)$")


def gradle_field(script: str, key: str) -> str:
    match = re.search(r"^\s*" + re.escape(key) + r"\s+['\"]?([^'\"\s]+)", script, re.M)
    if not match:
        raise ValueError(f"Cannot find Gradle field {key}")
    return match.group(1)


def build_metadata(base: dict, tag: str, sha: str, gradle_script: str) -> dict:
    if not VERSION_RE.fullmatch(tag):
        raise ValueError("Release tag must be vMAJOR.MINOR.PATCH")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Release source must be an exact 40-character commit SHA")
    if gradle_field(gradle_script, "applicationId") != APP_ID:
        raise ValueError("The Android application ID differs from the F-Droid recipe")
    version, code = gradle_field(gradle_script, "versionName"), int(
        gradle_field(gradle_script, "versionCode"))
    if "v" + version != tag:
        raise ValueError("Tag does not match Gradle versionName")
    if base.get("Repo") != REPO_URL or base.get("License") != "MIT":
        raise ValueError("Unexpected upstream repository or license")
    if base.get("Binaries") != BINARY_URL or base.get("AllowedAPKSigningKeys") != [SIGNING_CERT]:
        raise ValueError("Unexpected upstream binary URL or signing certificate")
    if base.get("AutoUpdateMode") != "None" or base.get("UpdateCheckMode") != "Static":
        raise ValueError("F-Droid updates must remain explicitly manual")
    if not base.get("Builds") or not all(b.get("disable") for b in base["Builds"]):
        raise ValueError("Source metadata must stay disabled; never publish it in place")
    if code <= max(int(b["versionCode"]) for b in base["Builds"]):
        raise ValueError("Release versionCode must increase over the old disabled recipe")

    obj = copy.deepcopy(base)
    obj["AuthorName"] = AUTHOR
    obj["Name"] = "Lyrion Display"
    # First submission starts at the selected published version, not with the
    # old disabled Java 17 v0.3.0 test recipe.
    obj["Builds"] = [{
        "versionName": version,
        "versionCode": code,
        "commit": sha,
        "subdir": "app",
        "sudo": [
            "apt-get update",
            "apt-get install -y openjdk-25-jdk-headless",
            "update-java-alternatives -a",
        ],
        "prebuild": [
            'echo "org.gradle.java.home=/usr/lib/jvm/java-25-openjdk-amd64" >> ../gradle.properties',
        ],
        "gradle": ["yes"],
    }]
    obj["CurrentVersion"], obj["CurrentVersionCode"] = version, code
    obj["AutoUpdateMode"], obj["UpdateCheckMode"] = "None", "Static"
    return obj


def prepare(repo: Path, tag: str, output: Path) -> None:
    match = VERSION_RE.fullmatch(tag)
    if match is None:
        raise ValueError("Invalid tag (expected vMAJOR.MINOR.PATCH)")
    def git(*args: str) -> str:
        return subprocess.check_output(["git", "-C", str(repo), *args], text=True).strip()
    sha = git("rev-parse", "HEAD")
    pinned = git("rev-list", "-n", "1", "refs/tags/" + tag)
    if sha != pinned:
        raise ValueError("Checkout HEAD must be exactly the immutable release-tag commit")
    gradle = (repo / "app/build.gradle").read_text(encoding="utf-8")
    source = yaml.safe_load((repo / "fdroid/metadata" / (APP_ID + ".yml")).read_text(encoding="utf-8"))
    doc = build_metadata(source, tag, sha, gradle)
    # Include meaningful listing copy in the standalone fdroiddata submission.
    # The existing source fastlane texts are the authoritative public content.
    description_dir = repo / "fastlane/metadata/android/en-US"
    doc["Summary"] = (description_dir / "short_description.txt").read_text(
        encoding="utf-8").strip()
    doc["Description"] = (description_dir / "full_description.txt").read_text(
        encoding="utf-8").strip()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(yaml.safe_dump(doc, sort_keys=False, allow_unicode=True, width=100),
                      encoding="utf-8")
    print(f"Prepared F-Droid metadata for {tag} ({sha}), author: {AUTHOR}; no publication")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--checkout", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        prepare(args.checkout.resolve(), args.tag, args.output)
    except (ValueError, FileNotFoundError, subprocess.CalledProcessError) as exc:
        parser.exit(1, f"F-Droid preparation failed: {exc}\n")
