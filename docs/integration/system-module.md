# Zairenkai System Module: in-app installation

The app can install the Zairenkai root modules through the user's root manager. This page
covers what is implemented, what each fact rests on, and what has not been tested on hardware.

## Package architecture

There is no single combined "system module". Zairenkai-Core and Zairenkai-FAS are separate
root modules, each with its own id, data directory, service and uninstall scripts. Merging them
would break that lifecycle: FAS restores its cpufreq floors in its own `uninstall.sh`. So the
**Zairenkai System Module package** is a versioned, signed release set of the two real modules:

| Module id | Role | Built from | Provides | Why it needs root |
|---|---|---|---|---|
| `zairenkai_core` | base | Zairenkai-Core `34e93e3` (unmerged topic branch), `module/` + `scripts/build-android.sh` | `zcored`: Core health, policy-checked actions, module inventory | A root daemon with a root-only socket (Core ADR-0005) |
| `zairenkai_fas` | optional | Zairenkai-FAS `3c83f0e` (dev) | `zfasd`: FAS status and controls | Writes cpufreq `scaling_min_freq` floors |

**Zip contents.** Each zip is exactly what the component's own build script produces:
- `module.prop`, `customize.sh`, `service.sh`, `uninstall.sh`, `action.sh`;
- `META-INF/com/google/android/{update-binary,updater-script}`;
- `bin/<daemon>`;
- licence files (Core: `LICENSE`, `NOTICE`, `THIRD_PARTY.md`; FAS: `THIRD_PARTY.md` only);
- `webroot/` (FAS).

There are no other files, no configuration or profile data, and no `system/` overlay.

**Manifest.** `integration/system-modules.json` is reviewed in Git. For each module it declares:
- id, role and features;
- requirements: architecture, ABI, minimum API, supported managers with minimum versions and
  their source evidence, required kernel paths, dependencies, conflicts;
- reboot;
- `lifecycle`: destinations, start, stop, uninstall, recovery and kernel conditions, as the
  modules' scripts implement them;
- risks, and `validated_on` (empty).

`build-components.sh` adds the version, versionCode, size, SHA-256, per-entry digests and the
source commit, and writes `assets/system-modules.json`.

**Repositories checked and left out:**
- **Zairenkai-Modules:** empty.
- **Zairenkai-Sdk:** no code.
- **ART:** its patches need a ROM build, and `zart` stays in the APK.
- **ThermalTools:** `zthermal` stays in the APK, read only. Its writable-control list stays
  empty, and nothing here enables a thermal write.
- **Database-Source:** an offline asset in the APK.

**Installed is not working.** A module's features count as working only when its runtime
answers:

| Runtime answer | State |
|---|---|
| Core health `ready`, or FAS status | `ACTIVE` |
| Core health `degraded` (for example FAS absent) | `DEGRADED` |
| No answer | `INSTALLED_NOT_INITIALIZED` |

## Acquisition: decision

| | A. Packaged in the APK (chosen) | B. Separately distributed release |
|---|---|---|
| APK size | +1.36 MB (both zips) | none |
| Update independence | a module update needs an app update | modules can update alone |
| Offline | works offline | needs the network at install time |
| Network permission | none (the app has no INTERNET permission) | INTERNET permission required |
| Infrastructure | none beyond the signed catalog | a release host, a version index, rollout and revocation |
| Exists today | yes | no: no release exists in Core, FAS or Zairenkai-Release (`list_releases`, 2026-10-21) |

A is the simplest safe choice: no network capability, no release host to secure. B can be
added when a module release pipeline exists. It would reuse the same signed catalog format.

## Trust: a signed catalog

- **The catalog is signed by a module-signing key.** `assets/system-modules.json` carries a
  detached signature (`system-modules.json.sig`: ECDSA P-256 over SHA-256, the format
  `openssl dgst -sha256 -sign` writes). The app verifies it (`CatalogSignature`, `ModuleRelease`)
  against the public keys in `integration/module-trust.json`. That list is reviewed in Git and
  copied into the APK.
- **A separate identity from the APK signer.** A debug-signed or rebuilt APK cannot authorise a
  module, and no private key is in the APK.
- **Today the list is empty.** No module-signing key has been created, so every build reports
  "No module-signing key is trusted yet" and **the app refuses to install either module**:
  - the flow stops in `MODULE_NOT_AVAILABLE`, with the compatibility result still shown;
  - checking and removing an existing installation still work.

  This is the missing prerequisite (`docs/build/release.md`).
- **Build-time check.** `module_asset.py` requires each zip's module.prop id, version and
  versionCode, rejects unsafe entries, and requires every installer file to be byte-identical
  to `module/` at the locked commit.
- **Install-time check.** `ModulePackageVerifier` checks the staged copy after staging and again
  immediately before the manager reads it. It checks:
  - the size and the SHA-256;
  - the exact entry set (no `..`, no absolute paths, no extras);
  - each entry's SHA-256;
  - module.prop id, version and versionCode;
  - the installer scripts matched to source.

  The release gate checks that licence files are present.

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
   - an unknown manager, or one too old → `MANAGER_UNSUPPORTED`;
   - module not packaged → `MODULE_NOT_AVAILABLE`.
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
4. `MODULE_AVAILABLE` only when the catalog signature is trusted. Otherwise
   `MODULE_NOT_AVAILABLE`, naming the missing prerequisite. That is the state of every build
   today.
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
   - `ACTIVE`: installed, enabled, and Core health `ready` (or FAS status answers);
   - `DEGRADED`: disabled, wrong version, or the runtime reports a problem (Core `degraded`);
   - `INSTALLED_NOT_INITIALIZED`: installed and enabled, but the service does not answer.

   A disabled module offers **Enable**, which runs the manager's own command (KernelSU and
   APatch: `module enable <id>`; Magisk: its app). The `disable` marker must then be gone, and
   the state becomes `INSTALL_PENDING_REBOOT`.
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

### When anything runs

- **Home card.** Shown when root is granted or a root-manager app is installed. The app is
  detected by package visibility only, as a hint. Magisk can hide itself, so absence proves
  nothing. The card lists the root-dependent capabilities from the registry and offers **Set up
  system integration** and **Not now** (stored). It runs nothing.
- **Checks need an explicit request.** Root verification, manager detection and the preflight
  run only on the System Module screen (from Home, System or Settings). Granting root alone
  checks and installs nothing, and app start-up runs no module check.
- **Registry.** The registry (`SystemModuleSubsystem`) shows each module's last known state
  without running a command.

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
| Detection, preflight, verification, state machine, uninstall, enable, handoff decisions, unsafe-path refusal | Verified on the JVM: `SystemModuleTest` (21), `SystemModuleAdapterTest` (1), `SystemModuleTrustTest` (5), `SystemModuleRegistryTest` (1) and the catalog contract test, against a scripted su |
| Catalog signature: openssl's ECDSA P-256 format verifies in the app; tampered, unknown-key, unsigned and empty-trust cases are refused | Verified on the JVM: `SystemModuleTrustTest`, with a throwaway key whose private half was deleted (`contracts/system-modules/test-trust.json`, never trusted by the app) |
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
