# Project context: Zairenkai-App

> Owned by this repository. Update it whenever verified facts change. Label every statement
> **Verified** (with `Evidence:`), **Unverified**, or **Unknown / Not Configured**.

## Identity

| Field | Value | Status |
|---|---|---|
| Repository | `ZairenkaiPrjctX/Zairenkai-App` | Verified. Evidence: GitHub API |
| GitHub description | ZairenkaiPrjctX Source | Verified. Evidence: GitHub API |
| Visibility | private | Verified. Evidence: GitHub API |
| Default branch | `dev` | Verified. Evidence: `git ls-remote`, 2026-10-19 |
| Purpose | Unified Android app for the Zairenkai components | Verified. Evidence: `app/`, `integration-core/`, `docs/architecture/overview.md` |
| Application id | `com.zairenkaiprjctx.app`, version 0.1.0 (1), minSdk 29, targetSdk 35 | Verified. Evidence: `aapt2 dump badging`, 2026-10-19 |
| Licence | Apache-2.0 (`LICENSE`, `NOTICE`, SPDX headers); bundled font OFL-1.1, device data CC BY-SA 3.0 | Verified. Evidence: maintainer decision 2026-10-21; repository tree; `docs/integration/license-inventory.md` |

## Contents

| Area | Location | Status |
|---|---|---|
| Gradle build (AGP 8.9.1, Gradle 8.14.3, Kotlin 2.1.20) | `settings.gradle.kts`, `gradle/libs.versions.toml` | Verified. Evidence: build 2026-10-19 |
| JVM integration layer (Kotlin + Java), incl. root-manager adapters and the module installer | `integration-core/` | Verified. Evidence: 74 tests, 2026-10-21 |
| Android app, design system, Java platform adapters | `app/` | Verified on JVM. Evidence: 24 Robolectric tests, 2026-10-21. **Unverified** on a device |
| Organization inventory (16 repositories), lock, scripts | `integration/` | Verified. Evidence: scripts exit 0, re-audit 2026-10-21 |
| CI | `.github/workflows/ci.yml` (reduced, scripts, full-integration), `release.yml` | Reduced build and scripts verified on GitHub. Evidence: runs 39622352061 (f34246c), 39623939493 (b9ce39b). full-integration **fails** there at "Require the CI GitHub App": no CI run has built native components (`docs/build/ci.md`) |
| Docs | `README.md`, `docs/` | Verified. Evidence: files present |

## Key facts and limitations

- Material 3 Expressive APIs are internal in stable `material3` 1.4.0. The app pins
  `1.5.0-alpha18`, the newest version compatible with compileSdk 35 and AGP 8.9.
  Verified. Evidence: compile error against 1.4.0; AAR metadata of alpha19+ requires
  compileSdk 37 and AGP 9.1.
- Core and FAS are reachable only through root (Core ADR-0005).
  Verified. Evidence: Core `docs/adr/0005-root-only-socket.md`.
- No Android device or emulator was available: install, launch, root prompts and SELinux
  behaviour are **Unverified**.
- The app can install the Core and FAS root modules through Magisk, KernelSU/KernelSU-Next or
  APatch. The manager commands were read in each manager's source. Verified. Evidence:
  `docs/integration/system-module.md`, `SystemModuleTest`. Installation on a device is
  **Unverified**: no device has a validation record.
- No release channel exists, so the module zips are packaged in the APK. Verified. Evidence:
  `list_releases` empty for Core, FAS and Release, 2026-10-21.
- Module installation needs a catalog signed by a key in `integration/module-trust.json`, which
  is empty. Every build therefore refuses to install. Verified. Evidence: `SystemModuleTrustTest`,
  `verify_integration.py report --release`, 2026-10-21.
- Release blocked by signing: APK unsigned, catalog unsigned, no trusted module key. The licence
  checks pass. Verified. Evidence: the release gate, 2026-10-21.
- App, ART and FAS are Apache-2.0 (maintainer decision 2026-10-21). The ART and FAS licence
  commits (`31caf46`, `9d87236`) are on their topic branches and not merged; the lock pins them.
  Verified. Evidence: `integration/repository-lock.json`, `docs/integration/license-inventory.md`.
- FAS stays internal-only: its licence is not merged, and the first hardware test is Core only.
  The provenance audit found no copied GPL code. Verified. Evidence:
  `docs/integration/fas-provenance.md`, 2026-10-21.
- Draft PRs Core #2 and ThermalTools #2 bring the pinned topic branches to `dev`; they are not
  merged. Verified. Evidence: GitHub, 2026-10-21.

## Authoritative sources

| Topic | Source |
|---|---|
| Repository facts | this file |
| Architecture | `AGENT/ARCHITECTURE.md` → `docs/architecture/overview.md` |
| Repositories and revisions | `integration/organization-repositories.json`, `integration/repository-lock.json` |
| Verification | `docs/testing/verification-report.md` |
| Organization standards | `ZairenkaiPrjctX/.github` → `agent-foundation/standards/` |

## Related repositories

- Zairenkai-Core (`34e93e3`, topic branch), Zairenkai-FAS (`9d87236`, topic branch), Zairenkai-ART (`31caf46`, topic branch),
  Zairenkai-ThermalTools (`9d4b310`, topic branch) and Zairenkai-Database-Source (`d9c4290`) are
  integrated. Verified. Evidence: `integration/repository-lock.json`, contract tests, 2026-10-21.
- The other 10 Zairenkai repositories have no runtime capability, and anime-extensions is a separate
  product. Verified. Evidence: `integration/repository-audit.md` (re-audit 2026-10-21).
- The Core, ThermalTools, ART and FAS pins are unmerged topic-branch commits. Verified. Evidence:
  `git log origin/dev..origin/ccr-63f8aa87-1b0paj` in each repository, 2026-10-21.

## Open questions

- Which device will run the first on-device validation?
- Release signing: who creates the APK keystore and the module-signing key, and who holds them (`docs/build/release.md`)?
- When will the CI GitHub App for the full integration build be created (`docs/build/ci.md`)?
- When should the app move to Material 3 1.5.0 stable (and the compileSdk 37 / AGP 9.1
  toolchain)?
