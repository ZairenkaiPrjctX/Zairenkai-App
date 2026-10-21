# Building Zairenkai App

## Toolchain

| Tool | Version | Source |
|---|---|---|
| JDK | 17+ (21 used here) | – |
| Gradle | 8.14.3 (wrapper, distribution SHA-256 pinned) | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.9.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.1.20 (Compose compiler plugin) | same |
| Android SDK | platform 35, build-tools 35.0.0 | `local.properties` `sdk.dir` |
| For components: Rust stable with `aarch64/armv7/x86_64-linux-android` targets, NDK r27d, python3 ≥ 3.11 | | `integration/scripts/build-components.sh` |

## Steps

```sh
# 1. Packaged components at the locked commits (needs read access to the private repositories)
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
