# Physical-device validation: Core module alone

**No device has been validated.** Nothing on this page has been run. The step-by-step table and
the recovery paths for each root manager are in `docs/integration/system-module.md` ("First
hardware validation"). This page sets the rules: scope, what counts as evidence, when to stop,
and the record to file.

## Scope

- **One module:** `zairenkai`, which carries zcored and zfasd. **FAS stays switched off.** The
  module installs it with `enabled = false`; do not enable it during this test. Step 1 and
  the last step confirm that the superseded `zairenkai_core` / `zairenkai_fas` modules are
  absent, and the "after" output must show no changed cpufreq node.
- **One dedicated, recoverable, rooted device.** It must not be a daily phone. Before starting
  you need a backup, the stock boot image for the installed firmware, adb authorisation and
  fastboot, and the manager's safe mode tried once on this device.
- **One root manager and version**, from the supported list: Magisk ≥ v21.2, KernelSU,
  KernelSU-Next or APatch. A result validates that device codename with that manager version,
  nothing broader.
- **Nothing else changes on the device.** No thermal, cooling, CPU/GPU frequency, governor,
  boot-image, firmware or partition change, by the app or by hand. zthermal only reads.

## What counts as evidence

Only output captured on the physical device during this procedure counts:
- `device-readonly-check.sh` outputs;
- the app's screens (screenshots), including its "Installer output";
- `zcored ctl health`;
- the manager's own module list.

These do **not** count as device validation, and must never be cited as such:
- JVM and Robolectric tests;
- fake or simulated `su`, the `FakeRunner`/fixture runs, host runs of the binaries;
- emulators;
- a reduced or full CI build.

They show the code behaves as designed, not that a device does.

## Preconditions that block the test today

| Precondition | State on 2026-10-21 |
|---|---|
| APK from the full integration build (default profile, Core only), with `integration-report.json` | Only local builds exist; CI's full build waits for the GitHub App (`docs/build/ci.md`) |
| Catalog signed by a key in `integration/module-trust.json` | No key exists. The app stops at `MODULE_NOT_AVAILABLE` |
| A dedicated device | Not chosen |

**Do not work around the signature check.** Installing the zip from the manager by hand is a
manager test, not the app's install, and an untrusted key must never be added to the trust
list. Without a signed catalog, the only valid result is "blocked at `MODULE_NOT_AVAILABLE`"
(steps 1–4 of the table).

## Procedure summary

The full table is in `system-module.md`. In outline:
1. **Before.** Run `device-readonly-check.sh` and `--root`. Expect `zairenkai`,
   `zairenkai_core` and `zairenkai_fas` to be absent.
2. **Without root.** Home, Thermal and Runtime.
3. **Enable root** in the app.
4. **Check.** Run the System Module checks.
5. **Install** after confirmation. Expect `INSTALL_PENDING_REBOOT`.
6. **Staged state.** Run the root check again.
7. **Reboot.**
8. **Core runtime.** Run the root check, `zcored ctl health`, and the screen state.
9. **zthermal.** Its snapshot still reads, and the thermal nodes are unchanged.
10. **Remove** the module, reboot, and run the root check again.
11. **Optional failure paths.**

Expected Core runtime without FAS: `zcored ctl health` answers `degraded`, naming the FAS
adapter as unavailable. The screen shows `DEGRADED`. `ACTIVE` would need every Core dependency
healthy. `INSTALLED_NOT_INITIALIZED` means zcored did not answer: record it as a failure, with
`zcored.log` and `/data/adb/zairenkai/core/failed`.

## Stop and recover when

- the device does not finish booting within five minutes of the reboot;
- the manager or the app reports an install exit code other than 0;
- `/data/adb/zairenkai/core/failed` or `/data/adb/zairenkai/fas/failed` exists, or a daemon restarts repeatedly;
- zfasd reports `enabled: true` without the tester enabling it;
- any thermal or cpufreq node differs between the "before" and "after" outputs;
- a module other than `zairenkai` was added, changed or removed.

Recover through the manager's safe mode or its module removal first
(`system-module.md`, "Prerequisites"), then reflash the stock boot image if needed. Record what
happened.

## Record

One file per device and manager: `docs/validation/<codename>-<YYYY-MM-DD>.md`. Attach the four
outputs (`1-before.txt`, `2-staged.txt`, `3-after.txt`, `4-removed.txt`). Only after a complete,
successful record, add the codename to `zairenkai`'s `validated_on` in
`integration/system-modules.json`, in a reviewed pull request.

```
Tester / date (UTC): <who> / <YYYY-MM-DD hh:mm>
Device: <manufacturer model> (<codename>), SoC <soc>, ABIs <list>
Android <release> (API <n>), build <ro.build.type>, security patch <date>
Kernel: <uname -r>   Page size: <n>   SELinux: <Enforcing|Permissive>
Root manager: <family> <version> (<versionCode>); su -v <…>; su -V <…>; CLI <path, version>
Recovery path tried on this device: <how>   Stock boot image kept: <yes>
App APK: <sha256> (installed copy: <sha256 from device-readonly-check>), build <CI run id | local>, app commit <sha>
Catalog: signature <verified, key id …>; module zairenkai <version> (<versionCode>), zip <sha256>, built from Modules <commit> (Core <commit>, FAS <commit>)
Preflight: <verdict>; WARN/FAIL checks: <…>
Before: zairenkai <absent>; zairenkai_core <absent>; zairenkai_fas <absent>; other modules: <list>
Install: method <direct|handoff>, command shown <…>, exit <n>; installer output attached
Staged: modules_update/zairenkai versionCode <n>
Reboot: <normal | problem: …>, boot time <s>
Core runtime: zcored ctl health <ready|degraded: reasons>; failed marker <absent>; log tail attached; screen state <ACTIVE|DEGRADED|INSTALLED_NOT_INITIALIZED>
FAS runtime: zfasd ctl status running <yes/no>, enabled <false>; failed marker <absent>
SELinux denials (avc, zairenkai): <none | list>
zthermal: snapshot <ok>, zones readable/denied <n/n>; thermal and cpufreq nodes unchanged: <yes/no>
Removal: method <…>, result after reboot <module and /data/adb/zairenkai gone: yes/no>; other modules unchanged: <yes/no>
Failures and recovery: <none | what, when, how recovered>
Not covered: <e.g. other managers, other firmware versions>
```
