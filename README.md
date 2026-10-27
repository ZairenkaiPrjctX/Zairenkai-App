# Zairenkai App

The Android app for the Zairenkai ecosystem. One APK reaches the components that exist in the
`ZairenkaiPrjctX` organization, each through its real interface:

| Component | Repository | How the app reaches it |
|---|---|---|
| Control plane | Zairenkai-Core | root: `zcored ctl` (protocol v1). Health, telemetry, module inventory, and policy-checked actions with a dry-run preview |
| Frame-aware scheduling | Zairenkai-FAS | root: `zfasd ctl`. Status (frame statistics, decision, thermal cap) and control when Core is not running |
| Tuning telemetry | Zairenkai-Tuning, shipped by Modules and invoked by Core | Read-only CPU/GPU/memory/storage/SoC telemetry. Scheduler/governor writes stay blocked unless an exact-device hardware-validated profile authorizes them |
| Local AI | Zairenkai-Intelligence-Engine | Packaged `zie-run` for arm64-v8a/x86_64; model download/import is user-triggered and SHA-256 verified |
| Schema contracts | Zairenkai-Sdk | Canonical schemas and compatibility contracts; not a privileged runtime or a binary to install |
| ART inspection | Zairenkai-ART | packaged `zart` executable. ART module, ART/dexopt properties, ISA config vs CPU; dexopt status via root |
| Thermal diagnostics | Zairenkai-ThermalTools | packaged `zthermal` executable, read only: thermal zones, trip points, cooling devices; framework thermal status via root on request. No thermal controls exist |
| Device database | Zairenkai-Database-Source | packaged offline asset: 354 unverified community device candidates (CC BY-SA 3.0) |
| Android platform | – | public APIs: identity, thermal status and headroom, battery saver, ART APEX version |

The integration is deliberately explicit rather than copying every repository into the APK or root
module. The on-device AI runtime comes from Zairenkai-Intelligence-Engine; its model weights are a
separate, user-triggered download/import and are verified by manifest size and SHA-256.
Zairenkai-Sdk is the canonical schema/contract source, not a device daemon. Zairenkai-Graphics-Platform
contains the read-only `zgfx` Vulkan/OpenGL ES probe, but is **not yet packaged or exposed in this app**.
Other repositories are tracked in `integration/organization-repositories.json`; no feature is claimed
merely because a repository exists. See `integration/repository-audit.md` for exact revisions, build
provenance, and gaps.

- **End-to-end device validation is incomplete.** A v0.3.1 install has been observed on KernelSU 3.4.1,
  but Core's Tuning capabilities were degraded; v0.3.2 must be installed and re-tested before Tuning
  can be called validated. See `docs/testing/verification-report.md`.
- **Root is opt-in.** The app works without root; root features stay off until enabled in
  System › Root access.
- **System module.** With root, the app can install the Zairenkai Core module (FAS only in
  internal development builds) through the root manager's own installer, only after you confirm
  (`docs/integration/system-module.md`). Installation stays blocked until the module catalog is
  signed by a trusted module-signing key, and none exists yet. It has not been tested on a
  device.

## Build

```sh
ANDROID_NDK_HOME=/path/to/ndk integration/scripts/build-components.sh   # zart, zthermal + AFID asset at locked commits
./gradlew :integration-core:test :app:testDebugUnitTest lint assembleDebug
```

Full instructions: `docs/build/building.md`.

## Documentation

| Topic | File |
|---|---|
| Architecture, Kotlin/Java split | `docs/architecture/overview.md` |
| Commands and contracts per component | `docs/architecture/integration-map.md` |
| Repository capability matrix | `docs/integration/repository-capability-matrix.md` |
| Root and data handling | `docs/integration/privilege-model.md` |
| In-app module installation | `docs/integration/system-module.md` |
| CI and the full integration build | `docs/build/ci.md` |
| Release and signing (blocked) | `docs/build/release.md` |
| Device validation procedure | `docs/testing/device-validation.md` |
| Device, API and ABI compatibility | `docs/integration/compatibility-matrix.md` |
| Design system (Material 3 Expressive, Google Sans Flex) | `docs/design/design-system.md` |
| What was verified | `docs/testing/verification-report.md` |
| Inventory, lock, font provenance | `integration/organization-repositories.json`, `integration/repository-lock.json`, `integration/third-party-assets.json` |

## Licences

- **This repository:** Apache-2.0 (`LICENSE`, `NOTICE`), as are the bundled zart, zthermal and
  Core module. Each bundled component keeps its own licence and notices.
- **Bundled font:** Google Sans Flex, SIL OFL 1.1. "Google Sans Flex" is a trademark of
  Google LLC, and this app is not affiliated with Google.
- **Device data:** CC BY-SA 3.0 (LineageOS Wiki via AFID).
- **zart and zthermal:** Apache-2.0; their `LICENSE` and `NOTICE` are packaged in
  `assets/licenses/zart/` (with the AOSP attribution) and `assets/licenses/zthermal/`. Full
  inventory: `docs/integration/license-inventory.md`.
