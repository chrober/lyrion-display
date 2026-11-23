#!/usr/bin/env python3
"""One-time, SHA-leased cleanup, approved 2026-11-23. No releases or submissions.

Only removes specifically enumerated old branch refs and completed runs of
specifically enumerated workflows whose YAML files are ABSENT from main.
The GITHUB_TOKEN is restricted to this one-off, main-only GitHub Actions job.
"""
import json
import os
import sys
import time
from urllib import error, parse, request

OWNER_REPO = "chrober/lyrion-display"
EXPECTED_BRANCHES = {
  "build/fdroid-v0.3.0-repro-20261121": "21a8c421b66105af1aaf0708cc287ad5120f3e21",
  "build/v0.3.0-rc-qa-20261121": "92af772ae5f5333278c0be88ae1d49f956673bc6",
  "experiment/v040-early-now-playing-20261122": "b81428d498913a45febd4afad657e0f2cca41d01",
  "experiment/v040-early-trigger-onpagestarted-20261122": "8f9e7cf9c332146278a1016469cec0cff2c3eae0",
  "fdroid/buildserver-preflight-v030-20261121": "e2cbb2756e0282e5d19e5b0e7b99bccacb99872d",
  "fdroid/official-java17-recipe-20261121": "db1cc5546e7904af24093b2ef88a6573ab932b22",
  "fdroid/verify-v0.3.0-release": "c457a80b200a1031094d5c5262648a38b76ecfc3",
  "feature/v040-cold-start-perf-api25-20261122": "6c95ea525112cde9fcbfd6e070d2fad06fe82f93",
  "feature/v040-java25-rc-prep-20261122": "983b742959e91bdb4ce5c51686d563e9ca7cb0d2",
  "feature/v040-js-execution-commit-profiling-20261122": "3b24a5f56e1fc7b72ea10edb795e78c972989695",
  "feature/v040-startup-navigation-diagnostics-20261122": "ee6d590867633a4ab05c6c09e6f4bb90ed272e1d",
  "feature/v040-webview-visual-preraster-20261122": "52408e15fae77cf806aac63f421e3db5e6f0f2c9",
  "fix/startup-window-theme-20261123": "403c861f218fdc27e96b336b974b0602d72efdd7",
  "publish/v0.3.0": "fb4cb6359e1931519f586d6602c25e9b95e51e03",
  "publish/v0.4.0": "8481612c2a5c0746fe4bd74fd49ffb3153f01e8b",
  "qa/fdroid-java25-v030-20261122": "c4334de082d6af89ed7b009bf05dc8abf4b353fa",
  "rc/v0.4.0": "8793b1c1279560d0691caf54d3eeacdf2a646e0b",
  "release/prepare-v0.3.0": "d7360d726425288705cc29851433ffbed48ae117",
  "ui/settings-version-footer-20261121": "ee6fdcc1c24ec6f5e75f6ba9b363f8249107602b"
}
OBSOLETE_WORKFLOWS = {
  "424824682": ".github/workflows/convert-launcher-assets.yml",
  "424866303": ".github/workflows/build-screenshot-apk.yml",
  "424885629": ".github/workflows/screenshot-test-apk.yml",
  "424915482": ".github/workflows/rebrand-tv-banner-once.yml",
  "424917426": ".github/workflows/polish-banner-once.yml",
  "424920674": ".github/workflows/finish-banner-once.yml",
  "424933379": ".github/workflows/export-icon-reference-once.yml",
  "425008930": ".github/workflows/build-release-candidate.yml",
  "425090221": ".github/workflows/build-qa-device-apk.yml",
  "425124118": ".github/workflows/fdroid-source-probe.yml",
  "425192340": ".github/workflows/build-clock-firetv-test.yml",
  "425276432": ".github/workflows/build-autosave-firetv-test.yml",
  "425278028": ".github/workflows/signing-qa-test.yml",
  "425297922": ".github/workflows/build-fast-return-firetv-test.yml",
  "425518080": ".github/workflows/prune-obsolete-branches.yml",
  "425766401": ".github/workflows/build-v030-candidate.yml",
  "425792236": ".github/workflows/verify-v030-fdroid-qa.yml",
  "426600109": ".github/workflows/qa-fdroid-java25.yml",
  "426667815": ".github/workflows/qa-v040-java25.yml"
}
ACTIVE_PATHS = [
    ".github/workflows/ci.yml",
    ".github/workflows/android-emulator.yml",
    ".github/workflows/fdroid-preflight.yml",
    ".github/workflows/release-candidate.yml",
    ".github/workflows/release.yml",
    ".github/workflows/fdroid-submit.yml",
]
API = "https://api.github.com/repos/" + OWNER_REPO
TOKEN = os.environ.get("GH_TOKEN", "")


def api(method, path, optional=False):
    req = request.Request(
        API + path,
        method=method,
        headers={
            "Authorization": "Bearer " + TOKEN,
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "lyrion-display-one-time-repository-cleanup",
        },
    )
    try:
        with request.urlopen(req, timeout=45) as res:
            payload = res.read()
            return json.loads(payload) if payload else {}
    except error.HTTPError as exc:
        if optional and exc.code in (404, 410):
            return None
        # Never log response bodies, headers, tokens, or request authorization.
        raise RuntimeError(f"GitHub {method} {path.split('?')[0]} returned HTTP {exc.code}") from None


def pages(path, key):
    found = []
    for page in range(1, 30):
        separator = "&" if "?" in path else "?"
        obj = api("GET", f"{path}{separator}per_page=100&page={page}")
        batch = obj.get(key, []) if isinstance(obj, dict) else obj
        found.extend(batch)
        if len(batch) < 100:
            return found
    raise RuntimeError("Pagination exceeded maximum (refusing partial cleanup)")


