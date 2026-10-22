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
#   integration/out/assets/afid/device-candidates.json   AFID asset (integration/scripts/afid_asset.py)
#   integration/out/assets/system-modules/zairenkai.zip  the single root module: Zairenkai-Modules'
#                                                   assemble.py over the Core and FAS components
#   integration/out/assets/system-modules.json      module catalog: integration/system-modules.json plus
#                                                   size, SHA-256 and per-entry digests (module_asset.py)
#   integration/out/components.json               what was built, from which commit, with SHA-256
#
# Requires: git with read access to the private ZairenkaiPrjctX repositories, Rust with the
# aarch64/armv7/x86_64 Android targets, ANDROID_NDK_HOME (NDK r27d is what the component repos
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
(cd "$OUT/src/zart" && rm -rf dist && scripts/build-android.sh)
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
(cd "$OUT/src/zthermal" && rm -rf dist && scripts/build-android.sh)
ZT_VERSION=$(sed -n 's/^version = "\(.*\)"/\1/p' "$OUT/src/zthermal/Cargo.toml" | head -n1)
(cd "$OUT/src/zthermal/dist" && sha256sum -c --quiet SHA256SUMS)
for abi in arm64-v8a armeabi-v7a x86_64; do
  mkdir -p "$OUT/jniLibs/$abi"
  install -m 0755 "$OUT/src/zthermal/dist/zthermal-$ZT_VERSION-$abi" "$OUT/jniLibs/$abi/libzthermal.so"
done
mkdir -p "$OUT/assets/licenses/zthermal"
install -m 0644 "$OUT/src/zthermal/LICENSE" "$OUT/src/zthermal/NOTICE" "$OUT/assets/licenses/zthermal/"
notices "$OUT/src/zthermal" zthermal zthermal

# --- the zairenkai root module (Zairenkai-Modules, with Zairenkai-Core and Zairenkai-FAS) -----
# One module. Zairenkai-Modules at its locked commit names the Core and FAS commits it packages
# (components.lock.json); each is built by its own scripts/build-android.sh, then assembled by
# Modules' scripts/assemble.py, which checks every component file against its component.json.
# module_asset.py then checks every script in the zip against Modules' module/ sources.
MOD_COMMIT=$(field zairenkai-module commit)
MOD_DIR="$OUT/src/zairenkai-module"
fetch "$MOD_DIR" "$(field zairenkai-module url)" "$MOD_COMMIT"
pinned() { # pinned <component name in the Modules lock> <key>
  python3 -c 'import json,sys
c=[c for c in json.load(open(sys.argv[1]))["components"] if c["name"]==sys.argv[2]]
print(c[0][sys.argv[3]]) if c else sys.exit("components.lock.json has no "+sys.argv[2])' \
    "$MOD_DIR/components.lock.json" "$1" "$2"
}
# The ctl contracts the app speaks must be the daemons the module packages.
for pair in zcored-ctl-contract:zairenkai-core zfasd-ctl-contract:zairenkai-fas; do
  [ "$(field "${pair%%:*}" commit)" = "$(pinned "${pair#*:}" commit)" ] ||
    { echo "${pair%%:*} is not the ${pair#*:} commit Zairenkai-Modules packages" >&2; exit 1; }
done
rm -f "$OUT/assets/system-modules.json"
rm -rf "$OUT/assets/system-modules" "$OUT/assets/licenses/zairenkai-core" "$OUT/assets/licenses/zairenkai-fas"
mkdir -p "$OUT/assets/system-modules"
components=()
for spec in zairenkai-core:zcored zairenkai-fas:zfasd; do
  name=${spec%%:*}
  dir="$OUT/src/$name"
  fetch "$dir" "https://github.com/$(pinned "$name" repository).git" "$(pinned "$name" commit)"
  (cd "$dir" && rm -rf dist && scripts/build-android.sh >/dev/null)
  components+=(--component "$dir/dist/$name")
  notices "$dir" "${spec#*:}" "$name"
done
rm -rf "$MOD_DIR/dist"
python3 "$MOD_DIR/scripts/assemble.py" --lock "$MOD_DIR/components.lock.json" "${components[@]}" --out "$MOD_DIR/dist"
zip=$(ls "$MOD_DIR"/dist/zairenkai-v*.zip)
(cd "$MOD_DIR/dist" && sha256sum -c --quiet "$(basename "$zip").sha256")
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
fetch "$OUT/src/afid" "$(field afid-device-candidates url)" "$AFID_COMMIT" \
  /database/staging/unresolved/lineageos_wiki/ /NOTICE /LICENSE
python3 "$ROOT/integration/scripts/afid_asset.py" --source "$OUT/src/afid" --commit "$AFID_COMMIT" \
  --out "$OUT/assets/afid/device-candidates.json"

# --- record what was built ----------------------------------------------------------------
python3 - "$OUT" "$ZART_COMMIT" "$ZART_VERSION" "$ZT_COMMIT" "$ZT_VERSION" "$AFID_COMMIT" "$PROFILE" <<'PY'
import hashlib, json, pathlib, sys
out, zart_commit, zart_version, zt_commit, zt_version, afid_commit, profile = pathlib.Path(sys.argv[1]), *sys.argv[2:]
files = sorted(p for p in (out / "jniLibs").rglob("*") if p.is_file()) + \
        sorted(p for p in (out / "assets").rglob("*") if p.is_file())
rec = {
    "schema": 1,
    "profile": profile,
    "zart": {"commit": zart_commit, "version": zart_version},
    "zthermal": {"commit": zt_commit, "version": zt_version},
    "afid": {"commit": afid_commit},
    "files": {str(p.relative_to(out)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
}
(out / "components.json").write_text(json.dumps(rec, indent=2) + "\n")
(out / "assets" / "components.json").write_text(json.dumps(rec, indent=2) + "\n")
print(json.dumps(rec, indent=2))
PY
