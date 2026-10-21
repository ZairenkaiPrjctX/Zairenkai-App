#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
# Install the GitHub Actions secrets, variables and the "release" environment that ci.yml and
# release.yml need (docs/build/secrets-and-variables.md). Run by the maintainer, with gh logged in
# as a repository admin. Key material is read from files or prompts and piped to gh on stdin: it
# is never printed, passed as an argument or written anywhere.
#
# Usage:
#   setup-actions-secrets.sh status
#   setup-actions-secrets.sh environment
#   setup-actions-secrets.sh ci-app <numeric App ID> <App private key PEM>
#   setup-actions-secrets.sh module-key <module-signing private key PEM>
#   setup-actions-secrets.sh keystore <release keystore> <key alias>
set -euo pipefail
REPO=${ZAIRENKAI_APP_REPO:-ZairenkaiPrjctX/Zairenkai-App}
ENV_NAME=release
HERE=$(cd "$(dirname "$0")" && pwd)
TRUST="$HERE/../module-trust.json"

die() { echo "error: $*" >&2; exit 1; }

names() { # names <secret|variable> [--env release]
  gh "$1" list -R "$REPO" "${@:2}" --json name -q '.[].name'
}

status() {
  local repo_vars repo_secrets env_secrets missing=0 name
  repo_vars=$(names variable)
  repo_secrets=$(names secret)
  if gh api "repos/$REPO/environments/$ENV_NAME" --silent 2>/dev/null; then
    echo "environment $ENV_NAME: present"
    env_secrets=$(names secret --env "$ENV_NAME")
  else
    echo "environment $ENV_NAME: MISSING"
    env_secrets=""
    missing=1
  fi
  check() { # check <label> <list> <name>
    if grep -qx "$3" <<<"$2"; then echo "  ok       $1 $3"; else echo "  MISSING  $1 $3"; missing=1; fi
  }
  check "repo variable" "$repo_vars" ZAIRENKAI_CI_APP_ID
  check "repo secret  " "$repo_secrets" ZAIRENKAI_CI_APP_PRIVATE_KEY
  for name in ZAIRENKAI_MODULE_SIGNING_KEY ZAIRENKAI_RELEASE_KEYSTORE_BASE64 \
    ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD ZAIRENKAI_RELEASE_KEY_ALIAS ZAIRENKAI_RELEASE_KEY_PASSWORD; do
    check "env secret   " "$env_secrets" "$name"
  done
  return "$missing"
}

environment() {
  # Required reviewers are not offered for private repositories on the organization's plan
  # (HTTP 422, 2026-10-21), so the environment is limited to the dev branch instead.
  printf '%s' '{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}' |
    gh api -X PUT "repos/$REPO/environments/$ENV_NAME" --input - --silent
  if ! gh api "repos/$REPO/environments/$ENV_NAME/deployment-branch-policies" \
    -q '.branch_policies[].name' | grep -qx dev; then
    gh api -X POST "repos/$REPO/environments/$ENV_NAME/deployment-branch-policies" \
      -f name=dev -f type=branch --silent
  fi
  echo "environment $ENV_NAME: deployments limited to dev"
}

ci_app() {
  local app_id=${1:?usage: ci-app <numeric App ID> <private key PEM>} pem=${2:?private key PEM}
  [[ $app_id =~ ^[0-9]+$ ]] || die "the App ID is the number on the App's settings page, not the Client ID (Iv...)"
  [ -r "$pem" ] || die "cannot read $pem"
  grep -q -- '-----BEGIN \(RSA \)\?PRIVATE KEY-----' "$pem" || die "$pem is not a PEM private key"
  gh variable set ZAIRENKAI_CI_APP_ID -R "$REPO" --body "$app_id"
  gh secret set ZAIRENKAI_CI_APP_PRIVATE_KEY -R "$REPO" <"$pem"
  echo "set ZAIRENKAI_CI_APP_ID and ZAIRENKAI_CI_APP_PRIVATE_KEY. Delete the downloaded PEM now."
}

module_key() {
  local pem=${1:?usage: module-key <private key PEM>} key_id
  [ -r "$pem" ] || die "cannot read $pem"
  key_id=$(openssl pkey -in "$pem" -pubout -outform DER | sha256sum | cut -d' ' -f1)
  # Same rule as sign-module-catalog.sh: only a key whose public half is reviewed in Git.
  python3 - "$TRUST" "$key_id" <<'PY' || die "key $key_id is not in integration/module-trust.json; add its public half in a reviewed pull request first (docs/build/release.md)"
import json, sys
sys.exit(0 if any(k.get("key_id") == sys.argv[2] for k in json.load(open(sys.argv[1]))["keys"]) else 1)
PY
  gh secret set ZAIRENKAI_MODULE_SIGNING_KEY -R "$REPO" --env "$ENV_NAME" <"$pem"
  echo "set ZAIRENKAI_MODULE_SIGNING_KEY (key_id $key_id) in $ENV_NAME"
}

keystore() {
  local jks=${1:?usage: keystore <keystore> <alias>} alias=${2:?key alias}
  [ -r "$jks" ] || die "cannot read $jks"
  read -rsp "keystore password: " ZK_STORE_PASS; echo
  read -rsp "key password (empty: same as keystore): " ZK_KEY_PASS; echo
  ZK_KEY_PASS=${ZK_KEY_PASS:-$ZK_STORE_PASS}
  export ZK_STORE_PASS ZK_KEY_PASS
  keytool -list -keystore "$jks" -storepass:env ZK_STORE_PASS -alias "$alias" >/dev/null ||
    die "keytool cannot open alias $alias with that password"
  base64 -w0 <"$jks" | gh secret set ZAIRENKAI_RELEASE_KEYSTORE_BASE64 -R "$REPO" --env "$ENV_NAME"
  printf '%s' "$ZK_STORE_PASS" | gh secret set ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD -R "$REPO" --env "$ENV_NAME"
  printf '%s' "$alias" | gh secret set ZAIRENKAI_RELEASE_KEY_ALIAS -R "$REPO" --env "$ENV_NAME"
  printf '%s' "$ZK_KEY_PASS" | gh secret set ZAIRENKAI_RELEASE_KEY_PASSWORD -R "$REPO" --env "$ENV_NAME"
  unset ZK_STORE_PASS ZK_KEY_PASS
  echo "set the four ZAIRENKAI_RELEASE_KEYSTORE_* / KEY_* secrets in $ENV_NAME"
}

command -v gh >/dev/null || die "gh is required"
case "${1:-}" in
  status) status ;;
  environment) environment ;;
  ci-app) ci_app "${@:2}" ;;
  module-key) module_key "${@:2}" ;;
  keystore) keystore "${@:2}" ;;
  *) sed -n '9,14p' "$0" >&2; exit 2 ;;
esac
