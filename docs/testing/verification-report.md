# Verification report

- **Change:** branch `ccr-63f8aa87-1b0paj`, first application code in Zairenkai-App.
- **Date:** 2026-10-19.
- **Verdict:** **Partially verified.**
  - Every check runnable without hardware passed.
  - Nothing has run on an Android device or emulator: there is no KVM in the build container.

## Checks

| Check | Command | Configured | Executed | Result |
|---|---|---|---|---|
| Components at locked commits | `integration/scripts/build-components.sh` | yes | yes | exit 0. zart 0.1.0 @ `fc72192` (3 ABIs, `SHA256SUMS` verified); AFID asset @ `6e3db46` (353 candidates) |
| Contract capture from real binaries | `integration/scripts/capture-contracts.sh` | yes | yes | exit 0. zart, zcored @ `ad0c195`, zfasd @ `0877452` (observe-only) |
| Kotlin and Java unit tests (integration-core) | `./gradlew :integration-core:test --rerun-tasks` | yes | yes | 37 passed, 0 failed, 0 skipped (CoreClient 5, Data 4, FasClient 4, JavaInterop 3, ProcessCommandRunner 6, Registry 3, RootShell 6, Zart 6) |
| Compose UI, design-system and Java platform tests (Robolectric) | `./gradlew :app:testDebugUnitTest` | yes | yes | 19 passed, 0 failed (AppUi 5, DesignSystem 8, Language 3, PlatformJava 3) |
| Android lint | `./gradlew lint` | yes (`abortOnError`) | yes | 0 errors. 24 warnings, all "newer version available" (21 GradleDependency, 3 AndroidGradlePluginVersion) |
| Debug APK | `./gradlew assembleDebug` | yes | yes | built |
| Release APK | `./gradlew assembleRelease` | yes | yes | built, **unsigned** (no release key exists) |
| Build without components | `assembleDebug` with and without `-Pzairenkai.allowMissingComponents=true` | yes | yes | fails without the flag (message names the missing files); builds with it (no `libzart.so` in the APK) |
| Agent foundation | `validate_agent_setup.py .` | yes | yes | PASS, 0 errors, 0 warnings |
| Shell scripts | `shellcheck integration/scripts/*.sh` | yes | yes | clean |
| CI workflow | `actionlint .github/workflows/ci.yml` | yes | yes | clean. **Not run on GitHub**: no pull request exists |
| 16 KB alignment | `llvm-readelf -lW` on packaged `.so` files | yes | yes | arm64-v8a and x86_64: LOAD align 0x4000 |
| Secret scan of the APK | `grep` for private keys and tokens | yes | yes | no matches |

## Artifacts

| | Path | Size (bytes) | SHA-256 | Signing |
|---|---|---|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 34,354,838 | `1ca1af3a3e62c77fca360097e23d3d839749c5967a545061541b790e5087d95b` | APK Signature Scheme v2, Android Debug certificate (SHA-256 `d651160e…bfef`); `apksigner verify` passes |
| Release | `app/build/outputs/apk/release/app-release-unsigned.apk` | 27,401,212 | `0b916148d14c0403616b3b222780198b827695c59f51773177c71444db52add7` | unsigned (`apksigner verify` fails: no signature) |

These are build outputs, not committed. The hashes identify this build; a rebuild produces a new
debug signature.

APK contents (`aapt2 dump badging`, `unzip -l`):
- **Identity:** application id `com.zairenkaiprjctx.app`, version 0.1.0 (1), minSdk 29,
  targetSdk 35.
- **Permissions:** only AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No
  INTERNET permission.
- **Native code:** arm64-v8a, armeabi-v7a, x86_64. `lib/<abi>/libzart.so` matches
  `integration/out/components.json` byte for byte.
- **Font:** `res/font/google_sans_flex.ttf` (SHA-256 `a9ac7707…77ba`, same as the Google Fonts
  file).
- **Notices:** OFL and trademark notices in `assets/licenses/google-sans-flex/` (`OFL.txt`, `TRADEMARKS.md`).
- **Assets:** AFID candidates, inventory, lock, components record.
- **Manifest:** `allowBackup=false`, `extractNativeLibs=true`, `localeConfig` (en, in).

## Acceptance criteria and evidence

| Criterion | Evidence |
|---|---|
| Every repository has a disposition | `integration/organization-repositories.json` (15), `DataTest.inventoryCoversTheOrganizationAndMatchesTheLock` |
| Integrations use real interfaces, with no mocks posing as integration | Fixtures captured from the real binaries; `CoreClientTest`, `FasClientTest`, `ZartTest` |
| UI availability comes from the registry | `AppUiTest.homeShowsRealRegistryStates` (real container: Core shows ROOT_REQUIRED, ART shows NATIVE_COMPONENT_UNAVAILABLE in Robolectric) |
| No su call without consent | `RootShellTest.nothingRunsBeforeTheUserAsks`, `RegistryTest.withoutRootPrivilegedCapabilitiesSayWhy` |
| Shell injection impossible through inputs | `RootShellTest.wordsRejectShellSyntax`, `ZartTest.clientPaths` (no process for invalid input), `ProcessCommandRunnerTest.argumentsAreNotInterpretedByAShell` |
| Material 3 Expressive theme | `ZairenkaiTheme` → `MaterialExpressiveTheme`; `DesignSystemTest.themeAppliesTypographyAndBrandColours`, `darkModeUsesTheDarkScheme` |
| Google Sans Flex is the default face | `DesignSystemTest.everyTextStyleUsesGoogleSansFlex`, `bundledFontResourceLoads` |
| Status not by colour alone | `DesignSystemTest.statusIsTextAndStateDescriptionNotColourAlone` |
| Indonesian and English | `DesignSystemTest.indonesianStrings`; key parity script (182 strings, 3 plurals each) |
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
  - executing `libzart.so` from `nativeLibraryDir` as the app and as root;
  - SELinux denials;
  - real thermal-listener behaviour;
  - rendering on real hardware.
- **Compose UI tests ran under Robolectric**, not as instrumented tests. The Java Android
  components were tested the same way.
- **Material 3 1.5.0-alpha18 is a pre-release.**
- **CI has not run on GitHub.**
