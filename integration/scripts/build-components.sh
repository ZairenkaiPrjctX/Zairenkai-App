#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
# Build the packaged components at the exact commits in integration/repository-lock.json.
#
# Produces (git-ignored):
#   integration/out/jniLibs/<abi>/libzart.so      zart from Zairenkai-ART (scripts/build-android.sh)
#   integration/out/jniLibs/<abi>/libzthermal.so  zthermal from Zairenkai-ThermalTools (scripts/build-android.sh)
#   integration/out/assets/licenses/zthermal/     zthermal's LICENSE and NOTICE (Apache-2.0)
#   integration/out/assets/licenses/zart/         zart's LICENSE and NOTICE (Apache-2.0, with the AOSP attribution)
#   integration/out/assets/licenses/<component>/THIRD_PARTY_NOTICES.txt  licences of every Rust
#                                                   crate compiled into each binary (rust_notices.py)
#   integration/out/jniLibs/{arm64-v8a,x86_64}/libzierun.so  zie-run from Zairenkai-Intelligence-Engine
#   integration/out/assets/intelligence/          its tool registry, grammar and model manifest
#   integration/out/assets/afid/device-candidates.json   AFID asset (integration/scripts/afid_asset.py)
#   integration/out/assets/system-modules/zairenkai.zip  the single root module: Zairenkai-Modules'
#                                                   assemble.py over the Core and FAS components
#   integration/out/assets/system-modules.json      module catalog: integration/system-modules.json plus
#                                                   size, SHA-256 and per-entry digests (module_asset.py)
#   integration/out/components.json               what was built, from which commit, with SHA-256
#
# Requires: git (the locked ZairenkaiPrjctX repositories are public), Rust with the
# aarch64/armv7/x86_64 Android targets, cmake and ninja (zie-run), ANDROID_NDK_HOME (NDK r27d is what the component repos
# use), python3 >= 3.11. Sources are fetched by commit into integration/out/src; nothing is
# pushed. The fetched build script is the one reviewed at that commit; a different commit fails.
set -euo pipefail

cd "$(dirname "$0")/../.."
ROOT=$(pwd)
LOCK="$ROOT/integration/repository-lock.json"
OUT="$ROOT/integration/out"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to an Android NDK directory}"

field() { # field <component-id> <key>
  python3 -c 'import json,sys
c=[c for c in json.load(open(sys.argv[1]))["components"] if c["id"]==sys.argv[2]]
print(c[0][sys.argv[3]]) if c else sys.exit("no component "+sys.argv[2])' "$LOCK" "$1" "$2"
}

fetch() { # fetch <dir> <url> <commit> [sparse paths...]
  local dir=$1 url=$2 commit=$3
  shift 3
  if [ ! -d "$dir/.git" ]; then
    git init -q "$dir"
    git -C "$dir" remote add origin "$url"
  fi
  if [ "$#" -gt 0 ]; then
    git -C "$dir" sparse-checkout set --no-cone "$@"
  fi
  git -C "$dir" fetch -q --depth 1 origin "$commit"
  git -C "$dir" -c advice.detachedHead=false checkout -q --force FETCH_HEAD
  local head
  head=$(git -C "$dir" rev-parse HEAD)
  [ "$head" = "$commit" ] || { echo "fetched $head, expected $commit" >&2; exit 1; }
}

mkdir -p "$OUT/src"
rm -rf "$OUT/jniLibs" "$OUT/assets"

# --- zart (Zairenkai-ART) ---------------------------------------------------------------
ZART_COMMIT=$(field zart commit)
fetch "$OUT/src/zart" "$(field zart url)" "$ZART_COMMIT"
ZART_DIST="$OUT/src/zart/dist"
if python3 "$ROOT/integration/scripts/fetch_runtime_artifact.py" --lock "$LOCK" --component zart --key id --out "$ZART_DIST"; then
  echo "zart: consuming checksum-verified immutable artifact for $ZART_COMMIT"
else
  rc=$?
  [ "$rc" -eq 3 ] || { echo "zart artifact pin exists but verification failed; refusing source fallback" >&2; exit "$rc"; }
  echo "zart: no pinned artifact; building from the exact locked source commit"
  (cd "$OUT/src/zart" && rm -rf dist && scripts/build-android.sh)
