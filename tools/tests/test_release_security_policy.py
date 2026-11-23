"""Static release policy regression checks; no Android SDK or network required."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class ReleaseSecurityPolicyTests(unittest.TestCase):
    def test_permissions_are_least_privilege(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = {
            element.attrib[ANDROID + "name"]
            for element in manifest.findall("uses-permission")
        }
        self.assertEqual({
            "android.permission.INTERNET",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        }, permissions)

    def test_http_explicit_and_documented(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = manifest.find("application")
        self.assertEqual("true", app.attrib[ANDROID + "usesCleartextTraffic"])
        doc = (ROOT / "docs/SECURITY_NETWORK.md").read_text(encoding="utf-8")
        self.assertIn('android:usesCleartextTraffic="true"', doc)
        self.assertIn("HTTP is not encrypted", doc)

    def test_r8_only_release_and_api23_guard_kept(self):
        build = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
        release = build.split("release {", 1)[1].split("signingConfig", 1)[0]
        self.assertRegex(release, r"minifyEnabled\s+true")
        self.assertIn("proguard-android-optimize.txt", release)
        self.assertIn("'proguard-rules.pro'", release)
        rules = (ROOT / "app/proguard-rules.pro").read_text(encoding="utf-8")
        self.assertIn("WebViewApi23 { *; }", rules)
        self.assertIn("WebViewApi23$* { *; }", rules)
        self.assertNotRegex(rules, r"(?m)^-(?:dontobfuscate|dontoptimize)\b")


if __name__ == "__main__":
    unittest.main()
