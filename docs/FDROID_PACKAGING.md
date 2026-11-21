# F-Droid packaging and signing plan (NOT SUBMITTED)

The canonical app package name is `com.chrober.lmsmaterial.nowplaying`, despite the public name **Lyrion Display**.

## Recipe status: v0.3.0 pinned, still disabled

The disabled review recipe `fdroid/metadata/com.chrober.lmsmaterial.nowplaying.yml`
now points to the **exact published GitHub `v0.3.0` source commit**:
`fb4cb6359e1931519f586d6602c25e9b95e51e03`, with versionName
`0.3.0` and versionCode `9`. It deliberately remains **disabled**;
**no F-Droid submission or external publication** has been made.

The earlier pinned QA snapshot (`35b1749…`, versionCode 8) was used
only for the local F-Droid source-build and reproducibility experiments
documented below. It is **not** the actual v0.2.0 release.

Before official F-Droid inclusion:
1. **Completed locally:** exact v0.3.0 source build, published APK SHA-256,
   certificate and `apksigcopier compare --unsigned` passed (see evidence below).
2. Validate the **official recipe's** Java 17 `sudo`/Gradle selection against
   a clean F-Droid Trixie buildserver CI image. The workflow installs Bookworm
   OpenJDK 17 *from the exact disabled recipe*, rather than injecting Temurin
   from GitHub Actions. A passing CI image test is not official F-Droid
   production acceptance.
3. Complete genuine storefront screenshots, attribution, policy/security review
   and a device compatibility checklist.
4. **Already prepared but inactive:** the developer-signed `Binaries` URL and
   `AllowedAPKSigningKeys` fingerprint. These fields and the disabled build do
   not publish anything. After F-Droid approval, separately and explicitly
   authorize a manual `fdroiddata` merge request; never automatically enable
   the build or publish from a tag push.

## Published v0.3.0 — local independent reproducibility PASSED (21 Nov 2026)

