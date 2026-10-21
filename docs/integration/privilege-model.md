# Privilege model

| Mode | Used for | Mechanism | Status |
|---|---|---|---|
| A: standard Android | Device identity, thermal status and headroom, battery-saver state, ART APEX version, `zart inspect`, `zthermal snapshot`, AFID lookup | Public APIs as the app uid; the packaged zart and zthermal run as the app uid | Implemented |
| B: privileged bridge (Shizuku and similar) | – | No organization repository provides one | Not applicable |
| C: root | Core (health, telemetry, modules, actions), FAS (status, control), dexopt status, `zthermal snapshot --framework` (on request), Zairenkai module install and removal through the root manager's own CLI (`docs/integration/system-module.md`) | `su -c` through `RootShell` | Implemented; not validated on a device |
| D: remote service | – | No backend exists (License-Server is empty); the app has no INTERNET permission | Not applicable |

## Root rules (`integration-core/.../exec/RootShell.kt`)

- **Nothing before consent.** No `su` call happens until the user taps "Enable root integration".
  That runs `su -c id` and requires `uid=0(` in the answer; the root manager shows its own prompt.
  A stored consent is re-checked once per process start.
- **Validated words only.** A script is a list of `ShellWord`s, each one either:
  - a constant from this code base;
  - a validated argument (`[A-Za-z0-9._:/=@+~,-]`, at most 512 characters);
  - JSON produced by our own serializer from typed values (`[A-Za-z0-9 _.:,{}[]"-]`).

  Every word is rendered single-quoted, and no word can contain a quote, so the shell never
  interprets a value.
- **Strict package names.** Package names (dexopt status) are checked with zart's own rule
  (`PackageNames.isValid`) before any process starts.
- **Fixed command set.** Commands go only to `zcored ctl`, `zfasd ctl`, the packaged zart and the
  packaged zthermal. zthermal is only ever run as `snapshot` (optionally with `--framework`):
  `ThermalToolsClient` has no path to `apply`, `rollback` or `backup-config`.
- **Modules only through the manager.** `magisk --install-module`, `/data/adb/ksud module
  install|uninstall|enable` and `/data/adb/apd module install|uninstall|enable`, each only after a
  confirmation dialog. Module state is read with `cat` and `ls`. The app never writes into
  `/data/adb` itself.
- **Confirmation before changes.** Every action that changes the device shows a confirmation:
  Core's dry-run decision with all rule outcomes, or the direct-zfasd warning.

## Data handling

- **No identifiers.** No serial number, IMEI or account data is read: zart's property allowlist
  excludes them.
- **No other apps' names.** The dexopt dump drops other apps' package names.
- **No network, no backups.** No data leaves the device except the diagnostics text the user
  chooses to share, which contains capability states, versions, API level and ABIs.
  `allowBackup=false`, and the extraction rules exclude everything.
