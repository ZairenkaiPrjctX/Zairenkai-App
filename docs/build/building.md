# Building Zairenkai App

## Toolchain

| Tool | Version | Source |
|---|---|---|
| JDK | 17+ (21 used here) | – |
| Gradle | 9.6.0 (wrapper, distribution SHA-256 pinned) | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.10 (Compose compiler plugin and JVM module) | same |
| Android SDK | platforms 36 and 37, build-tools 36.0.0 (installed by CI and release workflows) | `local.properties` `sdk.dir` |
| For components: Rust stable with `aarch64/armv7/x86_64-linux-android` targets, NDK r27d, python3 ≥ 3.11 | | `integration/scripts/build-components.sh` |

## Steps

```sh
# 1. Packaged components at the locked commits (the locked repositories are public)
ANDROID_NDK_HOME=/path/to/android-ndk-r27d integration/scripts/build-components.sh

# 2. Tests, lint, APKs
./gradlew :integration-core:test :app:testDebugUnitTest lint assembleDebug assembleRelease

# Optional: refresh the contract fixtures from the real binaries
integration/scripts/capture-contracts.sh

# 3. Check the integrated build and write its report (engineering build; add --release for the gate)
python3 integration/scripts/verify_integration.py build
python3 integration/scripts/verify_integration.py report app/build/outputs/apk/release/app-release-unsigned.apk
```

CI: `docs/build/ci.md`. Release and signing: `docs/build/release.md`.

The app targets API 36 (Android 16), the Google Play target required for new apps and updates from
August 31, 2026. It compiles against API 37 because AGP 9.4 supports through API 37 and Material 3
Expressive releases after alpha18 require it.

Outputs:
- `app/build/outputs/apk/debug/app-debug.apk`, signed with the Android debug key;
- `app/build/outputs/apk/release/app-release-unsigned.apk`. No release signing configuration
  or key exists in the repository.

### Signing a release

Only `.github/workflows/release.yml` signs, with keys from the protected `release` environment
(`docs/build/release.md`). Locally, `assembleRelease` is unsigned unless
`ZAIRENKAI_RELEASE_KEYSTORE_FILE` and its passwords are set in the environment.

## Missing components

`packageDebug` and `packageRelease` fail unless the components exist. This prevents an APK
whose ART, thermal-sensor and device-database features silently do nothing.

`-Pzairenkai.allowMissingComponents=true` builds anyway. CI does this, because it has no
access to the other private repositories. In such an APK:
- ART inspection and zthermal sensors report `NATIVE_COMPONENT_UNAVAILABLE`;
- the device database reports `INITIALIZATION_FAILED`.

## Environment notes (this build container)

- **Maven Central rate limiting.** Maven Central answered HTTP 429, so a user-level init script
  (`~/.gradle/init.d/`, not in the repository) put Google's Maven Central mirror first.
- **Robolectric.** It downloads its runtime jars itself; `ROBOLECTRIC_REPO_URL` points it at a
  mirror when Maven Central is limited.
- **SDK licence.** Accepted with `sdkmanager --licenses` for the build environment.
