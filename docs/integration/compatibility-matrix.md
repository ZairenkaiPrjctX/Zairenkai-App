# Compatibility matrix

| Item | Supported | Evidence | Verified on device |
|---|---|---|---|
| Android version | minSdk 29 (Android 10) to targetSdk 35 | `app/build.gradle.kts`; `MATCH_APEX` and the thermal listener need API 29 | No |
| Thermal headroom | API 30+ (`UNSUPPORTED_DEVICE` below) | `PlatformSubsystem` | No |
| SoC fields, dynamic colour | API 31+ | `AndroidPlatformInfo`, `ZairenkaiTheme` | No |
| App language (English default, Indonesian on request) | API 29–32: in-app choice stored in preferences; API 33+: per-app language via `LocaleManager` (generated `localeConfig`: en, in) | `AppLanguage`, `LanguageTest` (SDK 32 and 35) | No |
| ABIs packaged | arm64-v8a, armeabi-v7a, x86_64 | `aapt2 dump badging`: native-code | No |
| 16 KB page size | zart and zthermal arm64-v8a and x86_64 LOAD segments aligned to 0x4000; AndroidX's own library 0x4000 | `llvm-readelf -lW` | No |
| zart inspect | Any of the ABIs above, as the app uid | `ZartTest` (host and fixture output) | No |
| zthermal snapshot | Any of the ABIs above, as the app uid. Thermal zone reads depend on SELinux policy for `untrusted_app`; a denied read is reported as `permission_denied`, never replaced. | `ThermalToolsTest`, ThermalTools `docs/COMPATIBILITY_MATRIX.md` | No |
| Dexopt status | Root, and ART Service's `art` shell command (zart's tested format: android-17) | Zart `docs/COMPATIBILITY_MATRIX.md` | No |
| Core / FAS | Root plus the installed modules at the locked revisions | contract tests | No |
| Root managers | Any `su` that supports `-c` and returns `uid=0` from `id` (Magisk and KernelSU provide one). KernelSU `su` may not exist as a file; absence is detected when spawning it. | `RootShell` tests | No |
| SoC vendors | No vendor-specific code; nothing is claimed per vendor | – | – |
| Screen sizes | Compact phones (bottom bar) and ≥ 600 dp (navigation rail); font scale 2.0 renders | `AppUiTest` (Robolectric, 320 dp and 840 dp) | No |

No real device or emulator was available (no KVM). Every row is therefore verified only on the
JVM (Robolectric) or on the host.
