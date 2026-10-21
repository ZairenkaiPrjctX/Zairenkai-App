# Verification report

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
- **Core and ThermalTools revisions are unmerged topic-branch commits** (`"merged": false` in
  the lock).

## First report (2026-10-19)

Components zart @ `fc72192`, AFID @ `6e3db46` (353 candidates); contracts zcored @ `ad0c195`,
zfasd @ `0877452`; 37 + 19 tests passed; lint 0 errors. The four defects listed above were
found and fixed then.
