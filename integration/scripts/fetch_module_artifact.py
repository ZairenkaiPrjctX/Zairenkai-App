#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""Download and validate the exact published module release locked by the App.

Exit 0: a verified release asset was downloaded.
Exit 3: the lock has no artifact pin yet (the build may use the source-build fallback).
Any other failure is fatal. URLs are constructed only for the allowlisted Zairenkai Modules repo.
"""
from __future__ import annotations
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath

MODULES_REPO = "ZairenkaiPrjctX/Zairenkai-Modules"
SHA1 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
TAG = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
ASSET = re.compile(r"^zairenkai-v\d+\.\d+\.\d+\.zip$")


def fail(msg: str) -> None:
    raise SystemExit(f"module artifact verification failed: {msg}")


def get_bytes(url: str) -> bytes:
    headers = {"User-Agent": "Zairenkai-App-module-artifact/1.0", "Accept": "application/octet-stream"}
    if token := os.environ.get("GITHUB_TOKEN"):
        headers["Authorization"] = f"Bearer {token}"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=120) as response:
            return response.read()
    except (urllib.error.URLError, TimeoutError) as exc:
        fail(f"cannot download {url}: {exc}")


def digest(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def validate_source_lock(source_lock: dict, expected_version: str) -> None:
    """Keep source-built fallback and a release ZIP on the same module identity/version."""
    module = source_lock.get("module")
    if not isinstance(module, dict):
        fail("components.lock.json has no module identity")
    if module.get("id") != "zairenkai" or module.get("repository") != MODULES_REPO:
        fail("components.lock.json module id/repository differs from the allowlisted module")
    if module.get("version") != expected_version:
        fail(
            f"components.lock.json module version {module.get('version')!r} "
            f"differs from the App lock {expected_version!r}"
        )


def check_archive(data: bytes, expected_version: str, expected_version_code: int) -> None:
    try:
        z = zipfile.ZipFile(__import__("io").BytesIO(data))
    except zipfile.BadZipFile:
        fail("downloaded artifact is not a valid ZIP")
    with z:
        infos = z.infolist()
        names = [i.filename for i in infos]
        if len(names) != len(set(names)):
            fail("zip contains duplicate entries")
        for name in names:
            posix = PurePosixPath(name)
            if name.startswith("/") or "\\" in name or ".." in posix.parts or name.endswith("/"):
                fail(f"unsafe zip path {name!r}")
        if "module.prop" not in names or "SHA256SUMS" not in names:
            fail("module.prop or SHA256SUMS missing")
        props = {}
        for line in z.read("module.prop").decode("utf-8").splitlines():
            if "=" in line:
                key, value = line.split("=", 1)
                props[key] = value
        if props.get("id") != "zairenkai" or props.get("version") != f"v{expected_version}":
            fail(f"module.prop version/id mismatch: {props.get('version')}/{props.get('id')}")
        if not props.get("versionCode", "").isdigit():
            fail("module.prop versionCode is missing or invalid")
        if int(props["versionCode"]) != expected_version_code:
            fail("module.prop versionCode differs from the release manifest")
        sums = {}
        for line in z.read("SHA256SUMS").decode("utf-8").splitlines():
            match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._+/-]+)", line)
            if not match:
                fail(f"malformed SHA256SUMS line {line!r}")
            sha, name = match.groups()
            if name in sums:
                fail(f"duplicate SHA256SUMS path {name}")
            sums[name] = sha
        zip_file_set = {n for n in names if not n.startswith("META-INF/") and n != "SHA256SUMS"}
        if zip_file_set != set(sums):
            fail(f"SHA256SUMS entry set mismatch (missing={sorted(zip_file_set-set(sums))}, extra={sorted(set(sums)-zip_file_set)})")
        for name, expected in sums.items():
            actual = digest(z.read(name))
            if actual != expected:
                fail(f"internal SHA-256 mismatch for {name}")


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        fail("usage: fetch_module_artifact.py <app-lock.json> <modules-source-dir> <output-dir>")
    lock_path, modules_source, out_dir = Path(argv[1]), Path(argv[2]), Path(argv[3])
    app_lock = json.loads(lock_path.read_text(encoding="utf-8"))
    module = next((c for c in app_lock.get("components", []) if c.get("id") == "zairenkai-module"), None)
    if module is None:
        fail("App lock is missing zairenkai-module")
    artifact = module.get("release_artifact")
    if not artifact:
        print("No release_artifact pin found; build-components.sh will build from the locked source commits.")
        return 3
    if artifact.get("repository") != MODULES_REPO:
        fail("artifact repository is not the allowlisted Zairenkai Modules repository")
    tag = artifact.get("release_tag", "")
    version = module.get("module_version", "")
    name = artifact.get("asset_name", "")
    if not TAG.fullmatch(tag) or tag != f"v{version}" or not ASSET.fullmatch(name) or name != f"zairenkai-{tag}.zip":
        fail("unsafe or inconsistent release tag, module version or asset name")
    source_commit = module.get("commit", "")
    if not SHA1.fullmatch(source_commit):
        fail("App lock module source commit is not a full SHA-1")
    manifest_name = artifact.get("manifest_name", "")
    manifest_sha = artifact.get("manifest_sha256", "")
    if manifest_name != "artifact-manifest.json" or not SHA256.fullmatch(manifest_sha):
        fail("manifest pin missing or malformed")
    if not SHA256.fullmatch(str(artifact.get("asset_sha256", ""))) or not isinstance(artifact.get("asset_size_bytes"), int):
        fail("asset SHA-256/size pin malformed")
    prefix = f"https://github.com/{MODULES_REPO}/releases/download/{tag}/"
    raw_manifest = get_bytes(prefix + manifest_name)
    if digest(raw_manifest) != manifest_sha:
        fail("release artifact manifest digest differs from the App lock")
    try:
        manifest = json.loads(raw_manifest)
    except json.JSONDecodeError as exc:
        fail(f"artifact manifest is invalid JSON: {exc}")
    if manifest.get("schema_version") != 1 or manifest.get("repository") != MODULES_REPO:
        fail("release manifest schema/repository mismatch")
    if manifest.get("release_tag") != tag or manifest.get("module_version") != version or manifest.get("source_commit") != source_commit:
        fail("release manifest tag, version or source commit differs from the App lock")
    version_code = manifest.get("module_version_code")
    if not isinstance(version_code, int) or version_code < 1 or module.get("module_version_code") != version_code:
        fail("release manifest module_version_code differs from the App lock")

    source_lock_path = modules_source / "components.lock.json"
    if not source_lock_path.is_file():
        fail("locked Modules source checkout has no components.lock.json")
    source_lock = json.loads(source_lock_path.read_text(encoding="utf-8"))
    validate_source_lock(source_lock, version)
    expected_components = sorted({c["name"]: {"name": c["name"], "repository": c["repository"], "commit": c["commit"]} for c in source_lock.get("components", [])}.values(), key=lambda c: c["name"])
    manifest_components = sorted(manifest.get("components", []), key=lambda c: c.get("name", ""))
    if manifest_components != expected_components:
        fail("release artifact component commits differ from components.lock.json at the pinned source commit")
    locked_components = artifact.get("component_commits", {})
    for c in expected_components:
        pin = locked_components.get(c["name"], {})
        if pin.get("repository") != c["repository"] or pin.get("commit") != c["commit"]:
            fail(f"App component pin mismatch for {c['name']}")

    asset = next((a for a in manifest.get("assets", []) if a.get("name") == name), None)
    if not asset or asset.get("sha256") != artifact["asset_sha256"] or asset.get("size_bytes") != artifact["asset_size_bytes"]:
        fail("release manifest asset entry differs from App lock SHA-256/size")
    data = get_bytes(prefix + name)
    actual_sha = digest(data)
    if len(data) != artifact["asset_size_bytes"] or actual_sha != artifact["asset_sha256"]:
        fail(f"downloaded archive size/hash mismatch (size={len(data)}, sha256={actual_sha})")
    check_archive(data, version, version_code)
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / name
    out.write_bytes(data)
    (out_dir / "artifact-manifest.json").write_bytes(raw_manifest)
    print(f"Verified {tag}: {name}, {len(data)} bytes, SHA-256 {actual_sha}; source {source_commit}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
