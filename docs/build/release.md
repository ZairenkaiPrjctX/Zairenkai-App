# Release process and signing

**Status (2026-10-21): no release can be produced.** The release gate
(`verify_integration.py report <apk> --release`) lists five blockers, and each needs a
maintainer decision or a key. FAS is not one of them, because it is excluded from distributable
builds altogether: the gate also fails on any internal build, or any APK carrying a module other
than `zairenkai_core` (`docs/integration/fas-provenance.md`).

| Blocker | What resolves it |
|---|---|
| The APK is unsigned | A release keystore in the protected `release` environment (below) |
| The module catalog is unsigned | A module-signing key in the `release` environment |
| `integration/module-trust.json` lists no key | Add the module-signing public key in a reviewed pull request |
| Zairenkai-App has no licence | The maintainer chooses one |
| Zairenkai-ART (zart, packaged) has no licence | The maintainer chooses one; keep the AOSP Apache-2.0 attribution (`docs/integration/license-inventory.md`) |

Until all five are resolved, builds are **engineering artifacts, not for distribution**:
- debug APK: signed with the Android debug key;
- release APK: unsigned.

The app refuses to install modules from them, because their catalog has no trusted signature.

## Two signing identities

| Identity | Signs | Trusted by | Why separate |
|---|---|---|---|
| APK signing key (`ZAIRENKAI_RELEASE_KEYSTORE_*`) | The APK | Android (updates must keep the same key) | Controls who can update the app |
| Module-signing key (`ZAIRENKAI_MODULE_SIGNING_KEY`, ECDSA P-256) | `assets/system-modules.json` (detached `system-modules.json.sig`) | The app, through `integration/module-trust.json` | The module zips run as root. Their release must be authorised by a key reviewed in Git, independently of whoever built the APK. A rebuilt or debug-signed APK cannot authorise modules. |

Neither private key may be committed, embedded in the APK, written outside `$RUNNER_TEMP`, or
printed. `verify_integration.py apk` fails on key or keystore files and on private-key or
token markers in the APK.

## Creating the module-signing key (maintainer, offline)

```sh
openssl ecparam -name prime256v1 -genkey -noout -out module-signing.pem   # keep offline / in a secret store
openssl pkey -in module-signing.pem -pubout -out module-signing.pub.pem
integration/scripts/module-trust-key.sh module-signing.pub.pem "<owner>" <YYYY-MM-DD>
```

Then:
1. Add the printed entry to `integration/module-trust.json` in a reviewed pull request.
2. Store the private PEM as the secret `ZAIRENKAI_MODULE_SIGNING_KEY` in the `release`
   environment.
3. `sign-module-catalog.sh` refuses any key whose public half is not in the trust list.

To rotate, add the new key, release, then remove the old key in a later release.

## APK signing key

Generate a keystore with `keytool -genkeypair -keyalg EC` (or RSA 3072+) outside the
repository. Store in the `release` environment:
- `ZAIRENKAI_RELEASE_KEYSTORE_BASE64` (the keystore, base64);
- `ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD`;
- `ZAIRENKAI_RELEASE_KEY_ALIAS`;
- `ZAIRENKAI_RELEASE_KEY_PASSWORD`.

`app/build.gradle.kts` creates the release signing configuration only when
`ZAIRENKAI_RELEASE_KEYSTORE_FILE` points to a file. Otherwise `assembleRelease` stays unsigned.

## Running a release

`.github/workflows/release.yml`, run manually in the protected `release` environment:
1. Lock check, then the CI GitHub App token (`docs/build/ci.md`).
2. Keys are written to `$RUNNER_TEMP` (mode 600).
3. `build-components.sh` signs the catalog.
4. Tests, lint and a signed `assembleRelease`.
5. The keys and the token are removed.
6. The release gate: `verify_integration.py report --release`. It runs `apksigner verify`,
   requires a signed catalog and a trusted key, and requires a licence file for every
   distributed component.

The job fails while any blocker remains. No release has been run.
