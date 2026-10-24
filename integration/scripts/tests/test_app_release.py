# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""integration/scripts/app_release.py: the version and notes the release workflow publishes."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import app_release  # noqa: E402

LOG = """# Changelog

## [Unreleased]

## [0.3.0-beta.2] - 2026-11-02
### Fixed
- A thing

## [0.2.0] - 2026-10-30
- Released
"""


class AppReleaseTest(unittest.TestCase):
    def test_version_from_the_build_file(self):
        self.assertEqual(app_release.version('x\nval appVersion = "0.3.0-beta.2"\n'), "0.3.0-beta.2")
        for bad in ('val appVersion = "0.3"', 'val appVersion = "0.3.0-rc.1"', 'val appVersion = "0.3.0-beta.0"', "nothing"):
            with self.assertRaises(ValueError):
                app_release.version(bad)

    def test_notes_are_the_versions_entry(self):
        self.assertEqual(app_release.notes(LOG, "0.3.0-beta.2"), "### Fixed\n- A thing\n")
        self.assertEqual(app_release.notes(LOG, "0.2.0"), "- Released\n")
        with self.assertRaises(ValueError):
            app_release.notes(LOG, "0.2.1")
        with self.assertRaises(ValueError):
            app_release.notes(LOG, "Unreleased")
        # 0.2.0 must not match 0.2.0-beta.1 or 0.2.01.
        self.assertNotIn("A thing", app_release.notes(LOG, "0.2.0"))

    def test_the_repository_is_releasable(self):
        root = Path(__file__).resolve().parents[3]
        v = app_release.version((root / "app" / "build.gradle.kts").read_text())
        self.assertTrue(app_release.notes((root / "CHANGELOG.md").read_text(), v).strip())


if __name__ == "__main__":
    unittest.main()
