# App versions, release channels and in-app updates

## Versions

`app/build.gradle.kts` declares `appVersion`, the only place a version is written:

| Kind | Format | GitHub release | Example | versionCode |
|---|---|---|---|---|
| Release | `MAJOR.MINOR.PATCH` | normal release | `0.2.0` | 20099 |
| Beta | `MAJOR.MINOR.PATCH-beta.N` (N 1–98) | pre-release | `0.2.0-beta.1` | 20001 |

`versionCode = MAJOR·1 000 000 + MINOR·10 000 + PATCH·100 + (N for a beta, 99 for a release)`,
so every version, beta or not, has a larger code than the one before and Android accepts it as an
update. MINOR and PATCH stay below 100. The app parses the same format
(`integration-core/.../update/AppVersion.kt`); `UpdateTest` and `UpdateUiTest` check that the
build file, the parser and the built APK agree. The first build was 0.1.0 with code 1.

`CHANGELOG.md` has one `## [version] - date` entry per source version (Keep a Changelog). The
central integrated builder allocates the next stable release version and adds generated build
notes in its temporary checkout; it does not rewrite this repository's source history.

## Integrated beta channel

The central `Zairenkai-Release` workflow has a manual `channel` choice. `beta` resolves, verifies,
and builds the same immutable 15-repository source matrix as stable, then publishes two separate
GitHub pre-releases: App `vX.Y.Z-beta.N` and module `module-vX.Y.Z`. The shared manifest records
`channel: beta` and both exact artifact digests. Module versions advance past every published
stable or beta module version, so a later stable module can update a beta-installed module.

Settings › Updates on the Beta channel offers published App prereleases as signed APK downloads.
The matching beta APK packages the module from the same source matrix. The module status card also
shows a module prerelease on the Beta channel; stable users see only stable App and module releases.
Root-manager `updateJson` remains on the stable feed, so beta module delivery is through the
integrated beta APK path.

Settings › About shows the full version, "0.2.0-beta.1 (20001)", the source commit and its date
(`BuildConfig.GIT_COMMIT`, `GIT_DATE`, from git at build time; "unknown" from a source archive),
and the SHA-256 of the signing certificate, so a user can compare it with the release's.

## Integrated release pipeline

`Zairenkai-Release` resolves all 15 repositories configured in its `build/repositories.json` to
immutable `dev` SHAs, verifies each checkout in a matrix, and builds the App and root module from
the same source snapshot. A schedule checks for changed source revisions every six hours; a
successful full build publishes immutable stable App (`vX.Y.Z`) and module (`module-vX.Y.Z`)
releases in `Zairenkai-Release`. Each release carries SHA-256 checksums and the shared
`build-manifest.json`, including source commits and the builder commit. The workflow is configured
to stop if its release-environment signing secrets are unavailable or any validation fails.

The first live workflow run and signed output remain **unverified**. Repository visibility and
required Actions environment secrets also need checking before the products can reach users.

## How the app updates itself

Settings › Updates; code in `app/.../update/Updates.kt` and `integration-core/.../update/`.

| Step | What happens | Checked |
|---|---|---|
| Check | `GET https://api.github.com/repos/ZairenkaiPrjctX/Zairenkai-Release/releases` (no token, no device data; User-Agent with the app version) — daily through JobScheduler when online (on by default, can be turned off), or on request | HTTPS on GitHub's hosts only; drafts, tags that are not versions, a pre-release flag that disagrees with the version, and releases without both assets are ignored |
| Channel | Release: newest release. Beta: newest release or pre-release | Only versions newer than the installed one |
| Notify | One notification per version, if notifications are allowed (Android 13+ asks) | Tapping it opens Settings › Updates |
| Download | Only after the user taps "Download and install"; to the app's cache | Size bounded by the release record (and 300 MB); SHA-256 must equal the `.sha256` asset |
| APK check | PackageManager reads the file | Same package, exactly the version the release names, newer, signed by the same certificate as the installed app |
| Install | A PackageInstaller session; Android's installer asks the user. Without "Install unknown apps" for this app, the user is sent to that setting first | SHA-256 re-checked right before the session |

The SHA-256 file guards the download; the signing check is what makes an update authentic, and
Android enforces the same rule when it installs. A debug-signed or locally built APK therefore
cannot be updated from a release (the app says so), and a release can never be replaced by an
APK signed by anyone else.

**Unverified:** the central Release repository has not published the integrated products yet, so
no check, download or install has run against a real central release or on a device. The updater
uses unauthenticated GitHub requests; the central repository and its releases must be public for
ordinary installations to receive updates.

## Permissions this needs

`INTERNET` (also for the AI model download the user starts, `docs/integration/local-ai.md`), `ACCESS_NETWORK_STATE` (the daily job waits for a network; the model download asks first on mobile data), `RECEIVE_BOOT_COMPLETED`
(the daily job survives reboots), `POST_NOTIFICATIONS` (asked when needed) and
`REQUEST_INSTALL_PACKAGES` (granted by the user per app). `UpdateUiTest` fails if any other
`android.permission` appears.

## Release build hardening and a signed dry run

The release build type is not debuggable (`isDebuggable` and `isJniDebuggable` false). It signs
with schemes v2 and v3 only (no v1 JAR signing) and leaves out the dependency metadata block. It
uses `res/xml/network_security_config.xml`: cleartext is off and only the system CAs are trusted.
`verify_integration.py report <apk> --release` checks these properties on the built APK:

- the manifest flags;
- the permissions (only the five above, plus AndroidX's own receiver permission);
- a v2 or v3 signature.

Dry run, 2026-10-23, on the x86-64 Linux host, with a throwaway key (RSA 3072, valid 1 day)
generated in the session's scratch directory and never committed:

1. `zipalign -P 16 4` and `apksigner sign --v1-signing-enabled false`.
2. `apksigner verify --min-sdk-version 29`: verified with v3. apksigner adds no v2 block when v3
   covers the minimum SDK.
3. `zipalign -c -P 16 4`: ok.
4. `ANDROID_HOME=… verify_integration.py report <apk> --release`: one blocker,
   `module catalog not signed (no ZAIRENKAI_MODULE_SIGNING_KEY_FILE)`. Signing the catalog needs
   the maintainer's module-signing key, which only the `release` environment holds.

**Unverified:** the Release workflow itself has never run, and no APK signed with the
maintainer's key exists.
