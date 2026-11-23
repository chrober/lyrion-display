#!/usr/bin/env bash
# Check source reproducibility of the checked-out Android version against a
# second unsigned build in F-Droid Trixie using OpenJDK 25. Never publishes.
# No signing secrets/keystores are available inside Docker.
set -euo pipefail

: "$GITHUB_WORKSPACE"
: "$GITHUB_SHA"
test "$(git -C "$GITHUB_WORKSPACE" rev-parse HEAD)" = "$GITHUB_SHA"
gradle_file="$GITHUB_WORKSPACE/app/build.gradle"
version_name="$(sed -nE "s/^[[:space:]]*versionName[[:space:]]+'([^']+)'.*$/\1/p" "$gradle_file")"
version_code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]+([0-9]+).*$/\1/p' "$gradle_file")"
[[ "$version_name" =~ ^[0-9]+[.][0-9]+[.][0-9]+$ ]] || { echo "::error::Invalid versionName"; exit 1; }
[[ "$version_code" =~ ^[0-9]+$ ]] || { echo "::error::Invalid versionCode"; exit 1; }
grep -Eq '^[[:space:]]*minSdk[[:space:]]+22$' "$gradle_file"
grep -Fq "applicationId 'com.chrober.lmsmaterial.nowplaying'" "$gradle_file"
echo "Source-build check: v$version_name (versionCode $version_code)"

unsigned="$GITHUB_WORKSPACE/app/build/outputs/apk/release/app-release-unsigned.apk"
test -s "$unsigned"

# Preserve the historical F-Droid v0.3.0 recipe.
grep -Fq "versionName: '0.3.0'" "$GITHUB_WORKSPACE/fdroid/metadata/com.chrober.lmsmaterial.nowplaying.yml"
grep -Fq 'java-17-openjdk-amd64' "$GITHUB_WORKSPACE/fdroid/metadata/com.chrober.lmsmaterial.nowplaying.yml"
grep -Fq 'disable:' "$GITHUB_WORKSPACE/fdroid/metadata/com.chrober.lmsmaterial.nowplaying.yml"

qa="$(mktemp -d)"
fdroidserver_dir="$qa-fdroidserver"
trap 'sudo rm -rf -- "$qa" "$fdroidserver_dir"' EXIT
chmod 755 "$qa"
mkdir -p "$qa/metadata" "$qa/build" "$qa/tmp"
# The repository declares its custom F-Droid categories in config/categories.yml;
# without this the independent fdroid lint cannot resolve "Multimedia".
cp -R "$GITHUB_WORKSPACE/fdroid/config" "$qa/config"

# The repository URL MUST match metadata Repo or fdroidserver deletes the
# clone and fetches a new one, losing the exact PR commit. Fetch the synthetic
# PR merge ref explicitly when it is not already on the default branch.
source_checkout="$qa/build/com.chrober.lmsmaterial.nowplaying"
git clone --quiet --no-checkout https://github.com/chrober/lyrion-display.git "$source_checkout"
if ! git -C "$source_checkout" cat-file -e "$GITHUB_SHA^{commit}" 2>/dev/null; then
  if [[ "$GITHUB_REF" =~ ^refs/pull/[0-9]+/merge$ ]]; then
    git -C "$source_checkout" fetch --quiet origin "$GITHUB_REF"
  else
    git -C "$source_checkout" fetch --quiet origin "$GITHUB_SHA"
  fi
fi
git -C "$source_checkout" checkout --quiet "$GITHUB_SHA"
test "$(git -C "$source_checkout" rev-parse HEAD)" = "$GITHUB_SHA"
# fdroidserver uses this marker to prove the pre-seeded Git checkout belongs
# to the requested VCS URL. Without it, it deletes the checkout and loses the
# PR-only synthetic merge commit before the build even starts.
printf '%s\n' 'git https://github.com/chrober/lyrion-display.git' \
  > "$qa/build/.fdroidvcs-com.chrober.lmsmaterial.nowplaying"

# F-Droid metadata is TEMPORARY and not committed/submitted/published.
# The v0.3.0 recipe and its Java 17 configuration are never modified.
cat > "$qa/metadata/com.chrober.lmsmaterial.nowplaying.yml" <<EOF
Categories:
  - Multimedia
