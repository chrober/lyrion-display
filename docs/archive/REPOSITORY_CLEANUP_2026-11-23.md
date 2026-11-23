# Repository cleanup audit — 2026-11-23

After the stable Lyrion Display v0.4.0 release and the successful manual F-Droid
**dry run**, the user requested removal of obsolete branches and old Actions
workflow registrations.

The only permanent branch is `main`. Release history is preserved in immutable
tags `v0.3.0` and `v0.4.0`. Six active workflows stay in
`.github/workflows/`, including the **manual-only** F-Droid submission.

## Former branch head references (before pruning)

These commits remain addressable by full SHA while GitHub retains the objects.
A branch may be restored if needed by creating a ref at its recorded commit.
The five divergent historical branches were experiments/abandoned release work;
none has an open pull request.

| Removed branch | Last SHA |
| --- | --- |
| `build/fdroid-v0.3.0-repro-20261121` | `21a8c421b66105af1aaf0708cc287ad5120f3e21` |
| `build/v0.3.0-rc-qa-20261121` | `92af772ae5f5333278c0be88ae1d49f956673bc6` |
| `experiment/v040-early-now-playing-20261122` | `b81428d498913a45febd4afad657e0f2cca41d01` |
| `experiment/v040-early-trigger-onpagestarted-20261122` | `8f9e7cf9c332146278a1016469cec0cff2c3eae0` |
| `fdroid/buildserver-preflight-v030-20261121` | `e2cbb2756e0282e5d19e5b0e7b99bccacb99872d` |
| `fdroid/official-java17-recipe-20261121` | `db1cc5546e7904af24093b2ef88a6573ab932b22` |
| `fdroid/verify-v0.3.0-release` | `c457a80b200a1031094d5c5262648a38b76ecfc3` |
| `feature/v040-cold-start-perf-api25-20261122` | `6c95ea525112cde9fcbfd6e070d2fad06fe82f93` |
| `feature/v040-java25-rc-prep-20261122` | `983b742959e91bdb4ce5c51686d563e9ca7cb0d2` |
| `feature/v040-js-execution-commit-profiling-20261122` | `3b24a5f56e1fc7b72ea10edb795e78c972989695` |
| `feature/v040-startup-navigation-diagnostics-20261122` | `ee6d590867633a4ab05c6c09e6f4bb90ed272e1d` |
| `feature/v040-webview-visual-preraster-20261122` | `52408e15fae77cf806aac63f421e3db5e6f0f2c9` |
| `fix/startup-window-theme-20261123` | `403c861f218fdc27e96b336b974b0602d72efdd7` |
| `publish/v0.3.0` | `fb4cb6359e1931519f586d6602c25e9b95e51e03` |
| `publish/v0.4.0` | `8481612c2a5c0746fe4bd74fd49ffb3153f01e8b` |
| `qa/fdroid-java25-v030-20261122` | `c4334de082d6af89ed7b009bf05dc8abf4b353fa` |
| `rc/v0.4.0` | `8793b1c1279560d0691caf54d3eeacdf2a646e0b` |
| `release/prepare-v0.3.0` | `d7360d726425288705cc29851433ffbed48ae117` |
| `ui/settings-version-footer-20261121` | `ee6fdcc1c24ec6f5e75f6ba9b363f8249107602b` |

## Retired Actions workflows

Only completed historical runs of these already removed workflow files are
deleted to declutter GitHub Actions. The six current workflow registrations and
their runs, Dependabot, F-Droid dry run, and the released v0.4.0 APK are excluded.

| Workflow ID | Former YAML |
| --- | --- |
| `424824682` | `.github/workflows/convert-launcher-assets.yml` |
| `424866303` | `.github/workflows/build-screenshot-apk.yml` |
| `424885629` | `.github/workflows/screenshot-test-apk.yml` |
| `424915482` | `.github/workflows/rebrand-tv-banner-once.yml` |
| `424917426` | `.github/workflows/polish-banner-once.yml` |
| `424920674` | `.github/workflows/finish-banner-once.yml` |
| `424933379` | `.github/workflows/export-icon-reference-once.yml` |
| `425008930` | `.github/workflows/build-release-candidate.yml` |
| `425090221` | `.github/workflows/build-qa-device-apk.yml` |
| `425124118` | `.github/workflows/fdroid-source-probe.yml` |
| `425192340` | `.github/workflows/build-clock-firetv-test.yml` |
| `425276432` | `.github/workflows/build-autosave-firetv-test.yml` |
| `425278028` | `.github/workflows/signing-qa-test.yml` |
| `425297922` | `.github/workflows/build-fast-return-firetv-test.yml` |
| `425518080` | `.github/workflows/prune-obsolete-branches.yml` |
| `425766401` | `.github/workflows/build-v030-candidate.yml` |
| `425792236` | `.github/workflows/verify-v030-fdroid-qa.yml` |
| `426600109` | `.github/workflows/qa-fdroid-java25.yml` |
| `426667815` | `.github/workflows/qa-v040-java25.yml` |

The temporary main-only cleanup runner was SHA-guarded and removed from CI
after its one successful use. This audit does not alter app behavior.
