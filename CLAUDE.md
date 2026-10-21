# CLAUDE.md: Zairenkai-App

Instructions for Claude Code in `ZairenkaiPrjctX/Zairenkai-App`. Keep this file under 200 lines;
put detail in `AGENT/`.

@AGENTS.md
@AGENT/PROJECT_CONTEXT.md

## Current state (read first)

This repository is the **Zairenkai App** (Android, Kotlin + Java, Jetpack Compose with
Material 3 Expressive, Google Sans Flex). Architecture: `docs/architecture/overview.md`.
- `:integration-core`: pure JVM. Registry, typed clients (Core, FAS, zart, zthermal, AFID), root shell,
  and `ProcessCommandRunner` (Java).
- `:app`: Compose UI and design system (`design/`), Android adapters in Java (`platform/`).
- `integration/`: organization inventory, revision lock, component build and contract-capture
  scripts.

Nothing has run on a device yet (`docs/testing/verification-report.md`).

Non-negotiables in this repo:
- **Never mock an integration in production code.** Availability comes from probes; an
  unprobed capability is NOT_VERIFIED, never AVAILABLE.
- **Root commands are `ShellScript`s of validated `ShellWord`s, never strings.** No `su` call
  before the user enables root.
- **zthermal is read only.** The app runs only `zthermal snapshot` (with `--framework` through
  root, on request). Never `apply`, `rollback` or `backup-config`; no thermal control in the UI.
- **Modules are installed only by the root manager's own installer**, after the user confirms,
  from the zip packaged in the APK, and only when its catalog is signed by a key in
  `integration/module-trust.json` (`docs/integration/system-module.md`). Never add a key to that
  list without the maintainer, and never put a private key anywhere in the repository or APK. Never
  write into `/data/adb` directly, never install on startup, and never mark a module ACTIVE
  without reading its state and its runtime.
- **FAS stays out of distributable builds** until its licence commit is merged and it is
  validated on hardware (`docs/integration/fas-provenance.md`): only `ZAIRENKAI_INTERNAL_BUILD=1`
  packages it, labelled internal. Never change FAS files or headers to make an audit pass.
- **Licensing:** the app is Apache-2.0 (`LICENSE`, `NOTICE`). New source files start with
  `SPDX-License-Identifier: Apache-2.0` and `Copyright 2026 ZairenkaiPrjctX contributors`. Never
  change third-party headers or notices (Gradle wrapper, font, AOSP attribution, device data).
- **Actions go through Core while it answers.** Direct `zfasd ctl` only when Core is down, and
  only after confirmation.
- **Contract fixtures come from the real binaries** (`capture-contracts.sh`), never hand-written
  JSON claiming to be real.
- **New UI uses the design system** (`design/`): no hard-coded colours or fonts, and status is
  shown as icon + text, never colour alone. Every UI string goes in `values/` (English, the default
  language whatever the system language) and `values-in/` (`AppLanguage`).
- **Material 3 is pinned to 1.5.0-alpha18 on purpose** (`docs/design/design-system.md`). Do not
  move to alpha19+ without the compileSdk 37 / AGP 9.1 migration.
- **No secrets, signing keys or tokens in the repository or the APK.**

## Commands

| Purpose | Command | Status |
|---|---|---|
| Build components | `ANDROID_NDK_HOME=<ndk> integration/scripts/build-components.sh` | Verified. Evidence: exit 0, 2026-10-21 |
| Capture contracts | `integration/scripts/capture-contracts.sh` | Verified as root (exit 0, 2026-10-21). As non-root (CI): Unverified; failed in CI runs 39639587745/39639605601, fixed 2026-10-22 |
| Test (JVM, Kotlin + Java) | `./gradlew :integration-core:test` | Verified. Evidence: 74 passed, 2026-10-21 |
| Test (Robolectric UI + Java) | `./gradlew :app:testDebugUnitTest` | Verified. Evidence: 24 passed, 2026-10-21 |
| Lint | `./gradlew lint` | Verified. Evidence: 0 errors, 2026-10-21 |
| Build | `./gradlew assembleDebug assembleRelease` | Verified. Evidence: debug signed with the debug key, release unsigned, 2026-10-21 |
| Shell lint | `shellcheck integration/scripts/*.sh` | Verified. Evidence: clean, 2026-10-21 |
| Integration checks | `python3 integration/scripts/verify_integration.py lock\|access\|build\|apk <apk>\|report <apk> [--release]` | Verified. Evidence: lock/access/build/apk/report ok; `--release` fails on 3 blockers (signing), 2026-10-21 |
| Verifier tests | `python3 -m unittest discover -s integration/scripts/tests` | Verified. Evidence: 17 passed, 2026-10-21 |
| Actions secrets (maintainer) | `integration/scripts/setup-actions-secrets.sh help\|status\|environment\|app\|module-signing\|keystore` | Verified. Evidence: 10 tests with a fake `gh`, 2026-10-21; `status` against GitHub all ok, exit 0, 2026-10-22 |
| Device check (read only) | `integration/scripts/device-readonly-check.sh [--root]` | Configured (no device available) |
| Validate agent foundation | `python3 <path-to-.github>/agent-foundation/scripts/validate_agent_setup.py .` | Configured |

Only list commands that exist in this repository. Never invent one.

## Working rules

- Inspect the relevant code before editing it. Preserve unrelated and uncommitted changes.
- Generated files are edited at their source, then regenerated. Managed foundation files
  (listed in `AGENT/.foundation-manifest.json`) are edited in `ZairenkaiPrjctX/.github`, not here.
- Destructive or outward-facing actions (deleting data, force-push, history rewrites,
  publishing releases, deploying, flashing devices) need explicit human approval each time.
- Do not claim a build, test, or signature succeeded without showing the output.

## Git

- Work on a topic branch; open **draft** pull requests; never merge or approve.
- Commits must be cryptographically signed. If signing fails, stop and report the error.
  Trailer format and verification: `AGENT/policies/git-and-commit-signing.md`.

## Where things are

- `AGENT/README.md`: index of the agent library (prompts, policies, playbooks, templates).
- `.claude/skills/`: run `/repo-audit`, `/implement-feature`, `/bug-fix`, `/test-and-verify`,
  `/security-review`, `/release-readiness`.
- `.claude/rules/`: path-scoped rules, loaded automatically.
