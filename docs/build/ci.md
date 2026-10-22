# Continuous integration

`.github/workflows/ci.yml` has three jobs. Only one of them builds the integrated app.

| Job | What it builds | Needs | Status label |
|---|---|---|---|
| `reduced` | Unit and UI tests, lint, a debug APK **without** native components (`-Pzairenkai.allowMissingComponents=true`) | nothing | "reduced build (no native components)" |
| `scripts` | shellcheck, Python compile, the source-lock check (`verify_integration.py lock`), the verifier's own tests | nothing | |
| `full-integration` | Every component at its locked commit (`build-components.sh`), contracts re-captured from the real binaries, tests, lint, debug and release APKs **with** native components required, APK inspection and the integration report | nothing (the locked repositories are public) | "full integration build" |

A green `reduced` job says nothing about the integrated app. Only `full-integration` does.

`full-integration` builds the single root module `zairenkai` from Zairenkai-Modules at its
locked commit: Modules' `components.lock.json` names the Core, FAS and Tuning commits, each is built
with its own `scripts/build-android.sh`, and Modules' `scripts/assemble.py` packages them
(`docs/integration/system-module.md`).

**Status, 2026-10-22:** every locked repository is public (`list_repos`, 2026-10-22), so the job
no longer uses the CI GitHub App: it fetches them without credentials (next section). No run has
exercised this version of the job yet: Unverified.

**Earlier status, 2026-10-21:**
- run 39622352061 (f34246c) and run 39623939493 (b9ce39b): `scripts` and `reduced` passed;
  `full-integration` failed at "Require the CI GitHub App", because the App does not exist yet;
- no CI run has built the native components or the module zips yet. Only local builds have
  (`docs/testing/verification-report.md`).

## Fetching the locked sources

Every repository in `integration/repository-lock.json` (Core, FAS, ART, ThermalTools,
Database-Source, Modules) and every component Modules locks is public (`list_repos`,
2026-10-22; anonymous `git ls-remote` from a session, 2026-10-22). The job therefore holds no
token for them: `actions/checkout` keeps no credentials (`persist-credentials: false`), and
`build-components.sh` fetches each locked commit over plain HTTPS. The workflow has only
`contents: read` on this repository.

If a locked repository becomes private again, `verify_integration.py access` **fails** the job
("Locked repositories must be publicly readable"); it never falls back to the reduced build.
Making a component private would need a new, reviewed fetch credential; the former CI GitHub App
steps were removed on 2026-10-22.

The former values `ZAIRENKAI_CI_APP_ID` and `ZAIRENKAI_CI_APP_PRIVATE_KEY` are no longer read
by any workflow. `setup-actions-secrets.sh status` names them as unused; the maintainer may
delete them and uninstall the App (`docs/build/secrets-and-variables.md`).

Pull requests from forks can run the job too, as it needs no secret.

Make `full integration build (locked sources, native components)` a required check on `dev`
(maintainer, repository settings).

## What the full job checks

`integration/scripts/verify_integration.py` (tests: `integration/scripts/tests/`):
- **`lock`:**
  - schema; every component has the required fields;
  - full 40-character SHAs, `url` equal to `https://github.com/<repository>.git`, boolean
    `merged`, supported ABIs;
  - no duplicates, no missing mandatory component, agreement with the inventory.
- **`access`:** every locked repository answers `git ls-remote` without credentials. The
  failure message says locked repositories must be publicly readable.
- **`build`:**
  - each checkout's `HEAD` equals its locked commit;
  - every file in `components.json` matches its SHA-256;
  - zart and zthermal exist for arm64-v8a, armeabi-v7a and x86_64;
  - the device asset was generated from the locked commit and is not empty;
  - the module zips were built from their locked commits and match the catalog, and their
    installers are matched to source;
  - every Rust binary's crate notices and zart's AOSP attribution are present.
- **`apk`:**
  - every built component is inside the APK byte for byte, with ABI coverage;
  - the Android library notices, the module trust list, the lock and the catalog are present;
  - there are no key or keystore files and no private-key or token markers.
- **`report`:** writes `integration/out/integration-report.{json,md}`. For each component it
  records:
  - pinned commit;
  - merged or unmerged;
  - checkout verified;
  - packaged or not;
  - artifact digests, licence status, the APK and catalog signature state, and the CI run id.

The full job's artifact is named `engineering-build-unsigned-not-for-distribution`. It is not
a release (`docs/build/release.md`).
