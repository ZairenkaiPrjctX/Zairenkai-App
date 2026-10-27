#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""Checks for the complete integration build, and its report.

  verify_integration.py lock                 validate integration/repository-lock.json (and the inventory)
  verify_integration.py access               every locked repository can be fetched with the current credentials
  verify_integration.py build                checkouts, components.json, notices, module catalog (after build-components.sh)
  verify_integration.py apk <apk> [--release] APK contents against the build; --release also requires
                                             signatures and distributable licences
  verify_integration.py report <apk> [--release]   all of the above, writing integration/out/integration-report.{json,md}

Exit status 1 lists every failure; nothing is skipped silently. Uses only the standard library,
git, and (optionally) apksigner from the Android SDK.
"""
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCK = pathlib.Path(os.environ.get("ZAIRENKAI_LOCK", ROOT / "integration/repository-lock.json"))
INVENTORY = ROOT / "integration/organization-repositories.json"
OUT = pathlib.Path(os.environ.get("ZAIRENKAI_OUT", ROOT / "integration/out"))
ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64")
SHA1 = re.compile(r"^[0-9a-f]{40}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")

# Where build-components.sh / capture-contracts.sh check out each lock component.
SRC_DIRS = {
    "zart": "zart",
    "zthermal": "zthermal",
    "afid-device-candidates": "afid",
    "zairenkai-module": "zairenkai-module",
    "zcored-ctl-contract": "core",
    "zfasd-ctl-contract": "fas",
    "zie-run": "zie",
}
# Packaged in the APK (contracts are only called at run time). zairenkai-module is the single
# root module; it packages zcored, zfasd and the GPL-3.0 ztune with its source (2026-10-22).
# zie-run is the Local AI runner (Zairenkai-Intelligence-Engine), built for two ABIs only.
PACKAGED = ("zart", "zthermal", "afid-device-candidates", "zairenkai-module", "zie-run")
ZIE_ABIS = ("arm64-v8a", "x86_64")
ZIE_ASSETS = ("assets/intelligence/tools.json", "assets/intelligence/output.gbnf", "assets/intelligence/model.json",
              "assets/licenses/zie-run/LICENSE", "assets/licenses/zie-run/NOTICE", "assets/licenses/llama.cpp/LICENSE")
INTERNAL_ONLY = ()


def profile() -> str:
    try:
        return json.loads((OUT / "components.json").read_text()).get("profile", "distributable")
    except Exception:  # noqa: BLE001 - no build yet
        return "distributable"


def packaged() -> tuple:
    return PACKAGED + (INTERNAL_ONLY if profile() == "internal" else ())
REQUIRED_FIELDS = ("id", "repository", "url", "branch", "commit", "merged", "packaging", "license", "integration_status")
FORBIDDEN = re.compile(r"\.(pem|key|jks|keystore|p12|pfx)$|(^|/)\.git/|(^|/)id_(rsa|ed25519)", re.I)
SECRET_MARKERS = (b"-----BEGIN PRIVATE KEY-----", b"-----BEGIN EC PRIVATE KEY-----", b"-----BEGIN RSA PRIVATE KEY-----",
                  b"-----BEGIN OPENSSH PRIVATE KEY-----", b"github_pat_", b"ghp_", b"ghs_")


def sha256(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def load_lock(errors: list) -> dict:
    try:
        lock = json.loads(LOCK.read_text())
    except Exception as e:  # noqa: BLE001 - reported, not hidden
        errors.append(f"lock: unreadable: {e}")
        return {"components": []}
    if lock.get("schema_version") != 1:
        errors.append("lock: schema_version must be 1")
    seen = set()
    for c in lock.get("components", []):
        cid = c.get("id", "?")
        for f in REQUIRED_FIELDS:
            if f not in c:
                errors.append(f"lock: {cid}: missing {f}")
        if cid in seen:
            errors.append(f"lock: duplicate component {cid}")
        seen.add(cid)
        if not SHA1.match(str(c.get("commit", ""))):
            errors.append(f"lock: {cid}: commit {c.get('commit')!r} is not a full SHA-1")
        if c.get("url") != f"https://github.com/{c.get('repository')}.git":
            errors.append(f"lock: {cid}: url {c.get('url')!r} does not match repository {c.get('repository')!r}")
        if not isinstance(c.get("merged"), bool):
            errors.append(f"lock: {cid}: merged must be true or false")
        for abi in c.get("abis", []):
            if abi not in ABIS:
                errors.append(f"lock: {cid}: unsupported ABI {abi}")
        pin = c.get("release_artifact")
        # The single root module consumes a versioned zip with its own richer manifest contract;
        # direct native/data components use the generic artifact-${source_commit} contract.
        if pin is not None and cid != "zairenkai-module":
            if not isinstance(pin, dict):
                errors.append(f"lock: {cid}: release_artifact must be an object")
            else:
                if pin.get("repository") != c.get("repository"):
                    errors.append(f"lock: {cid}: artifact repository does not match source repository")
                if pin.get("source_commit") != c.get("commit"):
                    errors.append(f"lock: {cid}: artifact source_commit does not match locked source commit")
                if pin.get("release_tag") != f"artifact-{c.get('commit')}":
                    errors.append(f"lock: {cid}: artifact release tag is not pinned to the exact commit")
                if not SHA256.match(str(pin.get("asset_sha256", ""))):
                    errors.append(f"lock: {cid}: artifact asset SHA-256 is malformed")
                if not SHA256.match(str(pin.get("manifest_sha256", ""))):
                    errors.append(f"lock: {cid}: artifact manifest SHA-256 is malformed")
                if not isinstance(pin.get("asset_size_bytes"), int) or pin.get("asset_size_bytes", 0) <= 0:
                    errors.append(f"lock: {cid}: artifact size must be a positive integer")
                for key in ("asset_name", "manifest_name", "component"):
                    value = pin.get(key)
                    if not isinstance(value, str) or not value or "/" in value or "\\" in value or value in (".", ".."):
                        errors.append(f"lock: {cid}: artifact {key} is missing or unsafe")
        if cid not in SRC_DIRS:
            errors.append(f"lock: {cid}: unknown component (add it to verify_integration.py)")
    for cid in PACKAGED + INTERNAL_ONLY:
        if cid not in seen:
            errors.append(f"lock: mandatory component {cid} is missing")
    return lock


def check_lock() -> list:
    errors: list = []
    lock = load_lock(errors)
    inv = {r["name"]: r for r in json.loads(INVENTORY.read_text())["repositories"]}
    for c in lock["components"]:
        repo = str(c.get("repository", "")).split("/")[-1]
        rec = inv.get(repo)
        if rec is None:
            errors.append(f"lock: {c.get('id')}: {repo} is not in the inventory")
        elif rec["audited_commit"] != c.get("commit"):
            errors.append(f"lock: {c.get('id')}: inventory audited {rec['audited_commit'][:12]}, lock pins {str(c.get('commit'))[:12]}")
    return errors


def check_access() -> list:
    errors: list = []
    lock = load_lock(errors)
    for url in sorted({c["url"] for c in lock["components"] if "url" in c}):
        r = subprocess.run(["git", "ls-remote", "--heads", url], capture_output=True, text=True, timeout=120,
                           env={**os.environ, "GIT_TERMINAL_PROMPT": "0"})
        if r.returncode != 0:
            # Every locked repository is public and fetched without credentials.
            errors.append(f"access: cannot fetch {url} (git exit {r.returncode}). Locked repositories must be "
                          "publicly readable; CI holds no token for them.")
    return errors


def check_build() -> list:
    errors: list = []
    lock = {c["id"]: c for c in load_lock(errors)["components"]}
    for cid, d in SRC_DIRS.items():
        if cid not in lock or cid not in packaged():
            continue
        src = OUT / "src" / d
        head = subprocess.run(["git", "-C", str(src), "rev-parse", "HEAD"], capture_output=True, text=True)
        if head.returncode != 0:
            errors.append(f"build: {cid}: no checkout at {src}")
        elif head.stdout.strip() != lock[cid]["commit"]:
            errors.append(f"build: {cid}: checkout is {head.stdout.strip()[:12]}, lock pins {lock[cid]['commit'][:12]}")
    comp_path = OUT / "components.json"
    if not comp_path.is_file():
        return errors + ["build: integration/out/components.json missing: run build-components.sh"]
    comp = json.loads(comp_path.read_text())
    for name, digest in comp.get("files", {}).items():
        f = OUT / name
        if not f.is_file():
            errors.append(f"build: {name} listed in components.json but missing")
        elif sha256(f.read_bytes()) != digest:
            errors.append(f"build: {name} does not match its recorded SHA-256")
    for key, cid in (("zart", "zart"), ("zthermal", "zthermal"), ("afid", "afid-device-candidates"), ("intelligence", "zie-run")):
        if comp.get(key, {}).get("commit") != lock.get(cid, {}).get("commit"):
            errors.append(f"build: components.json {key} commit differs from the lock")
    module_record = comp.get("zairenkai_module", {})
    module_lock = lock.get("zairenkai-module", {})
    if module_record.get("commit") != module_lock.get("commit"):
        errors.append("build: components.json Zairenkai-Modules source commit differs from the lock")
    expected_artifact_tag = (module_lock.get("release_artifact") or {}).get("release_tag")
    if expected_artifact_tag and module_record.get("artifact_release_tag") != expected_artifact_tag:
        errors.append("build: packaged module artifact tag differs from the App lock")
    files = set(comp.get("files", {}))
    for abi in ABIS:
        for lib in ("libzart.so", "libzthermal.so"):
            if f"jniLibs/{abi}/{lib}" not in files:
                errors.append(f"build: jniLibs/{abi}/{lib} not built")
    for abi in ZIE_ABIS:
        if f"jniLibs/{abi}/libzierun.so" not in files:
            errors.append(f"build: jniLibs/{abi}/libzierun.so not built")
    if any(n.endswith("/libzierun.so") and n.split("/")[1] not in ZIE_ABIS for n in files):
        errors.append("build: zie-run built for an ABI its build does not support")
    if any(n.endswith(".gguf") for n in files):
        errors.append("build: a model file was packaged; the user imports it")
    for req in ("assets/afid/device-candidates.json", "assets/system-modules.json",
                "assets/system-modules/zairenkai.zip",
                "assets/licenses/zthermal/LICENSE", "assets/licenses/zthermal/NOTICE",
                "assets/licenses/zart/LICENSE", "assets/licenses/zart/NOTICE") + ZIE_ASSETS:
        if req not in files:
            errors.append(f"build: {req} not built")
    internal = profile() == "internal"
    if any(n.startswith("assets/system-modules/") and n != "assets/system-modules/zairenkai.zip" for n in files):
        errors.append("build: a module other than zairenkai.zip was built")
    for c in ("zart", "zthermal", "zairenkai-core", "zairenkai-fas", "zairenkai-tuning"):
        if not (OUT / f"assets/licenses/{c}/THIRD_PARTY_NOTICES.txt").is_file():
            errors.append(f"build: third-party notices for {c} missing")
    if not (OUT / "assets/licenses/zart/AOSP-ATTRIBUTION.txt").is_file():
        errors.append("build: zart's AOSP (Apache-2.0) attribution missing")
    afid = OUT / "assets/afid/device-candidates.json"
    if afid.is_file():
        a = json.loads(afid.read_text())
        if a.get("generated_from", {}).get("commit") != lock.get("afid-device-candidates", {}).get("commit"):
            errors.append("build: device database asset was generated from another commit")
        if not a.get("devices"):
            errors.append("build: device database asset has no devices")
    cat_path = OUT / "assets/system-modules.json"
    if cat_path.is_file():
        cat = json.loads(cat_path.read_text())
        for m in cat["modules"]:
            comp_id = {"zairenkai": "zairenkai-module"}.get(m["id"])
            if comp_id is None or m["source"]["commit"] != lock.get(comp_id, {}).get("commit"):
                errors.append(f"build: module {m['id']} was built from {m['source']['commit'][:12]}, not the locked commit")
            z = OUT / "assets" / m["file"]
            if z.is_file() and sha256(z.read_bytes()) != m["sha256"]:
                errors.append(f"build: {m['file']} does not match the catalog")
            if z.is_file():
                with zipfile.ZipFile(z) as mz:
                    missing = {"LICENSE", "NOTICE"} - set(mz.namelist())
                if missing:
                    errors.append(f"build: {m['file']} lacks {sorted(missing)}: the zip must carry its own notices")
            if not {"customize.sh", "META-INF/com/google/android/update-binary"} <= set(m["source_matched"]):
                errors.append(f"build: module {m['id']}: installer not matched to source")
            if m.get("distribution") != "distributable" and not internal:
                errors.append(f"build: internal module {m['id']} in a distributable build")
    return errors


def apk_facts(apk: pathlib.Path) -> dict:
    with zipfile.ZipFile(apk) as z:
        names = z.namelist()
        digests = {n: sha256(z.read(n)) for n in names if n.startswith(("lib/", "assets/"))}
        secrets = [n for n in names if any(m in z.read(n) for m in SECRET_MARKERS)]
    return {"names": names, "digests": digests, "secrets": secrets}


def build_tool(name: str):
    """A build-tools executable (apksigner, aapt2) from PATH or the newest SDK build-tools."""
    tool = shutil.which(name)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if tool is None and sdk:
        cands = sorted(pathlib.Path(sdk, "build-tools").glob(f"*/{name}"), key=lambda p: [int(x) if x.isdigit() else x for x in re.split(r"[.-]", p.parent.name)])
        tool = str(cands[-1]) if cands else None
    return tool


# The only android.permission entries the app may request (docs/build/releases.md): every one
# serves app updates. A new one is a reviewed change to this list.
ALLOWED_PERMISSIONS = {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.RECEIVE_BOOT_COMPLETED", "android.permission.POST_NOTIFICATIONS",
    "android.permission.REQUEST_INSTALL_PACKAGES",
}


def manifest_problems(apk: pathlib.Path) -> list:
    """Release hardening read from the compiled manifest (aapt2): not debuggable, not test-only, no
    cleartext traffic, a network security config, no backup, and only the allowed permissions."""
    tool = build_tool("aapt2")
    if tool is None:
        return ["release: aapt2 not found: the manifest cannot be checked"]
    r = subprocess.run([tool, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)], capture_output=True, text=True)
    if r.returncode != 0:
        return [f"release: aapt2 could not read the manifest: {r.stderr.strip()[:200]}"]
    tree = r.stdout
    def attr(name):
        m = re.search(r"A: http://schemas\.android\.com/apk/res/android:" + name + r"\([^)]*\)=([^\s]+)", tree)
        return m.group(1).strip('"') if m else None
    problems = []
    if attr("debuggable") not in (None, "false", "0x0"):
        problems.append("release: the APK is debuggable")
    if attr("testOnly") not in (None, "false", "0x0"):
        problems.append("release: the APK is test-only")
    if attr("usesCleartextTraffic") not in ("false", "0x0"):
        problems.append("release: cleartext traffic is not disabled (android:usesCleartextTraffic)")
    if attr("networkSecurityConfig") is None:
        problems.append("release: no android:networkSecurityConfig")
    if attr("allowBackup") not in ("false", "0x0"):
        problems.append("release: android:allowBackup is not false")
    requested, element = set(), None
    for line in tree.splitlines():
        e = re.match(r"\s*E: (\S+)", line)
        if e:
            element = e.group(1)
            continue
        n = re.match(r'\s*A: http://schemas\.android\.com/apk/res/android:name\([^)]*\)="([^"]+)"', line)
        if n and element == "uses-permission":
            requested.add(n.group(1))
    requested = {p for p in requested if p.startswith("android.permission.")}
    if extra := requested - ALLOWED_PERMISSIONS:
        problems.append(f"release: unexpected permissions {sorted(extra)}")
    return problems


def apk_signed(apk: pathlib.Path) -> tuple:
    tool = build_tool("apksigner")
    if tool is None:
        return None, "apksigner not found"
    r = subprocess.run([tool, "verify", "--print-certs", "--verbose", str(apk)], capture_output=True, text=True)
    if r.returncode != 0:
        return False, "unsigned or invalid signature"
    if not re.search(r"Verified using v(2|3|3\.1) scheme \(APK Signature Scheme v(2|3|3\.1)\): true", r.stdout):
        return False, "signed without an APK Signature Scheme v2/v3 signature"
    cert = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", r.stdout)
    dn = re.search(r"certificate DN: (.*)", r.stdout)
    return True, f"{dn.group(1) if dn else '?'} (SHA-256 {cert.group(1)[:16] if cert else '?'}…)"


def check_apk(apk: pathlib.Path, release: bool) -> list:
    errors: list = []
    if not apk.is_file():
        return [f"apk: {apk} does not exist"]
    facts = apk_facts(apk)
    comp = json.loads((OUT / "components.json").read_text()) if (OUT / "components.json").is_file() else {"files": {}}
    for name, digest in comp["files"].items():
        entry = ("lib/" + name[len("jniLibs/"):]) if name.startswith("jniLibs/") else name
        if facts["digests"].get(entry) != digest:
            errors.append(f"apk: {entry} missing or different from the build")
    for abi in ABIS:
        for lib in ("libzart.so", "libzthermal.so"):
            if f"lib/{abi}/{lib}" not in facts["names"]:
                errors.append(f"apk: lib/{abi}/{lib} missing (ABI coverage)")
    for abi in ZIE_ABIS:
        if f"lib/{abi}/libzierun.so" not in facts["names"]:
            errors.append(f"apk: lib/{abi}/libzierun.so missing (Local AI)")
    if any(n.endswith(".gguf") for n in facts["names"]):
        errors.append("apk: a model file is packaged; the user imports it")
    for req in ("assets/licenses/android/NOTICES.txt", "assets/licenses/zairenkai-app/LICENSE",
                "assets/licenses/zairenkai-app/NOTICE", "assets/integration/module-trust.json",
                "assets/integration/repository-lock.json", "assets/system-modules.json"):
        if req not in facts["names"]:
            errors.append(f"apk: {req} missing")
    bad = [n for n in facts["names"] if FORBIDDEN.search(n)]
    if bad:
        errors.append(f"apk: forbidden files {bad}")
    if facts["secrets"]:
        errors.append(f"apk: private-key or token markers in {facts['secrets']}")
    if release:
        signed, detail = apk_signed(apk)
        if signed is not True:
            errors.append(f"release: APK not signed ({detail})")
        errors.extend(manifest_problems(apk))
        if "assets/system-modules.json.sig" not in facts["names"]:
            errors.append("release: module catalog not signed (no ZAIRENKAI_MODULE_SIGNING_KEY_FILE)")
        trust = json.loads((ROOT / "integration/module-trust.json").read_text())
        if not trust["keys"]:
            errors.append("release: integration/module-trust.json lists no module-signing key")
        for cid, lic in licence_status().items():
            if not lic["distributable"]:
                errors.append(f"release: {cid} has no licence file at its pinned commit: it cannot be distributed")
        if profile() != "distributable":
            errors.append("release: this is an internal development build (ZAIRENKAI_INTERNAL_BUILD=1)")
        if any(n.startswith("assets/system-modules/") and not n.endswith("zairenkai.zip") for n in facts["names"]):
            errors.append("release: the APK contains a module other than zairenkai")
    return errors


def licence_status() -> dict:
    """Whether each packaged component (and the app) has a licence file at its pinned commit."""
    errors: list = []
    lock = {c["id"]: c for c in load_lock(errors)["components"]}
    status = {"Zairenkai-App": {"licence_file": (ROOT / "LICENSE").is_file()}}
    for cid in packaged():
        src = OUT / "src" / SRC_DIRS[cid]
        status[cid] = {"licence_file": (src / "LICENSE").is_file(), "declared": lock.get(cid, {}).get("license", "")}
    for v in status.values():
        v["distributable"] = v["licence_file"]
    return status


def report(apk: pathlib.Path, release: bool) -> list:
    lock_errors, build_errors = check_lock(), check_build()
    apk_errors = check_apk(apk, release)
    errors = lock_errors + build_errors + apk_errors
    lock = json.loads(LOCK.read_text())
    signed, sig_detail = apk_signed(apk) if apk.is_file() else (None, "no APK")
    cat_sig = (OUT / "assets/system-modules.json.sig").is_file()
    components = []
    for c in lock["components"]:
        src = OUT / "src" / SRC_DIRS.get(c["id"], c["id"])
        head = subprocess.run(["git", "-C", str(src), "rev-parse", "HEAD"], capture_output=True, text=True)
        components.append({
            "id": c["id"], "repository": c["repository"], "pinned_commit": c["commit"], "merged": c["merged"],
            "checkout_verified": head.returncode == 0 and head.stdout.strip() == c["commit"],
            "packaged": c["id"] in packaged(),
        })
    comp = json.loads((OUT / "components.json").read_text()) if (OUT / "components.json").is_file() else {}
    rep = {
        "schema": 1,
        "kind": "release" if release else ("internal development build (not for distribution)" if profile() == "internal" else "engineering (not for distribution)"),
        "profile": profile(),
        "ci_run": os.environ.get("GITHUB_RUN_ID"),
        "app_commit": subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip(),
        "apk": {"path": str(apk), "sha256": sha256(apk.read_bytes()) if apk.is_file() else None, "signed": signed, "signature": sig_detail},
        "module_catalog_signed": cat_sig,
        "components": components,
        "artifacts": comp.get("files", {}),
        "licences": licence_status(),
        "errors": errors,
        "result": "pass" if not errors else "fail",
    }
    (OUT / "integration-report.json").write_text(json.dumps(rep, indent=2) + "\n")
    md = [f"# Integration build report ({rep['kind']})", "", f"Result: **{rep['result']}**", "",
          f"App commit `{rep['app_commit'][:12]}`, CI run `{rep['ci_run']}`.", "",
          f"APK `{apk.name}`: SHA-256 `{rep['apk']['sha256']}`; signed: {signed} ({sig_detail}).",
          f"Module catalog signed: {cat_sig}.", "",
          "| Component | Repository | Pinned commit | Merged | Checkout verified | Packaged |", "|---|---|---|---|---|---|"]
    md += [f"| {c['id']} | {c['repository']} | `{c['pinned_commit'][:12]}` | {c['merged']} | {c['checkout_verified']} | {c['packaged']} |" for c in components]
    md += ["", "| Licence status | Licence file | Distributable |", "|---|---|---|"]
    md += [f"| {k} | {v['licence_file']} | {v['distributable']} |" for k, v in rep["licences"].items()]
    md += ["", "## Failures", ""] + ([f"- {e}" for e in errors] or ["none"])
    (OUT / "integration-report.md").write_text("\n".join(md) + "\n")
    return errors


def main() -> None:
    args = sys.argv[1:]
    if not args:
        sys.exit(__doc__)
    cmd, release = args[0], "--release" in args
    rest = [a for a in args[1:] if a != "--release"]
    if cmd == "lock":
        errors = check_lock()
    elif cmd == "access":
        errors = check_access()
    elif cmd == "build":
        errors = check_build()
    elif cmd == "apk":
        errors = check_apk(pathlib.Path(rest[0]), release)
    elif cmd == "report":
        errors = report(pathlib.Path(rest[0]), release)
    else:
        sys.exit(__doc__)
    for e in errors:
        print(f"FAIL {e}")
    print(f"{cmd}: {'ok' if not errors else f'{len(errors)} failure(s)'}")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
