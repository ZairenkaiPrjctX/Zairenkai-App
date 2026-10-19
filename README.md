# Zairenkai App

The Android app for the Zairenkai ecosystem. One APK reaches the components that exist in the
`ZairenkaiPrjctX` organization, each through its real interface:

| Component | Repository | How the app reaches it |
|---|---|---|
| Control plane | Zairenkai-Core | root: `zcored ctl` (protocol v1). Health, telemetry, module inventory, and policy-checked actions with a dry-run preview |
| Frame-aware scheduling | Zairenkai-FAS | root: `zfasd ctl`. Status (frame statistics, decision, thermal cap) and control when Core is not running |
| ART inspection | Zairenkai-ART | packaged `zart` executable. ART module, ART/dexopt properties, ISA config vs CPU; dexopt status via root |
| Device database | Zairenkai-Database-Source | packaged offline asset: 353 unverified community device candidates (CC BY-SA 3.0) |
| Android platform | – | public APIs: identity, thermal status and headroom, battery saver, ART APEX version |

The other repositories contain no implementation yet. The app says so, citing each one's
audited commit, instead of showing placeholders. Details: `integration/repository-audit.md`.

- **No device validation yet.** Nothing has run on an Android device
  (`docs/testing/verification-report.md`).
- **Root is opt-in.** The app works without root; root features stay off until enabled in
  System › Root access.

## Build

```sh
ANDROID_NDK_HOME=/path/to/ndk integration/scripts/build-components.sh   # zart + AFID asset at locked commits
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
| Device, API and ABI compatibility | `docs/integration/compatibility-matrix.md` |
| Design system (Material 3 Expressive, Google Sans Flex) | `docs/design/design-system.md` |
| What was verified | `docs/testing/verification-report.md` |
| Inventory, lock, font provenance | `integration/organization-repositories.json`, `integration/repository-lock.json`, `integration/third-party-assets.json` |

## Licences

- **This repository:** no licence has been chosen yet.
- **Bundled font:** Google Sans Flex, SIL OFL 1.1. "Google Sans Flex" is a trademark of
  Google LLC, and this app is not affiliated with Google.
- **Device data:** CC BY-SA 3.0 (LineageOS Wiki via AFID).
