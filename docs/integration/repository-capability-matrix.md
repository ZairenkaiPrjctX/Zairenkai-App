# Repository capability matrix

Revisions: `integration/repository-lock.json`. Statuses:
- **Implemented:** code exists and is exercised by tests.
- **Verified (host):** checked against the real binaries off-device.
- **Not verified on device:** no hardware was available.

| Repository | Audited revision | Responsibility (from source) | Reusable components | Integration target | Mechanism | Runtime requirements | Licence | Test strategy | Status |
|---|---|---|---|---|---|---|---|---|---|
| Zairenkai-Core | `ad0c195` | Policy-checked control plane: capability discovery, arbitration, verified execution, audit | `zcored ctl` with protocol v1 (`health`, `capabilities`, `context`, `modules`, `leases`, `audit`, `submit`) | `integration-core` `core/Core.kt`; Performance, Thermal and System screens | Root bridge: `su -c` with a fixed, validated argv | Root; module `zairenkai_core` installed and running | none chosen; nothing copied | `CoreClientTest` on responses captured from a real zcored | Implemented; verified (host); not verified on device |
| Zairenkai-FAS | `0877452` | Frame-aware CPU floors from SurfaceFlinger telemetry | `zfasd ctl status/enable/disable/profile/reset` | `fas/Fas.kt`; Performance and Thermal screens | Root bridge (direct only when Core is down) | Root; module `zairenkai_fas` | none chosen; nothing copied | `FasClientTest` on status captured from a real observe-only zfasd | Implemented; verified (host); not verified on device |
| Zairenkai-ART | `fc72192` | Read-only ART inspector `zart`; upstream ART patch management | `zart inspect`, `zart dexopt-status` (JSON) | `zart/Zart.kt`; Runtime screen | Native executable `lib/<abi>/libzart.so` (inspect as the app uid; dexopt-status via root) | inspect: none; dexopt-status: root or shell | none chosen; Apache-2.0 data from AOSP | `ZartTest` on host-run output and on library-serialised fixture reports; packaged SHA-256 matches the build | Implemented; verified (host); not verified on device |
| Zairenkai-Database-Source | `6e3db46` | AFID firmware metadata catalog | `database/staging/unresolved/lineageos_wiki/*.json` (353 unverified candidates) | `afid/Afid.kt`; Home and System screens | Build-time asset (`afid_asset.py`), offline | none | data CC BY-SA 3.0; code Apache-2.0 (not packaged) | `DataTest` on the generated asset and on fixtures | Implemented; the data itself is unverified upstream |
| .github | `4cdab7f` | Agent foundation templates and validator | validator script | development only | – | – | none chosen | foundation validator | Not applicable |
| ThermalTools, Sdk, Modules, Intelligence-Engine, License-Server, Graphics-Platform | see audit | none (foundation files only) | – | Registry records `NO_UPSTREAM_IMPLEMENTATION` | – | – | – | `RegistryTest` | Not applicable (no code) |
| Release, Docs, ZairenkaiPrjctX.github.io | see audit | none | – | – | – | – | – | – | Not applicable |
