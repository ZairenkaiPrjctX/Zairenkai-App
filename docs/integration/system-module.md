# Zairenkai System Module: in-app installation

The app can install the Zairenkai root modules through the user's root manager. This page
covers what is implemented, what each fact rests on, and what has not been tested on hardware.

## What is installed

| Module id | Role | Built from | Provides | Why it needs root |
|---|---|---|---|---|
| `zairenkai_core` | base | Zairenkai-Core `34e93e3` (`module/`, `scripts/build-android.sh`) | `zcored`: Core health, policy-checked actions, module inventory | A root daemon with a root-only socket (Core ADR-0005) |
| `zairenkai_fas` | optional | Zairenkai-FAS `3c83f0e` | `zfasd`: FAS status and controls | Writes cpufreq `scaling_min_freq` floors |

The spec is `integration/system-modules.json`. It records requirements, conflicts, risks, and
`validated_on`, which is empty for both modules.

Other repositories were checked and are not part of the module:
- **Zairenkai-Modules:** no packages.
- **Zairenkai-Sdk:** no code.
- **ART:** ART patches need a ROM or ART-module build; `zart` stays in the APK.
- **ThermalTools:** `zthermal` stays in the APK. It is read only and needs no daemon. Nothing
  here enables a thermal write.
- **Database-Source:** an offline asset.

Installing a module only installs it. Its features count as working only when its runtime
answers: `ACTIVE` needs Core health (or FAS status) to respond. Otherwise the module is
`DEGRADED`.

## Where the package comes from, and why it is trusted

- **Packaged, not downloaded.** No release pipeline, release signature, or published artifact
  exists:
  - `list_releases` returns nothing for Core, FAS and Zairenkai-Release (2026-10-21);
  - Zairenkai-Release is empty;
  - the app has no INTERNET permission.

  So the zips are built at the locked commits by `build-components.sh` and packaged in the APK.
  Nothing is fetched at run time.
- **Build-time check.** `integration/scripts/module_asset.py` checks each zip against its
  source:
  - module.prop id;
  - no unsafe entry;
  - every installer file byte-identical to `module/` at the locked commit.

  It then records the size, the SHA-256 and every entry's SHA-256 in
  `assets/system-modules.json`.
- **Trust root.** The APK signature. The catalog and the zip are covered by the same signature,
  so no checksum is trusted from an unverified source.
- **Install-time check.** `ModulePackageVerifier` checks the staged copy twice: after staging,
  and again immediately before the manager reads it. It checks:
  - the size;
  - the SHA-256;
  - the exact entry set (no `..`, no absolute paths, no extras);
  - each entry's SHA-256;
  - module.prop id, version and versionCode;
  - that the installer scripts were matched to source.
- **Not implemented: a network release channel with a signed manifest.** It needs:
  - a release signing key kept off the device;
  - a published, signed manifest;
  - an embedded public key;
  - the INTERNET permission.

  None of these exists yet. Adding that channel is a release-engineering decision.

## Root managers

Every command below was read in the manager's own source (shallow clones on 2026-10-21):

| Manager | Source commit | Identification | Install | State | Removal | Handoff |
|---|---|---|---|---|---|---|
| Magisk | topjohnwu/Magisk `af8c256` | `su -v` → `<ver>:MAGISKSU` (`su.cpp`); `magisk -V` must equal `su -V` | `magisk --install-module ZIP` (`magisk.rs`, `scripting.cpp`), fixed in v21.2 (`docs/changes.md`) → `/data/adb/modules_update/<id>` | files | **none**: Magisk has no single-module removal command, so the app opens the Magisk app | Magisk app (no zip intent filter): zip saved to Downloads, app opened |
| KernelSU | tiann/KernelSU `6d31a61` | `su -v` → `<ver>:KernelSU` (`su.rs`); `/data/adb/ksud module list` answers JSON; `module --help` lists `install` | `/data/adb/ksud module install ZIP` → modules_update | files | `ksud module uninstall <id>` | the manager's `ACTION_VIEW` `content://` zip filter (`AndroidManifest.xml`) |
| KernelSU-Next | KernelSU-Next/KernelSU-Next `b427a3f` | same as KernelSU (same su tag and ksud path) | same | files | same | same (package `com.rifsxd.ksunext`) |
| APatch | bmax121/APatch `e8e4651` | `su -v` → `<ver>:APatch` (`apd.rs`); `/data/adb/apd module list` JSON; `module --help` lists `install` | `/data/adb/apd module install ZIP` | files | `apd module uninstall <id>` | APatch app (no zip intent filter): zip saved to Downloads, app opened |

- **Accepting a manager.** A manager is accepted only when the su tag and its CLI agree.
  - A tag without a matching CLI is `Unsupported`.
  - Magisk older than v20.4 is `VersionIncompatible`: the module's update-binary refuses it.
  - Magisk v20.4 to v21.1 gets the handoff path, because `--install-module` was broken before
    v21.2.
  - A ksud or apd without an `install` subcommand is `UNVERIFIED`, and gets the handoff.
- **Other managers** (SuperSU and others) are `UNSUPPORTED`. They are never sent a command.
- **Module state.** It is read only with `cat` and `ls` from the layout all three managers
  share: `/data/adb/modules/<id>` and `/data/adb/modules_update/<id>`, with the markers
  `disable`, `remove` and `update`.
  - Sources: Magisk `util_functions.sh` `install_module`; ksud and apd `defs.rs` and
    `module.rs`.
  - Nothing is ever written there directly.
