#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""Refresh direct source-only App components to dev heads; module-owned Core/FAS remain locked to releases."""
from __future__ import annotations
import datetime as dt
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCK_PATH = ROOT / "integration/repository-lock.json"
INVENTORY_PATH = ROOT / "integration/organization-repositories.json"
# App-packaged direct components with their own immutable source revisions. Core/FAS/Tuning are
# deliberately excluded: they must change together via a published Modules artifact.
DIRECT = {
    "zart": "ZairenkaiPrjctX/Zairenkai-ART",
    "zthermal": "ZairenkaiPrjctX/Zairenkai-ThermalTools",
    "afid-device-candidates": "ZairenkaiPrjctX/Zairenkai-Database-Source",
    "zie-run": "ZairenkaiPrjctX/Zairenkai-Intelligence-Engine",
}
SHA1 = re.compile(r"^[0-9a-f]{40}$")


def commit_for(repository: str) -> str:
    url = f"https://api.github.com/repos/{repository}/commits/dev"
    h = {"Accept": "application/vnd.github+json", "User-Agent": "Zairenkai-App-source-pin-sync/1.0", "X-GitHub-Api-Version": "2022-11-28"}
    if token := os.environ.get("GITHUB_TOKEN"):
        h["Authorization"] = f"Bearer {token}"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=30) as response:
            data = json.loads(response.read())
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as exc:
        raise SystemExit(f"cannot resolve {repository}/dev: {exc}") from exc
    sha = data.get("sha", "")
    if not SHA1.fullmatch(sha) or f"github.com/{repository}/commit/{sha}" not in data.get("html_url", ""):
        raise SystemExit(f"malformed/foreign dev commit returned for {repository}")
    return sha


def main() -> int:
    lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    inv = json.loads(INVENTORY_PATH.read_text(encoding="utf-8"))
    by_id = {c["id"]: c for c in lock.get("components", [])}
    repos = {r["name"]: r for r in inv.get("repositories", [])}
    missing = set(DIRECT) - by_id.keys()
    if missing:
        raise SystemExit(f"App lock lacks expected direct components: {sorted(missing)}")
    changed = []
    date = dt.datetime.now(dt.timezone.utc).date().isoformat()
    for cid, full_name in DIRECT.items():
        row = by_id[cid]
        if row.get("repository") != full_name:
            raise SystemExit(f"{cid}: expected trusted repo {full_name}, got {row.get('repository')}")
        new_sha = commit_for(full_name)
        if row.get("commit") == new_sha:
            continue
        # A release artifact is valid only for its exact source commit; never carry a stale pin.
        row.pop("release_artifact", None)
        row["commit"] = new_sha
        row["branch"] = "dev"
        row["merged"] = True
        row["note"] = f"Automated candidate pin from {full_name}/dev at {new_sha}; the App full-integration CI must pass before merge."
        name = full_name.split("/", 1)[1]
        ir = repos.get(name)
        if ir is None:
            raise SystemExit(f"organization inventory lacks {name}")
        ir["audited_branch"] = "dev"
        ir["audited_commit"] = new_sha
        marker = f"Automated App source candidate {date} at {new_sha[:12]}"
        if marker not in str(ir.get("evidence", "")):
            ir["evidence"] = str(ir.get("evidence", "")).rstrip() + (" " if ir.get("evidence") else "") + marker + ": pinned for a full-integration CI review."
        changed.append((cid, new_sha))
    if not changed:
        print("All direct App source pins already match their dev heads.")
        return 0
    lock["locked_at"] = date
    LOCK_PATH.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    INVENTORY_PATH.write_text(json.dumps(inv, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for cid, sha in changed:
        print(f"Updated {cid}: {sha}")
    print("Only direct packaged source pins changed; Core/FAS remain tied to the published module release.")
    return 0

if __name__ == "__main__":
    main()
