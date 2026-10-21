# GitHub Actions secrets and variables

Every secret, variable and environment the Zairenkai workflows read, where each one lives, and
how to install it. Values are never written here or anywhere in Git; only names.

**Status, 2026-10-21:** nothing in the table below is installed. Zairenkai-App has no secrets
and no variables, and no CI GitHub App exists in the organization (`gh secret list`,
`gh variable list`, `GET /orgs/ZairenkaiPrjctX/installations`). The `release` environment
exists (created 2026-10-21T16:10Z) but has **no protection rule and no branch policy yet**: run
step 1 below (`setup-actions-secrets.sh environment`). Every value needs a key that only the maintainer can
create, so `full-integration` and `release.yml` keep failing until they are set.

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

`integration/scripts/setup-actions-secrets.sh` installs each group. It needs `gh` logged in as
a repository admin, reads keys from files and passwords from silent prompts, and passes them to
`gh` on stdin, so no value is printed, put on a command line or written to disk.

1. Create the environment:
   `integration/scripts/setup-actions-secrets.sh environment`
2. Create the CI GitHub App (`docs/build/ci.md`, steps 1 and 2: Contents read-only, installed on
   the five repositories only), generate a private key, then:
   `integration/scripts/setup-actions-secrets.sh ci-app <App ID> <downloaded .pem>`
   and delete the downloaded PEM.
3. Create the module-signing key offline and merge its public half into
   `integration/module-trust.json` (`docs/build/release.md`). Then:
   `integration/scripts/setup-actions-secrets.sh module-key <module-signing.pem>`
   The script refuses a key whose `key_id` is not in the trust list, like
   `sign-module-catalog.sh`.
4. Create the release keystore outside the repository (`docs/build/release.md`), then:
   `integration/scripts/setup-actions-secrets.sh keystore <release.jks> <alias>`
   It checks the alias and password with `keytool` before storing anything.
5. Check: `integration/scripts/setup-actions-secrets.sh status` lists each name as `ok` or
   `MISSING` and exits non-zero while anything is missing.

An agent must not run steps 2 to 4: the keys must never pass through a chat session or its logs.