- **Commands.** Every command is a `ShellScript` of validated words, run through `RootShell`:
  fixed paths, no input-built strings.
  - Timeouts: 10 s for reads, 180 s for install and uninstall.
  - Output is capped at 1 MiB, and the log keeps the last 8,000 characters.

## Flow and states

`ModuleInstallController` (`integration-core/.../systemmodule/InstallFlow.kt`):

1. Root state:
   - not requested → `ROOT_PERMISSION_REQUIRED`;
   - refused → `ROOT_PERMISSION_DENIED`;
   - no su → `ROOT_NOT_AVAILABLE`.

   No su call happens in these states.
2. `MANAGER_DETECTION`: two independent sources, as above.
3. `COMPATIBILITY_CHECK` (`Preflight.decide`, pure) checks:
   - ABI, kernel architecture (`uname -m`), API level;
   - manager family and version, installer method;
   - required paths (FAS: `/sys/devices/system/cpu/cpufreq`), dependencies;
   - conflicts (FAS: `fas_rs`, the module id in fas-rs `build.rs` at `09ba876`);
   - whether a /system overlay or metamodule is needed (neither module has `system/`);
   - free space (4 × the zip size);
   - an existing install (same version, staged, newer, disabled, removal pending);
   - reboot, and the validation record.

   Any failed check → `UNSUPPORTED` with the reasons.

   No device has a validation record, so a passing device is `EXPERIMENTAL`, never
   `COMPATIBLE`. A recognised manufacturer does not count as validation: only the device
   codename in `validated_on` does.
4. `MODULE_AVAILABLE`: the Home offer and the module screen.
5. The user taps **Install Zairenkai System Module**:
   - `DOWNLOAD_PENDING`: the packaged zip is copied to `cache/system-modules/`;
   - `VERIFYING_ARTIFACT`;
   - `READY_TO_INSTALL`, showing the verification results.
6. **Continue** → `AWAITING_USER_CONFIRMATION`: a dialog with the method, the reboot, any
   upgrade, and a required "not validated" acknowledgement.
7. `INSTALLING`: the manager's own installer. Then `VERIFYING_INSTALLED_STATE`: an exit status
   of 0 is not trusted, so the staged `module.prop` must show the expected versionCode.
   - staged → `INSTALL_PENDING_REBOOT`;
   - not staged → `INSTALL_FAILED`.
8. After a reboot (screen resume or **Check again**):
   - `ACTIVE`: installed, enabled, and the runtime answers;
   - `DEGRADED`: disabled, wrong version, or not answering.
9. Without a direct installer → `HANDOFF_READY`. The verified zip goes to the manager app.
   This is not reported as an automated install.
10. **Remove**:
    - KernelSU and APatch: the manager's uninstall command, after which the `remove` marker
      must be present → `REMOVAL_PENDING_REBOOT`;
    - Magisk: the app opens Magisk.

    Only the module's own id is ever passed. Its uninstall script deletes only its data
    directory.

The user can cancel at any point before `INSTALLING`. Cancelling deletes the staged copy, and
the device is never changed. **Not now** on the Home offer is stored. Settings › Zairenkai
System Module reopens the screen and can show the offer again.

### When the offer appears

- **Only after this session's grant.** The offer appears only after root is granted in this
  session: a stored grant re-checked at start-up does not trigger detection.
- **Read-only checks.** Root permission alone never installs anything. Detection and
  preflight only read.
- **On the module screen.** Opening the screen re-checks the modules. If root is not granted,
  that runs no su call.

## Rollback and recovery

- **No rollback.** Neither the managers nor the modules offer one.
- **Failed upgrades.** Upgrades are staged in modules_update, so a failed upgrade does not
  touch the active version before a reboot.
  - Magisk's `abort` deletes the staging directory (`util_functions.sh`). This was read in
    source.
  - Cleanup after a failed ksud or apd install was not checked.
- **Recovery** goes through the manager's own disable and remove. If the device does not boot,
  the managers' safe-mode or module-disable procedures apply, documented by each manager.
- **Nothing else is touched.** The feature changes no boot image, partition or thermal
  setting.

## Verified, and not verified

| Claim | Status |
|---|---|
| Detection, preflight, verification, state machine, uninstall, handoff decisions, unsafe-path refusal | Verified on the JVM: `SystemModuleTest` (21), `SystemModuleAdapterTest` (1) and the catalog contract test, against a scripted su |
| Handoff intents (FileProvider URI, package, grant flag; launch intents) | Verified under Robolectric: `ManagerHandoffTest` (3) |
| Module screen without root (no install offer, no su call) | Verified under Robolectric: `AppUiTest.systemModuleScreenNeedsRootAndOffersNoInstallWithoutIt` |
| Package contents: scripts identical to source, digests in the APK | Verified: `module_asset.py` at build time; APK entries' SHA-256 equal to the catalog (2026-10-21) |
| Install through Magisk, KernelSU, KernelSU-Next or APatch on a device | **Not verified**: no device, no manager |
| SELinux and mount-namespace access of each manager to the app's cache file | **Not verified** |
| `su -v` / `su -V` behaviour inside each manager's `su -c` shell | **Not verified** (read from source only) |
| Downloads saving through MediaStore | **Not verified** (Robolectric has no MediaStore provider) |
| Post-reboot `ACTIVE` | **Not verified** |

Validated devices and manager versions: **none**. When an install is tested, record it in
`validated_on` with the device codename, and the manager and version in this table.
