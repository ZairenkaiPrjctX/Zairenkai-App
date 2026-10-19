# Organization repository audit

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
| Zairenkai-Graphics-Platform | dev @ `b3dcca4457f3` | none | No implementation. |
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
