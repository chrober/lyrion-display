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

from ruamel.yaml import YAML

from fdroid_prepare_submission import (
    APP_ID, AUTHOR, BINARY_URL, REPO_URL, SIGNING_CERT, canonical_yaml,
)

# F-Droid uses YAML 1.2: unquoted "yes" in Gradle build lists is a string,
# not the boolean True returned by PyYAML's YAML 1.1 loader.
YAML_READER = YAML(typ="safe")

API = "https://gitlab.com/api/v4"
UPSTREAM = "fdroid/fdroiddata"
FILE = "metadata/" + APP_ID + ".yml"
SUMMARY_FILE = "metadata/" + APP_ID + "/en-US/summary.txt"


def localized_summary_action(summary: str, previous: str | None) -> dict | None:
    """New submission creates summary.txt; updates preserve or amend the existing file."""
    if not isinstance(summary, str):
        raise ValueError("Localized F-Droid summary must be text")
    clean = summary.strip()
    if not clean or "\n" in clean or len(clean) > 80:
        raise ValueError("Localized F-Droid summary must be one line of 1–80 characters")
    desired = clean + "\n"
    if previous == desired:
        return None
    return {"action": "create" if previous is None else "update",
            "file_path": SUMMARY_FILE, "content": desired}


def read_upstream_file(token: str, project_id: int, path: str, branch: str) -> str | None:
    """Read existing fdroiddata content; 404 means a new app/localization only."""
    address = (API + f"/projects/{project_id}/repository/files/" +
               parse.quote(path, safe="") + "/raw?" +
               parse.urlencode({"ref": branch}))
    req = request.Request(address, headers={"PRIVATE-TOKEN": token})
    try:
        with request.urlopen(req, timeout=30) as response:
            return response.read().decode("utf-8")
    except error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise RuntimeError(f"GitLab file read for {path} returned HTTP {exc.code}") from None
    except error.URLError as exc:
        raise RuntimeError("GitLab file read failed: " + type(exc.reason).__name__) from None


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
    old_signers = previous.get("AllowedAPKSigningKeys")
    if isinstance(old_signers, str):
        old_signers = [old_signers]  # F-Droid rewritemeta emits a scalar for one signer.
    if previous.get("Binaries") != BINARY_URL or old_signers != [SIGNING_CERT]:
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


def require_clean_ancestor(upstream_sha: str, fork_sha: str,
                           upstream_to_fork: dict, fork_to_upstream: dict) -> int:
    """Permit a strictly behind, otherwise unchanged fdroiddata fork.

    We require both direct GitLab comparisons to complete, no commits on the
    fork that are missing from upstream, and the official upstream HEAD as
    the final commit in the forward comparison. A diverged or unverifiable
    fork fails closed rather than submitting unexpected history.
    """
    if upstream_sha == fork_sha:
        return 0
    if not all(isinstance(x, dict) and not x.get("compare_timeout") and
               isinstance(x.get("commits"), list)
               for x in (upstream_to_fork, fork_to_upstream)):
        raise ValueError("Cannot verify fdroiddata fork ancestry via GitLab")
    if upstream_to_fork["commits"]:
        raise ValueError("fdroiddata fork has commits not present in official upstream")
    behind_commits = fork_to_upstream["commits"]
    if not behind_commits or behind_commits[-1].get("id") != upstream_sha:
        raise ValueError("fdroiddata fork is not a verified ancestor of upstream")
    return len(behind_commits)


def submit_to_gitlab(token: str, fork_id: int, tag: str, metadata: dict,
                     summary: str) -> str:
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
    upstream_sha = up.get("commit", {}).get("id")
    fork_sha = own.get("commit", {}).get("id")
    if not upstream_sha or not fork_sha:
        raise ValueError("Could not verify both fdroiddata default branch commits")
    if upstream_sha != fork_sha:
        # F-Droid fdroiddata receives frequent upstream commits. An unchanged
        # fork may be a few commits behind by the time independent QA finishes.
        # GitLab cross-project MRs support this: only reject divergent/advanced
        # forks, not clean ancestors of upstream. Compare immutable SHAs, never
        # live branch names, to avoid TOCTOU changes during verification.
        compare_path = f"/projects/{upstream['id']}/repository/compare"
        latest_to_fork = api_call(token, "GET", compare_path + "?" +
                                  parse.urlencode({"from": upstream_sha, "to": fork_sha,
                                                   "straight": "true"}))
        fork_to_latest = api_call(token, "GET", compare_path + "?" +
                                  parse.urlencode({"from": fork_sha, "to": upstream_sha,
                                                   "straight": "true"}))
        lag = require_clean_ancestor(upstream_sha, fork_sha,
                                     latest_to_fork, fork_to_latest)
        print(f"fdroiddata fork is a clean upstream ancestor ({lag} upstream commits behind).")
    else:
        print("fdroiddata fork is synchronized with upstream.")

    branch = "submit-" + APP_ID.replace(".", "-") + "-" + tag.replace(".", "-")
    if api_call(token, "GET", f"/projects/{fork_id}/repository/branches/" + branch,
                allow_missing=True) is not None:
        raise ValueError("Submission branch already exists; review the existing branch/MR first")

    previous_metadata = read_upstream_file(
        token, upstream["id"], FILE, upstream_branch)
    previous_summary = read_upstream_file(
        token, upstream["id"], SUMMARY_FILE, upstream_branch)
    old = YAML_READER.load(previous_metadata) if previous_metadata is not None else None
    operation, resulting = merge_upstream(old, metadata)
    # The English short description belongs in localized summary.txt, not
    # the YAML root, otherwise upstream make-summary-translatable.py fails.
    resulting.pop("Summary", None)
    content = canonical_yaml(resulting)
    actions = [{"action": operation, "file_path": FILE, "content": content}]
    summary_action = localized_summary_action(summary, previous_summary)
    if summary_action is not None:
        actions.append(summary_action)

    api_call(token, "POST", f"/projects/{fork_id}/repository/commits", {
        "branch": branch,
        "start_branch": fork_branch,
        "commit_message": f"Submit Lyrion Display {tag} to F-Droid",
        "actions": actions,
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
    parser.add_argument("--summary", type=Path, required=True)
    parser.add_argument("--confirmation", required=True)
    args = parser.parse_args()
    if args.confirmation != "SUBMIT " + args.tag:
        parser.exit(1, "The explicit submission confirmation did not match the release tag.\n")
    if not os.environ.get("GITLAB_FDROID_TOKEN"):
        parser.exit(1, "Missing FDROID_GITLAB_TOKEN secret.\n")
    try:
        fork_id = int(os.environ.get("GITLAB_FDROID_FORK_PROJECT_ID", "0"))
        document = YAML_READER.load(args.metadata.read_text(encoding="utf-8"))
        summary = args.summary.read_text(encoding="utf-8")
        url = submit_to_gitlab(os.environ["GITLAB_FDROID_TOKEN"], fork_id,
                               args.tag, document, summary)
        print("F-Droid metadata Merge Request created: " + url)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
                out.write("### Manual F-Droid submission\n" + url + "\n")
    except (ValueError, RuntimeError, OSError) as exc:
        parser.exit(1, f"Submission not completed: {exc}\n")
