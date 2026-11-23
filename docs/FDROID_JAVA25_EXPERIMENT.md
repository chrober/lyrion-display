# Java 25 source-build experiment — published v0.3.0 (22 Nov 2026)

**Outcome: Java 25 builds successfully; the published v0.3.0 APK is NOT reproducible using Java 25.**

## Why test Java 25?

F-Droid's current buildserver supports Debian Trixie OpenJDK 25. Gradle 9.9.1
can run on Java 25. The existing F-Droid v0.3.0 recipe nevertheless specifies
Java 17 because the *already published* GitHub APK was compiled using that
toolchain and upstream-signed reproducibility depends on identical contents.

Using a newer JVM for the build does **not** change the APK's Android
`minSdk` or automatically require a newer Android version. This repo's
existing Java source/target compatibility is 8.

## Controlled QA experiment

- PR: [#32](https://github.com/chrober/lyrion-display/pull/32)
- Completed [GitHub Actions run 44996718331](https://github.com/chrober/lyrion-display/actions/runs/44996718331)
- Exact published source: `fb4cb6359e1931519f586d6602c25e9b95e51e03`
- Package: `com.chrober.lmsmaterial.nowplaying`, versionName `0.3.0`, versionCode `9`
- F-Droid `buildserver-trixie` QA container with Debian OpenJDK **25.0.5.1**
- Gradle **9.9.1**, Android Gradle Plugin **9.5.1**, Android SDK 35
- Metadata was locally copied for QA: `sudo` installed Java 25,
  `prebuild` pinned `org.gradle.java.home` to Java 25.
  The QA copy omitted `Binaries` and `AllowedAPKSigningKeys` **only to
  defer upstream signature verification until after the unsigned APK
  was produced**. The repository's actual disabled metadata was never changed.

## Results

- `fdroid build --test --force` completed with **1 build succeeded**.
  No Java 25 incompatibility prevented the v0.3.0 source from building.
- Java 25 unsigned APK SHA-256:
  `3cae98eee9e42c5644b6d0bf60fd224fc315c6f4019ab7af7dd59843ddf441ed`
- Java 17 unsigned APK SHA-256 from the passing official-format recipe QA:
  `4de2f0deaa2ad9edc7cff597ba60af599122a5183bc348b3107a4c6c902e5e88`
- All common ZIP payload entries are identical **except `classes.dex`**.
  The original signed APK additionally contains three ordinary v1-signature
  entries, which are not content regressions.
- Original signed `classes.dex` SHA-256:
  `cb68f2f42c5a69c6b3d419e5eb4adf9291f7167086193115d979cfc52746e008`
- Java 25 `classes.dex` SHA-256:
  `93712b9ac52def8b33185f47c4ba0e1829b889825bdfd72f137662fcc586645c`
- Existing v0.3.0 GitHub APK checksum and developer certificate check: **passed**.
- `apksigcopier compare --unsigned` against the published v0.3.0:
  **failed** with APK integrity / `CHUNKED_SHA256` mismatch.
- The job intentionally reports `REPRODUCIBLE=false` while the workflow
  exits successfully: **CI success means the experiment completed**, not
  that v0.3.0 reproduced.

## Decision and next steps

1. **Keep Java 17 for published v0.3.0.** Never re-label the Java 25 build
   as that release, switch signing certificates, or re-upload the v0.3.0 APK.
2. **Java 25 is eligible for a future release** after normal QA, including
   emulator/device testing and a new GitHub release APK built with Java 25.
3. At that future release, run the matching F-Droid Java 25 source build and
   compare it to the **same new release** using `apksigcopier` before enabling
   any F-Droid build. Updating just the F-Droid JDK for an old developer-signed
   release is not sufficient.
4. The historical Java 25-vs-v0.3.0 experiment was completed in PR #32;
   its dedicated workflow has been removed as obsolete. The evidence remains
   in this document and in the archived Actions run.
   The actual [disabled F-Droid recipe](../fdroid/metadata/com.chrober.lmsmaterial.nowplaying.yml)
   and `check_fdroid_recipe.py` continue to enforce Java 17 for v0.3.0.

**No release, signing action, F-Droid submission, or publication took place.**
