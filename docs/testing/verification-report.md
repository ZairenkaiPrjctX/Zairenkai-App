# Verification report

## Long text, richer screens, Settings › Appearance (2026-10-23)

**Verdict: verified on an x86-64 Linux host under Robolectric.** Nothing ran on a device: blur,
launcher icon switching on real launchers, Google Fonts download and the photo picker are
**Unverified** on hardware.

| Check | Command / evidence | Result |
|---|---|---|
| App tests | `ANDROID_HOME=/opt/android/sdk ./gradlew :app:testDebugUnitTest` | 43 passed, 10 skipped (screenshot gallery) |
| App tests with screenshots | `ZAIRENKAI_SCREENSHOT_DIR=<dir> ./gradlew :app:testDebugUnitTest` | 53 passed, 0 skipped; 56 PNGs |
| JVM tests | `./gradlew :integration-core:test` | 99 passed (unchanged) |
| Lint | `./gradlew lint` | 0 errors; 24 warnings, all `GradleDependency` / `AndroidGradlePluginVersion` |
| Build | `./gradlew assembleDebug assembleRelease -Pzairenkai.allowMissingComponents=true` | both APKs built |
| Permissions | `aapt2 dump permissions app-release-unsigned.apk` | no INTERNET; only the AndroidX receiver permission |
| Notices | `assets/licenses/android/NOTICES.txt` in the APK | `haze` 1.6.10 and `ui-text-google-fonts` 1.11.4 listed, Apache-2.0 |

New tests:
- `LongTextLayoutTest` (NATIVE): every component with oversized text on a 320 dp screen at 1.5×
  font keeps its text on screen and never squeezed below 64 dp; checked to fail on a deliberately
  squeezed row.
- `AppearanceTest`: contrast of every generated scheme (see `docs/design/design-system.md`), hex
  parsing, settings round trip (a broken stored list is dropped), launcher alias switching with
  exactly one alias enabled, settings search opening the wallpaper page.

The mpvRx repository (AGPL-3.0, `Riteshp2001/mpvRx` at `f5f616b`) was read for behaviour only:
the app is Apache-2.0, so no code was copied. The Google Play services font-provider certificates
come from `android/user-interface-samples` at `2c0b04e` (Apache-2.0), unchanged
(`integration/third-party-assets.json`).

## UI/UX redesign: Material 3 Expressive, Zairenkai AI screen (2026-10-23)

**Verdict: verified on an x86-64 Linux host under Robolectric.** Nothing ran on a device or an
emulator: touch feel, frame rate of the wavy indicators and the AI orb, and TalkBack order are
**Unverified** on hardware.

| Check | Command / evidence | Result |
|---|---|---|
| App tests | `ANDROID_HOME=/opt/android/sdk ./gradlew :app:testDebugUnitTest` | 36 passed, 6 skipped (the screenshot gallery, which runs only with `ZAIRENKAI_SCREENSHOT_DIR`) |
| App tests with screenshots | `ZAIRENKAI_SCREENSHOT_DIR=<dir> ./gradlew :app:testDebugUnitTest` | 42 passed, 0 skipped; 34 PNGs |
| JVM tests | `./gradlew :integration-core:test` | 99 passed, 0 failed, 0 skipped (unchanged) |
| Lint | `./gradlew lint` | 0 errors; 24 warnings, all `GradleDependency` / `AndroidGradlePluginVersion` |
| Build | `./gradlew assembleDebug assembleRelease -Pzairenkai.allowMissingComponents=true` | both APKs built, without the native components (the flag) |

New tests: `RedesignTest` (Home's next steps and capability groups, the AI transcript and
approval rules, the AI screen in the real app: unavailable badge, disabled composer, a prompt
opening Thermal) and `ConnectedChoiceTouchTest` (real touches on every segment of a connected
button group, NATIVE graphics). `AppUiTest` and `LanguageTest` follow the new navigation
(Runtime is a page of System; destinations are found by their `nav-*` test tags).

**Robolectric hit-testing.** A real touch on the first or last segment of a connected button
group never arrived under LEGACY graphics, even a 60 ms press, while the middle segment and the
semantics action worked. The end segments have path outlines, which LEGACY graphics cannot
hit-test; under NATIVE graphics all three take the touch. This also explains the segmented-button
finding recorded below under "Defects found and fixed during verification".

**Screenshots** (`app/src/test/kotlin/.../screenshots/ScreenshotGallery.kt`, 411 × 891 dp,
xxhdpi, light and dark):
- `host-*`: the real `AppContainer` on this JVM, so states are what the registry probed here (no
  root, no packaged native components);
- `captured-*`: root granted and every su command answered with output captured from the real
  zcored, zfasd and ztune binaries (`integration-core/src/test/resources/contracts`); anything
  not captured fails as on a device without the component;
- `preview-ai-chat-*`: the AI components with sample turns, labelled as samples. The app itself
  never shows a transcript until an on-device runtime exists.

One gallery run timed out waiting for the registry while lint ran in parallel; three later runs
without lint passed.

## Typed tuning telemetry; every pin in `dev` (2026-10-23)

