#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
# Set up the GitHub Actions configuration that ci.yml and release.yml read
# (docs/build/secrets-and-variables.md). Run by the maintainer in their own terminal, with gh
# logged in as a repository admin. Keys and passwords are read from files or silent prompts and
# reach gh on stdin only: they are never printed, put on a command line or written to the
# repository. Every change on GitHub is confirmed first; an existing value is replaced only after
# typing "overwrite". Run "setup-actions-secrets.sh help" for each subcommand's prerequisites.
set -euo pipefail
REPO=${ZAIRENKAI_APP_REPO:-ZairenkaiPrjctX/Zairenkai-App}
ORG=${REPO%%/*}
ENV_NAME=release
HERE=$(cd "$(dirname "$0")" && pwd)
TRUST=${ZAIRENKAI_MODULE_TRUST:-$HERE/../module-trust.json}
KEYS=${ZAIRENKAI_KEYS_DIR:-$HOME/.zairenkai-keys}
MODULE_KEY="$KEYS/module-signing.pem"
KEYSTORE="$KEYS/release.jks"
APP_REPOS="Zairenkai-ART Zairenkai-Core Zairenkai-Database-Source Zairenkai-FAS Zairenkai-Modules Zairenkai-ThermalTools"
ENV_SECRETS="ZAIRENKAI_MODULE_SIGNING_KEY ZAIRENKAI_RELEASE_KEYSTORE_BASE64 ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD ZAIRENKAI_RELEASE_KEY_ALIAS ZAIRENKAI_RELEASE_KEY_PASSWORD"

die() { echo "error: $*" >&2; exit 1; }

usage() {
  cat <<EOF
Usage: setup-actions-secrets.sh <subcommand> [arguments]

Target repository: $REPO (override with ZAIRENKAI_APP_REPO). Every subcommand except help needs
gh logged in (gh auth login) with admin access to it.

  status
      Lists each variable and secret as ok or MISSING (names only, never values), the release
      environment and its branch policy, the trust list and the local key directory.
      Exits 1 while anything is missing.

  environment
      Creates the "$ENV_NAME" environment and limits deployments to the dev branch.

  app <App ID> <private key PEM>
      Prerequisite: a GitHub App owned by $ORG with only Contents: read-only, installed on
      only $APP_REPOS, and a private key generated on the App's settings page ("Generate a
      private key"). The App ID is the number on that page, not the Client ID (Iv...).
      Checks that the key authenticates as that App and that its permissions and installation
      match, then sets variable ZAIRENKAI_CI_APP_ID and secret ZAIRENKAI_CI_APP_PRIVATE_KEY at
      repository level (ci.yml reads them outside any environment).

  module-signing [owner]
      Creates an ECDSA P-256 key in $KEYS (mode 700; reuses an existing key), adds only its
      public half to integration/module-trust.json, then sets ZAIRENKAI_MODULE_SIGNING_KEY in
      the "$ENV_NAME" environment. Commit the trust list change in a reviewed pull request:
      release.yml refuses the key until it is merged. Needs openssl and python3.

  keystore
      Creates the Android release keystore $KEYSTORE (PKCS12, RSA 4096, 30 years) after asking
      for the alias, the certificate name and the password (empty: a random one, saved in
      $KEYS/release-keystore.password), or reuses an existing one after checking its
      password. Then sets ZAIRENKAI_RELEASE_KEYSTORE_BASE64, _KEYSTORE_PASSWORD, _KEY_ALIAS and
      _KEY_PASSWORD in the "$ENV_NAME" environment. Needs keytool (JDK 17+) and base64.

  help
      This text.

Back up $KEYS offline. Losing release.jks or its password means the app can never be updated.
EOF
}

confirm() {
  local answer
  read -rp "$1 [y/N] " answer || true
  [[ $answer =~ ^([yY]|[yY][eE][sS])$ ]] || die "cancelled; nothing was changed on GitHub"
}

require_gh() {
  command -v gh >/dev/null || die "gh is required (pkg install gh / apt install gh)"
  gh auth status >/dev/null 2>&1 || die "gh is not logged in: run gh auth login"
  [ "$(gh api "repos/$REPO" --jq .permissions.admin 2>/dev/null)" = true ] ||
    die "the gh account has no admin access to $REPO (or the repository does not exist)"
}

env_exists() { gh api "repos/$REPO/environments/$ENV_NAME" --silent 2>/dev/null; }

require_env() {
  env_exists || die "environment $ENV_NAME does not exist: run setup-actions-secrets.sh environment first"
}

names() { # names <secret|variable> [--env <name>]
  gh "$1" list -R "$REPO" "${@:2}" --json name -q '.[].name'
}

# guard <kind> <name> [--env <name>]: ask before replacing a value that already exists.
guard() {
  local answer
  names "$1" "${@:3}" | grep -qx "$2" || return 0
  read -rp "$1 $2 already exists. Type 'overwrite' to replace it: " answer || true
  [ "$answer" = overwrite ] || die "kept the existing $2; nothing more was changed"
}

key_id_of() { openssl pkey -in "$1" -pubout -outform DER | sha256sum | cut -d' ' -f1; }

trusted() { # trusted <key_id>
  python3 - "$TRUST" "$1" <<'PY'
import json, sys
sys.exit(0 if any(k.get("key_id") == sys.argv[2] for k in json.load(open(sys.argv[1]))["keys"]) else 1)
PY
}

keys_dir() {
  umask 077
  mkdir -p "$KEYS"
  chmod 700 "$KEYS"
}

status() {
  require_gh
  local missing=0 repo_vars repo_secrets env_secrets="" name policy
  check() { # check <label> <list> <name>
    if grep -qx "$3" <<<"$2"; then echo "  ok       $1 $3"; else echo "  MISSING  $1 $3"; missing=1; fi
  }
  repo_vars=$(names variable)
  repo_secrets=$(names secret)
  echo "repository $REPO"
  if env_exists; then
    env_secrets=$(names secret --env "$ENV_NAME")
    policy=""
    # Without a custom policy the list endpoint answers 404, and gh prints that body on stdout.
    if [ "$(gh api "repos/$REPO/environments/$ENV_NAME" --jq '.deployment_branch_policy.custom_branch_policies // false')" = true ]; then
      policy=$(gh api "repos/$REPO/environments/$ENV_NAME/deployment-branch-policies" \
        -q '[.branch_policies[].name] | join(",")' 2>/dev/null) || policy=""
    fi
    if [ -n "$policy" ]; then
      echo "  ok       environment $ENV_NAME (deploys from: $policy)"
    else
      echo "  MISSING  environment $ENV_NAME branch policy (any branch can deploy)"
      missing=1
    fi
  else
    echo "  MISSING  environment $ENV_NAME"
    missing=1
  fi
  check "repo variable" "$repo_vars" ZAIRENKAI_CI_APP_ID
  check "repo secret  " "$repo_secrets" ZAIRENKAI_CI_APP_PRIVATE_KEY
  for name in $ENV_SECRETS; do
    check "env secret   " "$env_secrets" "$name"
  done
  echo "trust list: $(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["keys"]))' "$TRUST") key(s) in $TRUST"
  if [ -d "$KEYS" ]; then
    echo "local keys: $KEYS (mode $(stat -c %a "$KEYS"))"
    if [ -f "$MODULE_KEY" ]; then
      if trusted "$(key_id_of "$MODULE_KEY")"; then
        echo "  ok       module-signing.pem is in the trust list"
      else
        echo "  MISSING  module-signing.pem is not in the trust list"
        missing=1
      fi
    fi
    [ -f "$KEYSTORE" ] && echo "  present  release.jks"
  else
    echo "local keys: none in $KEYS"
  fi
  return "$missing"
}

environment() {
  require_gh
  confirm "Create or update environment $ENV_NAME on $REPO, deployable only from dev?"
  # Required reviewers are refused for this private repository on the organization's plan
  # (HTTP 422, 2026-10-21), so the environment is limited to the dev branch instead.
  printf '%s' '{"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}' |
    gh api -X PUT "repos/$REPO/environments/$ENV_NAME" --input - --silent
  if ! gh api "repos/$REPO/environments/$ENV_NAME/deployment-branch-policies" \
    -q '.branch_policies[].name' 2>/dev/null | grep -qx dev; then
    gh api -X POST "repos/$REPO/environments/$ENV_NAME/deployment-branch-policies" \
      -f name=dev -f type=branch --silent
  fi
  echo "environment $ENV_NAME: deployments limited to dev"
}

# Authenticate as the App with a JWT signed by the key, then read its permissions and its
# installation's repositories with a short-lived installation token, revoked at the end. Neither
# the JWT nor the token is printed.
check_app() { # check_app <App ID> <PEM>
  python3 - "$1" "$2" "$ORG" "$APP_REPOS" <<'PY'
import base64, json, subprocess, sys, time, urllib.error, urllib.request
app_id, pem, org, wanted = sys.argv[1], sys.argv[2], sys.argv[3], set(sys.argv[4].split())
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
now = int(time.time())
unsigned = b64(b'{"alg":"RS256","typ":"JWT"}') + "." + b64(json.dumps({"iat": now - 60, "exp": now + 540, "iss": app_id}).encode())
sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", pem], input=unsigned.encode(), capture_output=True, check=True).stdout
jwt = unsigned + "." + b64(sig)

def call(path, token, method="GET", scheme="Bearer"):
    req = urllib.request.Request("https://api.github.com" + path, method=method, headers={
        "Authorization": f"{scheme} {token}", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            body = r.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as e:
        sys.exit(f"GitHub refused {method} {path.split('?')[0]}: HTTP {e.code}"
                 + (" (the key does not belong to App ID " + app_id + ", or the App ID is wrong)" if path == "/app" else ""))

app = call("/app", jwt)
problems = []
if app["owner"]["login"] != org:
    problems.append(f"the App is owned by {app['owner']['login']}, not {org}")
perms = {k: v for k, v in app["permissions"].items() if k != "metadata"}
if perms != {"contents": "read"}:
    problems.append(f"the App's permissions are {perms}; only contents: read is allowed")
inst = next((i for i in call("/app/installations", jwt) if i["account"]["login"] == org), None)
if inst is None:
    problems.append(f"the App is not installed on {org}")
else:
    if inst["repository_selection"] != "selected":
        problems.append("the App is installed on all repositories; choose 'Only select repositories'")
    token = call(f"/app/installations/{inst['id']}/access_tokens", jwt, method="POST")["token"]
    try:
        repos = {r["name"] for r in call("/installation/repositories?per_page=100", token)["repositories"]}
    finally:
        call("/installation/token", token, method="DELETE")
    if repos != wanted:
        problems.append(f"the App is installed on {sorted(repos)}; it must be exactly {sorted(wanted)}")
print(f"App {app['slug']} (ID {app_id}), owner {app['owner']['login']}, permissions {app['permissions']}")
if problems:
    sys.exit("refusing to store it:\n  - " + "\n  - ".join(problems))
PY
}

app() {
  local app_id=${1:-} pem=${2:-}
  if [ -z "$app_id" ] || [ -z "$pem" ]; then
    die "usage: setup-actions-secrets.sh app <App ID> <private key PEM>"
  fi
  [[ $app_id == Iv* ]] && die "$app_id is the Client ID; use the numeric App ID from the App's settings page"
  [[ $app_id =~ ^[0-9]+$ ]] || die "the App ID is a number (the App's settings page, 'App ID')"
  [ -r "$pem" ] || die "cannot read $pem"
  openssl rsa -in "$pem" -check -noout >/dev/null 2>&1 ||
    die "$pem is not an RSA private key. Use the .pem downloaded from the App's settings page ('Generate a private key'); a locally made OpenSSL key does not belong to the App"
  require_gh
  check_app "$app_id" "$pem"
  confirm "Set repository variable ZAIRENKAI_CI_APP_ID and secret ZAIRENKAI_CI_APP_PRIVATE_KEY on $REPO?"
  guard variable ZAIRENKAI_CI_APP_ID
  guard secret ZAIRENKAI_CI_APP_PRIVATE_KEY
  gh variable set ZAIRENKAI_CI_APP_ID -R "$REPO" --body "$app_id"
  gh secret set ZAIRENKAI_CI_APP_PRIVATE_KEY -R "$REPO" <"$pem"
  echo "set ZAIRENKAI_CI_APP_ID and ZAIRENKAI_CI_APP_PRIVATE_KEY. Delete the downloaded PEM: GitHub keeps no copy, and you can generate a new key on the App's page."
}

module_signing() {
  local owner=${1:-} key_id entry
  command -v openssl >/dev/null || die "openssl is required"
  require_gh
  require_env
  owner=${owner:-$(gh api user --jq .login)}
  keys_dir
  if [ -f "$MODULE_KEY" ]; then
    echo "reusing the existing $MODULE_KEY"
  else
    openssl ecparam -name prime256v1 -genkey -noout -out "$MODULE_KEY"
    echo "created $MODULE_KEY"
  fi
  chmod 600 "$MODULE_KEY"
  openssl pkey -in "$MODULE_KEY" -pubout -out "$KEYS/module-signing.pub.pem"
  key_id=$(key_id_of "$MODULE_KEY")
  if trusted "$key_id"; then
    echo "key $key_id is already in $TRUST"
  else
    entry=$("$HERE/module-trust-key.sh" "$KEYS/module-signing.pub.pem" "$owner" "$(date +%F)")
    python3 - "$TRUST" "$entry" <<'PY'
import json, sys
path, entry = sys.argv[1], json.loads(sys.argv[2])
trust = json.load(open(path))
trust["keys"].append(entry)
trust["note"] = trust["note"].split(" Empty:")[0]
open(path, "w").write(json.dumps(trust, indent=2) + "\n")
PY
    echo "added the public key $key_id to $TRUST: commit it in a reviewed pull request"
  fi
  confirm "Set ZAIRENKAI_MODULE_SIGNING_KEY (key $key_id) in environment $ENV_NAME of $REPO?"
  guard secret ZAIRENKAI_MODULE_SIGNING_KEY --env "$ENV_NAME"
  gh secret set ZAIRENKAI_MODULE_SIGNING_KEY -R "$REPO" --env "$ENV_NAME" <"$MODULE_KEY"
  echo "set ZAIRENKAI_MODULE_SIGNING_KEY in $ENV_NAME"
}

keystore() {
  local alias cn again pass_file="$KEYS/release-keystore.password" name
  command -v keytool >/dev/null || die "keytool is required (a JDK 17 or newer)"
  require_gh
  require_env
  keys_dir
  read -rp "key alias [zairenkai-release]: " alias || true
  alias=${alias:-zairenkai-release}
  [[ $alias =~ ^[A-Za-z0-9._-]+$ ]] || die "the alias may use only letters, digits, . _ -"
  if [ -f "$KEYSTORE" ]; then
    echo "reusing the existing $KEYSTORE"
    if [ -f "$pass_file" ]; then ZK_PASS=$(<"$pass_file"); else read -rsp "keystore password: " ZK_PASS || true; echo; fi
  else
    read -rp "certificate name (CN) [ZairenkaiPrjctX]: " cn || true
    cn=${cn:-ZairenkaiPrjctX}
    [[ $cn =~ ^[A-Za-z0-9\ ._-]+$ ]] || die "the name may use only letters, digits, spaces, . _ -"
    read -rsp "keystore password (12+ characters; empty: generate one): " ZK_PASS || true; echo
    if [ -z "$ZK_PASS" ]; then
      ZK_PASS=$(openssl rand -base64 33 | tr -d '\n')
      (umask 077; printf '%s' "$ZK_PASS" >"$pass_file")
      echo "generated a password and saved it in $pass_file"
    else
      [ ${#ZK_PASS} -ge 12 ] || die "the password must have at least 12 characters"
      read -rsp "repeat the password: " again || true; echo
      [ "$ZK_PASS" = "$again" ] || die "the passwords differ"
      unset again
    fi
    export ZK_PASS
    # PKCS12 keeps a single password: the key password equals the keystore password.
    keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass:env ZK_PASS \
      -keypass:env ZK_PASS -alias "$alias" -keyalg RSA -keysize 4096 -validity 10950 \
      -dname "CN=$cn, O=ZairenkaiPrjctX" >/dev/null
    chmod 600 "$KEYSTORE"
    echo "created $KEYSTORE"
  fi
  export ZK_PASS
  keytool -list -keystore "$KEYSTORE" -storepass:env ZK_PASS -alias "$alias" >/dev/null 2>&1 ||
    die "keytool cannot open alias $alias in $KEYSTORE with that password"
  echo "certificate $(keytool -list -v -keystore "$KEYSTORE" -storepass:env ZK_PASS -alias "$alias" | grep -m1 'SHA256:' | sed 's/^[[:space:]]*//')"
  confirm "Set the four ZAIRENKAI_RELEASE_* secrets in environment $ENV_NAME of $REPO?"
  for name in ZAIRENKAI_RELEASE_KEYSTORE_BASE64 ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD ZAIRENKAI_RELEASE_KEY_ALIAS ZAIRENKAI_RELEASE_KEY_PASSWORD; do
    guard secret "$name" --env "$ENV_NAME"
  done
  base64 -w0 <"$KEYSTORE" | gh secret set ZAIRENKAI_RELEASE_KEYSTORE_BASE64 -R "$REPO" --env "$ENV_NAME"
  printf '%s' "$ZK_PASS" | gh secret set ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD -R "$REPO" --env "$ENV_NAME"
  printf '%s' "$alias" | gh secret set ZAIRENKAI_RELEASE_KEY_ALIAS -R "$REPO" --env "$ENV_NAME"
  printf '%s' "$ZK_PASS" | gh secret set ZAIRENKAI_RELEASE_KEY_PASSWORD -R "$REPO" --env "$ENV_NAME"
  unset ZK_PASS
  echo "set the keystore secrets in $ENV_NAME. Back up $KEYS offline."
}

case "${1:-help}" in
  status) status ;;
  environment) environment ;;
  app) app "${@:2}" ;;
  module-signing) module_signing "${@:2}" ;;
  keystore) keystore ;;
  help | -h | --help) usage ;;
  *) usage >&2; exit 2 ;;
esac
