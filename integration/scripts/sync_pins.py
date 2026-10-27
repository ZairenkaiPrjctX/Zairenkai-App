#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""Synchronize App lock/inventory after Modules and Intelligence Engine PRs merge.

Run only with real, merged commit SHAs from the canonical repositories. Published module
artifacts are handled separately by sync_published_artifacts.py, which validates the release
manifest, component locks, exact ZIP SHA-256 and byte count before setting a release-artifact pin.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCK_PATH = ROOT / "integration/repository-lock.json"
INVENTORY_PATH = ROOT / "integration/organization-repositories.json"
CORE_REPO = "Zairenkai-Core"
MODULES_REPO = "Zairenkai-Modules"
ENGINE_REPO = "Zairenkai-Intelligence-Engine"
CORE_COMMIT = "377ba4508321557f378d0188f05ff5c643ee62b4"
SHA1 = re.compile(r"^[0-9a-f]{40}$")
SEMVER = re.compile(r"^\d+\.\d+\.\d+$")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--module-commit", required=True, help="full SHA of the merged Modules commit containing the requested version")
    parser.add_argument("--module-version", required=True, help="module version contained in that merge, e.g. 0.3.2")
    parser.add_argument("--intelligence-commit", required=True, help="full SHA of the merged Intelligence Engine commit")
    args = parser.parse_args()
    for flag, value in (("--module-commit", args.module_commit), ("--intelligence-commit", args.intelligence_commit)):
        if not SHA1.fullmatch(value):
            parser.error(f"{flag} must be a full 40-character lowercase SHA-1")
    if not SEMVER.fullmatch(args.module_version):
        parser.error("--module-version must be MAJOR.MINOR.PATCH")

    lock = load_json(LOCK_PATH)
    inventory = load_json(INVENTORY_PATH)
    components = {item["id"]: item for item in lock.get("components", [])}
    repos = {item["name"]: item for item in inventory.get("repositories", [])}
    required_components = {"zcored-ctl-contract", "zairenkai-module", "zie-run"}
    missing = required_components - components.keys()
    if missing:
        raise SystemExit(f"refusing to write; App lock lacks {sorted(missing)}")
    for repo in (CORE_REPO, MODULES_REPO, ENGINE_REPO):
        if repo not in repos:
            raise SystemExit(f"refusing to write; inventory lacks {repo}")

    core = components["zcored-ctl-contract"]
    core["commit"] = CORE_COMMIT
    core["branch"] = "dev"
    core["merged"] = True
    core["note"] = (
        "Core dev revision includes Android-specific ownership validation for /data; protected "
        "/data/adb and executable paths remain root-owned and not group/other-writable. The next "
        "published Modules release must ship the same Core/component commits as its manifest."
    )

    module = components["zairenkai-module"]
    module["commit"] = args.module_commit
    module["branch"] = "dev"
    module["merged"] = True
    module["module_version"] = args.module_version
    module.pop("release_artifact", None)  # Never leave a stale v0.3.1 artifact pin next to a new source pin.
    module["note"] = (
        f"Synced to merged Modules dev commit containing module v{args.module_version}; it must pin "
        f"Core {CORE_COMMIT}. The release artifact is not trusted until sync_published_artifacts.py "
        "validates a published artifact-manifest.json, its component commits, ZIP digest and size."
    )

    engine = components["zie-run"]
    if engine.get("repository") != "ZairenkaiPrjctX/Zairenkai-Intelligence-Engine":
        raise SystemExit("refusing to write; zie-run repository does not match the canonical Intelligence Engine")
    engine["commit"] = args.intelligence_commit
    engine["branch"] = "dev"
    engine["merged"] = True
    engine["note"] = (
        f"Synced to merged Intelligence Engine dev commit {args.intelligence_commit}. Registry, grammar, "
        "native runtime and model manifest are built from this immutable source revision by App full-integration CI."
    )

    date = dt.datetime.now(dt.timezone.utc).date().isoformat()
    lock["locked_at"] = date
    for repo_name, commit, message in (
        (CORE_REPO, CORE_COMMIT, "Android-specific /data ownership validation is pinned for next module build."),
        (MODULES_REPO, args.module_commit, f"Module v{args.module_version} source pin updated; release manifest/digest validation remains required."),
        (ENGINE_REPO, args.intelligence_commit, "Pinned source for full-integration native engine build and registry/grammar verification."),
    ):
        row = repos[repo_name]
        row["audited_branch"] = "dev"
        row["audited_commit"] = commit
        evidence = str(row.get("evidence", "")).rstrip()
        marker = f"App integration sync {date} at {commit[:12]}"
        if marker not in evidence:
            row["evidence"] = evidence + (" " if evidence else "") + marker + ": " + message

    LOCK_PATH.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    INVENTORY_PATH.write_text(json.dumps(inventory, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"App source lock synced: Core {CORE_COMMIT}; Modules {args.module_commit} (v{args.module_version}); Intelligence Engine {args.intelligence_commit}")
    print("Next: python3 integration/scripts/verify_integration.py lock")
    print("Only after a matching release is published, run sync_published_artifacts.py and review the resulting changes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
