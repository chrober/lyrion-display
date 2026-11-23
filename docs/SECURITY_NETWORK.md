# Network and release security

This document records intentional trade-offs for **Lyrion Display**. It does
not modify the already published v0.4.0 tag or APK. Future releases must be
built and tested from their own immutable source tag.

## Cleartext HTTP: intentional, not an accidental fallback

The application supports Lyrion Music Server with Material Skin on a server
selected by the user, through either UDP discovery or a manually supplied
HTTP/HTTPS server origin. Standard local Lyrion installations commonly serve
Material Skin over **http://<server>:9000**. The app therefore retains

`android:usesCleartextTraffic="true"`

in its Android manifest. The Android manifest cannot express a dynamic
allowlist for arbitrary user-configured IP addresses and hostnames. Replacing
it with a hard-coded Network Security Config allowlist would break legitimate
installations; simply disabling HTTP would break many local servers. Craig
Drummond's independently maintained Material Skin Android client also enables
this flag in its F-Droid-published 0.10.1 source.

**Security implications:** HTTP is not encrypted or authenticated by TLS.
Someone with a suitable position on the network might intercept or alter
server responses. This is a deliberate compatibility trade-off, not an
assertion that every LAN is trusted. Users should prefer a trusted home LAN
or a properly configured HTTPS endpoint when practical. Do not enter
sensitive credentials into a server over HTTP. The application does not
currently support authenticated Lyrion servers.

The WebView's main-frame navigation is constrained to the configured server
origin and Material Skin path by `ServerAddress.isAllowedMaterialNavigation`;
this is an independent navigation restriction, **not** transport encryption.
User-specified HTTP servers are not limited to private address ranges.

## Minimal Android manifest permissions

| Permission | Decision | Reason |
| --- | --- | --- |
| `INTERNET` | Keep | HTTP/HTTPS requests to a user-selected Lyrion server |
| `CHANGE_WIFI_MULTICAST_STATE` | Keep | Temporarily acquire and release `WifiManager.MulticastLock` while receiving UDP eIPAD discovery replies |
| `ACCESS_WIFI_STATE` | Remove in the next release | The app never queries Wi-Fi connection, scan, or configuration state; discovery uses UDP sockets and the multicast lock |

Server discovery is best-effort and manual server entry remains available.
Future changes to the discovery mechanism must reassess these permissions.
A CI gate inspects the **built release APK**, not only the source manifest.

## Release optimization (R8)

The next release enables R8 code shrinking and obfuscation with Android's
`proguard-android-optimize.txt` defaults and a narrowly scoped
`app/proguard-rules.pro`. The API 23 WebView visual-state callback wrapper
is kept intact to avoid R8 merging its API 23 calls into the API 22 path.
No broad `-dontobfuscate`/`-dontoptimize` exceptions are used.

CI must build the unsigned R8 release, confirm its mapping file, and compare
the APK byte-for-byte with an independently rebuilt unsigned APK using the
F-Droid Trixie/Java 25 pipeline. Android API 22, 25, 29, and 35 emulator jobs
also run instrumentation tests and temporarily sign/install/start an R8
release build using a **CI-only ephemeral key**. That CI key is never used
for publication. Real Fire OS/TV remote and server-discovery testing must
still be performed before publishing any future release.

## Reference

- [Craig Drummond Android manifest, F-Droid 0.10.1 source](https://github.com/CDrummond/lms-material-app/blob/0a87cf28e8e88faaf4909c8f3c820c3e79f9787a/lms-material/src/main/AndroidManifest.xml)
- [Android Wi-Fi multicast lock documentation](https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock)
- [Android R8 keep rule guidance](https://developer.android.com/topic/performance/app-optimization/keep-rules-overview)