License: MIT
SourceCode: https://github.com/chrober/lyrion-display
RepoType: git
Repo: https://github.com/chrober/lyrion-display.git
Builds:
  - versionName: '$version_name'
    versionCode: $version_code
    commit: $GITHUB_SHA
    disable: 'Internal QA only, not submitted or published'
    subdir: app
    sudo:
      - apt-get update
      - apt-get install -y openjdk-25-jdk-headless
      - update-java-alternatives -a
    prebuild:
      - echo "org.gradle.java.home=/usr/lib/jvm/java-25-openjdk-amd64" >> ../gradle.properties
    gradle:
      - 'yes'
AutoUpdateMode: None
UpdateCheckMode: Static
CurrentVersion: '$version_name'
CurrentVersionCode: $version_code
EOF

cat > "$qa/build-qa.sh" <<'SH'
#!/bin/bash
set -euo pipefail
. /etc/profile.d/bsenv.sh
export PATH="$fdroidserver:$PATH" PYTHONPATH="$fdroidserver"
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
java -version
git config --global --add safe.directory /home/vagrant/fdroidserver
git config --global --add safe.directory /build/build/com.chrober.lmsmaterial.nowplaying
fdroid readmeta
fdroid lint com.chrober.lmsmaterial.nowplaying
fdroid build --test --force com.chrober.lmsmaterial.nowplaying 2>&1 | tee qa-source-build.log
test -s "tmp/com.chrober.lmsmaterial.nowplaying_${VERSION_CODE}.apk"
SH

git clone --quiet --depth=1 https://gitlab.com/fdroid/fdroidserver.git "$fdroidserver_dir"
chmod -R a+rX "$fdroidserver_dir"
chmod -R a+rwX "$qa"
docker run --rm --user root --entrypoint /bin/bash \
  -v "$qa:/build" -w /build \
  -v "$fdroidserver_dir:/home/vagrant/fdroidserver:ro" \
  -e VERSION_CODE="$version_code" \
  registry.gitlab.com/fdroid/fdroidserver:buildserver-trixie \
  -e -u -o pipefail -c '
    apt-get update
    apt-get install -y openjdk-25-jdk-headless
    update-java-alternatives -a
    test -x /usr/lib/jvm/java-25-openjdk-amd64/bin/java
    /usr/lib/jvm/java-25-openjdk-amd64/bin/java -version
    runuser -u vagrant -- env HOME=/home/vagrant VERSION_CODE="$VERSION_CODE" /bin/bash /build/build-qa.sh
  '

fdroid_unsigned="$qa/tmp/com.chrober.lmsmaterial.nowplaying_${version_code}.apk"
test -s "$fdroid_unsigned"
echo "Runner unsigned APK:"
sha256sum "$unsigned"
echo "F-Droid Trixie Java 25 unsigned APK:"
sha256sum "$fdroid_unsigned"
if ! cmp -s "$unsigned" "$fdroid_unsigned"; then
  python3 - "$unsigned" "$fdroid_unsigned" <<'PY'
import hashlib
import sys
import zipfile
with zipfile.ZipFile(sys.argv[1]) as runner, zipfile.ZipFile(sys.argv[2]) as fdroid:
    a, b = set(runner.namelist()), set(fdroid.namelist())
    print("Runner-only entries:", sorted(a - b))
    print("F-Droid-only entries:", sorted(b - a))
    for name in sorted(a & b):
        if runner.read(name) != fdroid.read(name):
            print("DIFFERENT:", name,
                  hashlib.sha256(runner.read(name)).hexdigest(),
                  hashlib.sha256(fdroid.read(name)).hexdigest())
PY
  echo "::error::Runner and F-Droid Java 25 unsigned APK bytes differ. Do not sign/distribute RC."
  exit 1
fi
# Optional: the manual F-Droid workflow also compares the same independent
# unsigned source build against the *published, developer-signed* release APK.
# By default keep nothing, so the existing RC/QA behavior remains unchanged.
if [[ -n "${FDROID_QA_UNSIGNED_OUT:-}" ]]; then
  mkdir -p "$(dirname "$FDROID_QA_UNSIGNED_OUT")"
  cp "$fdroid_unsigned" "$FDROID_QA_UNSIGNED_OUT"
fi
echo "REPRODUCIBLE=true; unsigned APK byte-for-byte identical." | tee "$GITHUB_WORKSPACE/qa-fdroid-reproducibility-result.txt"
if [[ -n "$GITHUB_STEP_SUMMARY" ]]; then
  echo "### v$version_name Java 25 source reproducibility: PASS" >> "$GITHUB_STEP_SUMMARY"
fi
