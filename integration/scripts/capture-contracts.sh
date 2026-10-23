#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
# Capture real outputs of the locked component revisions as contract fixtures for the parser
# tests (integration-core/src/test/resources/contracts/).
#
# Builds zart, zthermal, zcored, zfasd and ztune for the host at the commits in integration/repository-lock.json
# and runs them in temporary directories:
#   - zart inspect / dexopt-status (host: Android inputs report "unavailable");
#   - zthermal snapshot against ThermalTools' own synthetic device fixtures (--root, read only)
#     and against this host (no thermal zones, no getprop: every source reports unsupported);
#   - zcored run --dir TMP, then zcored ctl health|capabilities|context|modules|leases|audit and a
#     dry-run submit (Core writes no device nodes; its state stays in TMP);
#   - zfasd run --observe-only --dir TMP (in-memory control backend: no cpufreq writes), then
#     zfasd ctl status and profile.
#   - Core's own module inventory over a temporary root holding the module.prop packaged in
#     zairenkai.zip, so the modules result carries an installed module and its version;
#   - zcored ctl tuning on the host daemon, which has no ztune installed (Core's real error), and
#     Core's own tuning adapter running ztune (built for the host from the commit
#     Zairenkai-Modules locks) on this host's /sys and /proc, wrapped in Core's Response;
#   - the module catalog build-components.sh wrote from the real module zips (run it first).
# Requires: git read access to the repositories, Rust (stable), python3.
set -euo pipefail

cd "$(dirname "$0")/../.."
ROOT=$(pwd)
LOCK="$ROOT/integration/repository-lock.json"
SRC="$ROOT/integration/out/src"
DEST="$ROOT/integration-core/src/test/resources/contracts"
TMP=$(mktemp -d)
cleanup() {
  for pid in ${CORE_PID:-} ${FAS_PID:-}; do
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true # the daemons write a final state file on exit
  done
  rm -rf "$TMP"
}
trap cleanup EXIT

field() {
  python3 -c 'import json,sys
c=[c for c in json.load(open(sys.argv[1]))["components"] if c["id"]==sys.argv[2]]
print(c[0][sys.argv[3]])' "$LOCK" "$1" "$2"
}
fetch() {
  local dir=$1 url=$2 commit=$3
  [ -d "$dir/.git" ] || { git init -q "$dir"; git -C "$dir" remote add origin "$url"; }
  git -C "$dir" fetch -q --depth 1 origin "$commit"
  git -C "$dir" -c advice.detachedHead=false checkout -q --force FETCH_HEAD
  [ "$(git -C "$dir" rev-parse HEAD)" = "$commit" ] || { echo "wrong commit in $dir" >&2; exit 1; }
}
wait_for() { for _ in $(seq 1 50); do [ -S "$1" ] && return 0; sleep 0.2; done; echo "no socket $1" >&2; return 1; }
# capture <file> <command...>: the command's stdout is the fixture. On failure, say which
# command failed and show what it printed (zcored ctl prints its error response on stdout).
capture() {
  local out=$1
  shift
  "$@" >"$out" || {
    local code=$?
    echo "capture-contracts: '$*' exited $code; its output:" >&2
    cat "$out" >&2
    exit "$code"
  }
}
# capture_error <file> <command...>: like capture, for a command that must answer with Core's
# error response and exit 1 (zcored ctl's code for an error response).
capture_error() {
  local out=$1 code=0
  shift
  "$@" >"$out" || code=$?
  if [ "$code" -ne 1 ] || ! python3 -c 'import json,sys; sys.exit(json.load(open(sys.argv[1]))["ok"] is not False)' "$out"; then
    echo "capture-contracts: '$*' exited $code; expected an error response with exit 1. Output:" >&2
    cat "$out" >&2
    exit 1
  fi
}

mkdir -p "$SRC" "$DEST/zart" "$DEST/zthermal" "$DEST/core" "$DEST/fas" "$DEST/system-modules"
CATALOG="$ROOT/integration/out/assets/system-modules.json"
[ -f "$CATALOG" ] || { echo "run integration/scripts/build-components.sh first ($CATALOG missing)" >&2; exit 1; }
cp "$CATALOG" "$DEST/system-modules/catalog.json"
fetch "$SRC/zart" "$(field zart url)" "$(field zart commit)"
fetch "$SRC/zthermal" "$(field zthermal url)" "$(field zthermal commit)"
fetch "$SRC/core" "$(field zcored-ctl-contract url)" "$(field zcored-ctl-contract commit)"
fetch "$SRC/fas" "$(field zfasd-ctl-contract url)" "$(field zfasd-ctl-contract commit)"
(cd "$SRC/zart" && cargo build -q --locked --release -p zart)
(cd "$SRC/zthermal" && cargo build -q --locked --release -p zthermal)
(cd "$SRC/core" && cargo build -q --locked --release -p zcored)
(cd "$SRC/fas" && cargo build -q --locked --release -p zfasd)

ZART="$SRC/zart/target/release/zart"
capture "$DEST/zart/inspect-host.json" "$ZART" inspect
"$ZART" dexopt-status com.example.app >"$DEST/zart/dexopt-status-host.json" || true

# Populated reports: zart's own library serialises inspect/dexopt results for the fixture
# inputs used by its tests (fixture root + canned getprop/cmd output, no device).
mkdir -p "$TMP/zart-fixture/src"
cat >"$TMP/zart-fixture/Cargo.toml" <<TOML
[package]
name = "zart-fixture"
version = "0.0.0"
edition = "2021"
publish = false
[dependencies]
zart = { path = "$SRC/zart/crates/zart" }
serde_json = "1"
[workspace]
TOML
cp "$ROOT/integration/scripts/zart_fixture_main.rs" "$TMP/zart-fixture/src/main.rs"
cp "$SRC/zart/Cargo.lock" "$TMP/zart-fixture/Cargo.lock"
(cd "$TMP/zart-fixture" && cargo run -q --release -- "$TMP/zart-root" "$DEST/zart")

