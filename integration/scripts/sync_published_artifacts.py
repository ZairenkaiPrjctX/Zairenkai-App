#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""Pin the latest *published* Zairenkai-Modules release with a verifiable artifact manifest.

This script only edits the working tree. The App workflow opens a PR and explicitly runs the
full-integration CI workflow against that branch; it never commits directly to dev.
"""
from __future__ import annotations
import datetime as dt
import hashlib
import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCK_PATH = ROOT / "integration/repository-lock.json"
INVENTORY_PATH = ROOT / "integration/organization-repositories.json"
MODULES_REPO = "ZairenkaiPrjctX/Zairenkai-Modules"
EXPECTED_COMPONENTS = {
    "zairenkai-core": "ZairenkaiPrjctX/Zairenkai-Core",
    "zairenkai-fas": "ZairenkaiPrjctX/Zairenkai-FAS",
    "zairenkai-tuning": "ZairenkaiPrjctX/Zairenkai-Tuning",
}
SHA1 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
TAG = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
ASSET_NAME = re.compile(r"^zairenkai-v\d+\.\d+\.\d+\.zip$")


def headers(accept: str = "application/vnd.github+json") -> dict[str, str]:
    h = {"Accept": accept, "User-Agent": "Zairenkai-App-artifact-sync/1.0", "X-GitHub-Api-Version": "2022-11-28"}
    if token := os.environ.get("GITHUB_TOKEN"):
        h["Authorization"] = f"Bearer {token}"
    return h


def get_bytes(url: str, accept: str = "application/vnd.github+json") -> bytes:
    req = urllib.request.Request(url, headers=headers(accept))
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            return response.read()
    except (urllib.error.URLError, TimeoutError) as exc:
        raise SystemExit(f"artifact sync: fetch failed for {url}: {exc}") from exc


def api_json(url: str):
    try:
        return json.loads(get_bytes(url))
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise SystemExit(f"artifact sync: invalid JSON from {url}: {exc}") from exc


def semver(tag: str) -> tuple[int, int, int] | None:
    m = TAG.fullmatch(tag)
    return tuple(map(int, m.groups())) if m else None


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def published_manifest_release() -> tuple[dict, dict, bytes] | None:
    url = f"https://api.github.com/repos/{MODULES_REPO}/releases?per_page=100"
    releases = api_json(url)
    if not isinstance(releases, list):
        raise SystemExit("artifact sync: GitHub releases response was not a list")
    candidates = []
    for release in releases:
        if not isinstance(release, dict) or release.get("draft") or release.get("prerelease"):
            continue
        version = semver(str(release.get("tag_name", "")))
        if version is None:
            continue
        assets = release.get("assets", [])
        asset = next((a for a in assets if isinstance(a, dict) and a.get("name") == "artifact-manifest.json"), None)
        if asset and asset.get("browser_download_url"):
            candidates.append((version, release, asset))
    if not candidates:
        return None
    _, release, asset = max(candidates, key=lambda v: (v[0], str(v[1].get("published_at") or "")))
    raw = get_bytes(asset["browser_download_url"])
    try:
        manifest = json.loads(raw)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise SystemExit(f"artifact sync: {release['tag_name']} has invalid artifact-manifest.json: {exc}") from exc
    return release, manifest, raw


def validate_manifest(release: dict, manifest: dict, raw: bytes) -> dict:
    tag = release.get("tag_name", "")
    version_tuple = semver(tag)
    if version_tuple is None:
        raise SystemExit(f"artifact sync: invalid module release tag {tag!r}")
    version = tag[1:]
    if manifest.get("schema_version") != 1 or manifest.get("repository") != MODULES_REPO:
        raise SystemExit("artifact sync: manifest schema/repository mismatch")
    if manifest.get("release_tag") != tag or manifest.get("module_version") != version:
        raise SystemExit("artifact sync: manifest tag/version does not match its GitHub release")
    source_commit = manifest.get("source_commit", "")
    if not SHA1.fullmatch(source_commit):
        raise SystemExit("artifact sync: manifest source_commit is not a full SHA-1")
    component_rows = manifest.get("components")
    if not isinstance(component_rows, list):
        raise SystemExit("artifact sync: manifest components is not a list")
    components = {}
    for row in component_rows:
        if not isinstance(row, dict):
            raise SystemExit("artifact sync: malformed component record")
        name, repository, commit = row.get("name"), row.get("repository"), row.get("commit")
        if name not in EXPECTED_COMPONENTS or repository != EXPECTED_COMPONENTS[name] or not SHA1.fullmatch(str(commit or "")):
            raise SystemExit(f"artifact sync: unknown/malformed component record {row!r}")
        if name in components:
            raise SystemExit(f"artifact sync: duplicate component {name}")
        components[name] = {"repository": repository, "commit": commit}
    if set(components) != set(EXPECTED_COMPONENTS):
        raise SystemExit(f"artifact sync: expected exactly {sorted(EXPECTED_COMPONENTS)} in manifest; got {sorted(components)}")
    version_code = manifest.get("module_version_code")
    if not isinstance(version_code, int) or version_code < 1:
        raise SystemExit("artifact sync: module_version_code is missing or invalid")
    assets = manifest.get("assets")
    if not isinstance(assets, list):
        raise SystemExit("artifact sync: manifest assets is not a list")
    asset_name = f"zairenkai-{tag}.zip"
    asset = next((a for a in assets if isinstance(a, dict) and a.get("name") == asset_name), None)
    if asset is None or not ASSET_NAME.fullmatch(asset_name):
        raise SystemExit(f"artifact sync: manifest does not declare {asset_name}")
    if not SHA256.fullmatch(str(asset.get("sha256", ""))) or not isinstance(asset.get("size_bytes"), int) or asset["size_bytes"] < 1:
        raise SystemExit("artifact sync: manifest has invalid asset digest/size")
    return {
        "module_version": version,
        "module_version_code": version_code,
        "source_commit": source_commit,
        "asset_name": asset_name,
        "asset_sha256": asset["sha256"],
        "asset_size_bytes": asset["size_bytes"],
        "manifest_sha256": sha256(raw),
        "manifest_name": "artifact-manifest.json",
        "release_tag": tag,
        "component_commits": components,
        "source_url": f"https://github.com/{MODULES_REPO}/releases/download/{tag}/{asset_name}",
    }


def load(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def update_inventory(inventory: dict, repository: str, commit: str, message: str) -> None:
    name = repository.split("/", 1)[1]
    entry = next((r for r in inventory.get("repositories", []) if r.get("name") == name), None)
    if entry is None:
        raise SystemExit(f"artifact sync: organization inventory lacks {name}")
    entry["audited_branch"] = "dev"
    entry["audited_commit"] = commit
    evidence = str(entry.get("evidence", "")).rstrip()
    stamp = dt.datetime.now(dt.timezone.utc).date().isoformat()
    marker = f"Automated release pin {stamp} at {commit[:12]}"
    if marker not in evidence:
        entry["evidence"] = evidence + (" " if evidence else "") + marker + ": " + message


def sync() -> bool:
    result = published_manifest_release()
    if result is None:
        print("No published Modules release with artifact-manifest.json exists yet; the module source-build fallback remains enabled.")
        return False
    release, raw_manifest, raw = result
    art = validate_manifest(release, raw_manifest, raw)
    lock = load(LOCK_PATH)
    inventory = load(INVENTORY_PATH)
    by_id = {c["id"]: c for c in lock.get("components", [])}
    for required in ("zairenkai-module", "zcored-ctl-contract", "zfasd-ctl-contract"):
        if required not in by_id:
            raise SystemExit(f"artifact sync: App repository lock lacks {required}")
    module = by_id["zairenkai-module"]
    old_version = str(module.get("module_version", "0.0.0"))
    new_version = art["module_version"]
    if semver("v" + new_version) < semver("v" + old_version):
        print(f"Published artifact v{new_version} is older than the App's v{old_version} pin; refusing downgrade.")
        return False
    if semver("v" + new_version) == semver("v" + old_version) and module.get("release_artifact", {}).get("asset_sha256") == art["asset_sha256"]:
        print(f"App already pins the published Modules v{new_version} artifact and digest.")
        return False

    module["commit"] = art["source_commit"]
    module["branch"] = "dev"
    module["merged"] = True
    module["module_version"] = new_version
    module["module_version_code"] = art["module_version_code"]
    module["release_artifact"] = {
        "repository": MODULES_REPO,
        "release_tag": art["release_tag"],
        "asset_name": art["asset_name"],
        "asset_sha256": art["asset_sha256"],
        "asset_size_bytes": art["asset_size_bytes"],
        "manifest_name": art["manifest_name"],
        "manifest_sha256": art["manifest_sha256"],
        "component_commits": art["component_commits"],
    }
    module["note"] = (
        f"Synced to published module release {art['release_tag']} at immutable source commit {art['source_commit']}. "
        "The App verifies artifact-manifest.json, zip size/SHA-256, module version, component commits and installer-source equality before packaging it."
    )
    # The protocol clients and the packaged module must always share the same exact Core/FAS source SHAs.
    core_sha = art["component_commits"]["zairenkai-core"]["commit"]
    fas_sha = art["component_commits"]["zairenkai-fas"]["commit"]
    for cid, sha, repo in (
        ("zcored-ctl-contract", core_sha, EXPECTED_COMPONENTS["zairenkai-core"]),
        ("zfasd-ctl-contract", fas_sha, EXPECTED_COMPONENTS["zairenkai-fas"]),
    ):
        row = by_id[cid]
        row["commit"] = sha
        row["branch"] = "dev"
        row["merged"] = True
        row["note"] = f"Pinned from the published Modules artifact manifest {art['release_tag']}; must match its bundled component commit."
        update_inventory(inventory, repo, sha, f"consumed through the published Modules artifact {art['release_tag']}.")
    update_inventory(inventory, MODULES_REPO, art["source_commit"], f"release {art['release_tag']} is locked by manifest + archive SHA-256.")
    tuning = art["component_commits"]["zairenkai-tuning"]["commit"]
    update_inventory(inventory, EXPECTED_COMPONENTS["zairenkai-tuning"], tuning, f"bundled in published Modules artifact {art['release_tag']}.")
    lock["locked_at"] = dt.datetime.now(dt.timezone.utc).date().isoformat()
    LOCK_PATH.write_text(json.dumps(lock, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    INVENTORY_PATH.write_text(json.dumps(inventory, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Pinned Modules {art['release_tag']} / source {art['source_commit']}")
    print(f"  artifact: {art['asset_name']} size={art['asset_size_bytes']} sha256={art['asset_sha256']}")
    print(f"  Core: {core_sha}; FAS: {fas_sha}; Tuning: {tuning}")
    print("The App workflow must open a PR and run the full integration CI on that branch before merge.")
    return True


if __name__ == "__main__":
    sync()
