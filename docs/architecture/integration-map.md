# Integration map

```
                         ┌─────────────────────────── APK ───────────────────────────┐
 Home / Performance /    │ ViewModels ──► IntegrationRegistry ──► subsystems          │
 Thermal / Runtime /     │                     │                                      │
 System / Settings       │   CoreClient   FasClient   ZartClient   AfidCatalog        │
                         │        │           │          │   │          │            │
                         │        └──── RootShell ───────┘   │      assets/afid/     │
                         │                │ su -c 'w1' 'w2'…  │      device-          │
                         │                │                   │      candidates.json  │
                         │                │  lib/<abi>/libzart.so, libzthermal.so     │
                         └────────────────┼───────────────────┼───────────────────────┘
                                          ▼                   ▼
             /data/adb/modules/zairenkai_core/bin/zcored ctl --dir /data/adb/zairenkai-core …
             /data/adb/modules/zairenkai_fas/bin/zfasd  ctl --dir /data/adb/zairenkai-fas  …
             <nativeLibraryDir>/libzart.so dexopt-status <package>   (root)
             <nativeLibraryDir>/libzart.so inspect                   (app uid, no root)
             <nativeLibraryDir>/libzthermal.so snapshot              (app uid, no root)
             <nativeLibraryDir>/libzthermal.so snapshot --framework  (root, on request)
```

| Path | Command | Parsed by | Contract source | Fixture |
|---|---|---|---|---|
| Core health | `zcored ctl --dir … health` | `CoreClient.health` | Core `docs/api/PROTOCOL.md` | `contracts/core/health.json` |
| Core capabilities / modules / context / audit | `ctl capabilities` … `ctl audit '{"limit":N}'` | `CoreClient` | same | `contracts/core/*.json` |
| Core action | `ctl submit '{"operation":{…},"dry_run":true\|false,"reason":"zairenkai-app"}'` | `ActionResult` | same | `contracts/core/submit-dry-run.json` |
| FAS status | `zfasd ctl --dir … status` | `FasClient.status` | FAS `docs/INTEGRATION.md` | `contracts/fas/status.json` |
| FAS control | `ctl enable\|disable\|reset\|profile <enum>` | `FasClient.command`, `setProfile` | same | `contracts/fas/profile-ok.json` |
| ART inspection | `libzart.so inspect` | `ZartParser.inspect` | zart `inspect.rs` (schema 1) | `contracts/zart/inspect-*.json` |
| Dexopt status | `su -c '<libzart.so>' 'dexopt-status' '<pkg>'` | `ZartParser.dexoptStatus` | zart `dexopt.rs` | `contracts/zart/dexopt-*.json` |
| Thermal snapshot | `libzthermal.so snapshot`; `su -c '<libzthermal.so>' 'snapshot' '--framework'` | `ThermalToolsParser.snapshot` | ThermalTools `schemas/snapshot.schema.json` (schema_version 1) | `contracts/zthermal/snapshot-*.json` |
| Root manager | `su -v`, `su -V`; `magisk -V` or `<ksud\|apd> module list` and `module --help` | `RootManagerDetector` | manager sources (`docs/integration/system-module.md`) | scripted su in `SystemModuleTest` |
| Module install / state / removal | `magisk --install-module`, `<ksud\|apd> module install\|uninstall`; `cat`/`ls` of `/data/adb/modules{,_update}/<id>` | `ManagerAdapter`, `ModuleStateReader` | same | `SystemModuleTest`, `contracts/system-modules/catalog.json` |
| Device candidates | asset read | `AfidCatalog.parse` | `integration/scripts/afid_asset.py` | `fixtures/afid-two-devices.json` |

Routing rule: while Core answers, every action goes through Core (policy, arbitration, audit).
Direct `zfasd ctl` is used only when Core does not answer, the same path FAS's own WebUI uses.
The user confirms it first.

## Adding a component

1. Audit the repository, then add or update its entry in `organization-repositories.json`.
2. Pin it in `repository-lock.json`. If it is packaged, add a build step to
   `integration/scripts/build-components.sh`.
3. Write a typed client in `:integration-core` that uses only `CommandRunner` or `RootShell`.
4. Capture its real output in `capture-contracts.sh` and test the parser against it.
5. Add a `ZairenkaiSubsystem` that reports `Availability` honestly, then a UI section.