ZT="$SRC/zthermal/target/release/zthermal"
for dev in synthetic-qcom synthetic-pixel synthetic-mtk empty-host; do
  capture "$DEST/zthermal/snapshot-$dev.json" "$ZT" snapshot --framework --root "$SRC/zthermal/tests/fixtures/devices/$dev"
done
capture "$DEST/zthermal/snapshot-host.json" "$ZT" snapshot

CORE="$SRC/core/target/release/zcored"
mkdir -p "$TMP/core"
# Core answers root, and any other uid only when it is a configured principal ("uid N is not a
# configured Core client"). CI runs as an unprivileged user: register it as an app client allowed
# the one operation the dry-run submit asks for, as Core's own daemon tests do.
if [ "$(id -u)" -ne 0 ]; then
  printf '[[principals]]\nuid = %s\nrole = "app"\ngrants = ["fas_set_profile"]\n' "$(id -u)" >"$TMP/core/config.toml"
fi
"$CORE" run --dir "$TMP/core" >"$TMP/core.log" 2>&1 &
CORE_PID=$!
wait_for "$TMP/core/zcored.sock" || { cat "$TMP/core.log" >&2; exit 1; }
for m in health capabilities context modules leases; do
  capture "$DEST/core/$m.json" "$CORE" ctl --dir "$TMP/core" "$m"
done
capture "$DEST/core/audit.json" "$CORE" ctl --dir "$TMP/core" audit '{"limit":5}'
capture "$DEST/core/submit-dry-run.json" "$CORE" ctl --dir "$TMP/core" submit \
  '{"operation":{"op":"fas_set_profile","profile":"performance"},"dry_run":true}'
# The host has no installed module, so Core reports ztune as not installed.
capture_error "$DEST/core/tuning-not-installed.json" "$CORE" ctl --dir "$TMP/core" tuning

# The host has no /data/adb/modules: the daemon reports every module not_installed with
# version null. Core's inventory and Response types serialise one installed module instead.
CORE_ZIP="$ROOT/integration/out/assets/system-modules/zairenkai.zip"
[ -f "$CORE_ZIP" ] || { echo "run integration/scripts/build-components.sh first ($CORE_ZIP missing)" >&2; exit 1; }
python3 -c 'import sys, zipfile; sys.stdout.buffer.write(zipfile.ZipFile(sys.argv[1]).read("module.prop"))' "$CORE_ZIP" >"$TMP/core-module.prop"
mkdir -p "$TMP/core-fixture/src"
cat >"$TMP/core-fixture/Cargo.toml" <<TOML
[package]
name = "core-fixture"
version = "0.0.0"
edition = "2021"
publish = false
[dependencies]
zcore-domain = { path = "$SRC/core/crates/zcore-domain" }
zcore-platform = { path = "$SRC/core/crates/zcore-platform" }
serde_json = "1"
libc = "0.2"
[workspace]
TOML
cp "$ROOT/integration/scripts/core_fixture_main.rs" "$TMP/core-fixture/src/main.rs"
cp "$SRC/core/Cargo.lock" "$TMP/core-fixture/Cargo.lock"
(cd "$TMP/core-fixture" && cargo run -q --release -- modules "$TMP/core-module.prop" "$TMP/core-root" "$DEST/core/modules-installed.json")

# ztune at the commit Zairenkai-Modules locks (fetched by build-components.sh), built for the host.
TUNING_SRC="$ROOT/integration/out/src/zairenkai-tuning"
[ -f "$TUNING_SRC/Cargo.lock" ] || { echo "run integration/scripts/build-components.sh first ($TUNING_SRC missing)" >&2; exit 1; }
(cd "$TUNING_SRC" && cargo build -q --locked --release -p ztune)
(cd "$TMP/core-fixture" && cargo run -q --release -- tuning "$TUNING_SRC/target/release/ztune" "$DEST/core/tuning.json")

FAS="$SRC/fas/target/release/zfasd"
"$FAS" run --observe-only --dir "$TMP/fas" >"$TMP/fas.log" 2>&1 &
FAS_PID=$!
wait_for "$TMP/fas/zfasd.sock" || { cat "$TMP/fas.log" >&2; exit 1; }
sleep 3 # let the daemon publish a full snapshot
capture "$DEST/fas/status.json" "$FAS" ctl --dir "$TMP/fas" status
capture "$DEST/fas/profile-ok.json" "$FAS" ctl --dir "$TMP/fas" profile balanced

python3 - "$DEST" "$LOCK" "$(git -C "$TUNING_SRC" rev-parse HEAD)" <<'PY'
import json, sys, pathlib
dest, lock = pathlib.Path(sys.argv[1]), json.load(open(sys.argv[2]))
rev = {c["id"]: c["commit"] for c in lock["components"]}
(dest / "PROVENANCE.json").write_text(json.dumps({
    "captured_by": "integration/scripts/capture-contracts.sh",
    "host": "Linux container (no Android services: device inputs report unavailable)",
    "zart": rev["zart"], "zthermal": rev["zthermal"], "zcored": rev["zcored-ctl-contract"], "zfasd": rev["zfasd-ctl-contract"],
    "ztune": sys.argv[3],
}, indent=2) + "\n")
PY
ls -R "$DEST"
