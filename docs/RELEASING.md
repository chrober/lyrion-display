# Publishing signed Android releases

> **Branding permission:** Michael Herger [approved Lyrion branding for this app](https://github.com/chrober/lyrion-display/issues/15#issuecomment-6979289683) on the condition that it remains **free and open source**. The distribution hold has been lifted. The release workflow builds and publishes automatically when triggered.

A release is published when a tag beginning with `v` is pushed (for example `v0.1.0`). For GitHub-only publication without a local tag push, a push to `publish/vX.Y.Z` also builds the signed APK and creates the matching tag and GitHub Release. Both routes require the tag to match `versionName` in `app/build.gradle`.

Previously published versions **v0.2.0 and v0.3.0 must not be rebuilt or retagged**. The next planned version, **v0.4.0**, uses `versionCode` **10**, Java **25** for the build tools, the same Android application ID and **the existing production signing certificate**. It must pass API 25 / API 29 emulator tests, byte-for-byte F-Droid Java 25 source reproducibility and [physical Fire TV Stick 4K (Fire OS 6) acceptance](V0.4.0-RELEASE-CANDIDATE.md) **before publication**. The new [signed RC workflow](../.github/workflows/release-candidate.yml) produces an Actions QA artifact without publishing a GitHub Release or submitting to F-Droid.

## 1. Create and safeguard your release keystore

An Android signing key is needed for users to install future updates over an existing release APK. **Back it up securely**. Losing the key prevents normal updates with the same application ID.

Example with the JDK's `keytool`:

```sh
keytool -genkeypair -v -keystore now-playing-release.jks \
  -alias now-playing -keyalg RSA -keysize 3072 -validity 10000
```

Never commit your `.jks` file or passwords. The project's `.gitignore` excludes keystore files.

## 2. Configure GitHub Actions secrets

Open repository **Settings → Secrets and variables → Actions** and create these four repository secrets:

| Secret | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the complete keystore file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias chosen when creating the keystore |
| `ANDROID_KEY_PASSWORD` | Private key password |

Encode the file on Windows using PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("now-playing-release.jks"))
```

Copy the entire output into the `ANDROID_KEYSTORE_BASE64` secret, without extra whitespace. On Linux, use `base64 -w0 now-playing-release.jks`.

Secrets are made available only to the release job; pull request CI does not use them.

## 3. Cut a release

1. Confirm Android CI on `main` is green.
2. Update both `versionCode` (integer, must increase for updates) and `versionName` (e.g. `0.1.0`) in `app/build.gradle`.
3. Merge any necessary updates to `main`.
4. Tag the desired commit: `git tag v0.1.0` and `git push origin v0.1.0`. Alternatively, push a dedicated branch named `publish/v0.1.0` at the chosen source commit to build and publish directly via GitHub Actions; the workflow creates the release tag at that commit.
5. Watch the **Publish Android release** workflow. It checks the tag/version match, lints/tests, builds an **unsigned** release APK, signs it with the existing production key using Android build-tools **34.0.0**, verifies the certificate fingerprint and `apksigcopier` signature-copy compatibility, and uploads `lyrion-display-vX.Y.Z.apk` plus its `.sha256` to a GitHub **pre-release**. The title is `Lyrion Display vX.Y.Z`.

The `apksigcopier` check compares the signed APK to its original unsigned input. It **does not** replace an independent F-Droid source-build verification of the exact tagged release. Follow the [F-Droid packaging checklist](FDROID_PACKAGING.md) before submitting a F-Droid metadata change. **No F-Droid submission is triggered by this release workflow.**

**Do not tag a release until the four signing secrets have been configured.** If a job fails, inspect the workflow log; the publish step runs only after successful verification.

## Debug builds

Every successful pull request or `main` CI run publishes an installable **debug APK** named `lyrion-display-debug.apk` inside the `lyrion-display-debug` workflow artifact. Debug builds use Android's debug signing certificate and cannot be updated in-place with the production release APK (uninstall first).
