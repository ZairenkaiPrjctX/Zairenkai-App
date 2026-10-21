#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
# Print the module-trust.json entry for a P-256 public key (PEM). Review it, then add it to
# integration/module-trust.json in a pull request. Only the public key is read.
#
# Usage: module-trust-key.sh <public key PEM> <owner> <YYYY-MM-DD>
set -euo pipefail
PUB=${1:?usage: module-trust-key.sh <public key PEM> <owner> <date>}
OWNER=${2:?owner}
DATE=${3:?date}
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
openssl pkey -pubin -in "$PUB" -outform DER -out "$TMP/pub.der"
openssl pkey -pubin -inform DER -in "$TMP/pub.der" -text -noout | grep -q 'prime256v1\|P-256' ||
  { echo "not a P-256 key" >&2; exit 1; }
python3 - "$TMP/pub.der" "$OWNER" "$DATE" <<'PY'
import base64, hashlib, json, sys
der = open(sys.argv[1], "rb").read()
print(json.dumps({"key_id": hashlib.sha256(der).hexdigest(), "public_key": base64.b64encode(der).decode(),
                  "owner": sys.argv[2], "added": sys.argv[3]}, indent=2))
PY
