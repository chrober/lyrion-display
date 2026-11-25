#!/usr/bin/env python3
"""Render the official new-app template, without contacting GitLab.

Checklist ticks are explicit human attestations, never inferred from a build.
The two official pipeline/report items remain open until upstream CI has run.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re

TEMPLATE_PATH = Path(__file__).resolve().parents[1] / "docs/fdroid/Default.md"
UPSTREAM_TEMPLATE_PATH = ".gitlab/merge_request_templates/Default.md"
UPSTREAM = "https://github.com/chrober/lyrion-display"
REQUIRED_ITEMS = frozenset(range(1, 29)) - {10, 11, 20, 22, 26, 27}
PENDING_ITEMS = {26, 27}


def reviewed_numbers(value: str) -> set[int]:
    if not value.strip():
        return set()
    if not re.fullmatch(r"\s*\d+(?:\s*,\s*\d+)*\s*", value):
        raise ValueError("Checklist IDs must be comma-separated integers")
    items = [int(x) for x in value.split(",")]
    if len(items) != len(set(items)) or not set(items) <= (set(range(1, 29)) - PENDING_ITEMS):
        raise ValueError("Unknown, duplicate or pending official CI checklist item")
    return set(items)


def validate_review(kind: str, reviewed: str, evidence: str, submitting: bool) -> set[int]:
    if kind not in {"new-app", "update"}:
        raise ValueError("Invalid submission kind")
    items = reviewed_numbers(reviewed)
    if kind == "update" and (items or evidence.strip()):
        raise ValueError("New-app checklist inputs are not used for updates")
    if kind == "new-app" and submitting:
        if not REQUIRED_ITEMS <= items:
            raise ValueError("Missing explicitly reviewed checklist IDs: " +
                             ",".join(map(str, sorted(REQUIRED_ITEMS - items))))
        if not evidence.strip() or "\n" in evidence or "\r" in evidence or not re.search(r"https://\S+", evidence):
            raise ValueError("Provide evidence for Inclusion Policy Quality point 6, with a public link")
    return items


def require_current_template(current: str | None) -> None:
    if current != TEMPLATE_PATH.read_text(encoding="utf-8"):
        raise ValueError("Official F-Droid MR template changed or is unavailable; review and update the pinned copy")


def render_description(tag: str, kind: str, reviewed: str = "",
                       evidence: str = "", submitting: bool = False) -> str:
    if not re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", tag):
        raise ValueError("Invalid release tag")
    items = validate_review(kind, reviewed, evidence, submitting)
    release = f"{UPSTREAM}/releases/tag/{tag}"
    source = f"{UPSTREAM}/tree/{tag}"
    common = (
        f"Source: {source}\n\nSigned APK and checksum: {release}\n\n"
        "Updates are deliberately manual (AutoUpdateMode: None; UpdateCheckMode: Static) "
        "so that each release is independently rebuilt and its developer signature verified "
        "before any request to update fdroiddata. This exception is requested for maintainer review.\n\n"
        "GitHub submission QA verifies source, unsigned APK reproducibility and the published "
        "developer signature. Official F-Droid CI and report review are still pending.\n"
    )
    if kind == "update":
        return f"Update Lyrion Display to {tag}.\n\n" + common

    template = TEMPLATE_PATH.read_text(encoding="utf-8")
    if len(re.findall(r"^\* \[ \]", template, re.M)) != 28:
        raise ValueError("Pinned template checklist changed; update the renderer")
    base = f"https://raw.githubusercontent.com/chrober/lyrion-display/{tag}/fastlane/metadata/android/en-US/images/phoneScreenshots"
    abstract = (
        "Lyrion Display is a dedicated, read-only fullscreen Now Playing display for "
        "Lyrion Music Server with the Material Skin plugin. Select a server and player "
        "once; subsequent launches display album artwork and track information immediately "
        "on Android/Fire TV, phones or tablets. Playback remains controlled from other Lyrion clients.\n\n"
        f"![Now Playing]({base}/1.jpg)\n\n![Settings]({base}/2.jpg)\n"
    )
    motivation = (
        "I wanted a dedicated display rather than a full music controller. The app combines "
        "the server's existing Material Skin with native LAN discovery, persistent player selection, "
        "TV remote-friendly settings, optional screen-awake behavior and independently configurable "
        "Now Playing elements. It requires no cloud account or additional server plugin beyond Material Skin.\n\n"
        "I am the upstream author and intend to maintain the app.\n\n"
        + common + "\n"
        "Previous inclusion request: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/58796\n\n"
        "Quality point 6 evidence: " + (evidence.strip() or "Not yet provided; inclusion eligibility must be reviewed before submission.") + "\n"
    )
    template = template.replace(
        "### Please introduce the functions and usage of your app in summary, and also attach some screenshots.\n",
        "### Please introduce the functions and usage of your app in summary, and also attach some screenshots.\n\n" + abstract, 1)
    template = template.replace(
        "### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.\n",
        "### Please elaborate why you wrote this app. Please highlight the unique functions it provides over other similar apps in F-Droid.\n\n" + motivation, 1)
    number = 0
    lines = []
    for line in template.splitlines():
        if line.startswith("* [ ]"):
            number += 1
            if number in items:
                line = line.replace("* [ ]", "* [x]", 1)
            # IDs are visible in Markdown source for the human dry-run review.
            line += f" <!-- checklist ID {number} -->"
        if line == "Closes rfp#<RFP issue number>":
            line = ("No RFP issue is currently known; add any relevant issue references during review.\n\n"
                    "Conditional items: no srclibs/external source repositories are required; "
                    "no ABI split is needed for this app without native ABI-specific libraries. "
                    "Official pipeline and Reports checks remain unchecked until upstream review.")
        lines.append(line)
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--kind", choices=["new-app", "update"], required=True)
    parser.add_argument("--reviewed-items", default="")
    parser.add_argument("--policy-evidence", default="")
    parser.add_argument("--submitting", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        args.output.write_text(render_description(args.tag, args.kind, args.reviewed_items,
                                                 args.policy_evidence, args.submitting), encoding="utf-8")
    except ValueError as exc:
        parser.exit(1, f"MR description not prepared: {exc}\n")
