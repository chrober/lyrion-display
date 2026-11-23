"""Pure unit coverage; test suite never makes a GitLab API call."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fdroid_submit_gitlab import merge_upstream, require_clean_ancestor  # noqa: E402
from fdroid_prepare_submission import APP_ID, AUTHOR, BINARY_URL, REPO_URL, SIGNING_CERT


class GitLabMetadataTests(unittest.TestCase):
    def setUp(self):
        self.new = {
            "AuthorName": AUTHOR,
            "Repo": REPO_URL,
            "Binaries": BINARY_URL,
            "AllowedAPKSigningKeys": [SIGNING_CERT],
            "AutoUpdateMode": "None", "UpdateCheckMode": "Static",
            "Builds": [{"versionName": "0.4.0", "versionCode": 10, "commit": "a" * 40}],
            "CurrentVersion": "0.4.0", "CurrentVersionCode": 10,
        }

    def test_first_app_creates_only_selected_release(self):
        action, doc = merge_upstream(None, self.new)
        self.assertEqual("create", action)
        self.assertEqual(1, len(doc["Builds"]))

    def test_existing_app_preserves_prior_released_versions(self):
        old = dict(self.new)
        old["Builds"] = [{"versionName": "0.3.0", "versionCode": 9}]
        action, doc = merge_upstream(old, self.new)
        self.assertEqual("update", action)
        self.assertEqual([9, 10], [x["versionCode"] for x in doc["Builds"]])
        self.assertEqual(AUTHOR, doc["AuthorName"])
        self.assertEqual(9, old["Builds"][0]["versionCode"])

    def test_duplicate_versions_and_unexpected_signers_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "already exists"):
            merge_upstream(self.new, self.new)
        old = dict(self.new, AllowedAPKSigningKeys=["bad-signing-key"])
        with self.assertRaisesRegex(ValueError, "signature policy"):
            merge_upstream(old, self.new)
        old = dict(self.new, AutoUpdateMode="Version %v")
        with self.assertRaisesRegex(ValueError, "not manual"):
            merge_upstream(old, self.new)


class ForkSynchronizationTests(unittest.TestCase):
    UP = "d" * 40
    FORK = "a" * 40

    def test_equal_heads_are_accepted(self):
        self.assertEqual(0, require_clean_ancestor(self.UP, self.UP, {}, {}))

    def test_clean_behind_fork_is_accepted(self):
        # Four upstream-only commits are not a reason to refuse an MR.
        upstream_to_fork = {"compare_timeout": False, "commits": []}
        fork_to_upstream = {"compare_timeout": False, "commits": [
            {"id": "b" * 40}, {"id": "c" * 40}, {"id": "e" * 40},
            {"id": self.UP},
        ]}
        self.assertEqual(4, require_clean_ancestor(
            self.UP, self.FORK, upstream_to_fork, fork_to_upstream))

    def test_fork_with_unique_history_is_rejected(self):
        forward = {"compare_timeout": False, "commits": [{"id": self.UP}]}
        reverse = {"compare_timeout": False, "commits": [{"id": self.FORK}]}
        with self.assertRaisesRegex(ValueError, "not present in official upstream"):
            require_clean_ancestor(self.UP, self.FORK, reverse, forward)

    def test_unverified_or_partial_compare_is_rejected(self):
        clean_reverse = {"compare_timeout": False, "commits": []}
        for forward in (
            {"compare_timeout": True, "commits": [{"id": self.UP}]},
            {"compare_timeout": False},
            {"compare_timeout": False, "commits": []},
            {"compare_timeout": False, "commits": [{"id": "b" * 40}]},
        ):
            with self.assertRaises(ValueError):
                require_clean_ancestor(self.UP, self.FORK, clean_reverse, forward)


if __name__ == "__main__":
    unittest.main()
