# CLAUDE.md: Zairenkai-App

Instructions for Claude Code in `ZairenkaiPrjctX/Zairenkai-App`. Keep this file under 200 lines;
put detail in `AGENT/`.

@AGENTS.md
@AGENT/PROJECT_CONTEXT.md

## Current state (read first)

This repository is the **Zairenkai App** (Android, Kotlin + Java, Jetpack Compose with
Material 3 Expressive, Google Sans Flex). Architecture: `docs/architecture/overview.md`.
- `:integration-core`: pure JVM. Registry, typed clients (Core, FAS, zart, zthermal, AFID, and ztune telemetry through Core), the
  Local AI agent (`ai/`), root shell, and `ProcessCommandRunner` (Java).
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
- **One root module, `zairenkai`** (Zairenkai-Modules), carries zcored, zfasd and ztune; data lives
  in `/data/adb/zairenkai/{core,fas,tuning}`. ztune is GPL-3.0-only: the app never runs it, links it
  or copies its code; it ships in the zip with its source. Never add a separately installed Zairenkai module. FAS is
  distributed in it by the maintainer's decision of 2026-10-22, before hardware validation
  (`docs/integration/fas-provenance.md`). Never change FAS files or headers to make an audit pass.
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
- **App updates come only from published GitHub Releases of this repository**, over HTTPS, and
  install only after the SHA-256 file, the package, the exact version, a newer versionCode and
  the same signing certificate check out, through Android's installer with the user's
  confirmation (`docs/build/releases.md`). Never install silently, never accept another signer,
  never publish a release (the release workflow only creates drafts). Apart from updates, the
  network is used only for the model download the user starts (below); never send device data.
- **Local AI is on-device only** (`docs/integration/local-ai.md`): `zie-run` runs as the app's uid
  with a fixed argv, and its build refuses networking imports. No remote AI API, ever. The model
  file is never packaged in the APK. It is downloaded only when the user taps Download (asking first
  on mobile data), only from Zairenkai-Intelligence-Engine's release `model-<model_id>`, or
  imported from a file, and it is kept only if its size and SHA-256 match the manifest. The model's output is re-checked against the closed
  registry. A system tool is only a Core dry run until the user approves, and Core applies it.

## Commands

| Purpose | Command | Status |
|---|---|---|
| Build components | `ANDROID_NDK_HOME=<ndk> [LLAMA_CPP_SRC=<llama.cpp b11500 checkout>] integration/scripts/build-components.sh` (needs cmake and ninja for zie-run) | Verified. Evidence: exit 0 with NDK r27d, module 0.3.1 at Modules `9175e1e`, zie-run at IE `af31fd7`, 2026-10-23 |
| Capture contracts | `integration/scripts/capture-contracts.sh` | Verified. Evidence: as root locally (2026-10-21, re-run at the merge-commit pins; 2026-10-23 with the new tuning captures, exit 0) and as uid 1001 in CI (run 39645071811; full integration 39827139485, 2026-10-22; with the tuning captures, run 39888821755, 2026-10-23) |
| Test (JVM, Kotlin + Java) | `./gradlew :integration-core:test` (with `ZIE_RUN` and `ZIE_MODEL` also the real runner and model) | Verified. Evidence: 126 passed, 1 skipped (real runner without the variables), 2026-10-24; the real-runner test passed with them, 8 s, 2026-10-23 |
| Test (Robolectric UI + Java) | `./gradlew :app:testDebugUnitTest` | Verified. Evidence: 74 passed, 0 skipped with `ZAIRENKAI_SCREENSHOT_DIR` set (without it the gallery is skipped), 2026-10-24, on x86-64 with `ANDROID_HOME=/opt/android/sdk`. On aarch64 the NATIVE-graphics tests (`InfoRowNativeLayoutTest`, `ConnectedChoiceTouchTest`, `LongTextLayoutTest`, the gallery) are skipped: no Linux aarch64 runtime in Robolectric 4.14 |
| Screenshots | `ZAIRENKAI_SCREENSHOT_DIR=<dir> ./gradlew :app:testDebugUnitTest --tests '*ScreenshotGallery*'` | Verified. Evidence: 63 PNGs (host, captured binary output, Indonesian at 1.3× font, settings pages, styled showcases, brand logo, About, Updates, System Module whole, AI chat over the real agent, AI model card, update-offer previews; light and dark), 2026-10-23 |
| Lint | `./gradlew lint` | Verified. Evidence: 0 errors (24 dependency-version warnings), 2026-10-24 |
| Build | `./gradlew assembleDebug assembleRelease` | Verified. Evidence: debug signed with the debug key, release unsigned, 2026-10-23; release signed in a dry run with a throwaway key, v3 verified (`docs/build/releases.md`) |
| Shell lint | `shellcheck integration/scripts/*.sh` | Verified. Evidence: clean, 2026-10-23 |
| Integration checks | `python3 integration/scripts/verify_integration.py lock\|access\|build\|apk <apk>\|report <apk> [--release]` | Verified. Evidence: lock/build/apk/report ok on the 0.2.0-beta.1 release APK, 2026-10-23; `--release` on the dry-run-signed APK: one blocker, the module catalog is unsigned (maintainer key), 2026-10-23 |
| Verifier tests | `python3 -m unittest discover -s integration/scripts/tests` | Verified. Evidence: 23 passed, 1 skipped, 2026-10-23 |
| Actions secrets (maintainer) | `integration/scripts/setup-actions-secrets.sh help\|status\|environment\|module-signing\|keystore` | Verified. Evidence: 9 tests with a fake `gh`, 2026-10-22 (the CI App subcommand was removed: the locked repositories are public); `status` against GitHub all ok, exit 0, 2026-10-22, before that change |
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
