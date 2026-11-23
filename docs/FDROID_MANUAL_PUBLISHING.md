# Manual F-Droid submissions (no automatic publishing)

The public developer/maintainer name is **Christoph O'Bermair**.
No personal email is required in F-Droid metadata.

## Publishing policy

- GitHub release tags or release workflow runs **never submit** anything to F-Droid.
- Only GitHub Actions **F-Droid submission (manual only)**, triggered using
  `workflow_dispatch`, may create a GitLab fdroiddata merge request.
- Default mode is **dry-run** (metadata checks, reproducible build, signing
  comparison, report upload); no GitLab access or credentials required.
- **submit** requires an exact confirmation string, GitHub's
  `fdroid-submission` protected environment, and a short-lived, narrow-scope
  GitLab token. Configure required reviewers on that environment. Without
  them the environment alone does not enforce human approval.
- Repository permissions are read-only. Secrets are exposed only to the
  conditional submit job, never to build containers.
- No scheduled, tag-based, push-based or release-based F-Droid submission.

## One-time setup (user action)

1. Create a GitLab account with the desired public display name, optionally
   using GitLab's private commit email feature.
2. Fork [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) to your
   GitLab account and keep the fork's default branch synchronized.
3. In GitHub repository Settings > Secrets and variables > Actions, add
   secret `FDROID_GITLAB_TOKEN` with the minimum GitLab API repository/MR
   permissions needed for that fork.
4. Add repository variable `FDROID_GITLAB_FORK_PROJECT_ID` with the
   verified numeric ID **89337343** for [chrober/fdroiddata](https://gitlab.com/chrober/fdroiddata).
   The fork is owned by the existing GitLab account `chrober`; do not
   confuse it with the upstream project ID 36528.
5. In GitHub Settings > Environments create `fdroid-submission` with
   required reviewers; restrict deployments to `main`.

These credentials/settings are intentionally **not configured by code**.

## Release procedure

1. Merge the reviewed release to `main` and publish the signed GitHub release.
2. In Actions, choose **F-Droid submission (manual only)** on branch `main`.
3. Run `release_tag=v0.4.0`, `mode=dry-run`. The workflow checks tag/source
   identity, Android version, source recipe, F-Droid metadata lint, independent
   F-Droid Trixie Java 25 rebuild, GitHub APK checksum/certificate and
   `apksigcopier compare --unsigned`.
4. Review the uploaded metadata + evidence before any GitLab submission.
5. Only after independent approval: rerun with `mode=submit` and
   `confirmation=SUBMIT v0.4.0`. Approve the protected environment; the
   workflow checks that the configured fork really belongs to fdroid/fdroiddata,
   verifies fork synchronization, commits metadata and opens a Merge Request
   **for review**. It cannot force publishing on f-droid.org.

For v0.4.0 the generated F-Droid recipe is pinned to the exact released
commit and Java 25 toolchain. The original **disabled** v0.3.0 Java 17
draft in this repo is deliberately preserved for historical QA.
`AutoUpdateMode: None` and `UpdateCheckMode: Static` remain in force.

## Limitations

The first GitLab MR is subject to F-Droid's licensing, metadata, scanner,
reproducibility and buildserver review. The buildserver CI image is not
proof of official production acceptance. Actual publishing occurs only
after F-Droid maintainers merge and process the metadata.

See [F-Droid's submission guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
and [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/).
