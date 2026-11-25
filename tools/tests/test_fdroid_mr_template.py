"""Offline regression coverage for the exact official template and approval gate."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fdroid_mr_template import (TEMPLATE_PATH, REQUIRED_ITEMS, render_description,
                               require_current_template, reviewed_numbers)

REVIEWED = ",".join(map(str, sorted(REQUIRED_ITEMS)))
EVIDENCE = "Human-reviewed active development history: https://example.org/history"


class TemplateTests(unittest.TestCase):
    def test_dry_run_preserves_all_sections_and_unchecked_items(self):
        body = render_description("v0.4.2", "new-app")
        template = TEMPLATE_PATH.read_text()
        for line in template.splitlines():
            if line.startswith("##") or line.startswith("* [ ]"):
                self.assertIn(line, body)
        self.assertEqual(28, body.count("* [ ]"))
        self.assertNotIn("* [x]", body)
        self.assertNotIn("Closes rfp#<RFP issue number>", body)
        self.assertIn("/v0.4.2/fastlane/", body)
        self.assertIn("/merge_requests/58796", body)

    def test_submit_requires_explicit_checklist_and_policy_evidence(self):
        for reviewed, evidence in [("", EVIDENCE), (REVIEWED, ""), (REVIEWED, "not a link"), (REVIEWED, EVIDENCE + "\n* [x] injected")]:
            with self.assertRaises(ValueError):
                render_description("v0.4.2", "new-app", reviewed, evidence, submitting=True)
        body = render_description("v0.4.2", "new-app", REVIEWED, EVIDENCE, submitting=True)
        self.assertEqual(len(REQUIRED_ITEMS), body.count("* [x]"))
        self.assertIn("* [ ] All pipelines should pass.", body)
        self.assertIn("* [ ] All warnings and errors", body)
        self.assertIn(EVIDENCE, body)

    def test_invalid_or_premature_ticks_rejected(self):
        for items in ("26", "27", "29", "0", "1,1", "all"):
            with self.assertRaises(ValueError):
                reviewed_numbers(items)

    def test_template_changes_fail_closed(self):
        current = TEMPLATE_PATH.read_text(encoding="utf-8")
        require_current_template(current)
        for changed in (None, current + "\n"):
            with self.assertRaises(ValueError):
                require_current_template(changed)

    def test_update_uses_update_description_not_new_app_template(self):
        body = render_description("v0.4.2", "update", submitting=True)
        self.assertIn("Update Lyrion Display to v0.4.2", body)
        self.assertNotIn("## Checklist", body)
        with self.assertRaises(ValueError):
            render_description("v0.4.2", "update", REVIEWED, EVIDENCE, submitting=True)
