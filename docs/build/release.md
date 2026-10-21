# Release process and signing

**Status (2026-10-21): no release can be produced, and none is ready.** The licences are
settled: App, ART, Core, FAS and ThermalTools are Apache-2.0 (`docs/integration/license-inventory.md`).
The release gate (`verify_integration.py report <apk> --release`) now lists three blockers. All
three need keys that only the maintainer can create:

| Blocker | What resolves it |
|---|---|
| The APK is unsigned | A release keystore in the protected `release` environment (below) |
| The module catalog is unsigned | A module-signing key in the `release` environment |
| `integration/module-trust.json` lists no key | Add the module-signing public key in a reviewed pull request |

The gate also fails on any internal build, and on any APK carrying a module other than
`zairenkai_core` (FAS stays internal: `docs/integration/fas-provenance.md`).

The gate is not the whole readiness question. Release readiness also needs:
- the full-integration CI job green on the release commit (`docs/build/ci.md`; it has never run,
  because the CI GitHub App does not exist yet);
- the Core-only hardware validation record (`docs/testing/device-validation.md`; no device has
  run it);
- the Core, ThermalTools, ART and FAS topic branches merged and the lock re-pinned to the merge
  commits: **done** 2026-10-21 (`11e9742`, `3bc71fe`, `0d8cd72`, `be08b2d`).

Until all of this holds, builds are **engineering artifacts, not for distribution**:
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

Not done by an agent: the private key must never pass through a chat session, a log, the
repository or a CI artifact. No development key exists to fall back on, and no build substitutes
one. Without the trusted key, every build refuses to install modules.

`integration/scripts/setup-actions-secrets.sh module-signing` does all of this (key in
`~/.zairenkai-keys`, public half into the trust list, private key into the `release`
environment; `docs/build/secrets-and-variables.md`). By hand, on a trusted machine, in a
directory outside any Git checkout:

```sh
openssl ecparam -name prime256v1 -genkey -noout -out module-signing.pem   # keep offline / in a secret store
openssl pkey -in module-signing.pem -pubout -out module-signing.pub.pem
integration/scripts/module-trust-key.sh module-signing.pub.pem "<owner>" <YYYY-MM-DD>
```

Then:
1. Check the printed `key_id` against the PEM:
   `openssl pkey -pubin -in module-signing.pub.pem -outform DER | sha256sum`.
   Add the printed entry (public key, `key_id`, owner, date) to `integration/module-trust.json`
   in a reviewed pull request. Only the public half goes into Git.
2. Store the private PEM as the secret `ZAIRENKAI_MODULE_SIGNING_KEY` in the `release`
   environment, with required reviewers. Keep an offline backup, then delete every other copy.
3. `sign-module-catalog.sh` refuses any key whose public half is not in the trust list.
4. This key is separate from the APK keystore and from the CI GitHub App key. Never reuse one for
   another.

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

## Installing the secrets

`integration/scripts/setup-actions-secrets.sh` creates the `release` environment and stores
these secrets (`docs/build/secrets-and-variables.md`).

## Running a release

`.github/workflows/release.yml`, run manually from `dev` in the `release` environment:
1. Lock check. Then, before any build, the job fails if it runs on another branch, if any of the
   seven values is missing, if the module key is not in the committed trust list, or if the
   keystore does not open with the stored alias and password.
2. The keys are written to `$RUNNER_TEMP` (mode 600), then the CI GitHub App token
   (`docs/build/ci.md`).
3. `build-components.sh` signs the catalog.
4. Tests, lint and a signed `assembleRelease`.
5. The keys and the token are removed.
6. `apksigner verify --verbose --print-certs`; the scheme lines, certificate DN and SHA-256
   digest go into the job summary.
7. The release gate: `verify_integration.py report --release`. It runs `apksigner verify` again,
   requires a signed catalog and a trusted key, and requires a licence file for every
   distributed component.

Nothing is published. The signed APK and the report are uploaded as the `release` workflow
artifact only when every step passed. On a failure, the job summary says so and the integration
report, if one was written, is uploaded as `release-failure-report`.

The job fails while any blocker remains. No release has been run.
