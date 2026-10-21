# Physical-device validation procedure

**No device has been validated.** Nothing below has been run yet. Results go in
`docs/validation/<codename>-<YYYY-MM-DD>.md`, one file per device and root-manager combination.
After a successful run, add the codename to the module's `validated_on` in
`integration/system-modules.json`. Testing one device validates that device and that manager
version, nothing more.

## Prerequisites

- A rooted test device you can recover: a backup, and knowing how to boot the manager's safe
  mode or remove modules from recovery.
- `adb`, and an APK built by the full integration build (its report and hashes).
- A module catalog signed by a trusted key. Without one the app refuses to install. That is
  the expected result on today's builds, and it is the first thing to record.
- Never disable thermal protection, and never change thermal or frequency settings by hand
  during the test.

## Steps

1. **Before (read only).** Run:

   ```sh
   integration/scripts/device-readonly-check.sh > before.txt
   integration/scripts/device-readonly-check.sh --root > before-root.txt
   ```

   It records:
   - identity, SoC, Android and kernel versions, page size;
   - the thermal and cpufreq nodes;
   - the root manager (`su -v`/`-V`, CLI, module list) and existing Zairenkai modules;
   - `getenforce` and any Zairenkai SELinux denials.

   It writes nothing.
2. **Install the app** (`adb install`). Note the APK SHA-256 from the integration report.
3. **In the app:**
   - Without root: Home, Thermal (zthermal snapshot as the app uid) and Runtime work. Record
     which thermal zones are readable or denied.
   - System › Root access › Enable: record the manager's prompt.
   - System › Zairenkai System Module: record the detected manager and version, every
     preflight check, the signature state, and the package verification lines.
   - If the catalog is trusted, install the base module. Record the installer output (the
     screen's "Installer output"), and confirm the state is `INSTALL_PENDING_REBOOT`.
4. **After install, before reboot:** run the root check again
   (`device-readonly-check.sh --root > staged.txt`).
   `/data/adb/modules_update/zairenkai_core/module.prop` must show the packaged versionCode.
5. **Reboot**, then wait two minutes.
6. **After reboot:** run the root check again (`after.txt`), and record:
   - `zcored ctl health` (`ready` or `degraded`, with reasons);
   - `/data/adb/zairenkai-core/failed` (must not exist) and `zcored.log`;
   - avc denials;
   - the module screen state (`ACTIVE`, `DEGRADED` or `INSTALLED_NOT_INITIALIZED`), and that
     it agrees with the readings.
7. **ThermalTools stays read only:** take the snapshot (Thermal screen and "Read with root").
   Confirm "Thermal controls" still says no validated control exists, and that no thermal node
   changed between `before` and `after`.
8. **Removal:** Remove on the module screen (KernelSU and APatch), or remove in the Magisk app.
   Reboot. The module and `/data/adb/zairenkai-core` must be gone, and other modules unchanged
   (compare the module lists).
9. **Failure paths, where safe:**
   - decline the root prompt (`ROOT_PERMISSION_DENIED`);
   - cancel before installing (nothing staged);
   - disable the module in the manager (`DEGRADED`, then the Enable action).

## Record template

```
Device: <manufacturer model> (<codename>), SoC <soc>, Android <release> (API <n>), kernel <uname -r>
Root manager: <family> <version> (<versionCode>), su -v: <…>
APK: <sha256> (integration report <run id>)    Module: zairenkai_core <version>, zip <sha256>
Catalog signature: <verified key id | blocked: …>
Install: <command shown> → exit <n>; staged versionCode <n>
Reboot: yes   Post-reboot state: <ACTIVE|DEGRADED|INSTALLED_NOT_INITIALIZED> — Core health <…>
FAS: <not installed | state>   SELinux denials: <none | list>
ThermalTools: <zones readable/denied>; thermal nodes unchanged: <yes/no>
Removal: <result after reboot>   Other modules unchanged: <yes/no>
Failures and recovery: <…>
```
