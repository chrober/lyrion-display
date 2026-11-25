# Manual F-Droid submissions (no automatic publishing)

The public developer/maintainer name is **Christoph O'Bermair**.
No personal email is required in F-Droid metadata.

## Publishing policy

- GitHub release tags or release workflow runs **never submit** anything to F-Droid.
- Only GitHub Actions **F-Droid submission (manual only)**, triggered using
  `workflow_dispatch`, may create a GitLab fdroiddata merge request.
- Default mode is **dry-run** (metadata checks, reproducible build, signing
  comparison, report upload); no GitLab access or credentials required.
- **submit** requires an exact confirmation string, an individually reviewed new-app checklist and policy evidence for first inclusion, GitHub's
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
4. Review the uploaded metadata, QA evidence and `mr-description.md` before any GitLab submission. The initial submission contains only `metadata/com.chrober.lmsmaterial.nowplaying.yml`; listing text and screenshots stay in upstream Fastlane.
5. For first inclusion, select `submission_kind=new-app`. Check the official
   [inclusion policy](https://f-droid.org/docs/Inclusion_Policy) (especially Quality
   point 6: at least three months of active development unless an exception applies),
   and only after eligibility is established provide `policy_evidence` with a public
   evidence link. Do not resubmit solely because the template is corrected.
   Read each numbered checklist item in the raw Markdown preview; enter the comma-
   separated IDs you have actually verified in `reviewed_items`. The required
   IDs are `1,2,3,4,5,6,7,8,9,12,13,14,15,16,17,18,19,21,23,24,25,28`.
   Conditional items 10, 11, 20, 22 may be left unchecked when not applicable;
   add any relevant issue links during review. Items 26 and 27 must remain unchecked
   until official fdroiddata CI and Reports are reviewed. For an already included
   app, select `update` instead and leave the new-app inputs empty.
6. Only after independent approval: rerun with `mode=submit`, the reviewed inputs,
   and `confirmation=SUBMIT <selected tag>`. Approve the protected environment; the
   workflow checks that the configured fork really belongs to fdroid/fdroiddata,
   verifies fork synchronization, commits metadata and opens a Merge Request
   **for review**. It cannot force publishing on f-droid.org.

For v0.4.0 the generated F-Droid recipe is pinned to the exact released
commit and Java 25 toolchain. The original **disabled** v0.3.0 Java 17
draft in this repo is deliberately preserved for historical QA.
`AutoUpdateMode: None` and `UpdateCheckMode: Static` remain in force.

## Official MR template

The new-app title is exactly `New app: Lyrion Display`. The description uses
the [official Default.md template](https://gitlab.com/fdroid/fdroiddata/-/blob/master/.gitlab/merge_request_templates/Default.md)
pinned in [fdroid/Default.md](fdroid/Default.md), with an abstract, motivation,
tag-pinned screenshots and the full checklist. The submission job checks the
upstream template at the verified upstream commit before making any write;
if it changed, update the pinned copy, renderer and tests before retrying.
Dry runs use the pinned template and do not contact GitLab. The workflow uses
submission tools pinned to its own dispatch commit even when building older
immutable release tags. No checkboxes are marked unless explicitly supplied by
the reviewer; upstream CI/report items cannot be marked by this workflow.

## Limitations

The first GitLab MR is subject to F-Droid's licensing, metadata, scanner,
reproducibility and buildserver review. The buildserver CI image is not
proof of official production acceptance. Actual publishing occurs only
after F-Droid maintainers merge and process the metadata.

See [F-Droid's submission guide](https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
and [Build Metadata Reference](https://f-droid.org/en/docs/Build_Metadata_Reference/).
