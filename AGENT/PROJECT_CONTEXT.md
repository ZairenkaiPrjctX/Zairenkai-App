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
| Application id | `com.zairenkaiprjctx.app`, minSdk 29, targetSdk 36; version 0.2.0-beta.1 (20001) since 2026-10-23, versionCode derived from the version (`docs/build/releases.md`) | Configured. Evidence: `app/build.gradle.kts`; APK validation pending Android SDK |
| Licence | Apache-2.0 (`LICENSE`, `NOTICE`, SPDX headers); bundled font OFL-1.1, device data CC BY-SA 3.0 | Verified. Evidence: maintainer decision 2026-10-21; repository tree; `docs/integration/license-inventory.md` |

## Contents

| Area | Location | Status |
|---|---|---|
| Gradle build (AGP 9.4.1, Gradle 9.6.0, Kotlin 2.4.10) | `settings.gradle.kts`, `gradle/libs.versions.toml` | Gradle project configuration and JVM tests pass. Evidence: `:app:tasks --all`, `:integration-core:test`; Android tasks pending SDK |
| JVM integration layer (Kotlin + Java), incl. root-manager adapters, the module installer and the update client | `integration-core/` | Verified. Evidence: 107 tests, 2026-10-23 |
| Android app, design system, Java platform adapters | `app/` | Earlier Robolectric verification: 74 tests passed (2026-10-24); current Actions run 40536572747 found two UI selector failures, addressed in this working tree. Android SDK unavailable locally; fresh CI run pending. **Unverified** on a device |
| Organization inventory (17 repositories: 16 Zairenkai, all public, plus the excluded anime-extensions), lock, scripts | `integration/` | Verified. Evidence: `verify_integration.py lock` ok, `list_repos` 2026-10-22 |
| CI | `.github/workflows/ci.yml` (reduced, scripts, full-integration), `release.yml` | Run 40545060428 failed because it requested nonexistent package `platforms;android-37`; GitHub runner images publish `android-37.0`. Workflows now install that package and alias its directory for AGP; awaiting a fresh Actions run. |
| Docs | `README.md`, `docs/` | Verified. Evidence: files present |

## Key facts and limitations

- Material 3 Expressive APIs are internal in stable `material3` 1.4.0. The app pins
  `1.5.0-alpha18`; the newer alpha19+ line requires compileSdk 37 and remains a separate migration.
  Verified. Evidence: compile error against 1.4.0; AAR metadata of alpha19+ requires
  compileSdk 37 and AGP 9.1.
- Core and FAS are reachable only through root (Core ADR-0005).
  Verified. Evidence: Core `docs/adr/0005-root-only-socket.md`.
- No automated device-lab or remote device-control access is available in this audit. The maintainer's screenshot proves that Module v0.3.1 was installed on KernelSU 3.4.1, but end-to-end Core telemetry, AI inference, SELinux behavior and tuning outcomes remain **Unverified** until the repaired build is retested.
- The app installs one root module, `zairenkai` (Zairenkai-Modules: zcored, zfasd and the GPL-3.0
  ztune with its source, data in `/data/adb/zairenkai/{core,fas,tuning}`), through Magisk, KernelSU/KernelSU-Next or APatch.
  Verified. Evidence: maintainer decision 2026-10-22; `integration/system-modules.json`; catalog
  fixture built from the real arm64 components (`docs/testing/verification-report.md`,
  2026-10-22). The separate `zairenkai_core` / `zairenkai_fas` modules are superseded. The manager commands were read in each manager's source. Verified. Evidence:
  `docs/integration/system-module.md`, `SystemModuleTest`. Installation on a device is
  **Unverified**: no device has a validation record.
