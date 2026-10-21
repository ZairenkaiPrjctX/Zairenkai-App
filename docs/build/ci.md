# Continuous integration

`.github/workflows/ci.yml` has three jobs. Only one of them builds the integrated app.

| Job | What it builds | Needs | Status label |
|---|---|---|---|
| `reduced` | Unit and UI tests, lint, a debug APK **without** native components (`-Pzairenkai.allowMissingComponents=true`) | nothing | "reduced build (no native components)" |
| `scripts` | shellcheck, Python compile, the source-lock check (`verify_integration.py lock`), the verifier's own tests | nothing | |
| `full-integration` | Every component at its locked commit (`build-components.sh`), contracts re-captured from the real binaries, tests, lint, debug and release APKs **with** native components required, APK inspection and the integration report | the CI GitHub App | "full integration build" |

A green `reduced` job says nothing about the integrated app. Only `full-integration` does.

## Why the full build needs credentials

Zairenkai-Core, Zairenkai-FAS and Zairenkai-ART are private (`list_repos`, 2026-10-21).
ThermalTools and Database-Source are public. `GITHUB_TOKEN` can read only this repository, so
the job uses a GitHub App installation token that is:
- **read only:** `permission-contents: read`, nothing else;
- **limited** to the five component repositories;
- **short-lived:** about one hour, and revoked when the job ends
  (`actions/create-github-app-token` v3.3.0, pinned by SHA);
- **header only:** git receives it as an HTTP header (`http.extraheader`, masked). It never
  appears in a URL, the workspace, `.git/config` or an artifact. The step after the build
  removes it.

Without the App, the job **fails** with "Full integration build blocked: the CI GitHub App is
not configured". It never falls back to the reduced build.

## Setting it up (maintainer, once)

1. Create a GitHub App owned by ZairenkaiPrjctX with **Repository permissions → Contents:
   Read-only** and nothing else. It needs no webhook.
2. Install it on Zairenkai-Core, Zairenkai-FAS, Zairenkai-ART, Zairenkai-ThermalTools and
   Zairenkai-Database-Source only.
3. In Zairenkai-App → Settings → Secrets and variables → Actions:
   - variable `ZAIRENKAI_CI_APP_CLIENT_ID` = the App's client ID;
   - secret `ZAIRENKAI_CI_APP_PRIVATE_KEY` = a private key generated for the App.
4. Make `full integration build (locked sources, native components)` a required check on
   `dev`.

Pull requests from forks get no secrets, so the job does not run for them.

## What the full job checks

`integration/scripts/verify_integration.py` (tests: `integration/scripts/tests/`):
- **`lock`:**
  - schema; every component has the required fields;
  - full 40-character SHAs, `url` equal to `https://github.com/<repository>.git`, boolean
    `merged`, supported ABIs;
  - no duplicates, no missing mandatory component, agreement with the inventory.
- **`access`:** every locked repository answers `git ls-remote` with the job's credentials.
  The failure message says the App is needed.
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
