# Project context: Zairenkai-App

> Owned by this repository. Update it whenever verified facts change. Label every statement
> **Verified** (with `Evidence:`), **Unverified**, or **Unknown / Not Configured**.

## Identity

| Field | Value | Status |
|---|---|---|
| Repository | `ZairenkaiPrjctX/Zairenkai-App` | Verified. Evidence: GitHub API |
| GitHub description | ZairenkaiPrjctX Source | Verified. Evidence: GitHub API |
| Visibility | public | Verified. Evidence: `list_repos`, 2026-10-22 (earlier entries said private) |
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
| Organization inventory (17 repositories: 16 Zairenkai, all public, plus the excluded anime-extensions), lock, scripts | `integration/` | Verified. Evidence: `verify_integration.py lock` ok, `list_repos` 2026-10-22 |
| CI | `.github/workflows/ci.yml` (reduced, scripts, full-integration), `release.yml` | Reduced build and scripts verified on GitHub. Evidence: runs 39622352061 (f34246c), 39623939493 (b9ce39b). full-integration failed there at "Require the CI GitHub App"; since 2026-10-22 it fetches the public locked repositories without a token (`docs/build/ci.md`). **Unverified** on GitHub: no run of that version yet |
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
- The app installs one root module, `zairenkai` (Zairenkai-Modules: zcored and zfasd, data in
  `/data/adb/zairenkai/{core,fas}`), through Magisk, KernelSU/KernelSU-Next or APatch.
  Verified. Evidence: maintainer decision 2026-10-22; `integration/system-modules.json`; catalog
  fixture built from the real arm64 components (`docs/testing/verification-report.md`,
  2026-10-22). The separate `zairenkai_core` / `zairenkai_fas` modules are superseded. The manager commands were read in each manager's source. Verified. Evidence:
  `docs/integration/system-module.md`, `SystemModuleTest`. Installation on a device is
  **Unverified**: no device has a validation record.
- No release channel exists, so the module zips are packaged in the APK. Verified. Evidence:
  `list_releases` empty for Core, FAS and Release, 2026-10-21.
- Module installation needs a catalog signed by a key in `integration/module-trust.json`. The
  list holds one maintainer key (`4330305a…`, 2026-10-21) from this change on; builds signed
  without it still refuse to install. Verified. Evidence: `SystemModuleTrustTest`,
  `setup-actions-secrets.sh status`, 2026-10-22.
- No signed APK or catalog has been built yet: the release gate last ran 2026-10-21, with the APK
  and catalog unsigned. The licence checks pass. Verified. Evidence: the release gate, 2026-10-21.
- App, ART and FAS are Apache-2.0 (maintainer decision 2026-10-21). The licence commits are
  merged: ART PR #2 (`0d8cd72`), FAS PR #3 (`be08b2d`).
  Verified. Evidence: `integration/repository-lock.json`, `docs/integration/license-inventory.md`.
- FAS is distributed inside the `zairenkai` module by the maintainer's decision of 2026-10-22,
  before hardware validation. It installs switched off (`enabled = false`) until the user enables it.
  The provenance audit found no copied GPL code. Verified. Evidence:
  `docs/integration/fas-provenance.md`, 2026-10-21.
- The two CI App values are no longer read by any workflow (the locked repositories are public,
  2026-10-22); the maintainer may delete them. Values set: CI App ID and key at repository level, five signing secrets in
  the `release` environment, which deploys only from `dev`; required reviewers are refused on the
  current plan, HTTP 422). Verified. Evidence: `setup-actions-secrets.sh status` exit 0,
  2026-10-22 (`docs/build/secrets-and-variables.md`). `release.yml` has never run: Unverified.
- Core and FAS are pinned to their topic-branch PR heads (Core PR #3 `4cdfe8e`, FAS PR #4
  `99cfb18`), which move them into the single module; ART, ThermalTools and Database-Source stay
  on `dev` merge commits. Verified. Evidence: `integration/repository-lock.json`, 2026-10-22.

## Authoritative sources

| Topic | Source |
|---|---|
| Repository facts | this file |
| Architecture | `AGENT/ARCHITECTURE.md` → `docs/architecture/overview.md` |
| Repositories and revisions | `integration/organization-repositories.json`, `integration/repository-lock.json` |
| Verification | `docs/testing/verification-report.md` |
| Organization standards | `ZairenkaiPrjctX/.github` → `agent-foundation/standards/` |

## Related repositories

- Zairenkai-ART (`0d8cd72`), Zairenkai-ThermalTools (`3bc71fe`) and Zairenkai-Database-Source
  (`d9c4290`), on `dev`, are integrated. Verified. Evidence: `integration/repository-lock.json`, contract tests, 2026-10-21.
- Zairenkai-Modules (`ea138ef`, topic branch) is integrated as the module source; Core (`4cdfe8e`)
  and FAS (`99cfb18`) are pinned to their topic-branch PR heads (`"merged": false`). Verified.
  Evidence: `integration/repository-lock.json`, 2026-10-22.
- Zairenkai-Tuning (GPL-3.0-only `ztune`, topic branch `0c226b2`, no default branch yet) is run by
  Core and packaged by Modules (`bd1eb06`, topic branch); the app's lock still pins Modules
  `ea138ef`, without it, and the app has no tuning client yet. Verified. Evidence:
  `integration/organization-repositories.json`, 2026-10-22.
- The other 8 Zairenkai repositories have no runtime capability. anime-extensions is a separate
  product and is excluded from the ecosystem audit (maintainer, 2026-10-22). Verified. Evidence:
  `integration/repository-audit.md` (re-audit 2026-10-21).

## Open questions

- Which device will run the first on-device validation?
- When should the app move to Material 3 1.5.0 stable (and the compileSdk 37 / AGP 9.1
  toolchain)?
