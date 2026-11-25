"""Pure unit coverage; test suite never makes a GitLab API call."""
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fdroid_submit_gitlab import (
    YAML_READER, merge_upstream, require_clean_ancestor, submit_to_gitlab, FILE,
)  # noqa: E402
from fdroid_prepare_submission import APP_ID, AUTHOR, BINARY_URL, REPO_URL, SIGNING_CERT
from fdroid_mr_template import REQUIRED_ITEMS, TEMPLATE_PATH, render_description


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

    def mocked_new_submission(self, template_text):
        reviewed = ",".join(map(str, sorted(REQUIRED_ITEMS)))
        evidence = "Reviewed active development: https://example.org/history"
        description = render_description("v0.4.0", "new-app", reviewed, evidence, True)
        answers = [
            {"path_with_namespace": "fdroid/fdroiddata", "id": 1, "default_branch": "master"},
            {"forked_from_project": {"id": 1}, "visibility": "public", "default_branch": "master"},
            {"commit": {"id": "a" * 40}}, {"commit": {"id": "a" * 40}},
            None, {}, {"web_url": "https://gitlab.com/example/merge_requests/1"},
        ]
        with patch("fdroid_submit_gitlab.api_call", side_effect=answers) as api, \
             patch("fdroid_submit_gitlab.read_upstream_file", side_effect=[None, template_text]), \
             patch("fdroid_submit_gitlab.canonical_yaml", return_value="canonical metadata\n"):
            if template_text != TEMPLATE_PATH.read_text(encoding="utf-8"):
                with self.assertRaisesRegex(ValueError, "template changed"):
                    submit_to_gitlab("fake-token", 2, "v0.4.0", self.new,
                                     "new-app", reviewed, evidence, description)
                self.assertFalse(any(call.args[1] == "POST" for call in api.call_args_list))
                return
            url = submit_to_gitlab("fake-token", 2, "v0.4.0", self.new,
                                   "new-app", reviewed, evidence, description)
            self.assertEqual("https://gitlab.com/example/merge_requests/1", url)
            posts = [call.args[3] for call in api.call_args_list if call.args[1] == "POST"]
            self.assertEqual(2, len(posts))
            self.assertEqual([{"action": "create", "file_path": FILE,
                               "content": "canonical metadata\n"}], posts[0]["actions"])
            self.assertEqual("New app: Lyrion Display", posts[1]["title"])
            self.assertEqual(description, posts[1]["description"])

    def test_new_app_posts_only_yaml_and_official_description(self):
        self.mocked_new_submission(TEMPLATE_PATH.read_text(encoding="utf-8"))

    def test_changed_upstream_template_blocks_all_writes(self):
        self.mocked_new_submission("changed template")

    def test_unreviewed_description_blocks_api_access(self):
        with patch("fdroid_submit_gitlab.api_call") as api:
            with self.assertRaises(ValueError):
                submit_to_gitlab("fake-token", 2, "v0.4.0", self.new,
                                 "new-app", "", "", "")
            api.assert_not_called()

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

    def test_fdroid_yaml_12_preserves_gradle_yes_as_string(self):
        doc = YAML_READER.load("Builds:\n  - gradle:\n      - yes\n")
        self.assertEqual(["yes"], doc["Builds"][0]["gradle"])

    def test_canonical_scalar_signer_is_accepted_for_next_release(self):
        old = dict(self.new)
        old["AllowedAPKSigningKeys"] = SIGNING_CERT
        old["Builds"] = [{"versionName": "0.3.0", "versionCode": 9}]
        action, updated = merge_upstream(old, self.new)
        self.assertEqual("update", action)
        self.assertEqual([9, 10], [b["versionCode"] for b in updated["Builds"]])

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
