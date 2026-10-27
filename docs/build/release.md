# Release process and signing

Versions, channels, draft releases and in-app updates: `docs/build/releases.md`.

The integrated release pipeline lives in `Zairenkai-Release`; see `docs/build/releases.md` for
its matrix and publication behavior. The APK release gate requires the single `zairenkai` module,
a signed catalog whose public key is in `integration/module-trust.json`, a signed APK, and the
license/provenance checks. The first live integrated build has not yet passed, and device
validation remains separate from a successful host build.

The App `release` environment does not grant its secrets to the central workflow. Configure the
same five named secrets in the Release repository's `release` environment. The workflow checks
that the module key matches the App's trusted public key and that the APK keystore opens.

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
2. Store the private PEM as `ZAIRENKAI_MODULE_SIGNING_KEY` in the `Zairenkai-Release` repository's
   `release` environment. Keep an offline backup, then delete every other copy.
3. `sign-module-catalog.sh` refuses any key whose public half is not in the trust list.
4. This key is separate from the APK keystore. Never reuse one for the other.

To rotate, add the new key, release, then remove the old key in a later release.

## APK signing key

Generate a keystore with `keytool -genkeypair -keyalg EC` (or RSA 3072+) outside the
repository. Store in `Zairenkai-Release`'s `release` environment:
- `ZAIRENKAI_RELEASE_KEYSTORE_BASE64` (the keystore, base64);
- `ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD`;
- `ZAIRENKAI_RELEASE_KEY_ALIAS`;
- `ZAIRENKAI_RELEASE_KEY_PASSWORD`.

`app/build.gradle.kts` creates the release signing configuration only when
`ZAIRENKAI_RELEASE_KEYSTORE_FILE` points to a file. Otherwise `assembleRelease` stays unsigned.

## Installing the secrets

`integration/scripts/setup-actions-secrets.sh` manages the App repository's environment. For the
central workflow, configure the existing key material separately in `Zairenkai-Release` →
Settings → Environments → `release`. Keep secret values in GitHub's secret UI; never print or
commit them.

## Automated central release

`.github/workflows/unified-release.yml` in `Zairenkai-Release` checks the 15 configured source
revisions every six hours, after builder changes, or through `workflow_dispatch`. If sources
changed, it runs Modules and Engine host suites, validates signing secrets, builds the module and
App, verifies the catalog and APK, records SHA-256/provenance, and publishes immutable module and
App releases. Only the publish job has `contents: write`. The first live run remains unverified;
central repository visibility and Release-environment secrets are rollout prerequisites.
