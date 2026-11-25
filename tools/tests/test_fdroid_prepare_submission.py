"""Fail-closed validation tests for manually prepared F-Droid metadata."""
import unittest
import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fdroid_prepare_submission import APP_ID, AUTHOR, build_metadata, canonical_yaml, validate_fastlane  # noqa: E402


class SubmissionMetadataTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original = yaml.safe_load(
            (Path(__file__).resolve().parents[2] / "fdroid/metadata" / (APP_ID + ".yml"))
            .read_text(encoding="utf-8"))
        cls.gradle = """applicationId 'com.chrober.lmsmaterial.nowplaying'
                       versionName '0.4.0'
                       versionCode 10"""

    def test_author_pseudonym_and_manual_release(self):
        original = yaml.safe_dump(self.original)
        item = build_metadata(self.original, "v0.4.0", "a" * 40, self.gradle)
        self.assertEqual(AUTHOR, item["AuthorName"])
        self.assertEqual("Christoph O'Bermair", item["AuthorName"])
        self.assertEqual("None", item["AutoUpdateMode"])
        self.assertEqual("Static", item["UpdateCheckMode"])
        self.assertEqual(10, item["Builds"][0]["versionCode"])
        self.assertNotIn("disable", item["Builds"][0])
        self.assertEqual("a" * 40, item["Builds"][0]["commit"])
        self.assertTrue(any("openjdk-25-jdk-headless" in command
                            for command in item["Builds"][0]["sudo"]))
        self.assertEqual("0.3.0", self.original["Builds"][0]["versionName"])
        self.assertEqual(original, yaml.safe_dump(self.original))

    def test_fdroid_canonical_metadata_is_stable(self):
        import shutil
        if not shutil.which("fdroid"):
            self.skipTest("fdroidserver not installed")
        item = build_metadata(self.original, "v0.4.0", "a" * 40, self.gradle)
        item["Summary"] = "Fullscreen Now Playing display for Lyrion Music Server"
        item["Description"] = (
            "This is an independently built app.\n\n"
            "The long description uses normal paragraphs."
        )
        text = canonical_yaml(item)
        self.assertIn("    versionCode: 10", text)
        self.assertIn("Description: |-", text)
        self.assertIn("AllowedAPKSigningKeys:", text)
        self.assertEqual(text, canonical_yaml(item))

    def test_listing_copy_is_not_in_submission_metadata(self):
        source = dict(self.original, Summary="short", Description="long")
        item = build_metadata(source, "v0.4.0", "a" * 40, self.gradle)
        self.assertNotIn("Summary", item)
        self.assertNotIn("Description", item)
        self.assertEqual("short", source["Summary"])

    def test_upstream_english_listing_and_screenshots(self):
        validate_fastlane(Path(__file__).resolve().parents[2])

    def test_bad_tag_version_sha_or_app_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "tag"):
            build_metadata(self.original, "0.4.0", "a" * 40, self.gradle)
        with self.assertRaisesRegex(ValueError, "SHA"):
            build_metadata(self.original, "v0.4.0", "main", self.gradle)
        with self.assertRaisesRegex(ValueError, "versionName"):
            build_metadata(self.original, "v0.4.1", "a" * 40, self.gradle)
        with self.assertRaisesRegex(ValueError, "application ID"):
            build_metadata(self.original, "v0.4.0", "a" * 40,
                           self.gradle.replace("com.chrober", "com.untrusted"))

    def test_never_change_signing_or_enable_autoupdate(self):
        for key, value in [("Binaries", "https://example.org/bad.apk"),
                           ("AllowedAPKSigningKeys", ["bad"]),
                           ("AutoUpdateMode", "Version %v")]:
            b = dict(self.original, **{key: value})
            with self.subTest(key=key), self.assertRaises(ValueError):
                build_metadata(b, "v0.4.0", "a" * 40, self.gradle)


if __name__ == "__main__":
    unittest.main()
