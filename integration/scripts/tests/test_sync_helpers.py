# SPDX-License-Identifier: Apache-2.0
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

artifact_sync = load("sync_published_artifacts", ROOT / "integration/scripts/sync_published_artifacts.py")
source_sync = load("sync_source_pins", ROOT / "integration/scripts/sync_source_pins.py")
fetch_artifact = load("fetch_module_artifact", ROOT / "integration/scripts/fetch_module_artifact.py")

class ArtifactSyncTests(unittest.TestCase):
    def test_semver_and_repository_scope(self):
        self.assertEqual((0, 3, 2), artifact_sync.semver("v0.3.2"))
        self.assertIsNone(artifact_sync.semver("v0.3.2-beta.1"))
        self.assertEqual(set(artifact_sync.EXPECTED_COMPONENTS), {"zairenkai-core", "zairenkai-fas", "zairenkai-tuning"})
        self.assertEqual(set(source_sync.DIRECT), {"zart", "zthermal", "afid-device-candidates", "zie-run"})

    def test_rejects_foreign_or_unpinned_manifest(self):
        manifest = {
            "schema_version": 1,
            "repository": "attacker/other",
            "release_tag": "v0.3.2",
            "module_version": "0.3.2",
            "source_commit": "a" * 40,
            "components": [],
            "assets": [],
        }
        with self.assertRaises(SystemExit):
            artifact_sync.validate_manifest({"tag_name": "v0.3.2"}, manifest, b"{}\n")

    def test_digest_is_stable_and_sha_format_checked(self):
        payload = b"zairenkai-artifact-test"
        self.assertEqual(hashlib.sha256(payload).hexdigest(), artifact_sync.sha256(payload))
        self.assertEqual(64, len(fetch_artifact.digest(payload)))

    def test_asset_names_are_version_scoped(self):
        self.assertIsNotNone(artifact_sync.ASSET_NAME.fullmatch("zairenkai-v0.3.2.zip"))
        self.assertIsNone(artifact_sync.ASSET_NAME.fullmatch("../../unsafe.zip"))

    def test_source_fallback_module_version_must_match_app_lock(self):
        source_lock = {
            "module": {
                "id": "zairenkai",
                "repository": "ZairenkaiPrjctX/Zairenkai-Modules",
                "version": "0.3.2",
            }
        }
        fetch_artifact.validate_source_lock(source_lock, "0.3.2")
        with self.assertRaises(SystemExit):
            fetch_artifact.validate_source_lock(source_lock, "0.3.1")

if __name__ == "__main__":
    unittest.main()
