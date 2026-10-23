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
| Application id | `com.zairenkaiprjctx.app`, minSdk 29, targetSdk 35; version 0.2.0-beta.1 (20001) since 2026-10-23 (0.1.0 (1) before), versionCode derived from the version (`docs/build/releases.md`) | Verified. Evidence: `aapt2 dump badging`, 2026-10-19; `UpdateUiTest`, 2026-10-23 |
| Licence | Apache-2.0 (`LICENSE`, `NOTICE`, SPDX headers); bundled font OFL-1.1, device data CC BY-SA 3.0 | Verified. Evidence: maintainer decision 2026-10-21; repository tree; `docs/integration/license-inventory.md` |

## Contents

| Area | Location | Status |
|---|---|---|
| Gradle build (AGP 8.9.1, Gradle 8.14.3, Kotlin 2.1.20) | `settings.gradle.kts`, `gradle/libs.versions.toml` | Verified. Evidence: build 2026-10-19 |
| JVM integration layer (Kotlin + Java), incl. root-manager adapters, the module installer and the update client | `integration-core/` | Verified. Evidence: 107 tests, 2026-10-23 |
| Android app, design system, Java platform adapters | `app/` | Verified on JVM. Evidence: 51 Robolectric tests (+14 screenshot renders), 2026-10-23. **Unverified** on a device |
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
- The app installs one root module, `zairenkai` (Zairenkai-Modules: zcored, zfasd and the GPL-3.0
  ztune with its source, data in `/data/adb/zairenkai/{core,fas,tuning}`), through Magisk, KernelSU/KernelSU-Next or APatch.
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
- Every locked commit but one is in its repository's `dev`: Core `345fc7f` and FAS `1c453db` (the
  merge commits of Core #3 and FAS #4); ART, ThermalTools and Database-Source on `dev` merge
  commits. Modules is pinned at `2d1f548` on its PR #3 branch (`"merged": false`) until that PR
  merges. Zairenkai-Tuning has no `dev`; it is pinned through Modules' `components.lock.json`
  (`0c226b2`). Verified. Evidence: `verify_integration.py lock`, 2026-10-23.
- The full-integration CI job passed for the first time on `d0db6b7` (run 39827139485: every
  component built at its locked commit, contracts re-captured, tests, lint, APKs with native
  components required, integration report), fetching the public sources without a token.
  Verified. Evidence: CI run 39827139485, 2026-10-22.
- The UI was redesigned on 2026-10-23 (Material 3 Expressive wavy indicators, segmented groups,
  five destinations with an AI tab; `docs/design/design-system.md`). The AI screen is prepared
  but honest: no on-device model is packaged, so `intelligence.analysis` stays
  NO_UPSTREAM_IMPLEMENTATION, the composer is disabled and no answer is shown. Verified. Evidence:
  `RedesignTest`, screenshot gallery, 2026-10-23. **Unverified** on a device.
- Settings › Appearance (palettes, custom themes, AMOLED, wallpaper, launcher icon variants,
  background animation, app font, UI scale, experimental glass bar) was added on 2026-10-23,
  modelled on mpvRx's behaviour (AGPL-3.0, no code copied). New dependencies: Haze 1.6.10 and
  `ui-text-google-fonts` (both Apache-2.0); Google Fonts come through Play services' font
  provider, never the app's own network access. Verified. Evidence: `AppearanceTest`, `aapt2 dump
  permissions`, generated NOTICES, 2026-10-23. **Unverified** on a device (blur, icon switching
  on real launchers, font download).

- In-app updates (2026-10-23): Settings › Updates checks GitHub Releases of this repository
  (Release or Beta channel, daily via JobScheduler or on request), notifies once per version,
  downloads only on request, and installs through Android's installer after the SHA-256 file,
  package, exact version, newer versionCode and same signing certificate check out. This added
  the app's first permissions: INTERNET, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED,
  POST_NOTIFICATIONS, REQUEST_INSTALL_PACKAGES. `release.yml` can create a draft release; the
  maintainer publishes it. Verified on the JVM. Evidence: `UpdateTest` (integration-core),
  `UpdateUiTest`, `test_app_release.py`, 2026-10-23. **Unverified**: no release exists yet (the
  releases API returned `[]`), and nothing ran on a device.
- Settings › About (version, commit, signing certificate, components, privacy, licences) and a
  redesigned System Module screen (installation journey, programs with digests, security) were
  added on 2026-10-23. Verified. Evidence: `UpdateUiTest`, screenshot gallery, 2026-10-23.

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
- Zairenkai-Modules is pinned at `2d1f548` (PR #3, module 0.3.0, not merged yet: `"merged": false`;
  re-pin to the merge commit). Core (`345fc7f`) and FAS (`1c453db`), the `dev` merge commits of
  Core #3 and FAS #4, are pinned with it. Verified. Evidence: `integration/repository-lock.json`,
  `verify_integration.py lock`, `build-components.sh`, 2026-10-23.
- Zairenkai-Tuning (GPL-3.0-only `ztune`, topic branch `0c226b2`, no default branch yet) ships in
  the module the app packages, with its source, and is run only by Core. The app reads ztune's
  telemetry through Core's `tuning` method (`core/Tuning.kt`, record `tuning.telemetry`, read
  only) and shows Core's `tuning.*` capabilities generically. Verified on the host. Evidence:
  `TuningContractTest` on fixtures from Core's adapter running the real ztune and from the live
  daemon, 2026-10-23. **Unverified** on a device.
- The other 8 Zairenkai repositories have no runtime capability for the app. Zairenkai-Sdk
  (contracts only, `ed44c90`) and Zairenkai-Intelligence-Engine (host-only Local AI spike,
  `cc3d48c`) now have code; the registry reports them `NO_UPSTREAM_IMPLEMENTATION` with that
  reason. anime-extensions is a separate product and is excluded from the ecosystem audit
  (maintainer, 2026-10-22). Verified. Evidence: `integration/repository-audit.md` (re-audit
  2026-10-21), `integration/organization-repositories.json` (re-audit 2026-10-23), `RegistryTest`.

## Open questions

- Which device will run the first on-device validation?
- When should the app move to Material 3 1.5.0 stable (and the compileSdk 37 / AGP 9.1
  toolchain)?
