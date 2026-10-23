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
//! Zairenkai-Modules locks, owned by the capturing user, and wraps its telemetry document in
//! Core's real Response, as the daemon's `tuning` method does. ztune reads this host's /sys and
//! /proc; nothing is written.
//!
//! Usage: core-fixture modules <module.prop> <temporary root> <output file>
//!        core-fixture tuning <absolute path to ztune> <output file>

use std::fs;
use std::path::PathBuf;
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

fn tuning(ztune: &str, out: &str) {
    // SAFETY: getuid has no preconditions.
    let uid = unsafe { libc::getuid() };
    let adapter = TuningAdapter::owned_by(SysRoot::real(), Arc::new(SystemRunner), Some(ztune), uid);
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
        ["tuning", ztune, out] => tuning(ztune, out),
        _ => {
            eprintln!("usage: core-fixture modules <module.prop> <temporary root> <output file>");
            eprintln!("       core-fixture tuning <absolute path to ztune> <output file>");
            std::process::exit(64);
        }
    }
}
