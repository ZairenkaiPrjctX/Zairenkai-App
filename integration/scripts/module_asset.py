#!/usr/bin/env python3
"""Check a built module zip against its source tree and record it for the app.

Usage: module_asset.py --spec integration/system-modules.json --id <module id>
                       --zip <built zip> --source <checkout at the locked commit>
                       --commit <sha> --repository <owner/name> --out <catalog json>

Checks, failing on any mismatch:
  - module.prop id equals the spec id; version and versionCode are present;
  - no absolute path, '..' component, duplicate or directory-only entry;
  - every entry that also exists under <source>/module/ is byte-identical to it (scripts,
    update-binary, updater-script, webroot), so the installer the app hands to the root manager
    is the reviewed source at that commit.
Appends (or replaces) the module's record in the catalog: spec fields, size, SHA-256, every
entry's SHA-256, and which entries were matched to source.
"""
import argparse
import hashlib
import json
import pathlib
import sys
import zipfile


def fail(msg: str) -> None:
    sys.exit(f"module_asset: {msg}")


def main() -> None:
    ap = argparse.ArgumentParser()
    for a in ("spec", "id", "zip", "source", "commit", "repository", "out"):
        ap.add_argument(f"--{a}", required=True)
    a = ap.parse_args()
    spec = {m["id"]: m for m in json.load(open(a.spec))["modules"]}
    if a.id not in spec:
        fail(f"{a.id} is not in {a.spec}")
    zpath, src = pathlib.Path(a.zip), pathlib.Path(a.source) / "module"
    data = zpath.read_bytes()
    entries, matched = {}, []
    with zipfile.ZipFile(zpath) as z:
        names = [i.filename for i in z.infolist()]
        if len(names) != len(set(names)):
            fail("duplicate entries")
        for info in z.infolist():
            n = info.filename
            if n.startswith("/") or ".." in n.split("/") or "\\" in n or info.is_dir():
                fail(f"unsafe entry {n!r}")
            body = z.read(n)
            entries[n] = hashlib.sha256(body).hexdigest()
            s = src / n
            if s.is_file():
                if s.read_bytes() != body:
                    fail(f"{n} differs from {s}")
                matched.append(n)
        prop = dict(
            line.split("=", 1) for line in z.read("module.prop").decode().splitlines() if "=" in line
        )
    if prop.get("id") != a.id:
        fail(f"module.prop id {prop.get('id')!r} != {a.id!r}")
    if not prop.get("version") or not prop.get("versionCode", "").isdigit():
        fail("module.prop lacks version or versionCode")
    for required in ("customize.sh", "META-INF/com/google/android/update-binary"):
        if required not in matched:
            fail(f"{required} missing or not matched to source")
    rec = dict(spec[a.id])
    rec.update(
        file=f"system-modules/{a.id}.zip",
        version=prop["version"],
        version_code=int(prop["versionCode"]),
        size=len(data),
        sha256=hashlib.sha256(data).hexdigest(),
        source={"repository": a.repository, "commit": a.commit},
        entries=entries,
        source_matched=sorted(matched),
    )
    out = pathlib.Path(a.out)
    cat = json.loads(out.read_text()) if out.exists() else {"schema": 1, "modules": []}
    cat["modules"] = [m for m in cat["modules"] if m["id"] != a.id] + [rec]
    out.write_text(json.dumps(cat, indent=2) + "\n")
    print(f"module_asset: {a.id} {prop['version']} ({len(entries)} entries, {len(matched)} matched to source)")


if __name__ == "__main__":
    main()
