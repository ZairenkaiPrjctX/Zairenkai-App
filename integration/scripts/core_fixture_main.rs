// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
//! Core results the host daemon cannot produce, serialised by Core's own code (used by
//! capture-contracts.sh).
//!
//! `modules`: the host has no /data/adb/modules, so the daemon's own capture only shows
//! `not_installed` entries with `version: null`. This runs Core's real inventory over a
//! temporary root that holds the module.prop packaged in zairenkai.zip, and wraps the result in
//! Core's real protocol Response.
//!
//! `tuning`: the daemon only runs a root-owned ztune from the installed module. This runs Core's
//! real tuning adapter (fixed argv, envelope validation) on a ztune built from the commit
//! Zairenkai-Modules locks, and wraps its telemetry document in Core's real Response, as the
//! daemon's `tuning` method does. ztune reads this host's /sys and /proc; nothing is written.
//!
//! Core's ownership check walks from the binary up to its SysRoot and requires every step to be
//! owned by `owner_uid`. Under the real root that walk ends at a root-owned `/`, so a non-root
//! capture (CI runs as uid 1001) can never pass it. The capture therefore uses a temporary
//! SysRoot owned by the capturing user: ztune is copied to `<work>/bin/ztune`, the path Core
//! executes, and the same file to `<work>/<work>/bin/ztune`, the path Core's check resolves
//! inside that SysRoot. Core's check, argv and validation run unmodified.
//!
//! Usage: core-fixture modules <module.prop> <temporary root> <output file>
//!        core-fixture tuning <ztune> <empty work directory> <output file>

use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use zcore_domain::protocol::Response;
use zcore_platform::exec::SystemRunner;
use zcore_platform::modules::{inventory, MODULES_DIR};
use zcore_platform::sysroot::SysRoot;
use zcore_platform::tuning::TuningAdapter;

fn write(out: &str, resp: &Response) {
    fs::write(out, serde_json::to_string_pretty(resp).expect("serialise") + "\n").expect("write");
}

fn modules(prop: &str, root: &str, out: &str) {
    let root = PathBuf::from(root);
    let module = root.join(MODULES_DIR.trim_start_matches('/')).join("zairenkai");
    fs::create_dir_all(&module).expect("create module directory");
    fs::copy(prop, module.join("module.prop")).expect("copy module.prop");
    let mods = inventory(&SysRoot::at(&root), MODULES_DIR);
    assert!(
        mods.iter().any(|m| m.id == "zairenkai" && m.version.is_some()),
        "inventory did not report zairenkai with a version: {mods:?}"
    );
    write(out, &Response::ok("fixture-modules", serde_json::to_value(&mods).expect("serialise")));
}

fn install(ztune: &str, dest: &Path, top: &Path) {
    fs::create_dir_all(dest.parent().expect("parent")).expect("create directories");
    fs::copy(ztune, dest).expect("copy ztune");
    fs::set_permissions(dest, fs::Permissions::from_mode(0o755)).expect("chmod ztune");
    // Directories created under the work directory: owned by this user, not group/other writable.
    let mut d = dest.parent();
    while let Some(dir) = d.filter(|d| d.starts_with(top)) {
        fs::set_permissions(dir, fs::Permissions::from_mode(0o755)).expect("chmod directory");
        d = dir.parent();
    }
}

fn tuning(ztune: &str, work: &str, out: &str) {
    let work = fs::canonicalize(work).expect("work directory");
    let executed = work.join("bin/ztune");
    let checked = work.join(executed.strip_prefix("/").expect("absolute work directory"));
    install(ztune, &executed, &work);
    install(ztune, &checked, &work);
    // SAFETY: getuid has no preconditions.
    let uid = unsafe { libc::getuid() };
    let binary = executed.to_str().expect("UTF-8 path");
    let adapter = TuningAdapter::owned_by(SysRoot::at(&work), Arc::new(SystemRunner), Some(binary), uid);
    match adapter.telemetry() {
        Ok(doc) => write(out, &Response::ok("fixture-tuning", doc)),
        Err(e) => {
            eprintln!("core-fixture: ztune telemetry through Core's adapter failed: {e}");
            std::process::exit(1);
        }
    }
}

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match args.iter().map(String::as_str).collect::<Vec<_>>().as_slice() {
        ["modules", prop, root, out] => modules(prop, root, out),
        ["tuning", ztune, work, out] => tuning(ztune, work, out),
        _ => {
            eprintln!("usage: core-fixture modules <module.prop> <temporary root> <output file>");
            eprintln!("       core-fixture tuning <ztune> <empty work directory> <output file>");
            std::process::exit(64);
        }
    }
}
