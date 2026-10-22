// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
//! Serialise Core's `modules` result with a Zairenkai module installed (used by
//! capture-contracts.sh). The host has no /data/adb/modules, so the daemon's own capture only
//! shows `not_installed` entries with `version: null`. This runs Core's real inventory over a
//! temporary root that holds the module.prop packaged in zairenkai.zip, and wraps the
//! result in Core's real protocol Response.
//!
//! Usage: core-fixture <module.prop> <temporary root> <output file>

use std::fs;
use std::path::PathBuf;

use zcore_domain::protocol::Response;
use zcore_platform::modules::{inventory, MODULES_DIR};
use zcore_platform::sysroot::SysRoot;

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let [prop, root, out] = args.as_slice() else {
        eprintln!("usage: core-fixture <module.prop> <temporary root> <output file>");
        std::process::exit(64);
    };
    let root = PathBuf::from(root);
    let module = root.join(MODULES_DIR.trim_start_matches('/')).join("zairenkai");
    fs::create_dir_all(&module).expect("create module directory");
    fs::copy(prop, module.join("module.prop")).expect("copy module.prop");
    let mods = inventory(&SysRoot::at(&root), MODULES_DIR);
    assert!(
        mods.iter().any(|m| m.id == "zairenkai" && m.version.is_some()),
        "inventory did not report zairenkai with a version: {mods:?}"
    );
    let resp = Response::ok("fixture-modules", serde_json::to_value(&mods).expect("serialise"));
    fs::write(out, serde_json::to_string_pretty(&resp).expect("serialise") + "\n").expect("write");
}