The **actual published GitHub APK** was reproduced in
[GitHub Actions run 44882528618](https://github.com/chrober/lyrion-display/actions/runs/44882528618),
using an independent `fdroidserver 2.4.5` source scan and build.

- Source tag `v0.3.0` and build recipe both resolve to
  `fb4cb6359e1931519f586d6602c25e9b95e51e03`.
- F-Droid source scanner and `fdroid build --test --force`: **1 build succeeded**.
- F-Droid unsigned APK SHA-256:
  `4de2f0deaa2ad9edc7cff597ba60af599122a5183bc348b3107a4c6c902e5e88`.
- The official GitHub `lyrion-display-v0.3.0.apk` passed its published
  `.sha256` check and has developer certificate SHA-256
  `af624ba3a730a5bf4a9d0b77e2bed2ec557744705362d4a4445e6dcb125f1dfb`.
- **`apksigcopier compare --unsigned published.apk fdroid-built.apk` PASSED**,
  confirming the published upstream signature is compatible with the
  independent source-build output.

This proves **local exact-release reproducibility** on GitHub-hosted Ubuntu.
A second independently reproducible build using F-Droid's Trixie **CI image**
and a provided Java 17 toolchain is documented below. Neither CI test proves
that the official production F-Droid buildserver accepts the app. The recipe
**remains disabled** pending those checks and storefront review.

## F-Droid Trixie CI-image verification — PASSED with Java 17 (21 Nov 2026)

[GitHub Actions run 44897086974](https://github.com/chrober/lyrion-display/actions/runs/44897086974)
reproduced the **exact already-published v0.3.0** in the F-Droid
`registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie` CI container
with **Temurin Java 17 explicitly mounted into the container**.
The source tree was checked out from its pinned tag
(`fb4cb6359e1931519f586d6602c25e9b95e51e03`) in a normal clone.

- F-Droid source scanner and `fdroid build --test --force`: **1 build succeeded**.
- APK integrity/certificate check of the already-published GitHub release: **passed**.
- Rebuilt unsigned APK SHA-256:
  `4de2f0deaa2ad9edc7cff597ba60af599122a5183bc348b3107a4c6c902e5e88`.
  This is identical to the previous successful exact-release Ubuntu build.
- ZIP entry inspection: **0 differing common unsigned-content entries**;
  the signed APK has only its three additional legacy signature entries.
- `apksigcopier compare --unsigned`: **passed**.

**Why Java 17 matters:** the unmodified Trixie CI image provides Java 21,
which built the app but produced a different `classes.dex` (all other
common APK entries were equal). With that output,
`apksigcopier compare --unsigned` **failed**.
The successful CI-image test provided Java 17 from the GitHub runner and
included its CA trust store in a read-only mount. This is a deliberate
experimental toolchain override, **not** evidence that the official
production F-Droid buildserver itself supplies Java 17.

Before submitting, establish which official F-Droid buildserver image,
JDK and Android toolchain will be used, and demonstrate that the **actual
supported F-Droid build recipe** can select Java 17 and reproduce this APK.
If the production environment cannot provide Java 17, further recipe/toolchain
work is needed. Never silently switch signing keys to bypass reproducibility.

## Production-compatible Java 17 build metadata (disabled draft)

F-Droid's [Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/)
supports `Builds[].sudo` in a **disposable buildserver VM**. Its
[reproducible-build guide](https://f-droid.org/docs/Reproducible_Builds/)
lists Java 17 from Debian Bookworm as supported alongside Java 21 from
Trixie. This is a recognized production technique and not an invented
metadata key; official `fdroiddata` recipes already use it.

The single disabled v0.3.0 build now specifies:

```yaml
    sudo:
      - echo "deb https://deb.debian.org/debian bookworm main" > /etc/apt/sources.list.d/bookworm.list
      - apt-get update
      - apt-get install -y -t bookworm openjdk-17-jdk-headless
      - update-java-alternatives -s java-1.17.0-openjdk-amd64
    prebuild:
      - echo "org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64" >> ../gradle.properties
```

The `prebuild` property selects the actual Gradle daemon JDK, independent
of the parent shell's `JAVA_HOME`. It modifies only the temporary source
checkout, **not** the v0.3.0 tag or GitHub release.

`sudo` runs automatically only in F-Droid **on-server** builds; ordinary
`fdroid build --test --force` on a local runner does *not* install packages.
Our dedicated PR QA workflow compensates by reading the validated `sudo`
commands from the draft metadata and executing them as root inside a
throwaway F-Droid Trixie CI container, followed by the exact source build
as the unprivileged `vagrant` user. It verifies the signed APK SHA-256,
developer certificate and `apksigcopier compare --unsigned`.

The application-wide `Binaries` and `AllowedAPKSigningKeys` fields are
now populated for the **existing** v0.3.0 GitHub release, but its only
`Builds` entry remains disabled; no upload/submission/update occurs.
The signing certificate is verified against the real published release.
`tools/check_fdroid_recipe.py` fails closed if the draft is activated,
privileged commands change, the JDK override disappears, the pinned tag
changes or signing metadata drifts.

**Important:** this test approximates the official buildserver's supported
sudo recipe, but does not itself execute an F-Droid production VM job or
constitute F-Droid acceptance. Java 17 availability may change as Debian
Bookworm ages. Prior to submission, repeat in the then-current official
environment and secure a human metadata/licensing review.

## Build expectations

- `gradle/wrapper/gradle-wrapper.properties` pins Gradle **9.9.1** for F-Droid's
  `gradlew-fdroid`. We do not bundle any wrapper JAR or fetched Gradle binary.
- Android Gradle Plugin **9.5.1**, JDK **17**, Android SDK platform 35.
- The release APK must be unsigned when no `ANDROID_KEYSTORE_PATH` is set;
  GitHub Actions signs its published APK with the existing private production key.
- The disabled recipe uses `gradle: [yes]` and `subdir: app`.
- During QA, `fdroid build --test --force com.chrober.lmsmaterial.nowplaying`
  can exercise the disabled recipe; do **not** use `--force` for publication.

## Verified local source-build preflight (20 Nov 2026)

We ran the **actual fdroidserver 2.4.5** metadata reader, linter,
source scanner and `fdroid build --test --force` on GitHub-hosted Ubuntu.
The disabled recipe at source commit `35b1749c9d4036ec765aaee51b7dfa2de43ed8a1`
succeeded: `1 build succeeded`, producing
`tmp/com.chrober.lmsmaterial.nowplaying_8.apk` (unsigned).
[CI evidence](https://github.com/chrober/lyrion-display/actions/runs/44760666259).

The pip-installed fdroidserver package does not contain its own
`gradlew-fdroid`, so for this **local** preflight we explicitly configured
Gradle 9.9.1 from GitHub Actions. The official F-Droid buildserver image
has its own Gradle tooling and still needs independent testing.

An archive entry comparison between the older signed PR #23 QA APK
(source revision `12b5f81`) and this F-Droid-built APK showed that
all common APK entries have matching uncompressed contents **except**
`META-INF/version-control-info.textproto`, which contains the expected
different Git revision. The signed APK also contains three v1-signature
entries missing from the unsigned APK. **This does not prove reproducibility**:
the two APKs have different source revisions, and a proper same-revision,
signature-aware comparison is required.

## Local QA reproducibility verified (20 Nov 2026)

A [controlled GitHub Actions test](https://github.com/chrober/lyrion-display/actions/runs/44764689613)
built the **same exact QA source commit**
`35b1749c9d4036ec765aaee51b7dfa2de43ed8a1` twice:

1. `fdroidserver 2.4.5` ran source scanning and its Gradle build with
   an ordinary git checkout.
2. A separate ordinary git clone ran Gradle 9.9.1 against the identical
   commit, with no signing credentials.

**The unsigned APKs matched byte-for-byte**, SHA-256:

`824b6eae13cc1cbd1c9a82d0feb35f15c8ab46817908bf3a23518a49738898ee`

The second APK was signed using **the existing production keystore** and
Android SDK `apksigner` from build-tools **34.0.0**. The command
`apksigcopier compare --unsigned signed.apk fdroid-unsigned.apk` **passed**.
This shows that local, source-built APKs can preserve the original
developer-signing certificate without changing the app ID.

**Important limitations:**

- This is a QA snapshot with `versionCode 8`, *not* the published
  `v0.2.0` tag. It must never be submitted as that release.
- A GitHub-hosted Ubuntu preflight is not the official F-Droid buildserver
  image. Real F-Droid environment and the exact next tagged release must
  be tested again.
- Signing with Android SDK build-tools **35.0.0** failed
  `apksigcopier` verification due to its APK Signing Block layout, as
  [documented by F-Droid](https://f-droid.org/en/docs/Reproducible_Builds/).
  For a future developer-signed F-Droid release, adapt the GitHub **release
  workflow** to sign with build-tools **34.0.0** and verify with
  `apksigcopier`. Do not modify or republish `v0.2.0`.
- Android Gradle Plugin embeds Git revision information in
  `META-INF/version-control-info.textproto`. Use a **normal git clone**,
  not `git worktree`, for independent builds. Our worktree experiment
  produced `NO_VALID_GIT_FOUND` and was not reproducible.
- None of these experiments uploaded, released or submitted an APK
  to F-Droid. The only packaged candidate in this branch remains disabled.

## Reproducible upstream signing (locally verified; official release pending)

To preserve in-place updates from the GitHub-signed APK, the official F-Droid metadata
can ultimately specify, **only once the new tagged release is verified**:

```yaml
# DO NOT activate until an independent F-Droid build was proven byte-for-byte
# equivalent to the unsigned contents of the exact GitHub release APK.
Binaries: https://github.com/chrober/lyrion-display/releases/download/v%v/lyrion-display-v%v.apk
AllowedAPKSigningKeys:
  - af624ba3a730a5bf4a9d0b77e2bed2ec557744705362d4a4445e6dcb125f1dfb
```

The certificate digest above comes from the established production key and must
be checked against the **exact first published F-Droid release candidate** before enabling it.

F-Droid checks reproducibility by copying the upstream signature onto
its independently built unsigned APK and verifying the result. Use
`apksigcopier compare --unsigned signed.apk unsigned.apk` for a preliminary
comparison. This works only when source, build tools and output bytes agree
(except for signing). Passing an independent GitHub build does **not** prove
that F-Droid's own build environment matches.

Without reproducible upstream signing, standard F-Droid signing uses a different
certificate. **Switching between the GitHub and F-Droid APKs would then require
uninstalling the app and losing its saved configuration.**

## Repeatable QA verification without submission

The permanently **disabled** recipe pins the exact existing GitHub v0.3.0 release (versionCode 9), not the earlier versionCode 8 QA snapshot. The validation tool `tools/check_fdroid_recipe.py` checks the tag/source identity and refuses an enabled recipe.

The workflow [F-Droid metadata and source-build preflight](../.github/workflows/fdroid-preflight.yml) provides these paths:

1. **Automatic on F-Droid-related PRs:** metadata parser/linter and an isolated test inside the F-Droid `registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie` CI container; the container installs Bookworm JDK 17 through the **actual validated recipe `sudo` commands**, then applies the recipe `prebuild` and compiles unprivileged. It reads source SHA, version and versionCode from the disabled recipe and compares the resulting unsigned APK against the existing, checksummed, developer-signed GitHub release using `apksigcopier`.
2. **Manual `source_build` input:** source scanner and `fdroid build --test --force` with pinned `fdroidserver 2.4.5` on GitHub-hosted Ubuntu, reproducing the previously successful test without hardcoded old QA commits.
3. **Manual `buildserver_image` input:** explicitly rerun the isolated official-recipe-style F-Droid CI container test without a PR.

GitHub Actions → **F-Droid metadata and source-build preflight (no publishing)** → **Run workflow**. Select `main` and enable only the intended test option(s). The local Ubuntu and CI-container jobs are independent: either can be selected without the other. A test success must be confirmed in its linked workflow run rather than inferred from the existence of the job.

These QA jobs have `contents: read` only, no signing secrets or GitLab token, do not change tags, do not activate the recipe, and **never submit or publish APKs**. Logs and checksum/certificate evidence are the only uploaded artifacts. The pre-existing signed GitHub APK is downloaded read-only solely for comparison.

**Scope limit:** F-Droid's buildserver **CI image** uses F-Droid's provisioning but is not proof that an actual official production build or metadata review has succeeded. That verification and a suitable final listing (genuine rights-cleared screenshots, attribution and policy review) are still required before any user-approved submission.

References:
- https://f-droid.org/en/docs/Submitting_to_F-Droid_Quick_Start_Guide/
- https://f-droid.org/en/docs/Build_Metadata_Reference/
- https://f-droid.org/en/docs/Reproducible_Builds/

## Source/licensing and branding

MIT licensed. The bundled colored waveform is adapted from the upstream
Material Android app and attributed under MIT, and Lyrion logo/name permission
was granted for free-and-open-source distribution as documented in
[BRANDING.md](BRANDING.md). Recheck external artwork permissions and storefront
screenshots before requesting inclusion.
