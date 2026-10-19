//! Serialise zart reports for fixture inputs (used by capture-contracts.sh). The inputs mirror
//! zart's own tests: a fixture root and canned getprop / `cmd package art dump` output.

use std::fs;
use std::path::PathBuf;

use zart::exec::FakeRunner;
use zart::sysroot::SysRoot;

const GETPROP: &str = "\
[dalvik.vm.isa.arm.features]: [default]
[dalvik.vm.isa.arm.variant]: [cortex-a55]
[dalvik.vm.isa.arm64.features]: [default]
[dalvik.vm.isa.arm64.variant]: [cortex-a76]
[dalvik.vm.usejit]: [true]
[pm.dexopt.install]: [speed-profile]
[ro.build.version.release]: [17]
[ro.build.version.sdk]: [37]
[ro.product.cpu.abilist]: [arm64-v8a,armeabi-v7a,armeabi]
[ro.serialno]: [MUST-NOT-APPEAR]
";

const APEX_LIST: &str = r#"<?xml version="1.0" encoding="utf-8"?>
<apex-info-list>
    <apex-info moduleName="com.android.art" modulePath="/data/apex/active/com.android.art@370000000.apex" preinstalledModulePath="/system/apex/com.google.android.art.apex" versionCode="370000000" versionName="" isFactory="false" isActive="true" lastUpdateMillis="1760000000000" provideSharedApexLibs="false" partition="SYSTEM"/>
    <apex-info moduleName="com.android.art" modulePath="/system/apex/com.google.android.art.apex" preinstalledModulePath="/system/apex/com.google.android.art.apex" versionCode="361000000" versionName="" isFactory="true" isActive="false" lastUpdateMillis="0" provideSharedApexLibs="false" partition="SYSTEM"/>
</apex-info-list>
"#;

const DUMP: &str = "\
[com.example.app]
  path: /data/app/~~abc==/com.example.app-xyz==/base.apk
    arm64: [status=speed-profile] [reason=bg-dexopt] [primary-abi]
      [location is /data/app/~~abc==/com.example.app-xyz==/oat/arm64/base.odex]
    arm: [status=run-from-apk] [reason=unknown]
      [location is unknown]
  known secondary dex files:
    /data/user/0/com.example.app/files/plugin.jar (public)
      arm64: [status=run-from-apk] [reason=unknown] [primary-abi]
        [location is unknown]
      class loader context: PCL[]
";

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let root = PathBuf::from(&args[1]);
    let out = PathBuf::from(&args[2]);
    fs::create_dir_all(root.join("proc/self/attr")).unwrap();
    fs::create_dir_all(root.join("apex")).unwrap();
    fs::write(root.join("apex/apex-info-list.xml"), APEX_LIST).unwrap();
    fs::write(root.join("proc/self/status"), "Name:\tzart\nUid:\t10123\t10123\t10123\t10123\n").unwrap();
    fs::write(root.join("proc/self/attr/current"), "u:r:untrusted_app:s0:c123,c256,c512,c768\0").unwrap();
    let mut auxv = Vec::new();
    // AT_PAGESZ 16384, AT_HWCAP crc32|atomics|fphp (no asimddp), AT_NULL.
    for v in [6u64, 16384, 16, (1 << 7) | (1 << 8) | (1 << 9), 0, 0] {
        auxv.extend_from_slice(&v.to_ne_bytes());
    }
    fs::write(root.join("proc/self/auxv"), auxv).unwrap();

    let runner = FakeRunner::default().with("getprop", 0, GETPROP);
    let report = zart::inspect::inspect(&SysRoot::at(&root), &runner, "aarch64");
    fs::write(out.join("inspect-fixture.json"), serde_json::to_string_pretty(&report).unwrap()).unwrap();

    let r = FakeRunner::default()
        .with("cmd package art dump com.example.app", 0, DUMP)
        .with(
            "cmd package art dump com.example.denied",
            255,
            "java.lang.SecurityException: ART service shell commands need root or shell access\n",
        );
    let ok = zart::dexopt::package_status(&r, "com.example.app");
    fs::write(out.join("dexopt-status-fixture.json"), serde_json::to_string_pretty(&ok).unwrap()).unwrap();
    let denied = zart::dexopt::package_status(&r, "com.example.denied");
    fs::write(out.join("dexopt-status-denied.json"), serde_json::to_string_pretty(&denied).unwrap()).unwrap();
}
