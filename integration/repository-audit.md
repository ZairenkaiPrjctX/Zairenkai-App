# Organization repository audit

## Re-audit, 2026-10-27 (live organization state)

The current organization has eleven device/app ecosystem repositories in this hand-off. The live
App full-integration CI (run 40434765619) succeeded on 2026-10-27: its reduced tests, source-lock
validation, native builds, binary/schema checks, APK inspection and artifact upload all passed.
The Modules release build (run 40483942101) also succeeded and produced the published `v0.3.1`
module ZIP. The Intelligence Engine model-release pipeline has now published the model release
`model-qwen2_5_0_5b_instruct_q4_0`, including the 428,730,208-byte GGUF, manifest, licence and
SHA-256 file.

| Area | Current observed state | Integration consequence |
|---|---|---|
| App → source components | Builds from exact commits in `repository-lock.json`, then records file hashes in `components.json` | Reproducible build, but source pins must be re-audited; `zie-run` had been pinned to a PR topic commit even after the merge |
| App → root module | Rebuilds Modules' locked Core/FAS/Tuning components and checks module scripts against the pinned Modules source | It does **not** yet consume the published Module ZIP asset directly; it rebuilds the exact locked source to re-verify it |
| Modules → Core/FAS/Tuning | Published v0.3.1 pins Core `345fc7f`, FAS `1c453db`, Tuning `0c226b2`; the accompanying patch proposes Core `377ba45` and module v0.3.2 | Version numbers are intentionally component-specific; compatibility is the tuple of full commits, schema versions and hashes, not one shared semver. The App lock must be re-pinned to the actual Modules merge SHA after that merge |
| Local AI | Runtime `zie-run` is packaged for arm64-v8a/x86_64; Qwen GGUF weights are a separate user download/import and SHA-256 is checked | The screenshot's “Model not imported” state means the AI composer is intentionally disabled until that 428 MB model is obtained and verified. This patch adds a first-run download action directly under the status badge |
| Tuning | Core rejects unsafe/untrusted executable paths before calling `ztune`; separately, Tuning denies real writes unless a device-exact, hardware-validated Tier D profile authorizes them | `PermissionRestricted` is currently a binary/path trust-gate failure, not proof that sysfs writes are allowed or that the underlying nodes themselves denied access. Exact device metadata from `zcored discover` is needed before changing the guard |
| Device validation | Component repositories document host/fixture CI and no physical-device validation for these components yet | Green CI proves build/fixture behaviour, not runtime compatibility on the user's ROM/kernel |

In the App patch, the machine-readable lock is re-pinned from the unmerged Intelligence Engine topic
commit to the current merged `dev` commit and to the exact Modules source commit that built the published
`v0.3.1` release. The companion Modules patch advances Core to `377ba45` and prepares v0.3.2; because
the merge SHA is not known yet, `integration/scripts/sync_pins.py` re-pins the App lock after the Modules
change is merged. The changes do not relax Tuning's fail-closed write policy or silently install modules.

---

## Re-survey, 2026-10-22

`list_repos` returns 17 repositories in `ZairenkaiPrjctX`, **all public** (earlier surveys
recorded most of them as private). Changes:

| Repository | Change | Effect on the app |
|---|---|---|
| Zairenkai-Tuning | **new**: GPL-3.0-only `ztune` (telemetry, SoC identity, exact-device tuning profiles), only branch `ccr-63f8aa87-1b0paj` @ `0c226b2`, no default branch | Not integrated: run by Core as a separate process and packaged with its Corresponding Source by Modules `bd1eb06` (topic branch). The app's lock still pins Modules `ea138ef`, without it |
| all component repositories | public | CI fetches the locked commits without the CI GitHub App token (`docs/build/ci.md`) |
| anime-extensions | excluded from the ecosystem audit by the maintainer | Not inspected again; never cloned, built, integrated or used as a reference |

## Merges and re-pin, 2026-10-21 (later the same day)

