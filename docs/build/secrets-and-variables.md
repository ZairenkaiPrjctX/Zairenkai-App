# GitHub Actions secrets and variables

Every secret, variable and environment the Zairenkai workflows read, where each one lives, and
how to install it. Values are never written here or anywhere in Git; only names.

**Status, 2026-10-22:** every value in the table below is set, and the `release` environment
deploys only from `dev` (`setup-actions-secrets.sh status`: all `ok`, exit 0). The maintainer
installed them. Set is not the same as working: `full-integration` got past the GitHub App
step for the first time (runs 39639587745, 39639605601), then failed in `capture-contracts.sh`,
and `release.yml` has never run. The module-signing public key enters `integration/module-trust.json`
with this change; `release.yml` accepts the key only once that is merged to `dev`.

## Zairenkai-App

| Name | Kind | Scope | Read by | Source of the value |
|---|---|---|---|---|
| `ZAIRENKAI_CI_APP_ID` | variable | repository | `ci.yml` full-integration, `release.yml` | The CI GitHub App's numeric **App ID** (not the Client ID `Iv…`) |
| `ZAIRENKAI_CI_APP_PRIVATE_KEY` | secret | repository | `ci.yml` full-integration, `release.yml` | A private key generated for that App (whole PEM) |
| `ZAIRENKAI_MODULE_SIGNING_KEY` | secret | environment `release` | `release.yml` | ECDSA P-256 private key whose public half is in `integration/module-trust.json` |
| `ZAIRENKAI_RELEASE_KEYSTORE_BASE64` | secret | environment `release` | `release.yml` | The APK release keystore, base64 |
| `ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD` | secret | environment `release` | `release.yml` | Keystore password |
| `ZAIRENKAI_RELEASE_KEY_ALIAS` | secret | environment `release` | `release.yml` | Key alias in the keystore |
| `ZAIRENKAI_RELEASE_KEY_PASSWORD` | secret | environment `release` | `release.yml` | Key password |

Repository-level values are also visible to the `release` environment job, so the CI App pair is
set once. The signing keys live only in `release`, so `ci.yml` (which has no `environment:`) can
never read them.

**Environment `release`:** `release.yml` declares `environment: release`. GitHub refuses
required reviewers on this private repository with the organization's current plan (HTTP 422
"the billing plan supports the required reviewers protection rule", 2026-10-21), so the
environment is protected by a deployment branch policy instead: only `dev` may deploy to it.
Upgrading the plan, or making the repository public, would allow required reviewers; add the
maintainer as one then.

Not needed: organization-level secrets or variables. Only Zairenkai-App uses these values, and
repository scope keeps them away from the other 15 repositories.

## Other repositories

| Repository | Values | Status |
|---|---|---|
| Zairenkai-Database-Source | variable `AFID_COMMIT_TRAILER` (trailer on bot commits) | Set, 2026-10-19 |
| Zairenkai-Database-Source | variables `AFID_MAX_DISCOVERY_PARALLEL` (2), `AFID_MAX_ANALYSIS_PARALLEL` (2), `AFID_JOB_TIMEOUT_MINUTES` (60), `AFID_MAX_ITEMS_PER_RUN` (20), `AFID_MAX_FETCH_FILES` (400), `AFID_MAX_FETCH_BYTES` (536870912), `AFID_RATE_LIMIT_DELAY` (1.0) | Optional: the workflows fall back to the defaults in brackets (`docs/UPDATE_SYSTEM.md` there). Set one only to tune a run |
| Core, FAS, ART, ThermalTools, Docs, Graphics-Platform, Intelligence-Engine, License-Server, Modules, Release, Sdk | none | Their workflows use only `GITHUB_TOKEN` (`grep secrets. vars.` over `.github/workflows`, 2026-10-21) |

The CI GitHub App is not a value in those repositories, but it must be **installed** on five of
them: Zairenkai-Core, Zairenkai-FAS, Zairenkai-ART, Zairenkai-ThermalTools and
Zairenkai-Database-Source (`docs/build/ci.md`).

## Installing (maintainer)

`integration/scripts/setup-actions-secrets.sh` (`help` lists each subcommand's prerequisites)
checks that `gh` is logged in with admin access to the repository and, for environment secrets,
that `release` exists. It asks before every change on GitHub and replaces an existing value only
after you type `overwrite`. Keys are read from files, passwords from silent prompts, and both
reach `gh` on stdin only: nothing is printed or put on a command line. Tests:
`integration/scripts/tests/test_setup_actions_secrets.py` (a fake `gh`, no real keys).

In Termux or a Linux shell, from the Zairenkai-App checkout:

1. `gh auth login` (an account with admin on the repository), then
   `integration/scripts/setup-actions-secrets.sh environment`: deployments only from `dev`.
2. Create the CI GitHub App in the browser (`docs/build/ci.md`, steps 1 and 2: Contents
   read-only, installed on the five repositories only) and click **Generate a private key**.
   Then `integration/scripts/setup-actions-secrets.sh app <App ID> <downloaded .pem>`. Before
   storing anything it authenticates as the App with that key, and refuses if the key belongs to
   another App, if the App has any permission besides Contents: read, or if it is installed on
   other repositories than the five. A locally made OpenSSL key cannot work: only GitHub issues
   App keys. Delete the downloaded PEM afterwards.
3. `integration/scripts/setup-actions-secrets.sh module-signing`: creates
   `~/.zairenkai-keys/module-signing.pem` (P-256, directory 700, file 600; an existing key is
   reused), adds only its public half to `integration/module-trust.json`, and stores the private
   key as `ZAIRENKAI_MODULE_SIGNING_KEY`. Commit the trust list in a reviewed pull request and
   merge it to `dev`: `release.yml` refuses a key that is not in the trust list of the commit it
   builds.
4. `integration/scripts/setup-actions-secrets.sh keystore`: asks for the alias, the certificate
   name and a password (empty: a random one in `~/.zairenkai-keys/release-keystore.password`),
   creates `~/.zairenkai-keys/release.jks` (PKCS12, RSA 4096, 30 years; key password = keystore
   password), prints its public SHA-256 fingerprint and stores the four keystore secrets.
5. `integration/scripts/setup-actions-secrets.sh status`: each name `ok` or `MISSING`, the
   branch policy, the trust list and whether the local module key is in it. Exits 1 while
   anything is missing.
6. Back up `~/.zairenkai-keys` offline. Losing `release.jks` or its password means no update of
   the app can ever be signed again.

An agent must not run steps 2 to 4: the keys must never pass through a chat session or its logs.