- The App packages the single Modules ZIP as the user-confirmed root-manager installer payload, and Modules now also publishes its own versioned ZIP release (`v0.3.1`, 2026-10-27). Core and FAS do not have independent stable binary-release assets. Verified. Evidence: App integration lock/catalog flow; Modules release run 40483942101. The App still needs its signing/trusted-catalog release gate before a signed APK can be published.
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
- The live App source lock currently pins Core `345fc7f`, FAS `1c453db`, Modules `a3efff6` (the source revision for published module v0.3.1), ART `0d8cd72`, ThermalTools `3bc71fe`, Database-Source `d9c4290`, and Intelligence-Engine `7a05fe5` (merged `dev`). Live Core `dev` is `377ba450`, which fixes Android `/data` ownership validation but is not yet in the published module; the bundled Modules patch prepares v0.3.2. Tuning `0c226b2` is the current `dev` head and remains nested under Modules' `components.lock.json`. Database-Source live `dev` has moved to `9267cec5`; regenerate the AFID asset before repinning it. Evidence: repository lock, live GitHub commit and workflow reads, 2026-10-27.
- The full-integration CI job passed for the first time on `d0db6b7` (run 39827139485: every
  component built at its locked commit, contracts re-captured, tests, lint, APKs with native
  components required, integration report), fetching the public sources without a token.
  Verified. Evidence: CI run 39827139485, 2026-10-22.
- The UI was redesigned on 2026-10-23 (Material 3 Expressive wavy indicators, segmented groups,
  five destinations with an AI tab; `docs/design/design-system.md`). Verified. Evidence:
  `RedesignTest`, screenshot gallery, 2026-10-23. **Unverified** on a device.
- Local AI: Intelligence-Engine `dev` `7a05fe5` (merged) supplies `zie-run` and model-release tooling; the App bundles the native runner, registry, grammar and manifest for arm64-v8a/x86_64. The Qwen2.5-0.5B-Instruct q4_0 model is now published at `model-qwen2_5_0_5b_instruct_q4_0`; the 428,730,208-byte weight file is separate, resumable and downloaded only on user action or imported, then accepted only when the manifest size and SHA-256 match. The AI UI patch surfaces the download action near the status badge when this is the sole missing prerequisite. Read-only tools run; system actions stay as Core dry runs until the user approves. Host tests exist, but on-device speed/memory/SELinux and model-output safety remain **Unverified**; the manifest says `safety_evaluation: not_evaluated`.
- AI failure handling (2026-10-27): unexpected inference exceptions become a retry prompt, and unexpected Core apply exceptions become a failed approval result; cancellation still propagates. The app does not claim a requested change succeeded unless Core verifies it. Evidence: `app/ui/ai/AiModels.kt`. Unit/UI verification is pending in this change.
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
- Zairenkai-Modules is pinned at `a3efff6`, the source revision used to publish module v0.3.1. Its current `components.lock.json` still pins Core `345fc7f` and FAS `1c453db`; the local Modules work proposes Core `377ba450` and module v0.3.2. Standalone App builds need the reviewed lock update; the central matrix build injects exact current source SHAs into temporary locks. Verified. Evidence: live release metadata, App source lock, and `Zairenkai-Release/scripts/prepare_build.py`, 2026-10-27.
- Zairenkai-Tuning (GPL-3.0-only `ztune`, live `dev` `0c226b2`) ships in the module with its licence and Corresponding Source and is run only by Core. The app reads telemetry through Core's `tuning` method and shows `tuning.*` capabilities. No Tier-D exact-device hardware-validated profile is shipped, so writes remain denied. Host tests pass; the user screenshot shows the current v0.3.1/Core path trust-gate problem. Full capability and telemetry behavior must be retested with the repaired module.
- Zairenkai-Intelligence-Engine is integrated at merged `dev` `7a05fe5`; App source locks track that commit. Verified. Evidence: lock/build checks and model-release metadata, 2026-10-27.
- Zairenkai-Sdk (`ed44c90`) supplies contracts only, not a daemon. Zairenkai-Graphics-Platform now has a read-only `zgfx` Vulkan/OpenGL ES probe at live `dev` `32a1c68`, but App does not package or expose it; the inventory keeps the feature not applicable until the adapter and UI are added. The repo README says licence confirmation is pending, so do not distribute it before that is resolved. Database-Source has newer upstream metadata than the App's pinned AFID asset and needs a regenerate/validate/re-pin change. anime-extensions is a separate product and excluded from the device ecosystem. Evidence: live repository trees, current integration inventory and `repository-audit.md`, 2026-10-27.

## Open questions

- Which device will run the first on-device validation?
- When should the app migrate from Material 3 1.5.0-alpha18 to a stable release?
