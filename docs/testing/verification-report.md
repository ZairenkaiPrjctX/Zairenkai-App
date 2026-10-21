# Verification report

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