fi
ZART_VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' "$OUT/src/zart/Cargo.toml" | head -n1)
for abi in arm64-v8a armeabi-v7a x86_64; do
  mkdir -p "$OUT/jniLibs/$abi"
  install -m 0755 "$OUT/src/zart/dist/zart-$ZART_VERSION-$abi" "$OUT/jniLibs/$abi/libzart.so"
done
(cd "$OUT/src/zart/dist" && sha256sum -c --quiet SHA256SUMS)
notices() { # notices <workspace> <package> <component>
  python3 "$ROOT/integration/scripts/rust_notices.py" --workspace "$1" --package "$2" \
    --target aarch64-linux-android --component "$3" --out "$OUT/assets/licenses/$3/THIRD_PARTY_NOTICES.txt"
}
notices "$OUT/src/zart" zart zart
install -m 0644 "$OUT/src/zart/LICENSE" "$OUT/src/zart/NOTICE" "$OUT/assets/licenses/zart/" # NOTICE: AOSP attribution
install -m 0644 "$ROOT/integration/notices/zart-aosp-attribution.txt" "$OUT/assets/licenses/zart/AOSP-ATTRIBUTION.txt"

# --- zthermal (Zairenkai-ThermalTools) ---------------------------------------------------
ZT_COMMIT=$(field zthermal commit)
fetch "$OUT/src/zthermal" "$(field zthermal url)" "$ZT_COMMIT"
ZT_DIST="$OUT/src/zthermal/dist"
if python3 "$ROOT/integration/scripts/fetch_runtime_artifact.py" --lock "$LOCK" --component zthermal --key id --out "$ZT_DIST"; then
  echo "zthermal: consuming checksum-verified immutable artifact for $ZT_COMMIT"
else
  rc=$?
  [ "$rc" -eq 3 ] || { echo "zthermal artifact pin exists but verification failed; refusing source fallback" >&2; exit "$rc"; }
  echo "zthermal: no pinned artifact; building from the exact locked source commit"
  (cd "$OUT/src/zthermal" && rm -rf dist && scripts/build-android.sh)
fi
ZT_VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' "$OUT/src/zthermal/Cargo.toml" | head -n1)
(cd "$OUT/src/zthermal/dist" && sha256sum -c --quiet SHA256SUMS)
for abi in arm64-v8a armeabi-v7a x86_64; do
  mkdir -p "$OUT/jniLibs/$abi"
  install -m 0755 "$OUT/src/zthermal/dist/zthermal-$ZT_VERSION-$abi" "$OUT/jniLibs/$abi/libzthermal.so"
done
mkdir -p "$OUT/assets/licenses/zthermal"
install -m 0644 "$OUT/src/zthermal/LICENSE" "$OUT/src/zthermal/NOTICE" "$OUT/assets/licenses/zthermal/"
notices "$OUT/src/zthermal" zthermal zthermal

# --- zie-run (Zairenkai-Intelligence-Engine) -----------------------------------------------
# The Local AI runner, built by the engine's own script against the llama.cpp commit it pins
# (LLAMA_CPP_SRC, if set, names a checkout of that commit). The script refuses networking imports.
# Only arm64-v8a and x86_64 are built. The model file is never packaged: the user imports it.
ZIE_COMMIT=$(field zie-run commit)
ZIE="$OUT/src/zie"
fetch "$ZIE" "$(field zie-run url)" "$ZIE_COMMIT"
ZIE_DIST="$ZIE/dist/zairenkai-intelligence"
if python3 "$ROOT/integration/scripts/fetch_runtime_artifact.py" --lock "$LOCK" --component zie-run --key id --out "$ZIE_DIST"; then
  echo "zie-run: consuming checksum-verified immutable artifact for $ZIE_COMMIT"
else
  rc=$?
  [ "$rc" -eq 3 ] || { echo "zie-run artifact pin exists but verification failed; refusing source fallback" >&2; exit "$rc"; }
  echo "zie-run: no pinned artifact; building runtime from the exact locked source commit"
  (cd "$ZIE" && rm -rf dist && scripts/build-android.sh)
fi
python3 - "$ZIE_DIST" "$ZIE_COMMIT" <<'PY'
import hashlib, json, pathlib, sys
d, commit = pathlib.Path(sys.argv[1]), sys.argv[2]
c = json.loads((d / "component.json").read_text())
if c["commit"] != commit:
    sys.exit(f"zie-run was built from {c['commit']}, expected {commit}")
for name, digest in c["files"].items():
    if hashlib.sha256((d / name).read_bytes()).hexdigest() != digest:
        sys.exit(f"zie-run component: {name} does not match component.json")