The four component pull requests were merged by the maintainer. Each merge commit's parents and
tree were checked (`git log -1 --format=%P`, `git diff --quiet <merge> <head>`):

| Repository | PR | Merge commit on `dev` | Parents | Tree identical to | CI on the merge |
|---|---|---|---|---|---|
| Zairenkai-ART | #2 | `0d8cd72` | `19f658d`, `8c6cea2` | `8c6cea2` | run 39631263321: success |
| Zairenkai-FAS | #3 | `be08b2d` | `3c83f0e`, `333365e` | `333365e` | run 39631199256: success |
| Zairenkai-Core | #2 | `11e9742` | `455368f`, `34e93e3` | `34e93e3` | run 39646717993: success |
| Zairenkai-ThermalTools | #2 | `3bc71fe` | `5358d4d`, `9d4b310` | `9d4b310` | run 39646811410: success |

The lock now pins these merge commits (`"merged": true` for every component). Rebuilt from them,
the Core and FAS module zips are byte-identical to the builds from the PR heads.

## Re-audit, 2026-10-21

`list_repos` now returns 16 repositories in `ZairenkaiPrjctX`: the 15 below plus
`anime-extensions` (public). Heads from `git ls-remote --heads`; content from `git ls-tree -r`.

| Repository | Change since 2026-10-19 | Locked revision now |
|---|---|---|
| Zairenkai-Core | dev = `455368f` (merge of PR #1, the `ad0c195` work). Topic branch `34e93e3` adds the read-only ThermalTools adapter and Apache-2.0; not merged | `34e93e3` (contract fixtures re-captured) |
| Zairenkai-FAS | dev = `3c83f0e` (merge of PR #2); tree identical to `0877452` | `3c83f0e` |
| Zairenkai-ART | dev = `19f658d` (merge of PR #1); tree identical to `fc72192` | `19f658d` |
| Zairenkai-Database-Source | dev = `d9c4290` (pipeline, workflows; 354 LineageOS candidates in staging) | `d9c4290` (asset regenerated) |
| Zairenkai-ThermalTools | **Now has code**: zthermal 0.1.0 on dev (`5358d4d`); 0.2.0 with Apache-2.0 on the topic branch `9d4b310`, not merged | `9d4b310` (new component `zthermal`) |
| Zairenkai-App | dev = `b288419` (merge of PR #1) | this repository |
| Sdk, Modules, Intelligence-Engine, License-Server, Graphics-Platform, Release, Docs, ZairenkaiPrjctX.github.io | unchanged: foundation files only | – |
| .github | topic branch `ee16385` (foundation tooling); no runtime capability | – |
| anime-extensions | newly listed. Aniyomi extensions-lib 16 source extensions (Kotlin; Internet Archive, Jellyfin), Apache-2.0, only branch `ccr-97bcb73d-9x6z9h` @ `049e9b3`. A separate product with no relation to device diagnostics | not integrated (not applicable) |

Pinning to unmerged topic-branch commits (Core, ThermalTools) is recorded as `"merged": false`
in `repository-lock.json`. Those commits are reachable only from their topic branches until
the pull requests merge.

## First audit

Surveyed 2026-10-19. The machine-readable form is `organization-repositories.json`. Revisions
the app is built from are pinned in `repository-lock.json`.

## Method

1. **Inventory.** `list_repos` (Claude Code remote tooling) enumerated every repository this
   account can reach: 15 in `ZairenkaiPrjctX`, plus 2 under the personal account `FebriCahyaa`.
   The two personal repositories are outside the organization and were not inspected.
2. **Branches and heads.** `git ls-remote --heads origin` for every repository.
3. **Content.** `git ls-tree -r` at each audited commit. A repository whose tree contains only
   `AGENT/`, `.claude/`, `AGENTS.md`, `CLAUDE.md` and `.gitignore` has no implementation.
4. **Sources.** For the repositories with code, the interfaces the app depends on were read at
   the audited commit:
   - Core: `docs/adr/0005-root-only-socket.md`, `module/`, `crates/zcore-domain/src/protocol.rs`;
   - FAS: `docs/INTEGRATION.md`, `crates/zfasd/src/main.rs`, `module/`;
   - ART: `crates/zart`;
   - Database-Source: `NOTICE`, `database/`, `schemas/`.
5. **Contracts.** The real binaries were built at the locked commits and run on the host to
   capture contract fixtures (`integration/scripts/capture-contracts.sh`).

No repository was inaccessible.

## Results

| Repository | Audited (branch @ commit) | Code | Disposition |
|---|---|---|---|
| Zairenkai-Core | ccr-63f8aa87-1b0paj @ `ad0c1954eb34` | Rust daemon + Magisk/KernelSU module | Integrated through root: `zcored ctl` (protocol v1). `dev` holds only foundation files; the topic branch is not merged. |
| Zairenkai-FAS | ccr-63f8aa87-1b0paj @ `0877452636a6` | Rust daemon + module + WebUI | Integrated through root: `zfasd ctl` (status schema 1). `dev` = merged PR #1. |
| Zairenkai-ART | ccr-63f8aa87-1b0paj @ `fc72192cfceb` | Rust `zart`, Python upstream tooling | `zart` packaged in the APK as a native executable. Upstream ART patches are build-time only (not applicable to an APK). |
| Zairenkai-Database-Source | dev @ `6e3db46b62bf` | Python AFID + JSON catalog | 353 unverified LineageOS-wiki device candidates packaged as an offline asset (CC BY-SA 3.0). The catalog has 0 verified devices. |
| .github | dev @ `4cdab7f75dc2` | Agent-foundation templates and validator | Development only; no runtime capability. |
| Zairenkai-ThermalTools | dev @ `6ff95424dcf4` | none | No implementation. Shown as "not implemented upstream". |
| Zairenkai-Sdk | dev @ `d482afebb6d3` | none | No implementation. |
| Zairenkai-Modules | dev @ `f226502613e6` | none | No module packages. The installed-module inventory comes from Core's `modules` method. |
| Zairenkai-Intelligence-Engine | dev @ `0191e36365b8` | none | No implementation; no analysis feature is shown. |
| Zairenkai-License-Server | dev @ `d563033d4e06` | none | No implementation; no licence check exists. The app has no network permission. |
| Zairenkai-Graphics-Platform | dev @ `32a1c689ef69` | Rust `zgfx`, read-only Vulkan/OpenGL ES probe | Implemented upstream, but not packaged or exposed in App; inventory marks it not applicable until an App adapter/UI exists. License still needs maintainer confirmation before distribution. |
| Zairenkai-Release | dev @ `0d804278b742` | none | Not applicable. |
| Zairenkai-Docs | dev @ `068bf87d02d0` | none | Not applicable. |
| ZairenkaiPrjctX.github.io | dev @ `27362a46141c` | none | Not applicable. |
| Zairenkai-App | dev @ `8aad1f6d8867` (before this work) | none | This repository. |

## Changes made to other repositories during the audit

**16 KB page-size fix.** zart, zcored and zfasd were linked with 4 KB-aligned ELF LOAD segments.
Devices with a 16 KB page size cannot load such binaries.

- Each repository now has a `.cargo/config.toml` setting `-z max-page-size=16384` for the 64-bit
  Android targets.
- Each `scripts/build-android.sh` now fails if a 64-bit binary is less aligned.
- Commits: ART `fc72192`, Core `ad0c195`, FAS `0877452`.

## Accounting

| | Count |
|---|---|
| Discovered (organization) | 15 |
| Fully inspected | 15 (tree at the audited commit; sources of the 5 with code) |
| Inaccessible | 0 |
| With applicable runtime capabilities | 4 (Core, FAS, ART, Database-Source) |
| With implemented integrations | 4 |
| Verified on a device | 0: no Android device or emulator was available |
| Verified against the real binaries (host contract capture) | 3 (zart, zcored, zfasd) + AFID data (generator run at the locked commit) |
| Not applicable (no code, or development-only) | 11 |
