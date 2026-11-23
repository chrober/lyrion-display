#!/usr/bin/env python3
"""Submit an already-reviewed release recipe to a user's fdroiddata fork.

Only run from the explicit GitHub Actions submit job after reproducibility,
signing checks and a protected-environment approval. Never publishes APKs.
"""
from __future__ import annotations

import argparse
import copy
import json
import os
from pathlib import Path
import re
from urllib import error, parse, request

import yaml

from fdroid_prepare_submission import APP_ID, AUTHOR, BINARY_URL, REPO_URL, SIGNING_CERT

API = "https://gitlab.com/api/v4"
UPSTREAM = "fdroid/fdroiddata"
FILE = "metadata/" + APP_ID + ".yml"


def api_call(token: str, method: str, path: str, body: dict | None = None,
             allow_missing: bool = False):
    url = API + path
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    req = request.Request(url, data=payload, method=method,
                          headers={"PRIVATE-TOKEN": token,
                                   "Content-Type": "application/json",
                                   "Accept": "application/json"})
    try:
        with request.urlopen(req, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else {}
    except error.HTTPError as exc:
        if allow_missing and exc.code == 404:
            return None
        # Never print the response body, headers or configured token.
        raise RuntimeError(f"GitLab {method} {path.split('?')[0]} returned HTTP {exc.code}") from None
    except error.URLError as exc:
        raise RuntimeError(f"GitLab {method} network request failed: {type(exc.reason).__name__}") from None


def merge_upstream(previous: dict | None, candidate: dict) -> tuple[str, dict]:
    """Do not destroy fdroiddata history when submitting subsequent versions."""
    if previous is None:
        return "create", candidate
    if not isinstance(previous, dict) or previous.get("Repo") != REPO_URL:
        raise ValueError("Existing fdroiddata app metadata is unrelated to this upstream repo")
    if previous.get("Binaries") != BINARY_URL or previous.get("AllowedAPKSigningKeys") != [SIGNING_CERT]:
        raise ValueError("Existing signature policy differs; manual F-Droid review required")
    if previous.get("AutoUpdateMode") != "None" or previous.get("UpdateCheckMode") != "Static":
        raise ValueError("Existing F-Droid update policy is not manual; do not overwrite it")
    code = candidate["CurrentVersionCode"]
    old = previous.get("Builds") or []
    if any(int(b.get("versionCode", -1)) >= code for b in old):
        raise ValueError("Version already exists or is not newer than fdroiddata's builds")
    doc = copy.deepcopy(previous)
    doc["AuthorName"] = AUTHOR
    doc["Builds"] = old + candidate["Builds"]
    doc["CurrentVersion"] = candidate["CurrentVersion"]
    doc["CurrentVersionCode"] = code
    return "update", doc


def submit_to_gitlab(token: str, fork_id: int, tag: str, metadata: dict) -> str:
    if not token or fork_id <= 0:
        raise ValueError("Protected GitLab credentials and numeric fork project ID required")
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError("Invalid release tag")
    if metadata.get("AuthorName") != AUTHOR or metadata.get("CurrentVersion") != tag[1:]:
        raise ValueError("Prepared metadata author or version does not match submission request")
    upstream_path = parse.quote(UPSTREAM, safe="")
    upstream = api_call(token, "GET", f"/projects/{upstream_path}")
    fork = api_call(token, "GET", f"/projects/{fork_id}")
    if upstream.get("path_with_namespace") != UPSTREAM:
        raise ValueError("Cannot confirm official fdroiddata project")
    original = fork.get("forked_from_project") or {}
    if original.get("id") != upstream.get("id"):
        raise ValueError("Configured GitLab project is not a direct fork of fdroid/fdroiddata")
    upstream_branch = upstream.get("default_branch")
    fork_branch = fork.get("default_branch")
    if not upstream_branch or not fork_branch:
        raise ValueError("Missing upstream/fork default branch")
    up = api_call(token, "GET", f"/projects/{upstream['id']}/repository/branches/"
                  + parse.quote(upstream_branch, safe=""))
    own = api_call(token, "GET", f"/projects/{fork_id}/repository/branches/"
                   + parse.quote(fork_branch, safe=""))
    if up.get("commit", {}).get("id") != own.get("commit", {}).get("id"):
        raise ValueError("Your fdroiddata fork is not synchronized with upstream's default branch")

    branch = "submit-" + APP_ID.replace(".", "-") + "-" + tag.replace(".", "-")
    if api_call(token, "GET", f"/projects/{fork_id}/repository/branches/" + branch,
                allow_missing=True) is not None:
        raise ValueError("Submission branch already exists; review the existing branch/MR first")

    file_url = f"/projects/{upstream['id']}/repository/files/{parse.quote(FILE, safe='')}/raw"
    old = None
    req = request.Request(API + file_url + "?" + parse.urlencode({"ref": upstream_branch}),
                          headers={"PRIVATE-TOKEN": token})
    try:
        with request.urlopen(req, timeout=30) as response:
            old = yaml.safe_load(response.read().decode("utf-8"))
    except error.HTTPError as exc:
        if exc.code != 404:
            raise RuntimeError(f"GitLab metadata read returned HTTP {exc.code}") from None
    operation, resulting = merge_upstream(old, metadata)
    content = yaml.safe_dump(resulting, sort_keys=False, allow_unicode=True, width=100)

    api_call(token, "POST", f"/projects/{fork_id}/repository/commits", {
        "branch": branch,
        "start_branch": fork_branch,
        "commit_message": f"Submit Lyrion Display {tag} to F-Droid",
        "actions": [{"action": operation, "file_path": FILE, "content": content}],
    })
    title = f"New App: Lyrion Display ({tag})" if operation == "create" else f"Update Lyrion Display to {tag}"
    desc = (
        f"Manually requested F-Droid submission for **Lyrion Display {tag}**.\n\n"
        f"Public maintainer: {AUTHOR}\n"
        f"Source and immutable tag: https://github.com/chrober/lyrion-display/tree/{tag}\n"
        f"Signed binary/checksum: https://github.com/chrober/lyrion-display/releases/tag/{tag}\n\n"
        "The source was independently rebuilt with F-Droid Trixie/OpenJDK 25, "
        "the unsigned APK matched byte-for-byte, and the published developer-signed APK "
        "passed SHA-256, certificate, and apksigcopier validation in the manually "
        "triggered GitHub QA workflow.\n\n"
        "AutoUpdateMode remains None and UpdateCheckMode remains Static. "
        "Please review the build recipe and reproducibility in the official F-Droid infrastructure."
    )
    mr = api_call(token, "POST", f"/projects/{fork_id}/merge_requests", {
        "source_branch": branch,
        "target_branch": upstream_branch,
        "target_project_id": upstream["id"],
        "title": title,
        "description": desc,
        "labels": "New App" if operation == "create" else "",
        "remove_source_branch": False,
    })
    url = mr.get("web_url")
    if not url or not url.startswith("https://gitlab.com/"):
        raise RuntimeError("GitLab did not return a verified merge request URL")
    return url


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--confirmation", required=True)
    args = parser.parse_args()
    if args.confirmation != "SUBMIT " + args.tag:
        parser.exit(1, "The explicit submission confirmation did not match the release tag.\n")
    if not os.environ.get("GITLAB_FDROID_TOKEN"):
        parser.exit(1, "Missing FDROID_GITLAB_TOKEN secret.\n")
    try:
        fork_id = int(os.environ.get("GITLAB_FDROID_FORK_PROJECT_ID", "0"))
        document = yaml.safe_load(args.metadata.read_text(encoding="utf-8"))
        url = submit_to_gitlab(os.environ["GITLAB_FDROID_TOKEN"], fork_id,
                               args.tag, document)
        print("F-Droid metadata Merge Request created: " + url)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
                out.write("### Manual F-Droid submission\n" + url + "\n")
    except (ValueError, RuntimeError, OSError) as exc:
        parser.exit(1, f"Submission not completed: {exc}\n")
