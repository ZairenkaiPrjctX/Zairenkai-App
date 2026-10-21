#!/usr/bin/env bash
# Capture real outputs of the locked component revisions as contract fixtures for the parser
# tests (integration-core/src/test/resources/contracts/).
#
# Builds zart, zthermal, zcored and zfasd for the host at the commits in integration/repository-lock.json
# and runs them in temporary directories:
#   - zart inspect / dexopt-status (host: Android inputs report "unavailable");
#   - zthermal snapshot against ThermalTools' own synthetic device fixtures (--root, read only)
#     and against this host (no thermal zones, no getprop: every source reports unsupported);
#   - zcored run --dir TMP, then zcored ctl health|capabilities|context|modules|leases|audit and a
#     dry-run submit (Core writes no device nodes; its state stays in TMP);
#   - zfasd run --observe-only --dir TMP (in-memory control backend: no cpufreq writes), then
#     zfasd ctl status and profile.
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
wait_for() { for _ in $(seq 1 50); do [ -S "$1" ] && return 0; sleep 0.2; done; echo "no socket $1" >&2; exit 1; }

mkdir -p "$SRC" "$DEST/zart" "$DEST/zthermal" "$DEST/core" "$DEST/fas"
fetch "$SRC/zart" "$(field zart url)" "$(field zart commit)"
fetch "$SRC/zthermal" "$(field zthermal url)" "$(field zthermal commit)"
fetch "$SRC/core" "$(field zcored-ctl-contract url)" "$(field zcored-ctl-contract commit)"
fetch "$SRC/fas" "$(field zfasd-ctl-contract url)" "$(field zfasd-ctl-contract commit)"
(cd "$SRC/zart" && cargo build -q --locked --release -p zart)
(cd "$SRC/zthermal" && cargo build -q --locked --release -p zthermal)
(cd "$SRC/core" && cargo build -q --locked --release -p zcored)
(cd "$SRC/fas" && cargo build -q --locked --release -p zfasd)

ZART="$SRC/zart/target/release/zart"
"$ZART" inspect >"$DEST/zart/inspect-host.json"
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
  "$ZT" snapshot --framework --root "$SRC/zthermal/tests/fixtures/devices/$dev" >"$DEST/zthermal/snapshot-$dev.json"
done
"$ZT" snapshot >"$DEST/zthermal/snapshot-host.json"

CORE="$SRC/core/target/release/zcored"
"$CORE" run --dir "$TMP/core" >"$TMP/core.log" 2>&1 &
CORE_PID=$!
wait_for "$TMP/core/zcored.sock"
for m in health capabilities context modules leases; do
  "$CORE" ctl --dir "$TMP/core" "$m" >"$DEST/core/$m.json"
done
"$CORE" ctl --dir "$TMP/core" audit '{"limit":5}' >"$DEST/core/audit.json"
"$CORE" ctl --dir "$TMP/core" submit \
  '{"operation":{"op":"fas_set_profile","profile":"performance"},"dry_run":true}' >"$DEST/core/submit-dry-run.json"

FAS="$SRC/fas/target/release/zfasd"
"$FAS" run --observe-only --dir "$TMP/fas" >"$TMP/fas.log" 2>&1 &
FAS_PID=$!
wait_for "$TMP/fas/zfasd.sock"
sleep 3 # let the daemon publish a full snapshot
"$FAS" ctl --dir "$TMP/fas" status >"$DEST/fas/status.json"
"$FAS" ctl --dir "$TMP/fas" profile balanced >"$DEST/fas/profile-ok.json"

python3 - "$DEST" "$LOCK" <<'PY'
import json, sys, pathlib
dest, lock = pathlib.Path(sys.argv[1]), json.load(open(sys.argv[2]))
rev = {c["id"]: c["commit"] for c in lock["components"]}
(dest / "PROVENANCE.json").write_text(json.dumps({
    "captured_by": "integration/scripts/capture-contracts.sh",
    "host": "Linux container (no Android services: device inputs report unavailable)",
    "zart": rev["zart"], "zthermal": rev["zthermal"], "zcored": rev["zcored-ctl-contract"], "zfasd": rev["zfasd-ctl-contract"],
}, indent=2) + "\n")
PY
ls -R "$DEST"
