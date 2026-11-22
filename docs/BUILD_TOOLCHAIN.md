# Android build toolchain

The application supports Android API **22+**, including Fire OS 6 (Android 7.1, API 25).

## Versions

- Android Gradle Plugin **9.5.1**
- Gradle **9.9.1** (AGP 9.5 requires Gradle 9.6.0 or newer)
- JDK **25** on GitHub Actions for v0.4.0 and later; historical v0.3.0 F-Droid builds still use JDK 17
- Android `minSdk 22`, `compileSdk 35`, `targetSdk 35`

Gradle and Java 25 run on the CI runner. **Neither is bundled in the installed APK**, so Fire OS 6 / Android 7.1 (API 25) compatibility is preserved. The app's Java source/target compatibility remains 8 and `minSdk` remains 22.

The Java 25 upgrade is **not** a retroactive rebuild of the published v0.3.0. Its existing disabled F-Droid metadata stays pinned to the exact published Java 17 source/build. A new v0.4.0 candidate requires separate JDK 25 source-build reproducibility verification before release.

Gradle runs on the CI runner. It is not part of the installed APK. Upgrading
the Android Gradle Plugin or Gradle does not itself change the app's minimum
Android version.

## GitHub Actions

Both CI and release workflows use:

- `actions/checkout@v7`
- `actions/setup-java@v6`
- `gradle/actions/setup-gradle@v6` with the basic open-source cache provider
- `actions/upload-artifact@v7` for CI artifacts

The build deliberately pins these major versions and uses CI to validate a
coordinated update instead of independently accepting a plugin update whose
required Gradle version is not available.

## Dependabot

Dependabot checks GitHub Actions and Gradle dependencies monthly and groups
related upgrades into reviewable pull requests. No automatic merge is
configured. Major updates are permitted, but should pass lint, tests, APK build,
and actual-device testing if the Android runtime or WebView behavior changes.

See also [Release instructions](RELEASING.md).
