#!/usr/bin/env bash
# Build the packaged components at the exact commits in integration/repository-lock.json.
#
# Produces (git-ignored):
#   integration/out/jniLibs/<abi>/libzart.so      zart from Zairenkai-ART (scripts/build-android.sh)
#   integration/out/jniLibs/<abi>/libzthermal.so  zthermal from Zairenkai-ThermalTools (scripts/build-android.sh)
#   integration/out/assets/licenses/zthermal/     zthermal's LICENSE and NOTICE (Apache-2.0)
#   integration/out/assets/licenses/<component>/THIRD_PARTY_NOTICES.txt  licences of every Rust
#                                                   crate compiled into each binary (rust_notices.py)
#   integration/out/assets/afid/device-candidates.json   AFID asset (integration/scripts/afid_asset.py)
#   integration/out/assets/system-modules/<id>.zip  root modules (Core, FAS) from their build-android.sh
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

# --- root modules (Zairenkai-Core, Zairenkai-FAS) ------------------------------------------
# Built by each repository's own script at the locked commit; module_asset.py checks every
# script in the zip against that commit's module/ sources before the zip is packaged.
mkdir -p "$OUT/assets/system-modules"
build_module() { # build_module <lock component> <module id> <dist prefix> <cargo package>
  local comp=$1 id=$2 prefix=$3 commit dir zip
  commit=$(field "$comp" commit)
  dir="$OUT/src/$comp"
  fetch "$dir" "$(field "$comp" url)" "$commit"
  (cd "$dir" && rm -rf dist && scripts/build-android.sh >/dev/null)
  zip=$(ls "$dir"/dist/"$prefix"-v*.zip)
  (cd "$dir" && sha256sum -c --quiet "dist/$(basename "$zip").sha256") # the .sha256 names dist/<zip>
  python3 "$ROOT/integration/scripts/module_asset.py" --spec "$ROOT/integration/system-modules.json" \
    --id "$id" --zip "$zip" --source "$dir" --commit "$commit" --repository "$(field "$comp" repository)" \
    --out "$OUT/assets/system-modules.json"
  install -m 0644 "$zip" "$OUT/assets/system-modules/$id.zip"
  notices "$dir" "$4" "$id"
}
rm -f "$OUT/assets/system-modules.json"
build_module zairenkai-core-module zairenkai_core zairenkai-core zcored
build_module zairenkai-fas-module zairenkai_fas zairenkai-fas zfasd
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
python3 - "$OUT" "$ZART_COMMIT" "$ZART_VERSION" "$ZT_COMMIT" "$ZT_VERSION" "$AFID_COMMIT" <<'PY'
import hashlib, json, pathlib, sys
out, zart_commit, zart_version, zt_commit, zt_version, afid_commit = pathlib.Path(sys.argv[1]), *sys.argv[2:]
files = sorted(p for p in (out / "jniLibs").rglob("*") if p.is_file()) + \
        sorted(p for p in (out / "assets").rglob("*") if p.is_file())
rec = {
    "schema": 1,
    "zart": {"commit": zart_commit, "version": zart_version},
    "zthermal": {"commit": zt_commit, "version": zt_version},
    "afid": {"commit": afid_commit},
    "files": {str(p.relative_to(out)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
}
(out / "components.json").write_text(json.dumps(rec, indent=2) + "\n")
(out / "assets" / "components.json").write_text(json.dumps(rec, indent=2) + "\n")
print(json.dumps(rec, indent=2))
PY
