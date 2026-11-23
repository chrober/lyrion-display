#!/usr/bin/env bash
# Called inside an active Android emulator on CI. No production signing keys.
# Use an ephemeral, throw-away certificate to test the ACTUAL R8 release APK.
set -euo pipefail
package=com.chrober.lmsmaterial.nowplaying
source_apk=app/build/outputs/apk/release/app-release-unsigned.apk
mapping=app/build/outputs/mapping/release/mapping.txt

gradle --no-daemon :app:assembleRelease
test -s "$source_apk"
test -s "$mapping"

temp="$(mktemp -d)"
trap 'rm -rf -- "$temp"' EXIT
password="ci-only-test-key"
keytool -genkeypair -noprompt -alias lyrion-ci-release \
  -keyalg RSA -keysize 2048 -validity 1 -storetype PKCS12 \
  -dname 'CN=CI-only, O=Not for distribution' \
  -keystore "$temp/ci-only.p12" -storepass "$password" -keypass "$password"

apksigner="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name apksigner | sort -V | tail -1)"
test -x "$apksigner"
"$apksigner" sign \
  --ks "$temp/ci-only.p12" --ks-key-alias lyrion-ci-release \
  --ks-pass "pass:$password" --key-pass "pass:$password" \
  --out "$temp/minified-release-ci-only.apk" "$source_apk"
"$apksigner" verify "$temp/minified-release-ci-only.apk"

# Debug and release APKs have the same applicationId but different keys.
adb uninstall "$package" >/dev/null 2>&1 || true
adb install "$temp/minified-release-ci-only.apk"
adb logcat -c
adb shell am start -W -n "$package/.MainActivity" | tee "$temp/launch.txt"
grep -Fq 'Status: ok' "$temp/launch.txt"
sleep 5
# 'pidof' is not available on every old Android/Fire OS image.
if ! adb shell ps | grep -F "$package"; then
  echo "::error::Minified release app process is not alive after launch"
  adb logcat -d -t 200
  exit 1
fi
echo "PASS: CI-only signed minified release launches on this Android API."