PY
ZIE_LLAMA=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["llama_cpp"]["tag"])' "$ZIE_DIST/component.json")
for abi in arm64-v8a x86_64; do
  mkdir -p "$OUT/jniLibs/$abi"
  install -m 0755 "$ZIE_DIST/bin/$abi/zie-run" "$OUT/jniLibs/$abi/libzierun.so"
done
mkdir -p "$OUT/assets/intelligence" "$OUT/assets/licenses/zie-run" "$OUT/assets/licenses/llama.cpp"
install -m 0644 "$ZIE_DIST/registry/tools.json" "$ZIE_DIST/registry/output.gbnf" "$OUT/assets/intelligence/"
install -m 0644 "$ZIE_DIST/models/qwen2.5-0.5b-instruct-q4_0.json" "$OUT/assets/intelligence/model.json"
install -m 0644 "$ZIE_DIST/LICENSE" "$ZIE_DIST/NOTICE" "$OUT/assets/licenses/zie-run/"
install -m 0644 "$ZIE_DIST/licenses/llama.cpp/LICENSE" "$OUT/assets/licenses/llama.cpp/LICENSE"

# --- the zairenkai root module (Zairenkai-Modules, with Core, FAS and Tuning) ---------------
# One module. Zairenkai-Modules at its locked commit names the Core, FAS and Tuning commits it packages
# (components.lock.json); each is built by its own scripts/build-android.sh, then assembled by
# Modules' scripts/assemble.py, which checks every component file against its component.json.
# module_asset.py then checks every script in the zip against Modules' module/ sources.
MOD_COMMIT=$(field zairenkai-module commit)
MOD_DIR="$OUT/src/zairenkai-module"
fetch "$MOD_DIR" "$(field zairenkai-module url)" "$MOD_COMMIT"
MODULE_LOCK="${ZAIRENKAI_MODULE_COMPONENTS_LOCK:-$MOD_DIR/components.lock.json}"
[[ -f "$MODULE_LOCK" ]] || { echo "module component lock is missing: $MODULE_LOCK" >&2; exit 1; }
pinned() { # pinned <component name in the Modules lock> <key>
  python3 -c 'import json,sys
c=[c for c in json.load(open(sys.argv[1]))["components"] if c["name"]==sys.argv[2]]
print(c[0][sys.argv[3]]) if c else sys.exit("components.lock.json has no "+sys.argv[2])' \
    "$MODULE_LOCK" "$1" "$2"
}
# The ctl contracts the app speaks must be the daemons the module packages.
for pair in zcored-ctl-contract:zairenkai-core zfasd-ctl-contract:zairenkai-fas; do
  [ "$(field "${pair%%:*}" commit)" = "$(pinned "${pair#*:}" commit)" ] ||
    { echo "${pair%%:*} is not the ${pair#*:} commit Zairenkai-Modules packages" >&2; exit 1; }
done
rm -f "$OUT/assets/system-modules.json"
rm -rf "$OUT/assets/system-modules" "$OUT/assets/licenses/zairenkai-core" "$OUT/assets/licenses/zairenkai-fas" \
  "$OUT/assets/licenses/zairenkai-tuning"
mkdir -p "$OUT/assets/system-modules" "$OUT/artifacts"
MODULE_ARTIFACT_MODE=source
MODULE_ARTIFACT_TAG=""
ARTIFACT_ZIP=""
# Exit 3 means no release pin is configured yet; any other verification error fails the build.
if python3 "$ROOT/integration/scripts/fetch_module_artifact.py" "$LOCK" "$MOD_DIR" "$OUT/artifacts"; then
  MODULE_ARTIFACT_MODE=release
  MODULE_ARTIFACT_TAG=$(python3 - "$LOCK" <<'PYTAG'
import json, sys
m = next(c for c in json.load(open(sys.argv[1]))["components"] if c["id"] == "zairenkai-module")
print(m["release_artifact"]["release_tag"])
PYTAG
)
  ARTIFACT_ZIP="$OUT/artifacts/zairenkai-${MODULE_ARTIFACT_TAG}.zip"
else
  rc=$?
  [ "$rc" -eq 3 ] || exit "$rc"
  echo "No locked release artifact; using the existing source-build fallback."
fi
components=()
# Fetch all pinned sources in both modes for protocol contract capture, provenance and license checks.
# In artifact mode, the already-built Android binaries are consumed from the release instead of being
# rebuilt independently; source trees are still fetched and hash/commit checked.
for spec in zairenkai-core:zcored zairenkai-fas:zfasd zairenkai-tuning:ztune; do
  name=${spec%%:*}
  dir="$OUT/src/$name"
  fetch "$dir" "https://github.com/$(pinned "$name" repository).git" "$(pinned "$name" commit)"
  if [ "$MODULE_ARTIFACT_MODE" = source ]; then
    (cd "$dir" && rm -rf dist && scripts/build-android.sh >/dev/null)
    components+=(--component "$dir/dist/$name")
  fi
  notices "$dir" "${spec#*:}" "$name"
done
if [ "$MODULE_ARTIFACT_MODE" = source ]; then
  rm -rf "$MOD_DIR/dist"
  assemble_args=(--lock "$MODULE_LOCK" "${components[@]}" --out "$MOD_DIR/dist")
  if [[ -n "${ZAIRENKAI_MODULE_VERSION:-}" ]]; then
    [[ -n "${ZAIRENKAI_MODULE_RELEASE_NOTES_FILE:-}" && -f "$ZAIRENKAI_MODULE_RELEASE_NOTES_FILE" ]] || {
      echo "ZAIRENKAI_MODULE_RELEASE_NOTES_FILE is required with ZAIRENKAI_MODULE_VERSION" >&2; exit 1;
    }
    assemble_args+=(--version "$ZAIRENKAI_MODULE_VERSION" --release-notes-file "$ZAIRENKAI_MODULE_RELEASE_NOTES_FILE")
    python3 - "$MOD_DIR/CHANGELOG.md" "$ZAIRENKAI_MODULE_VERSION" "$ZAIRENKAI_MODULE_RELEASE_NOTES_FILE" <<'PYNOTES'
import pathlib, sys
path, version, notes_file = pathlib.Path(sys.argv[1]), sys.argv[2], pathlib.Path(sys.argv[3])
text = path.read_text(encoding="utf-8")
notes = pathlib.Path(notes_file).read_text(encoding="utf-8").strip()
marker = f"## [{version}] - automated build"
if marker not in text:
    lines = text.splitlines(keepends=True)
    pos = next((i + 1 for i, line in enumerate(lines) if line.startswith("# ")), 0)
    lines[pos:pos] = ["\n", marker + "\n", "\n", notes + "\n\n"]
    path.write_text("".join(lines), encoding="utf-8")
PYNOTES
  fi
  python3 "$MOD_DIR/scripts/assemble.py" "${assemble_args[@]}"
  zip=$(ls "$MOD_DIR"/dist/zairenkai-v*.zip)
  (cd "$MOD_DIR/dist" && sha256sum -c --quiet "$(basename "$zip").sha256")
else
  zip="$ARTIFACT_ZIP"
  [ -f "$zip" ] || { echo "verified module artifact disappeared: $zip" >&2; exit 1; }
fi
python3 "$ROOT/integration/scripts/module_asset.py" --spec "$ROOT/integration/system-modules.json" \
  --id zairenkai --zip "$zip" --source "$MOD_DIR" --commit "$MOD_COMMIT" \
  --repository "$(field zairenkai-module repository)" --out "$OUT/assets/system-modules.json"
install -m 0644 "$zip" "$OUT/assets/system-modules/zairenkai.zip"

PROFILE=distributable
# The catalog is signed only when the module-signing key is provided (release builds); the
# key's public half must be in integration/module-trust.json. Without it the catalog stays
# unsigned and the app refuses to install modules from this build.
rm -f "$OUT/assets/system-modules.json.sig"
if [ -n "${ZAIRENKAI_MODULE_SIGNING_KEY_FILE:-}" ]; then
  "$ROOT/integration/scripts/sign-module-catalog.sh" "$ZAIRENKAI_MODULE_SIGNING_KEY_FILE"
else
  echo "module catalog left unsigned (ZAIRENKAI_MODULE_SIGNING_KEY_FILE not set): module installation is blocked in this build" >&2
fi

# --- AFID device candidates (Zairenkai-Database-Source) ----------------------------------
AFID_COMMIT=$(field afid-device-candidates commit)
mkdir -p "$OUT/assets/afid" "$OUT/artifacts"
if python3 "$ROOT/integration/scripts/fetch_runtime_artifact.py" --lock "$LOCK" --component afid-device-candidates --key id --out "$OUT/artifacts/afid-device-candidates"; then
  AFID_ASSET="$OUT/artifacts/afid-device-candidates/afid-device-candidates.json"
  python3 - "$AFID_ASSET" "$AFID_COMMIT" <<'PYAFID'
import json, pathlib, sys
p, commit = pathlib.Path(sys.argv[1]), sys.argv[2]
if not p.is_file(): raise SystemExit('verified AFID artifact lacks afid-device-candidates.json')
d = json.loads(p.read_text())
if d.get('generated_from', {}).get('commit') != commit: raise SystemExit('AFID artifact was generated from a different database commit')
if d.get('license', {}).get('spdx') != 'CC-BY-SA-3.0' or d.get('verification') != 'unverified':
    raise SystemExit('AFID artifact attribution/license is missing or unexpected')
if not d.get('devices'): raise SystemExit('AFID artifact has no candidates')
PYAFID
  install -m 0644 "$AFID_ASSET" "$OUT/assets/afid/device-candidates.json"
else
  rc=$?
  [ "$rc" -eq 3 ] || { echo "AFID artifact pin exists but verification failed; refusing generated-source fallback" >&2; exit "$rc"; }
  fetch "$OUT/src/afid" "$(field afid-device-candidates url)" "$AFID_COMMIT" \
    /database/staging/unresolved/lineageos_wiki/ /NOTICE /LICENSE
  python3 "$ROOT/integration/scripts/afid_asset.py" --source "$OUT/src/afid" --commit "$AFID_COMMIT" \
    --out "$OUT/assets/afid/device-candidates.json"
fi

# --- record what was built ----------------------------------------------------------------
python3 - "$OUT" "$ZART_COMMIT" "$ZART_VERSION" "$ZT_COMMIT" "$ZT_VERSION" "$AFID_COMMIT" "$PROFILE" "$ZIE_COMMIT" "$ZIE_LLAMA" "$MOD_COMMIT" "$MODULE_ARTIFACT_TAG" "$LOCK" <<'PY'
import hashlib, json, pathlib, sys
out, zart_commit, zart_version, zt_commit, zt_version, afid_commit, profile, zie_commit, zie_llama, module_commit, module_artifact_tag, lock_path = pathlib.Path(sys.argv[1]), *sys.argv[2:]
lock = json.loads(pathlib.Path(lock_path).read_text())
rows = {c["id"]: c for c in lock["components"]}
def provenance(cid, mode):
    pin = rows.get(cid, {}).get("release_artifact")
    return {"mode": mode, "release_tag": pin.get("release_tag") if pin else None,
            "source_commit": pin.get("source_commit") if pin else rows.get(cid, {}).get("commit"),
            "asset_sha256": pin.get("asset_sha256") if pin else None,
            "manifest_sha256": pin.get("manifest_sha256") if pin else None}
files = sorted(p for p in (out / "jniLibs").rglob("*") if p.is_file()) + \
        sorted(p for p in (out / "assets").rglob("*") if p.is_file())
rec = {
    "schema": 1,
    "profile": profile,
    "zart": {"commit": zart_commit, "version": zart_version},
    "zthermal": {"commit": zt_commit, "version": zt_version},
    "afid": {"commit": afid_commit},
    "intelligence": {"commit": zie_commit, "llama_cpp": zie_llama},
    "zairenkai_module": {"commit": module_commit, "artifact_release_tag": module_artifact_tag or None},
    "artifact_provenance": {
        "zart": provenance("zart", "release" if rows.get("zart", {}).get("release_artifact") else "source"),
        "zthermal": provenance("zthermal", "release" if rows.get("zthermal", {}).get("release_artifact") else "source"),
        "afid-device-candidates": provenance("afid-device-candidates", "release" if rows.get("afid-device-candidates", {}).get("release_artifact") else "source"),
        "zie-run": provenance("zie-run", "release" if rows.get("zie-run", {}).get("release_artifact") else "source"),
        "zairenkai-module": {"mode": "release" if module_artifact_tag else "source", "release_tag": module_artifact_tag or None,
                              "source_commit": module_commit,
                              "asset_sha256": (rows.get("zairenkai-module", {}).get("release_artifact") or {}).get("asset_sha256") if module_artifact_tag else None},
    },
    "files": {str(p.relative_to(out)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
}
(out / "components.json").write_text(json.dumps(rec, indent=2) + "\n")
(out / "assets" / "components.json").write_text(json.dumps(rec, indent=2) + "\n")
print(json.dumps(rec, indent=2))
PY
