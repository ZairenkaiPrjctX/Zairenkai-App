# Zairenkai System Module: in-app installation

The app can install the Zairenkai root module through the user's root manager. This page
covers what is implemented, what each fact rests on, and what has not been tested on hardware.

## Package architecture

There is **one** root module, `zairenkai`, built in Zairenkai-Modules (maintainer decision
2026-10-22). It carries both daemons; each keeps its own data directory and lifecycle inside
the module, so FAS still restores its cpufreq floors before its data is removed.

| Module id | Built from | Provides | Why it needs root |
|---|---|---|---|
| `zairenkai` | Zairenkai-Modules `ea138ef` (`module/` + `scripts/assemble.py`), with Zairenkai-Core `4cdfe8e` and Zairenkai-FAS `99cfb18` as its `components.lock.json` pins them | `zcored`: Core health, policy-checked actions, module inventory. `zfasd`: FAS status and controls | zcored is a root daemon with a root-only socket (Core ADR-0005); zfasd writes cpufreq `scaling_min_freq` floors once enabled |

The separate `zairenkai_core` and `zairenkai_fas` modules are superseded: the catalog lists them
as conflicts, the installer warns about them, and Core reports them as superseded.

**FAS starts switched off.** On a first install the module writes
`/data/adb/zairenkai/fas/config.toml` with `enabled = false`; zfasd runs and reports, but
boosts nothing until the user enables it in the app (Core's `fas_set_enabled`) or the WebUI.
FAS is distributed before its hardware validation, by the maintainer's decision
(`docs/integration/fas-provenance.md`).

**Zip contents** (Modules' `assemble.py`, which checks every component file against its
`component.json` at the locked commit):
- `module.prop`, `components.json` (generated), `customize.sh`, `service.sh`, `uninstall.sh`, `action.sh`;
- `META-INF/com/google/android/{update-binary,updater-script}`;
- `bin/zcored`, `bin/zfasd`;
- `defaults/fas-config.toml`, `webroot/` (FAS WebUI);
- licence files: `LICENSE` and `NOTICE` of Zairenkai-Modules, and each component's `LICENSE`,
  `NOTICE` and `THIRD_PARTY.md` under `licenses/<component>/`, checked by `verify_integration.py build`.

There are no other files and no `system/` overlay.

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
| APK size | +0.74 MB (the Core zip; FAS only in internal builds) | none |
| Update independence | a module update needs an app update | modules can update alone |
| Offline | works offline | needs the network at install time |
| Network permission | none (the app has no INTERNET permission) | INTERNET permission required |
| Infrastructure | none beyond the signed catalog | a release host, a version index, rollout and revocation |
| Exists today | yes | no: no release exists in Core, FAS or Zairenkai-Release (`list_releases`, 2026-10-21) |

A is the simplest safe choice: no network capability, no release host to secure. B can be
added when a module release pipeline exists. It would reuse the same signed catalog format.

## Trust: a signed catalog

Signatures and provenance answer different questions:

| Question | Answered by |
|---|---|
| Was this zip built from the reviewed source at the locked commit? (provenance) | The build: `build-components.sh` checks the checkout `HEAD` against the lock, and `module_asset.py` requires the installer scripts to be byte-identical to `module/` at that commit. The results (commit, digests, matched files) are recorded in the catalog and the integration report. |
| Did an authorised release process publish this catalog? (authenticity) | The module-signing key |

The APK signature proves neither. It only says who built that APK, and a debug key proves
nothing at all.

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

## First hardware validation: Core module only

**Not done yet.** No device has run it. The module screen and this page will only say
"validated" once the record below exists for a device, in `docs/validation/`, and its
codename is in `validated_on`.

### Scope

- **Module:** `zairenkai` (zcored and zfasd). FAS stays switched off for the whole test:
  do not enable it. Its first configuration has `enabled = false`, and the "after" outputs
  must show no changed cpufreq node.
- **One device, one manager.** The result validates that device codename with that root manager
  and version, nothing broader.
- **No thermal changes.** Do not disable thermal protection, change kernel or thermal controls,
  or make any other system change. ThermalTools stays read-only, and zthermal is only read.

### Prerequisites

1. **A dedicated, recoverable test device.** It must not be a daily phone. Before starting:
   - a full backup;
   - an unmodified boot image for the current firmware;
   - the manager's recovery path known and tried once on this device:
     - **Magisk:** Safe Mode disables all modules (`docs/faq.md`: press volume-down a few
       seconds before the boot animation, release when it appears). With adb working,
       `adb shell magisk --remove-modules` removes **all** modules and reboots, so use it only
       as a last resort;
     - **KernelSU and KernelSU-Next:** press and release volume-down more than three times
       after the first boot screen; the system's own Safe Mode also disables modules
       (`website/docs/guide/rescue-from-bootloop.md`);
     - **APatch:** its source has safe-mode handling (`apd/src/event.rs`, `utils.rs`), but the
       key combination was not checked here. Read APatch's own documentation before relying
       on it;
   - adb authorised, and fastboot available.
2. **A supported root manager**, as listed under "Root managers": Magisk ≥ v21.2, KernelSU or
   KernelSU-Next, or APatch.
3. **An APK from the full integration build** (default profile, Core only), with its
   `integration-report.json`. Record the APK SHA-256, and the Core zip's SHA-256 from
   `assets/system-modules.json`. For reference, the 2026-10-21 local build had:
   - zip `f7651bf70f4e58841a602b2925b27686343f8490a0c57316d8ffd196e8c75f41`, 741,218 bytes;
   - built from Core `34e93e34440efb90d64d4af1f9f10292d790575c`; rebuilt byte-identical from its
     merge commit `11e97429068e69cc889deba891f2015b77060bfb`.
4. **A catalog signed by a trusted module-signing key.** Without one the app stops at
   `MODULE_NOT_AVAILABLE`, which is the state of every build today. Do not bypass it:
   - no installing the zip by hand as "the app's install";
   - no adding an untrusted key.

   The maintainer creates the key (`docs/build/release.md`) first.

### Procedure

Every command below only reads, except the installation and removal, which the app performs
through the manager after you confirm.

| Step | Action | Record |
|---|---|---|
| 1 | `integration/scripts/device-readonly-check.sh > 1-before.txt` and `--root > 1-before-root.txt` | Device and SoC, Android and kernel (`uname -a`), page size, ABIs, root manager and version (`su -v`/`-V`, CLI), the initial module state of `zairenkai` (expected: absent), `getenforce`, avc denials, the installed APK's SHA-256 |
| 2 | Open the app without root: Home, Thermal (zthermal snapshot), Runtime | Which thermal zones are readable or `permission_denied` |
| 3 | System › Root access › Enable | The manager's prompt; the app shows root granted |
| 4 | System › Zairenkai System Module | Detected manager and installer method; every preflight check; signature state; distribution "Licensed for distribution"; experimental notice |
| 5 | **Install Zairenkai System Module**, then **Continue**, tick the acknowledgement, **Install** | Verification lines (size, SHA-256, entries, module.prop, scripts matched to Core `34e93e3`); the full "Installer output"; final state `INSTALL_PENDING_REBOOT` |
| 6 | `device-readonly-check.sh --root > 2-staged.txt` | `/data/adb/modules_update/zairenkai/module.prop` shows `versionCode=100` (`v0.1.0`) |
| 7 | Reboot, wait two minutes | Did it boot normally? |
| 8 | `device-readonly-check.sh --root > 3-after.txt`, then the module screen's **Check again** | `zcored ctl health` (`ready`, or `degraded` with reasons: `degraded` is expected while FAS is absent); `/data/adb/zairenkai/core/failed` (must not exist); the tail of `zcored.log`; avc denials; the screen state (`DEGRADED` or `ACTIVE`; `INSTALLED_NOT_INITIALIZED` if zcored does not answer), which must agree with the readings |
| 9 | Thermal screen (and "Read with root") | Snapshot taken; "Thermal controls" still says no validated control; thermal nodes unchanged between 1 and 3 |
| 10 | **Remove** (KernelSU and APatch) or remove in the Magisk app; reboot; `device-readonly-check.sh --root > 4-removed.txt` | Uninstall result; `zairenkai` and `/data/adb/zairenkai/core` gone; every other module unchanged (compare the module lists in 1 and 4) |
| 11 | Optional failure paths: decline the root prompt; cancel before installing; disable the module in the manager | `ROOT_PERMISSION_DENIED`; nothing staged; `DEGRADED`, then the manager's enable |

If anything fails, stop and recover through the manager first. Record exactly what happened,
including any boot problem, and attach the four output files.

### Record (`docs/validation/<codename>-<YYYY-MM-DD>.md`)

```
Device: <manufacturer model> (<codename>), SoC <soc>
Android <release> (API <n>), security patch <date>, kernel <uname -r>, page size <n>
Root manager: <family> <version> (<versionCode>); su -v <…>; installer method shown <direct|handoff>
Backup and recovery path checked: <yes: how>
APK: <sha256> from integration report <run id / local>; module zairenkai <version> zip <sha256>
Catalog signature: <verified, key id …>   Distribution: distributable
Preflight: <verdict> — <checks that were WARN or FAIL>
Initial module state: <absent | …>
Install: <exit, full installer output attached>; staged versionCode <n>
Reboot: <normal | problem: …>
Core after reboot: health <ready|degraded: reasons>; failed marker <absent>; log <attached>
App state after reboot: <ACTIVE|DEGRADED|INSTALLED_NOT_INITIALIZED>, consistent with readings: <yes/no>
SELinux: getenforce <…>; denials <none | list>   Permission failures: <none | list>
ThermalTools: snapshot <ok>; thermal nodes unchanged <yes/no>
Uninstall: <result>; final module state <absent>; other modules unchanged <yes/no>
Failures and recovery: <…>
```

