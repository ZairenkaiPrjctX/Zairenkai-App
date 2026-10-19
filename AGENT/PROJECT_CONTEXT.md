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
| Licence | none chosen (no `LICENSE` file); bundled font OFL-1.1, device data CC BY-SA 3.0 | Verified. Evidence: repository tree, `integration/third-party-assets.json` |

## Contents

| Area | Location | Status |
|---|---|---|
| Gradle build (AGP 8.9.1, Gradle 8.14.3, Kotlin 2.1.20) | `settings.gradle.kts`, `gradle/libs.versions.toml` | Verified. Evidence: build 2026-10-19 |
| JVM integration layer (Kotlin + Java) | `integration-core/` | Verified. Evidence: 37 tests |
| Android app, design system, Java platform adapters | `app/` | Verified on JVM. Evidence: 17 Robolectric tests. **Unverified** on a device |
| Organization inventory, lock, scripts | `integration/` | Verified. Evidence: scripts exit 0, 2026-10-19 |
| CI | `.github/workflows/ci.yml` | Verified locally. Evidence: actionlint clean. **Unverified** on GitHub |
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

## Authoritative sources

| Topic | Source |
|---|---|
| Repository facts | this file |
| Architecture | `AGENT/ARCHITECTURE.md` → `docs/architecture/overview.md` |
| Repositories and revisions | `integration/organization-repositories.json`, `integration/repository-lock.json` |
| Verification | `docs/testing/verification-report.md` |
| Organization standards | `ZairenkaiPrjctX/.github` → `agent-foundation/standards/` |

## Related repositories

- Zairenkai-Core (`ad0c195`), Zairenkai-FAS (`0877452`), Zairenkai-ART (`fc72192`) and
  Zairenkai-Database-Source (`6e3db46`) are integrated. Verified. Evidence:
  `integration/repository-lock.json`, contract tests.
- The other 11 repositories have no runtime capability. Verified. Evidence:
  `integration/repository-audit.md`.

## Open questions

- Which licence should the app use?
- Which device will run the first on-device validation?
- Release signing: where will the key live, and who signs?
- When should the app move to Material 3 1.5.0 stable (and the compileSdk 37 / AGP 9.1
  toolchain)?
