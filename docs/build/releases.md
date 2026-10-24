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

`CHANGELOG.md` has one `## [version] - date` entry per version (Keep a Changelog). The app shows
its own entry in Settings › About; the release workflow uses it as the release notes.
`integration/scripts/app_release.py` reads both (tested in `integration/scripts/tests`).

Settings › About shows the full version, "0.2.0-beta.1 (20001)", the source commit and its date
(`BuildConfig.GIT_COMMIT`, `GIT_DATE`, from git at build time; "unknown" from a source archive),
and the SHA-256 of the signing certificate, so a user can compare it with the release's.

## Cutting a release (maintainer)

1. Set `appVersion` (a beta: `X.Y.Z-beta.N`; a release: `X.Y.Z`) and write its `CHANGELOG.md`
   entry, in a reviewed pull request merged into `dev`.
2. Run the **Release** workflow on `dev` with **draft_release** checked. It builds and signs as
   before (`docs/build/release.md`), runs the release gate, names the APK
   `zairenkai-<version>.apk` with a `zairenkai-<version>.apk.sha256`, and a second job (the only
   one with `contents: write`) creates a **draft** release `v<version>`: marked pre-release for a
   beta, notes from the changelog. It refuses a version that already has a release.
3. Review the draft on GitHub and **publish** it. Only published releases reach users. Agents
   never publish (`CLAUDE.md`).

## How the app updates itself

Settings › Updates; code in `app/.../update/Updates.kt` and `integration-core/.../update/`.

| Step | What happens | Checked |
|---|---|---|
| Check | `GET https://api.github.com/repos/ZairenkaiPrjctX/Zairenkai-App/releases` (no token, no device data; User-Agent with the app version) — daily through JobScheduler when online (on by default, can be turned off), or on request | HTTPS on GitHub's hosts only; drafts, tags that are not versions, a pre-release flag that disagrees with the version, and releases without both assets are ignored |
| Channel | Release: newest release. Beta: newest release or pre-release | Only versions newer than the installed one |
| Notify | One notification per version, if notifications are allowed (Android 13+ asks) | Tapping it opens Settings › Updates |
| Download | Only after the user taps "Download and install"; to the app's cache | Size bounded by the release record (and 300 MB); SHA-256 must equal the `.sha256` asset |
| APK check | PackageManager reads the file | Same package, exactly the version the release names, newer, signed by the same certificate as the installed app |
| Install | A PackageInstaller session; Android's installer asks the user. Without "Install unknown apps" for this app, the user is sent to that setting first | SHA-256 re-checked right before the session |

The SHA-256 file guards the download; the signing check is what makes an update authentic, and
Android enforces the same rule when it installs. A debug-signed or locally built APK therefore
cannot be updated from a release (the app says so), and a release can never be replaced by an
APK signed by anyone else.

**Unverified:** no release exists yet (the releases API returns `[]`, captured in
`integration-core/src/test/resources/contracts/github/`), so no check, download or install has
run against a real release or on a device.

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