**Verdict: verified on an x86-64 Linux host and in CI** (run 39888821755 at `fbe9e44`: all three
jobs green, the tuning fixtures re-captured as uid 1001). Nothing ran on a device.

| Check | Command / evidence | Result |
|---|---|---|
| Pins in `dev` | `git merge-base --is-ancestor <pin> origin/dev` (Core `8931cbb`, FAS `99cfb18`, Modules `298311c`) | all three ancestors; lock `merged: true`, `branch: dev` |
| Components | `ANDROID_NDK_HOME=/opt/android/android-ndk-r27d integration/scripts/build-components.sh`; `verify_integration.py build` | exit 0; `build: ok` |
| Tuning fixtures | `integration/scripts/capture-contracts.sh` | exit 0. `core/tuning.json`: Core's own `TuningAdapter` (fixed argv, envelope checks) running ztune built at Tuning `0c226b2`, owned by the capturing user, wrapped in Core's `Response`. `core/tuning-not-installed.json`: the live daemon's `not_found` answer without ztune. Re-captured fixtures whose only changes were ids, clocks and pids were not committed |
| Host identifiers | review of `core/tuning.json` | none: SoC family `unknown`, virtual block devices `vd*`, memory sizes |
| JVM tests | `./gradlew :integration-core:test` | 99 passed, 0 failed, 0 skipped (11 new in `TuningContractTest`) |
| Robolectric | `ANDROID_HOME=/opt/android/sdk ./gradlew :app:testDebugUnitTest` | 29 passed, 0 skipped |
| Lint | `./gradlew lint` | 0 errors; 24 warnings, all `GradleDependency` / `AndroidGradlePluginVersion` |
| Lock, verifier, shell | `verify_integration.py lock`; `python3 -m unittest discover -s integration/scripts/tests`; `shellcheck integration/scripts/*.sh` | ok; 17 passed; clean |

Behaviour: `CoreClient.tuning()` refuses another schema, schema version, kind or component, drops
any value sent with a failed status, and never coerces a string into a number. Core answers
`not_found` for every ztune failure; only its message prefix (`Zairenkai-Tuning telemetry: not
installed`) separates a module without ztune (`COMPONENT_NOT_INSTALLED`) from a failing ztune
(`INITIALIZATION_FAILED`). A distinct Core error code would remove that dependency on wording.

The first CI run (39887060148 at `cf6fdb8`) failed in `capture-contracts.sh`: Core's binary check
walks from ztune to the SysRoot and requires the capturing uid at every step, which never holds
under `/` for uid 1001. Reproduced locally with `runuser -u ci` (uid 1001), fixed in the fixture with
a user-owned temporary SysRoot (Core unchanged), verified the same way, then green in CI.

Not verified: ztune on any phone (on this host every frequency,
governor and temperature reading is `unsupported`).

## Module with ztune; first green full-integration run (2026-10-22)

**Verdict: the integrated app built in CI for the first time; the re-pin is verified on the host
and waits for CI.** Nothing ran on a device.

| Check | Command / evidence | Result |
|---|---|---|
| Full integration on GitHub, at `d0db6b7` (pins Core `4cdfe8e`, Modules `ea138ef`) | CI run 39827139485 | **all jobs green**: anonymous fetch, every component built at its lock, `verify_integration.py build`, contracts re-captured, `:integration-core:test :app:testDebugUnitTest lint assembleDebug assembleRelease`, APK inspection and integration report |
| Re-pin: Core `8931cbb`, Modules `298311c` (module 0.2.0 with ztune and its source) | `ANDROID_NDK_HOME=… build-components.sh` | exit 0; zip SHA-256 `f4ad8d64…400d`, byte-identical to the build in Zairenkai-Modules from the same commits |
| Artifacts and notices | `verify_integration.py build` | `build: ok` (now also `licenses/zairenkai-tuning/THIRD_PARTY_NOTICES.txt`, 25 crates) |
| GPL obligations in the zip | `unzip -l zairenkai.zip` | `bin/ztune`, `licenses/zairenkai-tuning/{LICENSE,NOTICE,SOURCE.md,THIRD_PARTY.md}`, `source/zairenkai-tuning/zairenkai-tuning-0c226b2…-src.tar.gz` |
| Contracts | `capture-contracts.sh` | exit 0; every core/ and fas/ fixture re-captured on a Linux container at the new pins (Core now reports `tuning.gpu_governor`, `tuning.io_scheduler`, `tuning.telemetry` as `unsupported` without ztune; module version 0.2.0) |
| Lock | `verify_integration.py lock` | ok |
| Gradle at the new pins | `./gradlew :integration-core:test` | **not run locally**: Maven Central HTTP 429; CI on this push is the check |

## Public sources, no CI GitHub App (2026-10-22)

**Verdict: verified locally, not yet on GitHub.** Every ZairenkaiPrjctX repository is public
(`list_repos`, 2026-10-22), so `ci.yml` full-integration and `release.yml` no longer create a
GitHub App token: they check that the locked repositories can be fetched without credentials
and build from them. The signing secrets of the `release` environment are unchanged.