def require(assertion, msg):
    if not assertion:
        raise RuntimeError(msg)


def main():
    require(TOKEN, "Missing protected GITHUB_TOKEN")
    require(os.getenv("GITHUB_REPOSITORY") == OWNER_REPO, "Unexpected repository")
    require(os.getenv("GITHUB_REF") == "refs/heads/main", "Only default branch main may cleanup")
    require(os.getenv("RUN_REPOSITORY_CLEANUP") == "APPROVED-2026-11-23", "Explicit one-off run guard absent")
    sha = os.getenv("GITHUB_SHA")
    require(sha and len(sha) == 40, "Missing main commit identity")
    require(api("GET", "/git/ref/heads/main")["object"]["sha"] == sha, "Main moved during cleanup")
    release = api("GET", "/releases/tags/v0.4.0")
    require(release.get("prerelease") is False and release.get("draft") is False, "Stable v0.4.0 release missing")
    require(api("GET", "/git/ref/tags/v0.4.0") is not None, "v0.4.0 immutable release tag missing")
    require(not pages("/pulls?state=open", ""), "Open PRs found; not pruning any branches")

    tree = api("GET", "/git/trees/main?recursive=1")
    live = {x["path"] for x in tree["tree"] if x["path"].startswith(".github/workflows/")}
    require(live == set(ACTIVE_PATHS), "Unexpected active workflows: " + repr(sorted(live)))
    require(all(p not in live for p in OBSOLETE_WORKFLOWS.values()), "An obsolete workflow YAML reappeared")

    branches = pages("/branches", "")
    names = {b["name"]: b for b in branches}
    require(set(names).issubset(set(EXPECTED_BRANCHES) | {"main"}) and "main" in names,
            "Unexpected branch created since audit; refusing deletion")
    for name, expected_sha in EXPECTED_BRANCHES.items():
        if name not in names:
            continue  # Idempotent resume: already removed by the previous pass.
        current = names[name]
        require(current["commit"]["sha"] == expected_sha and not current["protected"],
                f"Branch SHA/protection changed: {name}")
    print(f"Validated {len(EXPECTED_BRANCHES)} exact branch refs and six active YAML workflows.", flush=True)

    # Collect the completed historical runs before mutations, with strong
    # workflow-ID AND path validation. Never purge recent or active workflows.
    all_runs = pages("/actions/runs", "workflow_runs")
    targets = {}
    for run in all_runs:
        rid = str(run["workflow_id"])
        if rid not in OBSOLETE_WORKFLOWS:
            continue
        require(run["path"] == OBSOLETE_WORKFLOWS[rid],
                f"Unexpected workflow id/path mapping: {rid}")
        require(run["status"] == "completed", f"Workflow run {run['id']} still active")
        targets.setdefault(rid, []).append(run["id"])
    print(f"Validated {sum(map(len, targets.values()))} old completed runs across "
          f"{len(targets)} obsolete workflows. Six current workflows are excluded.", flush=True)

    removed = []
    for name, expected_sha in EXPECTED_BRANCHES.items():
        if name not in names:
            continue
        # Confirm the exact head immediately before the irreversible deletion.
        current = api("GET", "/git/ref/heads/" + parse.quote(name, safe="/"))
        require(current["object"]["sha"] == expected_sha, f"Ref moved: {name}")
        api("DELETE", "/git/refs/heads/" + parse.quote(name, safe="/"))
        removed.append(name)
        print("Pruned branch: " + name, flush=True)

    # Disabling a deleted workflow may return 404; it is already unregistered.
    # Clearing its historic runs is the remaining sidebar-clutter remedy.
    cleaned = 0
    failed_disable = []
    for workflow_id, path in OBSOLETE_WORKFLOWS.items():
        try:
            result = api("PUT", "/actions/workflows/" + workflow_id + "/disable", optional=True)
            if result is None:
                failed_disable.append(workflow_id)
        except RuntimeError as exc:
            # GitHub can return HTTP 403 for a workflow whose YAML was
            # already deleted, even though Actions write permission allows
            # removal of its own completed history. Continue with run cleanup.
            if "returned HTTP 403" not in str(exc):
                raise
            failed_disable.append(workflow_id)
            print(f"Workflow {workflow_id} cannot be disabled (HTTP 403); "
                  "removing its completed legacy runs instead.", flush=True)
        for run_id in targets.get(workflow_id, []):
            api("DELETE", "/actions/runs/" + str(run_id))
            cleaned += 1
        print(f"Archived obsolete workflow {workflow_id} ({path}): "
              f"deleted {len(targets.get(workflow_id, []))} completed runs", flush=True)
    print(f"COMPLETE: branches={len(removed)}, historical_runs={cleaned}, "
          f"obsolete_workflows={len(OBSOLETE_WORKFLOWS)}, "
          f"already_unregistered={len(failed_disable)}. "
          "No current CI, v0.4.0 release, Dependabot, or F-Droid runs deleted.", flush=True)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(
                "### One-time repository cleanup\n"
                f"- Deleted {len(removed)} obsolete branches (exact SHA-validated)\n"
                f"- Removed {cleaned} old completed runs from {len(OBSOLETE_WORKFLOWS)} obsolete workflows\n"
                "- Preserved six active workflows, Dependabot, and all current release/F-Droid runs\n"
                "- Retained immutable v0.3.0 and v0.4.0 tags and main\n"
            )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        sys.exit("Repository cleanup stopped safely: " + str(exc))