| Check | Command / evidence | Result |
|---|---|---|
| Anonymous fetch of every locked repository | `python3 integration/scripts/verify_integration.py access` (no token configured in the session's git) | `access: ok` |
| Lock and inventory | `python3 integration/scripts/verify_integration.py lock` | `lock: ok` (inventory: 17 repositories, all public, Zairenkai-Tuning added, anime-extensions excluded) |
| Verifier and secrets-script tests | `python3 -m unittest discover -s integration/scripts/tests` | 17 passed (the two CI App subcommand tests replaced by one) |
| Shell lint | `shellcheck integration/scripts/*.sh` | clean |
| Workflow lint | `actionlint .github/workflows/*.yml` | clean |
| Gradle | `./gradlew :integration-core:test` | **not run**: Maven Central answered HTTP 429 (Too Many Requests) to this session, before compilation. The only Kotlin change is the inventory count in `DataTest` (16 → 17) |

Not verified: a GitHub run of the changed workflows; whether the maintainer deletes the unused
`ZAIRENKAI_CI_APP_ID` / `ZAIRENKAI_CI_APP_PRIVATE_KEY` values.

## One root module, `zairenkai` (2026-10-22)

**Verdict: verified on the host and in component CI.** Nothing ran on a device; the release
gate still fails on signing.

Maintainer decision: one root module assembled in Zairenkai-Modules, FAS included, all data in
`/data/adb/zairenkai`. Pins: Modules `ea138ef` (PR #1), Core `4cdfe8e` (PR #3), FAS `99cfb18`
(PR #4), all on topic branches.

| Check | Command / evidence | Result |
|---|---|---|
| Core | `cargo test --workspace`, clippy, fmt, shellcheck; CI 39700980963 (PR) and 39701977860 (dispatch at `4cdfe8e`) | 65 passed; all CI jobs green, including the NDK arm64 component |
| FAS | `cargo test --workspace`, clippy, fmt, shellcheck; CI 39700987721 (PR) and 39701982927 (dispatch at `99cfb18`) | 82 passed; all CI jobs green, including the NDK arm64 component |
| Modules | `python3 -m unittest discover -s tests`; shellcheck; actionlint | 12 passed; clean. CI `checks` green; CI `module` blocked: the CI GitHub App is not configured in Zairenkai-Modules |
| Real module | Modules' `assemble.py` over the components of the two dispatch runs; `module_asset.py` | `zairenkai-v0.1.0.zip`, 20 entries, 8 matched to source, SHA-256 `4937db2e…5a2c`. A component from a `pull_request` run (PR merge commit `5994ad3`) was correctly refused |
| First-install FAS config | real `zfasd check-config` (host build at `99cfb18`) on `defaults/fas-config.toml` | ok (`enabled = false`) |
| Contracts | `capture-contracts.sh` | `core/modules.json`, `core/modules-installed.json`, `system-modules/catalog.json` re-captured. The other core/fas fixtures were kept: this host is proot on an Android phone, and the new capture recorded that phone's identity (`PROVENANCE.json`) |
| App | `:integration-core:test`, `:app:testDebugUnitTest`, `lintDebug`, `verify_integration.py lock`, verifier tests | 88 (1 skipped), 29 (1 skipped: native graphics), lint 0 errors, lock ok, 18 passed |

Not verified: `build-components.sh` end to end (no NDK on this aarch64 host; the App's
`full-integration` job needs the CI GitHub App installed on Zairenkai-Modules); install, boot,
FAS enable and uninstall on any device or root manager.

## Re-pin to the component merge commits (2026-10-21)

**Verdict: verified on the host.** Nothing ran on a device, and the release gate still fails on
signing.

The maintainer merged all four component PRs; the record is `integration/repository-audit.md`:
- ART #2 → `0d8cd72`, dev CI 39631263321 ✓
- FAS #3 → `be08b2d`, dev CI 39631199256 ✓
- Core #2 → `11e9742`, dev CI 39646717993 ✓
- ThermalTools #2 → `3bc71fe`, dev CI 39646811410 ✓

Each merge commit's tree is identical to the PR head that was tested. The lock now pins the merge
commits, all `"merged": true`.

| Check | Result |
|---|---|
| `verify_integration.py lock`, `access` | ok |
| `ZAIRENKAI_INTERNAL_BUILD=1 build-components.sh` | exit 0; Core zip `f7651bf7…5f41` and FAS zip `9e3503a2…e7d5`, both byte-identical to the builds from the PR heads; `build` ok |
| `build-components.sh` (distributable) | exit 0; `build` ok; no FAS zip |
| `capture-contracts.sh` | exit 0; fixtures differ only in timestamps, process ids and the recorded source commits |
| `:integration-core:test` / `:app:testDebugUnitTest` | 74 / 24 passed, 0 failed |
| Verifier tests, lint | 7 passed; 0 lint errors |
| APKs (after rebasing onto the maintainer's release-signing commits) | debug 37,993,601 bytes `d7cd658d…d1fa`; release unsigned 30,315,188 bytes `e05333b3…678c` |
| `apk` (debug, release) | ok |
| Verifier and setup-script tests | 17 passed (includes the maintainer's `test_setup_actions_secrets.py`) |
| Release gate (local, no signing secrets here) | **fails on 2**: APK unsigned; catalog unsigned. The trust list now holds the maintainer's key `4330305a…` (key id checked against the public key) |

CI: run 39645071811, a dispatch at `8168526` (the maintainer's head, still on the topic-branch
pins), passed all three jobs, **including full-integration**. It is the first CI run that built
the native components. A run at the merge-commit pins follows this commit.


## Licence basis: authorship, Anthropic terms, AOSP NOTICE (2026-10-21)

**Verdict:** the licence declarations are consistent and audited. Signing, the full CI job and
device validation are still open, so this is **not release-ready**. Licence completion says
nothing about device compatibility. Nothing ran on a device.

Evidence reviewed:
- **Git history** of App, ART and FAS: every non-merge commit is by Claude Code sessions run by
  the maintainer, signed off except the first agent-foundation commit; the merges are the
  maintainer's; no other contributor.
- **Anthropic's terms:** Consumer Terms §4 (effective 2025-10-08) and Commercial Terms §B
  (effective 2025-06-17). Both assign Anthropic's rights in Outputs, "if any", to the user.
- **AOSP:**
  - `platform/art` `NOTICE` at `android-17.0.0_r1` (SHA-256 `613c3a67…405fd`) carries
    "Copyright (c) 2005-2013, The Android Open Source Project". This line is now repeated in
    ART's NOTICE, the app NOTICE and zart's attribution file.
  - The header of `instruction_set_features_arm64.cc` was checked as well.
- **Line comparison** of the App with Magisk `af8c256`, KernelSU `6d31a61`, KernelSU-Next
  `b427a3f` and APatch `e8e4651` (all GPL-3.0), ignoring imports:
  - 26, 29, 22 and 21 matching lines; no run of 3 or more;
  - every match is a common Compose, Android or Gradle idiom.
- **FAS comparison:** unchanged (0 runs).

Commits:
- ART `8c6cea2`: NOTICE and authorship record.
- FAS `f6de6ee` and `333365e`: NOTICE wording and authorship record.

None changes code. All three commits are signed and verified.

| Check | Result |
|---|---|
| SPDX headers | every own Kotlin, Java, Gradle, Python, shell, Rust and HTML file in App, ART and FAS; none added to the Gradle wrapper |
| Crate licences | zart 11, zthermal 25, Core 29, FAS 24 packages (incl. its 3 own crates): MIT OR Apache-2.0, MIT, Unlicense OR MIT, (MIT OR Apache-2.0) AND Unicode-3.0, Zlib. No copyleft |
| Android libraries | 112, all Apache-2.0, none undeclared |
| `build-components.sh` | internal exit 0 (FAS zip `9e3503a2…e7d5` with the new NOTICE); default exit 0; `verify_integration.py build` ok in both |
| `capture-contracts.sh` | exit 0 |
| `:integration-core:test` / `:app:testDebugUnitTest` | 74 / 24 passed, 0 failed |
| Verifier tests, lint | 7 passed; 0 lint errors |
| APKs | debug 37,993,601 bytes `ef9bb2ed…7a63`; release unsigned 30,314,996 bytes `d026533f…36eb`; no FAS zip |
| `verify_integration.py` lock, access, build, apk | ok |
| Release gate | **fails on 3**: APK unsigned; catalog unsigned; no trusted module key (unchanged; no gate was weakened, no key created) |

Remaining legal uncertainty: `docs/integration/license-inventory.md`, "Authorship and rights".


## Licences, component PRs, CI App input, Core-only device procedure (2026-10-21)

**Verdict: partially verified.** The licence work and every host check passed. Signing, the
full-integration CI job and device validation remain open, so this is **not release-ready**.
Nothing ran on a device.

CI evidence:

| Run | Repository, commit | Result |
|---|---|---|
| 39622352061 | App f34246c (dispatch) | scripts ✓, reduced ✓, full-integration ✗ at "Require the CI GitHub App" |
| 39623939493 | App b9ce39b (dispatch) | scripts ✓, reduced ✓, full-integration ✗ at the same step |
| 39625331038 | Core PR #2, 34e93e3 | fmt/clippy/tests ✓, MSRV ✓, shellcheck ✓, Android arm64 module ✓ |
| 39625349358 | ThermalTools PR #2, 9d4b310 | fmt/clippy/tests ✓, MSRV ✓, shellcheck ✓, Android binaries ✓ |

No run of the full-integration job has built native components: the CI GitHub App does not
exist yet. The reduced build proves nothing about the integrated APK.

Component repositories (topic branch `ccr-63f8aa87-1b0paj`; every commit SSH-signed and
verified):

| Repository | Commit | What | Local checks |
|---|---|---|---|
| ART | `31caf46` on dev `19f658d` | Apache-2.0: LICENSE, NOTICE with the AOSP attribution (header checked in the AOSP file at `android-17.0.0_r1`), SPDX headers, Cargo licence; LICENSE and NOTICE in `dist/` | cargo 26 passed; tools tests OK; clippy, fmt, shellcheck clean; `art_upstream.py validate` ok; `build-android.sh` exit 0 |
| FAS | `9d87236` on dev `3c83f0e` | Apache-2.0: LICENSE, NOTICE, SPDX headers, Cargo licence; LICENSE and NOTICE required in the zip | cargo 82 passed; clippy, fmt, shellcheck clean; zip `1c998502…7e2c` lists LICENSE, NOTICE, THIRD_PARTY.md |
| Core | `34e93e3` (unchanged) | draft PR #2 into dev | cargo 65 passed; clippy and fmt clean |
| ThermalTools | `9d4b310` (unchanged) | draft PR #2 into dev | cargo 109 passed; clippy and fmt clean |

App, default (distributable) profile:

| Check | Result |
|---|---|
| `build-components.sh` | exit 0; zart from `31caf46` with LICENSE and NOTICE; Core zip `f7651bf7…5f41`, unchanged and carrying LICENSE and NOTICE; catalog unsigned |
| `ZAIRENKAI_INTERNAL_BUILD=1 build-components.sh` | exit 0; FAS zip from `9d87236` (`1c998502…7e2c`); `verify_integration.py build` ok |
| `capture-contracts.sh` | exit 0; fixtures changed only in timestamps, process ids and the recorded source commits |
| `:integration-core:test` | 74 passed, 0 failed |
| `:app:testDebugUnitTest` | 24 passed, 0 failed |
| Verifier tests | 7 passed (adds `test_a_module_zip_without_its_licence_and_notice_fails`) |
| `lint` | 0 errors, 24 warnings |
| APKs | debug 37,993,601 bytes `8fe1f6e5…cb1b`; release unsigned 30,314,568 bytes `6d3d2ed1…b7f`. Both contain `licenses/zairenkai-app/{LICENSE,NOTICE}` and `licenses/zart/{LICENSE,NOTICE}` |
| `verify_integration.py` lock, access, build, apk | ok |
| Release gate (`--release`) | **fails on 3**: APK unsigned; catalog unsigned; no trusted module-signing key. The licence checks pass |
| shellcheck 0.10.0, actionlint 1.7.12 | clean |

Not done, and why:
- **CI GitHub App:** the maintainer must create it (`docs/build/ci.md`).
- **Module-signing key and APK keystore:** maintainer-held keys (`docs/build/release.md`). No
  agent may generate or handle the private halves, and no development key substitutes for them.
- **Device validation:** no device. The Core-only procedure is in
  `docs/testing/device-validation.md`.
- **Merging the component PRs and re-pinning:** needs the maintainer.


## Release gate, FAS provenance, first-hardware preparation (2026-10-21)

**Verdict: partially verified.** Host checks passed. Nothing ran on a device.

CI evidence:

| Run | Commit | Jobs | What it shows |
|---|---|---|---|
| 39608170919 | **7fc9cb2** (not 4bd0dd2) | reduced build, scripts: success | the zthermal commit, built without native components or module zips |
| 39617791701, 39618460955 | 4bd0dd2 | reduced build, scripts: success | the installer's JVM tests ran in `:integration-core:test`; no module zip was built or packaged |
| 39622352061 | f34246c | scripts ✓, reduced ✓, **full-integration ✗** at "Require the CI GitHub App" | the designed failure: no CI GitHub App exists. No run has built native components or module zips |

The push of f34246c triggered no `pull_request` run: PR #2 still lists 4bd0dd2 as its head.
Run 39622352061 was dispatched by hand.

Local results, default (distributable) profile:

| Check | Result |
|---|---|
| `build-components.sh` | exit 0; Core module only (`zairenkai_core` v0.1.0, `f7651bf7…5f41`, 741,218 bytes, unchanged); catalog unsigned |
| `ZAIRENKAI_INTERNAL_BUILD=1 build-components.sh` | exit 0; Core and FAS; `verify_integration.py build` ok; report labelled "internal development build (not for distribution)"; `apk --release` refuses it (internal build; module other than `zairenkai_core`) |
| `capture-contracts.sh` | exit 0; the catalog fixture is byte-identical to the build |
| `:integration-core:test` | 74 passed, 0 failed (adds `theSpecKeepsFasInternalWithItsConflict`, `anInternalModuleIsFlaggedInThePreflight`) |
| `:app:testDebugUnitTest` | 24 passed, 0 failed |
| Verifier tests | 6 passed |
| `lint` | 0 errors, 24 warnings ("newer version available") |
| APKs | debug 37,514,813 bytes `4d3f84d6…04e4`; release unsigned 30,300,624 bytes `ab1c0994…d074`; only `assets/system-modules/zairenkai_core.zip` (SHA-256 equal to the catalog) |
| `verify_integration.py` lock, build, apk, report | ok |
| Release gate (`--release`) | fails on 5: APK unsigned; catalog unsigned; no trusted module key; no licence for App, ART |
| FAS provenance | no copied or adapted code found (`docs/integration/fas-provenance.md`) |


## Signed module release, notices, full-integration CI (2026-10-21)

**Verdict: partially verified.**
- Every host check passed.
- The engineering integration report passes.
- The release gate fails on six real blockers (`docs/build/release.md`).
- Nothing ran on a device or emulator.
- The full-integration CI job has not run on GitHub: the CI GitHub App does not exist yet
  (`docs/build/ci.md`).

| Check | Command | Result |
|---|---|---|
| Components at locked commits | `build-components.sh` | exit 0. zart, zthermal (3 ABIs each), both module zips (reproducible: the catalog is byte-identical to the previous build before the lifecycle fields were added), AFID asset, crate notices for zart (11), zthermal (25), zairenkai_core (29), zairenkai_fas (21). Catalog left **unsigned**: no module-signing key |
| Contracts re-captured | `capture-contracts.sh` | exit 0 |
| integration-core tests | `./gradlew --rerun-tasks :integration-core:test` | 72 passed, 0 failed, 0 skipped (adds SystemModuleTrust 5, SystemModuleRegistry 1) |
| App tests (Robolectric) | `./gradlew --rerun-tasks :app:testDebugUnitTest` | 24 passed, 0 failed |
| Verifier tests | `python3 -m unittest discover -s integration/scripts/tests` | 6 passed (incorrect lock ×7 cases, unfetchable repository, missing and tampered native component, APK without components or with key material) |
| Lint | `./gradlew lint` | 0 errors, 24 warnings ("newer version available") |
| Android notices | `generateAndroidNotices` | 112 libraries, all Apache-2.0 |
| APKs | `assembleDebug assembleRelease` | debug 38,161,927 bytes `eaef2296…1505`; release unsigned 30,929,872 bytes `ef263c14…228e` |
| Source lock | `verify_integration.py lock` | ok |
| Repository access | `verify_integration.py access` | ok (with this session's credentials) |
| Build artifacts | `verify_integration.py build` | ok |
| APK contents | `verify_integration.py apk app-release-unsigned.apk` | ok: every component byte-identical, 3 ABIs, notices, trust list, no key material |
| Engineering report | `verify_integration.py report …` | pass (`integration/out/integration-report.md`) |
| Release gate | `verify_integration.py report … --release` | **fails, 6**: APK unsigned; catalog unsigned; no trusted module-signing key; no licence for App, zart (ART), FAS |
| Scripts and workflows | shellcheck 0.10.0, actionlint 1.7.12, `py_compile` | clean |
| Strings | key parity | 301 translatable strings in both languages |

Not run: the `full-integration` job on GitHub, emulator tests, any physical device, any root
manager.


## In-app system module installation (2026-10-21)

Branch `ccr-63f8aa87-1b0paj`. Design and evidence: `docs/integration/system-module.md`.
**Verdict: partially verified.** The logic is tested against a scripted su and Robolectric. No
module was installed through a real root manager.

| Check | Command | Result |
|---|---|---|
| Module zips at locked commits | `build-components.sh` (now includes Core `34e93e3` and FAS `3c83f0e` modules) | exit 0. `zairenkai_core` v0.1.0: 741,218 bytes, `f7651bf7…5f41`, 11 entries, 6 matched to source. `zairenkai_fas` v0.1.0: 622,959 bytes, `bfec727a…ffe1`, 10 entries, 7 matched |
| integration-core tests | `./gradlew --rerun-tasks :integration-core:test` | 66 passed, 0 failed, 0 skipped (adds SystemModule 21, SystemModuleAdapter 1, SystemModuleCatalogContract 1) |
| App tests (Robolectric) | `./gradlew --rerun-tasks :app:testDebugUnitTest` | 24 passed, 0 failed (adds ManagerHandoff 3, `AppUiTest.systemModuleScreenNeedsRootAndOffersNoInstallWithoutIt`) |
| Lint | `./gradlew lint` | 0 errors, 24 warnings ("newer version available" only) |
| APKs | `assembleDebug assembleRelease` | debug 37,980,802 bytes `c5711a26…3206`; release unsigned 30,831,987 bytes `69b5d5d7…b980` |
| Packaged modules equal the build | SHA-256 of `assets/system-modules/*.zip` in the release APK | equal to the catalog for both modules |
| Permissions | `aapt2 dump permissions` | unchanged: no INTERNET, no storage permission |
| Secret scan | grep for private keys and tokens in both APKs | no matches |
| Scripts, workflow, foundation | shellcheck, actionlint, `py_compile`, validator | clean |
| Strings | key parity `values/` and `values-in/` | 276 translatable strings in both |

Failure paths covered by `SystemModuleTest`:
- root absent, not enabled, or refused;
- unsupported and contradictory managers; Magisk too old, or old enough for handoff only; ksud
  without `install`;
- incompatible ABI, kernel architecture, API level, storage, manager or manager version;
- missing path, missing dependency, conflicting module;
- downgrade, staged version, pending removal, disabled module;
- corrupted, truncated, wrong-checksum, non-zip, wrong module id, extra entry, path-traversal
  and wrong-version packages; unmatched installer;
- unreadable artifact (the equivalent of a network failure: no network is used);
- installer exit 1, timeout, and exit 0 with nothing staged;
- cancellation before installation; the experimental acknowledgement;
- handoff without a CLI installer; pending reboot; ACTIVE vs DEGRADED by runtime;
- uninstall success, failure, and success without the remove marker; Magisk removal handoff;
- no command that changes the device during a failed preflight.

Not verified: any install, state read or removal on a real Magisk, KernelSU, KernelSU-Next or
APatch device; manager access to the app's cache file; MediaStore saving; post-reboot ACTIVE.
Devices with a validated install: **none**.

## zthermal integration (2026-10-21)

- **Change:** branch `ccr-63f8aa87-1b0paj` restarted from dev `b288419` on 2026-10-21.
  zthermal (Zairenkai-ThermalTools) is integrated, and every locked revision is refreshed.
- **Date:** 2026-10-21. The first report (2026-10-19) is summarised at the end.
- **Verdict:** **Partially verified.**
  - Every check runnable without hardware passed.
  - Nothing has run on an Android device or emulator: there is no KVM in the build container.

## Checks

| Check | Command | Configured | Executed | Result |
|---|---|---|---|---|
| Components at locked commits | `integration/scripts/build-components.sh` | yes | yes | exit 0. zart 0.1.0 @ `19f658d`, zthermal 0.2.0 @ `9d4b310` (3 ABIs each, `SHA256SUMS` verified); AFID asset @ `d9c4290` (354 candidates) |
| Contract capture from real binaries | `integration/scripts/capture-contracts.sh` | yes | yes | exit 0. zart; zthermal on four synthetic device fixtures and this host; zcored @ `34e93e3`; zfasd @ `3c83f0e` (observe-only) |
| Kotlin and Java unit tests (integration-core) | `./gradlew --rerun-tasks :integration-core:test` | yes | yes | 43 passed, 0 failed, 0 skipped (CoreClient 5, Data 4, FasClient 4, JavaInterop 3, ProcessCommandRunner 6, Registry 4, RootShell 6, ThermalTools 5, Zart 6) |
| Compose UI, design-system and Java platform tests (Robolectric) | `./gradlew --rerun-tasks :app:testDebugUnitTest` | yes | yes | 20 passed, 0 failed (AppUi 6, DesignSystem 8, Language 3, PlatformJava 3) |
| Android lint | `./gradlew lint` | yes (`abortOnError`) | yes | 0 errors. 24 warnings, all "newer version available" (21 GradleDependency, 3 AndroidGradlePluginVersion) |
| Debug APK | `./gradlew assembleDebug` | yes | yes | built |
| Release APK | `./gradlew assembleRelease` | yes | yes | built, **unsigned** (no release key exists) |
| Missing-component guard | `:app:verifyIntegrationComponents` with one `libzthermal.so` removed | yes | yes | fails and names `jniLibs/x86_64/libzthermal.so` |
| Agent foundation | `validate_agent_setup.py .` | yes | yes | PASS, 0 errors, 0 warnings |
| Shell scripts | `shellcheck integration/scripts/*.sh` (0.10.0) | yes | yes | clean |
| CI workflow | `actionlint` (1.7.12) | yes | yes | clean locally; GitHub run below |
| 16 KB alignment | `llvm-readelf -lW` on the packaged `.so` files | yes | yes | arm64-v8a and x86_64: every LOAD segment aligned to 0x4000 (zart, zthermal, AndroidX) |
| Secret scan of the APKs | `unzip -p` piped to `grep` for private keys, GitHub and AWS tokens; file names for keys, keystores, scripts | yes | yes | no matches |
| String parity | key comparison of `values/` and `values-in/` | yes | yes | 194 translatable strings in both, 3 plurals; the 3 extra in `values/` are `translatable="false"` |

## Artifacts

| | Path | Size (bytes) | SHA-256 | Signing |
|---|---|---|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 36,273,662 | `b71fcd29bb7a288a77b5d5db00961f848ba866dfc9ec1ac43c59986921f9201b` | APK Signature Scheme v2, Android Debug certificate (SHA-256 `d651160e…bfef`); `apksigner verify` passes |
| Release | `app/build/outputs/apk/release/app-release-unsigned.apk` | 29,210,092 | `4046fe5acd7c98830a805cd554aa8e3aeefb6e9ee51d64bf703cb4f716ce40bc` | unsigned (`apksigner verify` fails: no signature) |

These are build outputs, not committed. The hashes identify this build; a rebuild produces a new
debug signature.

APK contents (`aapt2 dump badging`, `unzip -l`):
- **Identity:** application id `com.zairenkaiprjctx.app`, version 0.1.0 (1), minSdk 29,
  targetSdk 35.
- **Permissions:** only AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No
  INTERNET permission.
- **Native code:** arm64-v8a, armeabi-v7a, x86_64. `lib/<abi>/libzart.so` and
  `lib/<abi>/libzthermal.so` match `integration/out/components.json` byte for byte. zthermal is
  a stripped PIE executable for `/system/bin/linker64` (arm64).
- **Font:** `res/font/google_sans_flex.ttf` (SHA-256 `a9ac7707…77ba`, same as the Google Fonts
  file).
- **Notices:** OFL and trademark notices in `assets/licenses/google-sans-flex/` (`OFL.txt`, `TRADEMARKS.md`);
  zthermal's Apache-2.0 `LICENSE` and `NOTICE` in `assets/licenses/zthermal/`.
- **Assets:** AFID candidates, inventory, lock, components record.
- **Manifest:** `allowBackup=false`, `extractNativeLibs=true`, `localeConfig` (en, in).

## Acceptance criteria and evidence

| Criterion | Evidence |
|---|---|
| zthermal runs only `snapshot`; no thermal write path | `ThermalToolsClient` has no other sub-command; `RegistryTest.thermalSnapshotStatesFollowWhatZthermalRead` asserts the exact argv; `ThermalToolsTest.qualcommFixtureWithFramework` asserts zthermal reports no writable control |
| Thermal availability is honest | `RegistryTest.thermalSnapshotStatesFollowWhatZthermalRead` (AVAILABLE with readable zones, UNSUPPORTED_DEVICE on a host without zones), `AppUiTest.thermalScreenSaysWhyZthermalCannotRunAndOffersNoControl` |
| Every repository has a disposition | `integration/organization-repositories.json` (16), `DataTest.inventoryCoversTheOrganizationAndMatchesTheLock` |
| Integrations use real interfaces, with no mocks posing as integration | Fixtures captured from the real binaries; `CoreClientTest`, `FasClientTest`, `ZartTest` |
| UI availability comes from the registry | `AppUiTest.homeShowsRealRegistryStates` (real container: Core shows ROOT_REQUIRED, ART shows NATIVE_COMPONENT_UNAVAILABLE in Robolectric) |
| No su call without consent | `RootShellTest.nothingRunsBeforeTheUserAsks`, `RegistryTest.withoutRootPrivilegedCapabilitiesSayWhy` |
| Shell injection impossible through inputs | `RootShellTest.wordsRejectShellSyntax`, `ZartTest.clientPaths` (no process for invalid input), `ProcessCommandRunnerTest.argumentsAreNotInterpretedByAShell` |
| Material 3 Expressive theme | `ZairenkaiTheme` → `MaterialExpressiveTheme`; `DesignSystemTest.themeAppliesTypographyAndBrandColours`, `darkModeUsesTheDarkScheme` |
| Google Sans Flex is the default face | `DesignSystemTest.everyTextStyleUsesGoogleSansFlex`, `bundledFontResourceLoads` |
| Status not by colour alone | `DesignSystemTest.statusIsTextAndStateDescriptionNotColourAlone` |
| Indonesian and English | `DesignSystemTest.indonesianStrings`; key parity check (194 translatable strings, 3 plurals each) |
| English by default, even on an Indonesian system | `LanguageTest.englishIsTheDefaultOnAnIndonesianSystem`, `choosingIndonesianSwitchesTheAppAndBack` (API 35, `LocaleManager`), `beforeAndroid13TheChoiceIsStoredAndTheActivityRecreated` (API 32) |
| Reduced motion | `DesignSystemTest.reducedMotionFollowsTheSystemAnimatorScale` |
| Adaptive layout, large font | `AppUiTest.wideScreensUseTheNavigationRail` (840 dp), `smallScreenWithLargeFontStillRenders` (320 dp, font scale 2) |
| Settings persist | `AppUiTest.themeChoiceIsAppliedAndPersisted` (real touch) |
| Kotlin ↔ Java interop | `JavaInteropTest`, `PlatformJavaTest`, `ProcessCommandRunnerTest`; all Java classes present in the APK dex |

## Defects found and fixed during verification

1. **4 KB-aligned binaries.** zart, zcored and zfasd were linked with 4 KB-aligned segments and
   would not load on 16 KB page-size devices. Fixed in ART, Core and FAS.
2. **Thermal listener crash.** `PowerManager.addThermalStatusListener` throws "Listener failed
   to set" when the thermal service refuses the listener, and the app would have crashed.
   `ThermalStatusMonitor.start` now returns null, and the UI polls instead.
3. **Silent output truncation.** Command output larger than the cap was silently truncated; it
   is now an error (zart and `ProcessCommandRunner`).
4. **Unreliable segmented button.** A real touch did not select a segment (Material 3 alpha18
   under Robolectric). Replaced with toggle buttons, which pass the real-touch test.

## Not verified, and why

- **No Android device or emulator was available.** Unverified as a result:
  - installing and launching the app;
  - root prompts in Magisk or KernelSU;
  - executing `libzart.so` and `libzthermal.so` from `nativeLibraryDir` as the app and as root;
  - which thermal zones SELinux lets the app uid read;
  - SELinux denials;
  - real thermal-listener behaviour;
  - rendering on real hardware.
- **Compose UI tests ran under Robolectric**, not as instrumented tests. The Java Android
  components were tested the same way.
- **Material 3 1.5.0-alpha18 is a pre-release.**
- **Zairenkai-Tuning has no default branch**: ztune is pinned only through Modules'
  `components.lock.json` (`0c226b2`, topic branch). Every commit in this app's lock is in its
  repository's `dev` (2026-10-23).

## First report (2026-10-19)

Components zart @ `fc72192`, AFID @ `6e3db46` (353 candidates); contracts zcored @ `ad0c195`,
zfasd @ `0877452`; 37 + 19 tests passed; lint 0 errors. The four defects listed above were
found and fixed then.
